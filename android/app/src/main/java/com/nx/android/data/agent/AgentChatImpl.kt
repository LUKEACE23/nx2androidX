package com.nx.android.data.agent

import com.nx.android.data.agent.tools.AgentTools
import com.nx.android.domain.agent.AgentChat
import com.nx.android.domain.agent.AgentEvent
import com.nx.android.domain.agent.AgentFailure
import com.nx.android.domain.agent.AgentMessage
import com.nx.android.domain.agent.AgentModelConfig
import com.nx.android.domain.agent.AgentRole
import com.nx.android.domain.agent.AgentToolCard
import com.nx.android.domain.agent.AgentToolState
import io.agentscope.core.ReActAgent
import io.agentscope.core.event.AgentEvent as FrameworkEvent
import io.agentscope.core.event.TextBlockDeltaEvent
import io.agentscope.core.event.ToolCallStartEvent
import io.agentscope.core.event.ToolResultEndEvent
import io.agentscope.core.event.ToolResultTextDeltaEvent
import io.agentscope.core.message.UserMessage
import io.agentscope.core.tool.Toolkit
import io.agentscope.extensions.model.openai.OpenAIChatModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow

/**
 * The assistant's runtime: one AgentScope [ReActAgent] per conversation, driven turn by turn.
 *
 * The agent is rebuilt when the model choice changes or the session is reset — the framework keeps the
 * conversation's context inside the agent, so a fresh conversation means a fresh agent. v0 tools are all
 * read-only (ADR-004's minimal landing): the permission system has nothing to gate, so it is not wired yet.
 */
@Singleton
class AgentChatImpl @Inject constructor(
    private val settings: AgentSettingsStore,
    private val tools: AgentTools,
) : AgentChat {

    private companion object {
        const val MAX_ITERS = 12

        /** The tool-result summaries shown on the cards stay short; the model sees the full text. */
        const val RESULT_SUMMARY_CHARS = 160

        val SYSTEM_PROMPT = """
            你是 nx2androidX（一个把 Switch 游戏移植到 Android 的应用）的内置助手，用简体中文回答。
            你能做：检索应用内置的 FAQ（kb_search）、查看游戏库状态（library_status）、查看最近一次构建的日志尾部（build_log_tail）。
            你不能做：读取或输出 prod.keys 的内容；读取游戏文件内容；修改任何设置或文件——你没有任何写工具，不要假装有，也不要建议玩家手动改文件。
            纪律：结论落在事实上——先用工具查，查不到就说查不到，不要猜；回答简洁，直接回答玩家的问题。
        """.trimIndent()
    }

    private var agent: ReActAgent? = null
    private var builtFor: String? = null
    private var nextId: Long = 0
    private var list: MutableList<AgentMessage> = mutableListOf()

    override val messages: List<AgentMessage> get() = list

    override fun reset() {
        agent = null
        builtFor = null
        list = mutableListOf()
    }

    override suspend fun send(userText: String): Flow<AgentEvent> = flow {
        val config = settings.config
        if (!config.configured) {
            emit(AgentEvent.Failed(AgentFailure.NO_KEY))
            return@flow
        }

        // The user's message joins the history at once (even if the turn fails, the question was asked)
        list.add(AgentMessage(nextId++, AgentRole.USER, userText))

        val fingerprint = "${config.baseUrl}|${config.modelName}|${config.apiKey.hashCode()}"
        val theAgent = agent?.takeIf { builtFor == fingerprint } ?: buildAgent(config).also {
            agent = it
            builtFor = fingerprint
        }

        val text = StringBuilder()
        val cards = LinkedHashMap<String, AgentToolCard>()
        val resultBuffers = HashMap<String, StringBuilder>()

        try {
            theAgent
                .streamEvents(listOf(UserMessage(userText)))
                .asFlow()
                .collect { event -> mapEvent(event, text, cards, resultBuffers)?.let { emit(it) } }
        } catch (e: CancellationException) {
            throw e   // the stop button: the turn is dropped, the history keeps the question
        } catch (e: Exception) {
            emit(AgentEvent.Failed(classify(e)))
            return@flow
        }

        finalize(text, cards)
        emit(AgentEvent.TurnDone(text.toString().trim()))
    }

    /**
     * Fold one framework event into the chat page's vocabulary, updating the accumulators.
     * Returns null for the events the page does not show (thinking blocks, call deltas, ...).
     */
    private fun mapEvent(
        event: FrameworkEvent,
        text: StringBuilder,
        cards: LinkedHashMap<String, AgentToolCard>,
        resultBuffers: HashMap<String, StringBuilder>,
    ): AgentEvent? = when (event) {
        is TextBlockDeltaEvent -> AgentEvent.TextDelta(event.delta)
        is ToolCallStartEvent -> {
            val card = AgentToolCard(event.toolCallId, event.toolCallName, "", AgentToolState.RUNNING)
            cards[event.toolCallId] = card
            AgentEvent.ToolStarted(card)
        }
        is ToolResultTextDeltaEvent -> {
            resultBuffers.getOrPut(event.toolCallId) { StringBuilder() }.append(event.delta)
            null
        }
        is ToolResultEndEvent -> {
            val summary = resultBuffers[event.toolCallId]?.toString()?.let(::shorten).orEmpty()
            cards[event.toolCallId]?.let { it.copy(state = AgentToolState.DONE, resultSummary = summary) }
            AgentEvent.ToolFinished(event.toolCallId, AgentToolState.DONE, summary)
        }
        else -> null
    }

    /** The turn is over: fold what accumulated into the history the next turn starts from. */
    private fun finalize(text: StringBuilder, cards: LinkedHashMap<String, AgentToolCard>) {
        list.add(
            AgentMessage(
                nextId++,
                AgentRole.ASSISTANT,
                text.toString().trim(),
                cards.values.toList(),
            ),
        )
    }

    private fun buildAgent(config: com.nx.android.domain.agent.AgentModelConfig): ReActAgent {
        val model = OpenAIChatModel.builder()
            .apiKey(config.apiKey)
            .baseUrl(config.baseUrl)
            .modelName(config.modelName)
            .stream(true)
            .build()
        val toolkit = Toolkit()
        toolkit.registerTool(tools)
        return ReActAgent.builder()
            .name("nx2a-assistant")
            .sysPrompt(SYSTEM_PROMPT)
            .model(model)
            .toolkit(toolkit)
            .maxIters(MAX_ITERS)
            .build()
    }

    private fun shorten(s: String): String =
        if (s.length <= RESULT_SUMMARY_CHARS) s
        else s.take(RESULT_SUMMARY_CHARS) + "…"

    /**
     * Failures are classified from what the model call threw: the chat page gives the words
     * (the data layer reports codes — my-docs/踩坑/01-界面语言.md §4).
     */
    internal fun classify(error: Throwable): AgentFailure = when {
        error is CancellationException -> throw error
        error.message?.contains("401") == true || error.message?.contains("403") == true -> AgentFailure.BAD_KEY
        error is java.io.IOException -> AgentFailure.NETWORK
        error.message?.contains("tool") == true && error.message?.contains("support") == true ->
            AgentFailure.MODEL_NO_TOOLS
        else -> AgentFailure.LOOP_ERROR
    }
}
