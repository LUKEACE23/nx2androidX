package com.nx.android.agent.env

import android.util.Log
import com.nx.android.agent.AgentChat
import com.nx.android.agent.AgentEvent
import com.nx.android.agent.AgentFailure
import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentModelConfig
import com.nx.android.agent.AgentPageContext
import com.nx.android.agent.AgentToolCard
import com.nx.android.agent.AgentToolState
import com.nx.android.agent.TurnJournal
import com.nx.android.agent.runtime.AgentEngineStore
import com.nx.android.agent.runtime.AgentSessionStore
import com.nx.android.agent.runtime.AgentSettingsStore
import com.nx.android.agent.runtime.AgentWorkspace
import com.nx.android.agent.runtime.composeTurn
import com.nx.android.agent.tools.AgentTools
import com.nx.android.domain.environment.GuestProcessLauncher
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * The assistant's strong tier: the panel's own [AgentChat] face on the pi process (ADR-009).
 *
 * pi lives in the guest as a long-lived `--mode rpc` subprocess speaking JSONL over stdio; this
 * class is only the adapter. The event stream maps straight onto the events the panel already
 * renders — text deltas stream, tool calls show as cards, `agent_settled` closes the turn — so
 * the UI treats the two tiers exactly alike.
 *
 * The session store is shared with the light tier: a conversation followed across engines keeps
 * one rendered history, though each engine only knows the turns it answered itself.
 */
@Singleton
class PiAgentChat @Inject constructor(
    private val guest: GuestProcessLauncher,
    private val proxy: GuestProxy,
    private val settings: AgentSettingsStore,
    private val tools: AgentTools,
    private val workspace: AgentWorkspace,
    private val sessionStore: AgentSessionStore,
    private val engine: AgentEngineStore,
) : AgentChat {

    private companion object {
        const val TAG = "nx2a-agent-pi"
        const val DEVICE_STATE_CHARS = 600

        /** 中止之后等引擎说它闲下来的上限：引擎回话之前，尾巴事件都还在路上。 */
        const val ABORT_ACK_TIMEOUT_MS = 3_000L
    }

    @Volatile private var process: Process? = null
    @Volatile private var lineBus: Channel<String>? = null
    @Volatile private var builtFor: String? = null

    /** 往引擎写命令的锁：两条命令不许交错的半行。 */
    private val writeLock = Any()

    /** 请求号：只是给引擎看的关联标记。 */
    private var requestSeq: Long = 0L

    /** 这一轮的账：会话里有哪些消息、什么时候落盘。 */
    private val journal = TurnJournal()

    /** 有一笔中止在途：下一轮开始前要等引擎确认它闲下来了。 */
    @Volatile private var abortPending: Boolean = false

    /** The session's context occupancy and its model's window (ADR-009's usage row); zeroed on switch/new. */
    private var sessionTokens: Long = 0L
    private var sessionWindow: Long = 0L

    /** pi's own session jsonl (from get_state) — the future --resume anchor for session switching. */
    private var piSessionFile: String? = null

    init {
        workspace.ensure()
        AgentSessionStore.migrateV0(File(workspace.root, "sessions"))
        journal.load(sessionStore.loadActive())
    }

    override val messages: List<AgentMessage> get() = journal.snapshot()

    /** A hard reset is the new-session semantic: a fresh store entry, empty history, process down. */
    override fun reset() {
        newSession()
    }

    /**
     * 中止当前这一轮：清掉排队的消息，再让引擎停下手里的活。
     *
     * 引擎进程留着——下一轮接着用同一段上下文。命令从后台线程写，不挡着界面。
     */
    override fun abort() {
        abortPending = true   // 先把「有中止在途」记上：下一轮开始前要等引擎的回话
        Thread({
            writeCommand(JSONObject().put("type", "clear_queue"))
            writeCommand(JSONObject().put("type", "abort"))
            Log.d(TAG, "abort sent")
        }, "pi-abort").apply { isDaemon = true }.start()
    }

    /** Switch to another conversation: pi resumes its own context via the stored session file. */
    override fun switchSession(id: String) {
        sessionStore.switch(id)
        val meta = sessionStore.list().firstOrNull { it.id == id }
        // pi's own context: if we have its session file, tell pi to load it (the model remembers)
        if (process?.isAlive == true && !meta?.piSessionFile.isNullOrBlank()) {
            val sessionPath = meta!!.piSessionFile
            Thread({
                runCatching {
                    val cmd = JSONObject()
                        .put("id", "switch-$$")
                        .put("type", "switch_session")
                        .put("sessionPath", sessionPath)
                    writeCommand(cmd)
                    Log.d(TAG, "pi session resumed: $sessionPath")
                }
            }, "pi-switch-session").apply { isDaemon = true }.start()
        }
        loadSession()
    }

    /** A fresh conversation: a new store entry, pi's context resets too. */
    fun newSession(): String {
        if (process?.isAlive == true) {
            Thread({
                runCatching {
                    val cmd = JSONObject()
                        .put("id", "new-$$")
                        .put("type", "new_session")
                    writeCommand(cmd)
                }
            }, "pi-new-session").apply { isDaemon = true }.start()
        }
        val id = sessionStore.create()
        loadSession()
        return id
    }

    private fun loadSession() {
        kill()
        journal.load(sessionStore.loadActive())
        val meta = sessionStore.list().firstOrNull { it.id == sessionStore.activeId() }
        sessionTokens = meta?.contextTokens ?: 0L
        // the window: pi's precise value after a turn; the static catalog before the first one
        sessionWindow = meta?.contextWindow
            ?.takeIf { it > 0 }
            ?: PiCatalog.contextWindowFor(PiCatalog.fullModelId(settings.config))
    }

    override suspend fun send(userText: String, context: AgentPageContext?): Flow<AgentEvent> = channelFlow {
        val config = settings.config
        if (!config.configured) { send(AgentEvent.Failed(AgentFailure.NO_KEY)); return@channelFlow }

        val bus = try {
            ensureProcess(config)
        } catch (e: Exception) {
            send(AgentEvent.Failed(classify(e))); return@channelFlow
        }
        // 中止在途：等引擎说它闲下来了，这一轮的尾巴才收干净
        awaitIdle(bus)
        // the state ride-along: the model's context window + pi's own session file, for free
        withContext(Dispatchers.IO) {
            writeCommand(JSONObject().put("id", "state-$$").put("type", "get_state"))
        }
        // earlier turns' leftovers (prompt echoes, stray events) must not read as this turn's
        while (true) { bus.tryReceive().getOrNull() ?: break }

        journal.beginTurn(userText)
        persist(force = true)   // 问过什么立刻记上，不等这一轮结束

        val composed = composeTurn(
            userText,
            workspace.readMemory(),
            context,
            deviceState = tools.libraryStatus().take(DEVICE_STATE_CHARS),
        )

        var settled = false
        var turnContext = 0L
        // 这一轮生成的 token：每条消息结束时报一次（带工具的轮次有不止一条）
        var turnOutput = 0L
        var turnEndOutput = 0L
        try {
            withContext(Dispatchers.IO) {
                writeCommand(
                    JSONObject()
                        .put("id", "req-${++requestSeq}")
                        .put("type", "prompt")
                        .put("message", composed),
                )
            }
            for (line in bus) {
                val obj = runCatching { JSONObject(line) }.getOrNull() ?: continue
                when (obj.optString("type")) {
                    "response" -> if (obj.optString("command") == "get_state") {
                        val data = obj.optJSONObject("data")
                        data?.optJSONObject("model")?.optLong("contextWindow")?.takeIf { it > 0 }?.let {
                            sessionWindow = it
                        }
                        data?.optString("sessionFile")?.takeIf { it.isNotBlank() }?.let {
                            piSessionFile = it
                        }
                    } else if (!obj.optBoolean("success", true)) {
                        Log.d(TAG, "command failed: ${obj.optString("error").take(200)}")
                        send(AgentEvent.Failed(classify(RuntimeException(obj.optString("error")))))
                        settled = true
                    }

                    "message_update" -> {
                        obj.optJSONObject("usage")?.let { usage ->
                            // the latest call saw the whole conversation: its input + produced output
                            // IS the current context occupancy — monotonic, never a zero snapshot
                            val seen = usage.optLong("input") + usage.optLong("cacheRead") +
                                usage.optLong("output")
                            if (seen > turnContext) {
                                turnContext = seen
                                send(AgentEvent.UsageUpdated(sessionTokens + turnContext, sessionWindow))
                            }
                        }
                        val ev = obj.optJSONObject("assistantMessageEvent") ?: continue
                        if (ev.optString("type") == "text_delta") {
                            val delta = ev.optString("delta", "")
                            if (delta.isNotEmpty()) {
                                journal.appendDelta(delta)
                                send(AgentEvent.TextDelta(delta))
                                persist()   // 写出来的字隔几秒落一次盘：App 没了也还剩这些
                            }
                        }
                    }

                    "tool_execution_start" -> send(
                        AgentEvent.ToolStarted(
                            AgentToolCard(
                                id = obj.optString("toolCallId"),
                                name = obj.optString("toolName"),
                                argsSummary = obj.optJSONObject("args")?.toString()?.take(60) ?: "",
                                state = AgentToolState.RUNNING,
                            ),
                        ),
                    )

                    "tool_execution_end" -> {
                        val resultText = obj.optJSONObject("result")
                            ?.optJSONArray("content")?.optJSONObject(0)?.optString("text") ?: ""
                        send(
                            AgentEvent.ToolFinished(
                                obj.optString("toolCallId"),
                                if (obj.optBoolean("isError")) AgentToolState.FAILED else AgentToolState.DONE,
                                resultText.take(80),
                            ),
                        )
                    }

                    "message_end" -> obj.optJSONObject("message")?.optJSONObject("usage")?.let { usage ->
                        val seen = usage.optLong("input") + usage.optLong("cacheRead") +
                            usage.optLong("output")
                        if (seen > turnContext) {
                            turnContext = seen
                            send(AgentEvent.UsageUpdated(sessionTokens + turnContext, sessionWindow))
                        }
                        // 这一条消息生成了多少：这一轮的回答要报自己的消耗
                        turnOutput += usage.optLong("output")
                    }

                    "turn_end" -> obj.optJSONObject("message")?.optJSONObject("usage")?.let { usage ->
                        val seen = usage.optLong("input") + usage.optLong("cacheRead") +
                            usage.optLong("output")
                        if (seen > turnContext) turnContext = seen
                        turnEndOutput = usage.optLong("output")
                    }

                    "agent_settled" -> settled = true
                }
                if (settled) break
            }
        } catch (e: CancellationException) {
            // 玩家按了停止：这一轮到此为止，已写出的部分算数
            journal.stopTurn()
            persist(force = true)
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "turn failed: $e")
            if (!settled && journal.draftText().isEmpty()) {
                // the process died mid-turn: the next turn respawns it
                kill()
                journal.failTurn()
                persist(force = true)
                send(AgentEvent.Failed(AgentFailure.LOOP_ERROR))
                return@channelFlow
            }
        }

        sessionTokens = maxOf(sessionTokens, turnContext)
        val text = journal.draftText().trim()
        // 这一轮自己花了多少生成量：消息级累加，引擎没报消息级 usage 时退回整轮那一条
        val tokens = if (turnOutput > 0) turnOutput else turnEndOutput
        journal.finishTurn(tokens)
        persist(force = true)
        send(AgentEvent.TurnDone(text, tokens))
    }

    /** 会话落盘：这一轮的内容与上下文占用一起写进会话文件。 */
    private fun persist(force: Boolean = false) {
        if (!force && !journal.dueForWrite()) return
        sessionStore.save(
            journal.snapshot(),
            sessionTokens,
            sessionWindow,
            piSessionFile.orEmpty(),
        )
        journal.markWritten()
    }

    /**
     * 等引擎闲下来。
     *
     * 中止命令是引擎确认「这一轮停了、我闲了」才回话的：它回话之前，这一轮的尾巴事件
     * （最后几个字、收尾）都还在路上。不等它就开下一轮，那些尾巴会算到下一轮头上。
     */
    private suspend fun awaitIdle(bus: Channel<String>) {
        if (!abortPending) return
        abortPending = false
        withTimeoutOrNull(ABORT_ACK_TIMEOUT_MS) {
            for (line in bus) {
                val obj = runCatching { JSONObject(line) }.getOrNull() ?: continue
                if (obj.optString("type") == "response" && obj.optString("command") == "abort") {
                    return@withTimeoutOrNull
                }
            }
        }
    }

    /** The long-lived rpc process; a config change respawns it on the next turn. */
    @Synchronized
    private fun ensureProcess(config: AgentModelConfig): Channel<String> {
        val fingerprint = "${config.baseUrl}|${config.modelName}|${config.apiKey.hashCode()}"
        if (process?.isAlive == true && builtFor == fingerprint) return checkNotNull(lineBus)
        kill()

        val base = guest.guestEnv()
        val nx2a = File(requireNotNull(base["NX2A_HOME"]) { "NX2A_HOME is not set in the guest env" })
        val binary = engine.binary
        check(binary.isFile) { "pi 没有安装（nx2a/agent/runtime/pi 不存在）" }

        val port = proxy.ensure()

        // the providers pi does not know by itself (glm through bigmodel.cn, doubao, an endpoint
        // of the player's own) are declared in the engine's models.json
        PiCatalog.modelsJson(config)?.let { json ->
            val piHome = File(engine.home, ".pi/agent")
            piHome.mkdirs()
            File(piHome, "models.json").writeText(json)
        }

        val pb = ProcessBuilder(
            listOf(
                binary.absolutePath, "--mode", "rpc",
                "--model", PiCatalog.fullModelId(config),
            ),
        ).directory(nx2a)
        // stdout is the protocol, stderr is diagnostics; blending them would corrupt the stream
        pb.redirectErrorStream(false)
        pb.environment().putAll(base)
        // the engine's home is the assistant's own folder: its models.json, its sessions and its
        // state stay there, and the environment's reset does not touch them
        pb.environment()["HOME"] = engine.home.absolutePath
        pb.environment()["http_proxy"] = "http://127.0.0.1:$port"
        pb.environment()["https_proxy"] = pb.environment()["http_proxy"] ?: ""
        pb.environment()["TERM"] = "dumb"
        val (keyName, keyValue) = PiCatalog.keyEnv(config.providerId, config.apiKey)
        pb.environment()[keyName] = keyValue

        val p = pb.start()
        val bus = Channel<String>(capacity = Channel.UNLIMITED)
        // 读引擎输出的两个线程：管道被拆的时候（进程被杀或重建）读会抛 IO 异常。
        // 那是正常收场，不是错误——让它逃出去就是 App 进程自杀。
        Thread({
            try {
                p.inputStream.use { stream ->
                    stream.reader(Charsets.UTF_8).buffered().useLines { lines ->
                        lines.forEach { bus.trySend(it) }
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "rpc stdout closed: ${e.message}")
            } finally {
                bus.close()
            }
        }, "pi-rpc-reader").apply { isDaemon = true }.start()
        Thread({
            try {
                p.errorStream.use { stream ->
                    stream.reader(Charsets.UTF_8).forEachLine { Log.d(TAG, it.take(200)) }
                }
            } catch (e: IOException) {
                Log.d(TAG, "rpc stderr closed: ${e.message}")
            }
        }, "pi-rpc-stderr").apply { isDaemon = true }.start()

        process = p
        lineBus = bus
        builtFor = fingerprint
        return bus
    }

    /**
     * 往引擎写一条命令。
     *
     * 写锁：两条命令不许交错的半行。写向已经走了的进程会抛 IO 异常——记一笔，不当错误。
     */
    private fun writeCommand(cmd: JSONObject) {
        val line = cmd.toString() + "\n"
        synchronized(writeLock) {
            val proc = process ?: return
            runCatching {
                proc.outputStream.write(line.toByteArray(Charsets.UTF_8))
                proc.outputStream.flush()
            }.onFailure { Log.d(TAG, "write failed: ${it.message}") }
        }
    }

    @Synchronized
    private fun kill() {
        process?.let { p ->
            runCatching { p.outputStream.close() }   // the orderly shutdown request
            runCatching { p.destroy() }
        }
        lineBus?.close()
        process = null
        lineBus = null
        builtFor = null
        abortPending = false   // 进程都没了，没有中止要等了
    }

    private fun classify(error: Exception): AgentFailure = when {
        error.message?.contains("401") == true || error.message?.contains("403") == true -> AgentFailure.BAD_KEY
        error is java.io.IOException -> AgentFailure.NETWORK
        else -> AgentFailure.LOOP_ERROR
    }
}

/** The provider facts both pi entry points share: the catalog's model id and the key's env var. */
internal object PiCatalog {

    /**
     * The context windows pi's own catalog reports for the models our presets offer — seeded from
     * `pi --list-models`. The runtime corrects them with pi's get_state once a process is up;
     * before that, the static value is what the head and the ring need to show a percent on
     * first open.
     */
    private val CONTEXT_WINDOWS: Map<String, Long> = mapOf(
        "minimax-cn/MiniMax-M3.1-Flash" to 1_048_576L,
        "minimax-cn/MiniMax-M3" to 1_048_576L,
        "minimax-cn/MiniMax-M2.7" to 204_800L,
        "minimax-cn/MiniMax-M2.1" to 204_800L,
        "minimax-cn/MiniMax-M2" to 204_800L,
        "deepseek/deepseek-chat" to 131_072L,
        "deepseek/deepseek-reasoner" to 131_072L,
        "moonshot/kimi-latest" to 262_144L,
        "zhipu/glm-4.6" to 204_800L,
        "zhipu/glm-4.5" to 131_072L,
    )

    fun contextWindowFor(fullModelId: String): Long = CONTEXT_WINDOWS[fullModelId] ?: 0L

    /** The providers pi knows by itself (its own catalog names them). */
    private val BUILT_IN = setOf("minimax", "deepseek", "kimi")

    /** The name the provider carries in the models.json and in the model id. */
    fun providerKey(providerId: String): String = when (providerId) {
        "glm" -> "zhipu"
        else -> providerId
    }

    fun fullModelId(config: AgentModelConfig): String =
        if (config.modelName.contains('/')) config.modelName
        else when (config.providerId) {
            "minimax" -> "minimax-cn/${config.modelName}"
            "glm" -> "zhipu/${config.modelName}"
            "deepseek" -> "deepseek/${config.modelName}"
            "kimi" -> "moonshot/${config.modelName}"
            else -> if (config.providerId in BUILT_IN) config.modelName
            else "${providerKey(config.providerId)}/${config.modelName}"
        }

    fun keyEnv(providerId: String, key: String): Pair<String, String> = when (providerId) {
        "minimax" -> "MINIMAX_CN_API_KEY" to key
        "glm" -> "ZHIPU_API_KEY" to key
        "deepseek" -> "DEEPSEEK_API_KEY" to key
        "kimi" -> "MOONSHOT_API_KEY" to key
        "doubao" -> "ARK_API_KEY" to key
        else -> "OPENAI_API_KEY" to key
    }

    /**
     * The models.json a provider of ours needs: pi knows some providers by itself, the others
     * (glm through bigmodel.cn, doubao, an endpoint of the player's own) are declared here — the
     * endpoint, the key's env var and the model.
     *
     * Null when pi knows the provider by itself.
     */
    fun modelsJson(config: AgentModelConfig): String? = when (config.providerId) {
        "minimax", "deepseek", "kimi" -> null
        "glm" -> """
            {
              "providers": {
                "zhipu": {
                  "baseUrl": "${config.baseUrl.trimEnd('/')}",
                  "api": "openai-completions",
                  "apiKey": "${'$'}ZHIPU_API_KEY",
                  "models": [
                    { "id": "glm-4.6", "contextWindow": 204800 },
                    { "id": "glm-4.5", "contextWindow": 131072 },
                    { "id": "glm-4.5-air", "contextWindow": 131072 }
                  ]
                }
              }
            }
        """.trimIndent()
        else -> {
            val key = providerKey(config.providerId)
            val (envName, _) = keyEnv(config.providerId, config.apiKey)
            val window = contextWindowFor("$key/${config.modelName}")
            val model = if (window > 0) """{ "id": "${config.modelName}", "contextWindow": $window }"""
            else """{ "id": "${config.modelName}" }"""
            """
                {
                  "providers": {
                    "$key": {
                      "baseUrl": "${config.baseUrl.trimEnd('/')}",
                      "api": "openai-completions",
                      "apiKey": "${'$'}${envName}",
                      "models": [ $model ]
                    }
                  }
                }
            """.trimIndent()
        }
    }
}
