package com.nx.android.agent.ui

import com.nx.android.agent.AgentChat
import com.nx.android.agent.AgentConversation
import com.nx.android.agent.AgentEvent
import com.nx.android.agent.AgentFailure
import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentModelConfig
import com.nx.android.agent.AgentPageContext
import com.nx.android.agent.AgentTextBatch
import com.nx.android.agent.env.PiRunner
import com.nx.android.agent.runtime.AgentModelProfile
import com.nx.android.agent.runtime.AgentModelStore
import com.nx.android.agent.runtime.AgentSessionMeta
import com.nx.android.agent.runtime.AgentSessionStore
import com.nx.android.agent.runtime.AgentSettingsStore
import com.nx.android.core.di.ApplicationCoroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the chat surfaces render: the conversation, the running turn, the strip's failure, the model choice. */
data class AgentUiState(
    val messages: List<AgentMessage> = emptyList(),
    val running: Boolean = false,
    val error: AgentFailure? = null,
    val config: AgentModelConfig = AgentModelConfig("", "", "", ""),

    /** The capsule and the setup gate both read this: a blank key or endpoint means the assistant cannot run. */
    val configured: Boolean = false,

    /** The env agent's smoke test (ADR-009): its button's state and the turn's text. */
    val piRunning: Boolean = false,
    val piResult: String? = null,

    /** The words half-typed: the drawer closes and opens, the draft stays (it lives here, not in composition). */
    val draft: String = "",

    /** The session dropdown's list, and which one answers now. */
    val sessions: List<AgentSessionMeta> = emptyList(),
    val activeSessionId: String? = null,

    /** The session's context occupancy and its model's window (the tool column's usage row). */
    val usageTokens: Long = 0L,
    val usageWindow: Long = 0L,

    /** The models the player configured: the chip's menu lists them. */
    val profiles: List<AgentModelProfile> = emptyList(),
    val activeProfileId: String = "",

    /** The message the running turn writes into: it shows as plain text until the turn ends. */
    val streamingId: Long? = null,
)

/**
 * The assistant's conversation, app-wide: one copy of the messages, of the turn that runs and of
 * the words half-typed.
 *
 * The engine lives in the environment; the rendered history lives here, and it lives here for the
 * whole process — the home drawer, the portrait chat page and the game drawer all read this one
 * copy, so a conversation carries over from one window to the next. The turn runs on the
 * application's own scope: it goes on while the player closes the drawer, and the answer is
 * there when they open it again.
 */
@Singleton
class AgentConversationState @Inject constructor(
    private val chat: AgentChat,
    private val settings: AgentSettingsStore,
    private val sessionStore: AgentSessionStore,
    private val piRunner: PiRunner,
    private val models: AgentModelStore,
    @ApplicationCoroutineScope private val scope: CoroutineScope,
) {

    private val conversation = AgentConversation()
    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    /** markdown 的预解析缓存：条目进屏时高度一次就对，滚动不再等解析 */
    val markdownCache = AgentMarkdownCache(scope)

    /** 那一轮跑在哪：主线程上（和从前一样），窗口关着它也照跑 */
    private val turnScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // 这些数字与列表会被后台的那一轮改，也会被界面读：给它们 volatile，跨线程看得见
    @Volatile private var config = settings.config
    @Volatile private var profiles = models.list()
    @Volatile private var activeProfileId = models.activeId()
    @Volatile private var sessions = sessionStore.list()
    @Volatile private var activeSessionId = sessionStore.activeId()
    @Volatile private var usageTokens = 0L
    @Volatile private var usageWindow = 0L
    @Volatile private var draft = ""
    @Volatile private var piRunning = false
    @Volatile private var piResult: String? = null
    private var turn: Job? = null

    init {
        // a cold start reads the active session's last known occupancy from the store — the live
        // UsageUpdated events take over from the first turn (fix: the usage row vanished after restart)
        val meta = sessions.firstOrNull { it.id == activeSessionId }
        usageTokens = meta?.contextTokens ?: 0L
        usageWindow = meta?.contextWindow ?: 0L
        conversation.load(chat.messages)
        publish()
    }

    /**
     * The assistant message the running turn's events land in; the turn's first event makes it.
     */
    private fun publish() {
        _uiState.value = AgentUiState(
            messages = conversation.messages,
            running = conversation.running,
            error = conversation.failure,
            config = config,
            configured = config.configured,
            piRunning = piRunning,
            piResult = piResult,
            draft = draft,
            sessions = sessions,
            activeSessionId = activeSessionId,
            usageTokens = usageTokens,
            usageWindow = usageWindow,
            profiles = profiles,
            activeProfileId = activeProfileId,
            streamingId = conversation.streamingId,
        )
        // markdown 的 AST 在这里备：冷启动就开始解析，首开抽屉时高度已经是最终值。
        // 放在面板里备是不够的——面板冷启动时根本不组合，那儿备就等于没备：
        // 首开那刻才开始解析，落底会停在半路（甚至卡住不动）。
        markdownCache.ensureAll(conversation.messages, conversation.streamingId)
    }

    /**
     * The model chip's menu: the profile the player picks answers from now on. The key stays with
     * the profile — the chip never shows it.
     */
    fun selectProfile(id: String) {
        if (conversation.running) return
        models.setActive(id)
        refreshModels()
    }

    /** Reads the models again: the settings page may have changed them while the panel was closed. */
    fun refreshModels() {
        profiles = models.list()
        activeProfileId = models.activeId()
        config = settings.config
        publish()
    }

    /** Another conversation answers from here on: history reloads, the process restarts next turn. */
    fun switchSession(id: String) {
        if (conversation.running || id == activeSessionId) return
        chat.switchSession(id)
        conversation.load(chat.messages)
        sessions = sessionStore.list()
        activeSessionId = sessionStore.activeId()
        val meta = sessions.firstOrNull { it.id == id }
        usageTokens = meta?.contextTokens ?: 0L
        usageWindow = meta?.contextWindow ?: 0L
        conversation.dismissFailure()
        publish()
    }

    /** The menu's new-chat entry: a fresh store entry; the process restarts on the next turn. */
    fun newSession() {
        if (conversation.running) return
        chat.reset()
        conversation.load(chat.messages)
        sessions = sessionStore.list()
        activeSessionId = sessionStore.activeId()
        conversation.dismissFailure()
        usageTokens = 0L
        usageWindow = 0L
        publish()
    }

    /** The input box is controlled: closing the drawer must not eat the player's half-typed words. */
    fun updateDraft(text: String) {
        draft = text
        publish()
    }

    /** The env agent's smoke test: one headless pi turn through the app's own proxy (ADR-009). */
    fun runPiTest() {
        if (piRunning) return
        piRunning = true
        piResult = null
        publish()
        scope.launch {
            piResult = piRunner.run("用一句话回答：你是什么模型").fold(
                onSuccess = { it.ifBlank { "（空回复）" } },
                onFailure = { "失败：${it.message}" },
            )
            piRunning = false
            publish()
        }
    }

    /** One turn: the words land at once, the answer streams in as the events arrive. */
    fun send(text: String, context: AgentPageContext? = null) {
        val words = text.trim()
        if (words.isEmpty() || conversation.running) return
        conversation.dismissFailure()
        draft = ""
        conversation.say(words)
        conversation.begin()
        publish()
        turn = turnScope.launch {
            val batch = AgentTextBatch()
            try {
                chat.send(words, context).collect { event ->
                    when (event) {
                        is AgentEvent.TextDelta -> {
                            // the deltas wait in the buffer: the answer is re-laid ten times a
                            // second, not once per token
                            batch.add(event.text)
                            if (batch.due()) flush(batch)
                        }
                        is AgentEvent.UsageUpdated -> {
                            usageTokens = event.contextTokens
                            usageWindow = event.contextWindow
                            publish()
                        }
                        else -> {
                            flush(batch)
                            apply(event)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e   // the stop button: the cancel stays visible to the scope
            } catch (e: Exception) {
                // 先把缓冲里的字并进正在写的那条：它们本来也已经从模型流出来了。
                // 反过来（先 Failed 后 flush）会造出一条"幽灵回答"——空壳删掉之后
                // flush 又新建了一条。
                flush(batch)
                apply(AgentEvent.Failed(AgentFailure.LOOP_ERROR))   // nothing the failure codes cover
            } finally {
                flush(batch)
                conversation.end()
                publish()
            }
        }
    }

    /**
     * 停止：这一轮到此为止。
     *
     * 界面立刻解锁（不等引擎），引擎那边收到中止命令后停下手里的活——它不跑完这一轮，
     * 进程也留着给下一轮。已写出来的字留在这一轮的回答里。
     */
    fun stop() {
        turn?.cancel()
        turn = null
        chat.abort()
    }

    /** The strip's way out (the next turn replaces the words anyway). */
    fun dismissError() {
        conversation.dismissFailure()
        publish()
    }

    /** The setup sheet's save: the choice lands where the data layer keeps it (the key never shows up here). */
    fun updateConfig(providerId: String, baseUrl: String, apiKey: String, modelName: String) {
        settings.update(AgentModelConfig(providerId, baseUrl, apiKey, modelName))
        config = settings.config
        conversation.dismissFailure()
        publish()
    }

    /** One event of the running turn lands in the rendered list. */
    private fun apply(event: AgentEvent) {
        conversation.apply(event)
        if (event is AgentEvent.TurnDone) {
            // the turn's save renamed nothing but touched updatedAt: keep the dropdown fresh
            sessions = sessionStore.list()
            activeSessionId = sessionStore.activeId()
        }
        publish()
    }

    /** The buffer leaves: its words join the answer being written. */
    private fun flush(batch: AgentTextBatch) {
        val text = batch.take()
        if (text.isEmpty()) return
        conversation.appendText(text)
        publish()
    }
}
