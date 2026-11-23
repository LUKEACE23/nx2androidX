package com.nx.android.agent.env

import android.util.Log
import com.nx.android.BuildConfig
import com.nx.android.agent.AgentChat
import com.nx.android.agent.AgentEngineMissingException
import com.nx.android.agent.AgentEvent
import com.nx.android.agent.AgentFailure
import com.nx.android.agent.AgentHistory
import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentModelConfig
import com.nx.android.agent.AgentPageContext
import com.nx.android.agent.AgentSubagentCallParser
import com.nx.android.agent.AgentToolCard
import com.nx.android.agent.AgentToolState
import com.nx.android.agent.runtime.AgentEngineStore
import com.nx.android.agent.runtime.AgentExtensions
import com.nx.android.agent.runtime.AgentSessionStore
import com.nx.android.agent.runtime.AgentSettingsStore
import com.nx.android.agent.runtime.AgentWorkspace
import com.nx.android.agent.runtime.composeTurn
import com.nx.android.agent.runtime.deviceState
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
    private val catalog: PiSessionCatalog,
    private val engine: AgentEngineStore,
) : AgentChat {

    private companion object {
        const val TAG = "nx2a-agent-pi"
        const val DEVICE_STATE_CHARS = 600

        /** 中止之后等引擎说它闲下来的上限：引擎回话之前，尾巴事件都还在路上。 */
        const val ABORT_ACK_TIMEOUT_MS = 3_000L

        /** 一条 RPC 命令等回话的上限。 */
        const val RPC_TIMEOUT_MS = 8_000L

        /** 新会话的标题：玩家第一句话的前几个字。 */
        const val TITLE_CHARS = 16
    }

    @Volatile private var process: Process? = null
    @Volatile private var lineBus: Channel<String>? = null
    @Volatile private var rpcBus: Channel<String>? = null
    @Volatile private var builtFor: String? = null

    /** 往引擎写命令的锁：两条命令不许交错的半行。 */
    private val writeLock = Any()

    /** 请求号：命令与回话的关联标记。 */
    private var requestSeq: Long = 0L

    /** 上次读到的历史（接口要用），与最近一次确认的会话文件（给新会话起名用）。 */
    private var loadedMessages: List<AgentMessage> = emptyList()
    private var lastSessionFile: String? = null

    /** 有一笔中止在途：下一轮开始前要等引擎确认它闲下来了。 */
    @Volatile private var abortPending: Boolean = false

    /** The session's context occupancy and its model's window; live from the engine, zeroed on switch/new. */
    private var sessionTokens: Long = 0L
    private var sessionWindow: Long = 0L

    /** pi's own session jsonl (from get_state) — the anchor the active pointer holds. */
    private var piSessionFile: String? = null

    init {
        workspace.ensure()
    }

    override val messages: List<AgentMessage> get() = loadedMessages

    /** A hard reset is the new-session semantic: a fresh engine session, empty history, process stays. */
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

    /** A fresh conversation: the engine's context resets; the file itself is created at the first prompt. */
    fun newSession(): String {
        if (process?.isAlive == true) {
            Thread({
                runCatching { writeCommand(JSONObject().put("id", "new-$$").put("type", "new_session")) }
            }, "pi-new-session").apply { isDaemon = true }.start()
        }
        sessionStore.clearActive()
        lastSessionFile = null
        piSessionFile = null
        loadedMessages = emptyList()
        sessionTokens = 0L
        sessionWindow = 0L
        return ""
    }

    /**
     * 读历史（冷启动、切换、下拉选择共用）：引擎起来 → 切到目标会话（指针的，或指定的）→
     * 取回消息与状态。进程新起时若不先切，第一轮会开一个新会话文件——所以永远先切再读。
     */
    override suspend fun history(prefer: String?): Result<AgentHistory> = withContext(Dispatchers.IO) {
        val config = settings.config
        if (!config.configured) return@withContext Result.failure(IllegalStateException("模型还没有配置"))
        if (!engine.installed) return@withContext Result.failure(AgentEngineMissingException())

        val bus = try {
            ensureProcess(config)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
        // 目标：指定的（下拉选择）→ 指针的（还在盘上才算）→ 最新的一段
        val target = prefer
            ?: sessionStore.activePath()?.takeIf { File(it).isFile }
            ?: catalog.list().firstOrNull()?.path
        if (target != null) {
            val switched = rpc(JSONObject().put("type", "switch_session").put("sessionPath", target))
            Log.d(TAG, "switch: ${switched?.optBoolean("success")}")
            if (switched == null) return@withContext Result.failure(IllegalStateException("切换会话没有回话"))
        }

        val state = rpc(JSONObject().put("type", "get_state"))
            ?: return@withContext Result.failure(IllegalStateException("引擎没有回话"))
        val file = state.optJSONObject("data")?.optString("sessionFile").orEmpty()
        val title = state.optJSONObject("data")?.optString("sessionName").orEmpty()

        val stats = rpc(JSONObject().put("type", "get_session_stats"))
        val usage = stats?.optJSONObject("data")?.optJSONObject("contextUsage")
        sessionTokens = usage?.optLong("tokens", 0L) ?: 0L
        sessionWindow = usage?.optLong("contextWindow", 0L) ?: 0L
        if (sessionWindow <= 0) {
            state.optJSONObject("data")?.optJSONObject("model")?.optLong("contextWindow")?.takeIf { it > 0 }
                ?.let { sessionWindow = it }
        }

        val raw = rpc(JSONObject().put("type", "get_messages"))
            ?.optJSONObject("data")?.optJSONArray("messages")
            ?: return@withContext Result.failure(IllegalStateException("取不到会话内容"))
        val history = ArrayList<JSONObject>(raw.length())
        for (i in 0 until raw.length()) raw.optJSONObject(i)?.let { history.add(it) }
        loadedMessages = PiHistoryMapper.map(history)
        Log.d(TAG, "messages: ${raw.length()} mapped: ${loadedMessages.size}")

        if (file.isNotBlank()) {
            piSessionFile = file
            lastSessionFile = file
            sessionStore.setActive(file)   // 指针跟着真实读取落到的地方
        }

        Result.success(
            AgentHistory(
                messages = loadedMessages,
                sessionFile = file,
                title = title,
                contextTokens = sessionTokens,
                contextWindow = sessionWindow,
            ),
        )
    }

    /**
     * 一问一答式的 RPC：带上请求号写命令，从回话通道等那条回应（其余的行不归它管）。
     * 回话与事件走两条通道，所以读历史与跑一轮可以同时发生。
     */
    private suspend fun rpc(cmd: JSONObject): JSONObject? {
        val id = "rpc-${++requestSeq}"
        cmd.put("id", id)
        withContext(Dispatchers.IO) { writeCommand(cmd) }
        val replies = rpcBus ?: return null
        return withTimeoutOrNull(RPC_TIMEOUT_MS) {
            for (line in replies) {
                val obj = runCatching { JSONObject(line) }.getOrNull() ?: continue
                if (obj.optString("id") == id) return@withTimeoutOrNull obj
            }
            null
        }
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
        // earlier turns' leftovers (prompt echoes, stray events) must not read as this turn's
        // 先清残留再问状态：引擎回话是毫秒级的，问完再清会把回话一起吃掉
        while (true) { bus.tryReceive().getOrNull() ?: break }
        // the state ride-along: the model's context window + pi's own session file, for free
        withContext(Dispatchers.IO) {
            writeCommand(JSONObject().put("id", "state-$$").put("type", "get_state"))
        }

        val composed = composeTurn(
            userText,
            workspace.readMemory(),
            context,
            deviceState = deviceState(
                BuildConfig.VERSION_NAME,
                tools.libraryStatus(),
                DEVICE_STATE_CHARS,
            ),
        )

        var settled = false
        var turnContext = 0L
        // 这一轮生成的 token：每条消息结束时报一次（带工具的轮次有不止一条）
        var turnOutput = 0L
        var turnEndOutput = 0L
        val answer = StringBuilder()
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
                                answer.append(delta)
                                send(AgentEvent.TextDelta(delta))
                            }
                        }
                    }

                    "tool_execution_start" -> {
                        val name = obj.optString("toolName")
                        val args = obj.optJSONObject("args")
                        val call = AgentSubagentCallParser.of(name, args)
                        send(
                            AgentEvent.ToolStarted(
                                AgentToolCard(
                                    id = obj.optString("toolCallId"),
                                    name = name,
                                    argsSummary = if (call != null) {
                                        call.description.ifBlank { call.type }
                                    } else {
                                        args?.toString()?.take(60) ?: ""
                                    },
                                    state = AgentToolState.RUNNING,
                                    subagent = call,
                                ),
                            ),
                        )
                    }

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

                    "message_end" -> {
                        val message = obj.optJSONObject("message")
                        // 子 Agent 跑完了：引擎发来一条自己的消息，正文是那份通知
                        if (message != null &&
                            message.optString("role") == "custom" &&
                            message.optString("customType") == SubagentNotifications.MESSAGE_TYPE
                        ) {
                            SubagentNotifications.parse(SubagentTranscripts.textOf(message))
                                ?.let { send(AgentEvent.Notification(it)) }
                        }
                        message?.optJSONObject("usage")?.let { usage ->
                            val seen = usage.optLong("input") + usage.optLong("cacheRead") +
                                usage.optLong("output")
                            if (seen > turnContext) {
                                turnContext = seen
                                send(AgentEvent.UsageUpdated(sessionTokens + turnContext, sessionWindow))
                            }
                            // 这一条消息生成了多少：这一轮的回答要报自己的消耗
                            turnOutput += usage.optLong("output")
                        }
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
            // 玩家按了停止：引擎自己把这一轮（含已写出的部分）收进它的会话，App 不用补
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "turn failed: $e")
            if (!settled && answer.isEmpty()) {
                // the process died mid-turn: the next turn respawns it
                kill()
                send(AgentEvent.Failed(AgentFailure.LOOP_ERROR))
                return@channelFlow
            }
        }

        sessionTokens = maxOf(sessionTokens, turnContext)
        // 这一轮自己花了多少生成量：消息级累加，引擎没报消息级 usage 时退回整轮那一条
        val tokens = if (turnOutput > 0) turnOutput else turnEndOutput
        // 轮次收尾，引擎闲下来了：问一次它现在在哪个会话文件（新会话的第一问会开新文件）。
        // 名字与指针都在这里落：新会话写上名字（玩家第一句话的前 16 字），指针指过去。
        withContext(Dispatchers.IO) {
            val state = rpc(JSONObject().put("type", "get_state"))
            val file = state?.optJSONObject("data")?.optString("sessionFile").orEmpty()
            if (file.isNotBlank() && file != lastSessionFile) {
                lastSessionFile = file
                piSessionFile = file
                sessionStore.setActive(file)
                writeCommand(
                    JSONObject().put("type", "set_session_name").put("name", userText.take(TITLE_CHARS)),
                )
            }
        }
        send(AgentEvent.TurnDone(answer.toString().trim(), tokens))
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

        // the extensions the installed package carries are pointed at before the engine starts:
        // it reads its settings when it comes up, not later
        AgentExtensions.enable(engine.home, engine.runtimeDir, engine.installedExtensions())

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
        // 回话走自己那条通道（读历史时用），其余事件走事件总线：分开走，两边才不会
        // 互相抢对方的行（读历史与跑一轮可以同时发生）。
        val replies = Channel<String>(capacity = Channel.UNLIMITED)
        rpcBus = replies
        // 读引擎输出的两个线程：管道被拆的时候（进程被杀或重建）读会抛 IO 异常。
        // 那是正常收场，不是错误——让它逃出去就是 App 进程自杀。
        Thread({
            try {
                p.inputStream.use { stream ->
                    stream.reader(Charsets.UTF_8).buffered().useLines { lines ->
                        lines.forEach { line ->
                            if (isRpcReply(line)) replies.trySend(line) else bus.trySend(line)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "rpc stdout closed: ${e.message}")
            } finally {
                bus.close()
                replies.close()
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

    /** 这一行是我们自己问出去的那条命令的回话吗（请求号以 rpc- 开头）。 */
    private fun isRpcReply(line: String): Boolean =
        runCatching { JSONObject(line).optString("id").startsWith("rpc-") }.getOrDefault(false)

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
        rpcBus?.close()
        process = null
        lineBus = null
        rpcBus = null
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
