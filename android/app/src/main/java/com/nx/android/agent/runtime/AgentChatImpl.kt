package com.nx.android.agent.runtime

import com.nx.android.agent.runtime.AgentWorkspace
import com.nx.android.agent.runtime.AgentSessionStore
import com.nx.android.agent.tools.AgentTools
import com.nx.android.agent.tools.MemoryTools
import com.nx.android.agent.tools.UiTools
import com.nx.android.agent.AgentChat
import com.nx.android.agent.AgentEvent
import com.nx.android.agent.AgentFailure
import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentModelConfig
import com.nx.android.agent.AgentPageContext
import com.nx.android.agent.AgentRole
import com.nx.android.agent.AgentToolCard
import com.nx.android.agent.AgentToolState
import io.agentscope.core.ReActAgent
import io.agentscope.core.event.AgentEvent as FrameworkEvent
import io.agentscope.core.event.TextBlockDeltaEvent
import io.agentscope.core.event.ToolCallStartEvent
import io.agentscope.core.event.ToolResultEndEvent
import io.agentscope.core.event.ToolResultTextDeltaEvent
import io.agentscope.core.message.AssistantMessage
import io.agentscope.core.message.UserMessage
import io.agentscope.core.model.transport.HttpTransportConfig
import io.agentscope.core.model.transport.OkHttpTransport
import io.agentscope.core.tool.Toolkit
import io.agentscope.extensions.model.openai.OpenAIChatModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
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
    private val uiTools: UiTools,
    private val memoryTools: MemoryTools,
    private val workspace: AgentWorkspace,
    private val sessionStore: AgentSessionStore,
) : AgentChat {

    private companion object {
        const val MAX_ITERS = 12
        /** The tool-result summaries shown on the cards stay short; the model sees the full text. */
        const val RESULT_SUMMARY_CHARS = 160

        /**
         * The page context rides into the model inside the user's turn; the block is capped by the
         * file-level CONTEXT_CHARS budget (35-页面上下文.md: ≤400 tokens).
         */

        val SYSTEM_PROMPT = """
            你是 nx2androidX（一个把 Switch 游戏移植到 Android 的应用）的内置助手，用简体中文回答。
            你能做：检索应用内置的 FAQ（kb_search）、查看游戏库状态（library_status）、查看最近一次构建的日志尾部（build_log_tail）、
            以及用 navigate 把玩家带到某个页面（游戏库/构建/设置，可选带上一个游戏直接选中）。
            玩家的消息里可能带「[页面上下文]」块：那是玩家此刻正在看的页面状态，回答优先结合它；它不是玩家说的话，不要复述它。
            你不能做：读取或输出 prod.keys 的内容；读取游戏文件内容；修改任何设置或文件——你没有任何写工具，不要假装有，也不要建议玩家手动改文件。
            纪律：结论落在事实上——先用工具查，查不到就说查不到，不要猜；回答简洁，直接回答玩家的问题。
        """.trimIndent()
    }

    private var agent: ReActAgent? = null
    private var builtFor: String? = null
    private var nextId: Long = 0
    private var list: MutableList<AgentMessage> = mutableListOf()

    init {
        workspace.ensure()
        list = sessionStore.load().toMutableList()
        nextId = (list.maxOfOrNull { it.id } ?: -1L) + 1L
    }

    override val messages: List<AgentMessage> get() = list

    override fun reset() {
        agent = null
        builtFor = null
        list = mutableListOf()
        sessionStore.clear()
    }

    override suspend fun send(userText: String, context: AgentPageContext?): Flow<AgentEvent> = flow {
        val config = settings.config
        if (!config.configured) {
            emit(AgentEvent.Failed(AgentFailure.NO_KEY))
            return@flow
        }

        // The user's message joins the history at once (even if the turn fails, the question was asked);
        // the history keeps the bare words — the page context rides into the model message only
        list.add(AgentMessage(nextId++, AgentRole.USER, userText))
        val composed = composeTurn(userText, workspace.readMemory().take(AgentWorkspace.MEMORY_INJECT_CHARS), context)
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
                .streamEvents(listOf(UserMessage(composed)))
                .asFlow()
                .collect { event -> mapEvent(event, text, cards, resultBuffers)?.let { emit(it) } }
        } catch (e: CancellationException) {
            throw e   // the stop button: the turn is dropped, the history keeps the question
        } catch (e: Exception) {
            emit(AgentEvent.Failed(classify(e)))
            return@flow
        }

        finalize(text, cards)
        sessionStore.save(list)
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

    private fun buildAgent(config: AgentModelConfig): ReActAgent {
        // The transport must be handed over explicitly: the factory default picks the JDK's
        // java.net.http.HttpClient, which Android does not ship — the first real request would
        // crash with ClassNotFoundException (found on device). OkHttp is native here and already
        // on the classpath.
        val transport = OkHttpTransport.builder()
            .config(HttpTransportConfig.builder().build())
            .build()
        val model = OpenAIChatModel.builder()
            .apiKey(config.apiKey)
            .baseUrl(config.baseUrl)
            .modelName(config.modelName)
            .stream(true)
            .httpTransport(transport)
            .build()
        val toolkit = Toolkit()
        toolkit.registerTool(tools)
        toolkit.registerTool(uiTools)
        toolkit.registerTool(memoryTools)
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

/** The page context's budget: ≤400 tokens ≈ 800 chars of CJK (35-页面上下文.md). */
internal const val CONTEXT_CHARS = 800

/**
 * The page context as the model reads it: a bounded block prepended to the player's words. Pure so the
 * JVM tests can drive the budget. The player's own words always come last; over budget the tail facts
 * drop and the cut is announced. [memory] is the long-term memory (already truncated by the caller).
 */
internal fun composeTurn(userText: String, memory: String, context: AgentPageContext?): String {
    val hasMemory = memory.isNotBlank()
    val hasContext = context != null
    if (!hasMemory && !hasContext) return userText
    val sb = StringBuilder()
    if (hasMemory) sb.append("[助手记忆] ").append(memory.trim()).append('\n')
    if (hasContext) {
        val block = StringBuilder("[页面上下文] ")
        if (context.subject != null) {
            block.append("正在处理：${context.subject.title}（${context.subject.id}）。")
        }
        for ((k, v) in context.facts) block.append("$k：$v。")
        if (block.length > CONTEXT_CHARS) {
            block.setLength(CONTEXT_CHARS)
            block.append("…（上下文超预算，已截断）")
        }
        sb.append(block).append('\n')
    }
    return sb.append("玩家说：").append(userText).toString()
}
