package com.nx.android.agent.runtime

import android.util.Log
import com.nx.android.agent.AgentChat
import com.nx.android.agent.AgentEvent
import com.nx.android.agent.AgentFailure
import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentModelConfig
import com.nx.android.agent.AgentPageContext
import com.nx.android.agent.AgentRole
import com.nx.android.agent.runtime.AgentWorkspace
import com.nx.android.agent.runtime.AgentSessionStore
import com.nx.android.agent.tools.AgentTools
import com.nx.android.agent.tools.MemoryTools
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The assistant's runtime: a hand-rolled ReAct loop over raw HTTP to the player's chosen BYOK
 * OpenAI-compatible endpoint (ADR-008). Answers stream token by token over SSE; tool calls ride
 * the same stream as `delta.tool_calls` fragments and are accumulated by index.
 */
@Singleton
class AgentChatImpl @Inject constructor(
    private val settings: AgentSettingsStore,
    private val tools: AgentTools,
    private val memoryTools: MemoryTools,
    private val workspace: AgentWorkspace,
    private val sessionStore: AgentSessionStore,
) : AgentChat {

    private companion object {
        const val TAG = "nx2a-agent"
        const val MAX_ROUNDS = 6
        const val SYSTEM_PROMPT = "你是 nx2androidX 的内置助手，用简体中文回答。"

        /** The conventions the workspace seeds: the model reads them every turn, bounded. */
        const val AGENTS_INJECT_CHARS = 1200
    }

    override val messages: List<AgentMessage> get() = list
    private var nextId: Long = 0
    private var list: MutableList<AgentMessage> = mutableListOf()

    init {
        workspace.ensure()
        list = sessionStore.load().toMutableList()
        nextId = (list.maxOfOrNull { it.id } ?: -1L) + 1L
    }

    override fun reset() {
        list = mutableListOf()
        sessionStore.clear()
    }

    /**
     * One turn: the deltas go out live through [channelFlow] (emission happens inside
     * `withContext(Dispatchers.IO)`, which only channelFlow tolerates), then the canonical
     * answer lands in the model's history.
     */
    override suspend fun send(userText: String, context: AgentPageContext?): Flow<AgentEvent> = channelFlow {
        val config = settings.config
        if (!config.configured) { send(AgentEvent.Failed(AgentFailure.NO_KEY)); return@channelFlow }
        list.add(AgentMessage(nextId++, AgentRole.USER, userText))
        val composed = composeTurn(userText, workspace.readMemory(), context)

        try {
            val answer = runReAct(config, composed) { delta -> send(AgentEvent.TextDelta(delta)) }
            list.add(AgentMessage(nextId++, AgentRole.ASSISTANT, answer))
            sessionStore.save(list)
            send(AgentEvent.TurnDone(answer))
        } catch (e: CancellationException) {
            throw e   // the stop button
        } catch (e: Exception) {
            Log.d(TAG, "send error: $e")
            send(AgentEvent.Failed(classify(e)))
        }
    }

    /** The ReAct loop: stream a round; tool calls are executed and fed back, text ends the loop. */
    private suspend fun runReAct(
        config: AgentModelConfig,
        message: String,
        onDelta: suspend (String) -> Unit,
    ): String {
        val system = SYSTEM_PROMPT + "\n\n" + workspace.readAgents().take(AGENTS_INJECT_CHARS)
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", message))
        for (round in 0 until MAX_ROUNDS) {
            val r = streamRound(config, messages, onDelta)
            if (r.toolCalls.isEmpty()) {
                Log.d(TAG, "round=$round answer: ${r.content.take(120)}")
                return r.content
            }
            Log.d(TAG, "round=$round tool calls: ${r.toolCalls.map { it.name }}")
            // the assistant's tool-call turn goes back verbatim, then one tool message per call
            messages.put(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", if (r.content.isEmpty()) JSONObject.NULL else r.content)
                    .put(
                        "tool_calls",
                        JSONArray().apply {
                            for (t in r.toolCalls) put(
                                JSONObject()
                                    .put("id", t.id.ifBlank { "call_${System.nanoTime()}" })
                                    .put("type", "function")
                                    .put(
                                        "function",
                                        JSONObject()
                                            .put("name", t.name)
                                            .put("arguments", t.args.ifBlank { "{}" }),
                                    ),
                            )
                        },
                    ),
            )
            for (t in r.toolCalls) {
                val result = dispatch(t.name, t.args.ifBlank { "{}" })
                Log.d(TAG, "tool ${t.name} → ${result.take(100)}")
                messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", t.id)
                        .put("content", result.take(2000)),
                )
            }
        }
        return "经过 $MAX_ROUNDS 轮仍未得出最终回答。"
    }

    /** One model round, streamed: content tokens go to [onDelta] live, tool fragments accumulate. */
    private suspend fun streamRound(
        config: AgentModelConfig,
        messages: JSONArray,
        onDelta: suspend (String) -> Unit,
    ): Round = withContext(Dispatchers.IO) {
        Log.d(TAG, "HTTP POST (stream) → ${config.baseUrl}/chat/completions")
        val body = JSONObject()
            .put("model", config.modelName)
            .put("messages", messages)
            .put("tools", toolsSchema())
            .put("stream", true)
        val conn = URL(config.baseUrl.trimEnd('/') + "/chat/completions")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 180_000
            conn.outputStream.write(body.toString().toByteArray(Charsets.UTF_8))
            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText()?.take(400) ?: ""
                throw RuntimeException("HTTP ${conn.responseCode}: $err")
            }
            val round = Round()
            val reader = conn.inputStream.bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val delta = runCatching {
                    JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                }.getOrNull() ?: continue
                val token = delta.optString("content", "")
                if (token.isNotEmpty()) {
                    round.content += token
                    onDelta(token)
                }
                val calls = delta.optJSONArray("tool_calls") ?: continue
                for (i in 0 until calls.length()) {
                    val c = calls.optJSONObject(i) ?: continue
                    val idx = c.optInt("index", round.toolCalls.size)
                    while (round.toolCalls.size <= idx) round.toolCalls.add(ToolCall())
                    val slot = round.toolCalls[idx]
                    c.optString("id", "").takeIf { it.isNotEmpty() }?.let { slot.id = it }
                    c.optJSONObject("function")?.let { fn ->
                        fn.optString("name", "").takeIf { it.isNotEmpty() }?.let { slot.name = it }
                        slot.args += fn.optString("arguments", "")
                    }
                }
            }
            round
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun dispatch(name: String, args: String): String = when (name) {
        "kb_search" -> tools.kbSearch(extractStr(args, "query") ?: args)
        "library_status" -> tools.libraryStatus()
        "build_log_tail" -> tools.buildLogTail(extractInt(args, "lines") ?: 40)
        "game_log" -> tools.gameLog(
            extractStr(args, "game"),
            extractStr(args, "which") ?: "last",
            extractInt(args, "lines") ?: 60,
        )
        "memory_save" -> memoryTools.save(extractStr(args, "lesson") ?: args)
        else -> "未知工具"
    }

    /** The tool belt the model is told about — the names match [dispatch] exactly. */
    private fun toolsSchema(): JSONArray {
        fun prop(type: String, desc: String): JSONObject =
            JSONObject().put("type", type).put("description", desc)
        fun fn(name: String, desc: String, props: JSONObject): JSONObject =
            JSONObject()
                .put("type", "function")
                .put(
                    "function",
                    JSONObject()
                        .put("name", name)
                        .put("description", desc)
                        .put(
                            "parameters",
                            JSONObject().put("type", "object")
                                .put("properties", props)
                                .put("required", JSONArray()),
                        ),
                )
        return JSONArray()
            .put(fn("kb_search", "检索应用内置的 FAQ 与指南", JSONObject().put("query", prop("string", "检索词"))))
            .put(fn("library_status", "查看游戏库：每个游戏的移植状态", JSONObject()))
            .put(fn("build_log_tail", "查看最近一次构建日志的尾部", JSONObject().put("lines", prop("integer", "行数，默认 40"))))
            .put(
                fn(
                    "game_log",
                    "查看某游戏的运行日志；不填 game 就是最近运行过的那个",
                    JSONObject()
                        .put("game", prop("string", "游戏名或 titleId"))
                        .put("which", prop("string", "last=最近一次，prev=上一次，rebuild=runtime 重编"))
                        .put("lines", prop("integer", "行数，默认 60")),
                ),
            )
            .put(fn("memory_save", "把值得记住的经验存进长期记忆", JSONObject().put("lesson", prop("string", "一句经验"))))
    }

    private fun extractStr(args: String, key: String): String? = try {
        val o = JSONObject(args); if (o.has(key) && !o.isNull(key)) o.optString(key) else null
    } catch (e: Exception) {
        null
    }

    private fun extractInt(args: String, key: String): Int? = try {
        val o = JSONObject(args); if (o.has(key) && !o.isNull(key)) o.optInt(key) else null
    } catch (e: Exception) {
        null
    }

    internal fun classify(error: Throwable): AgentFailure = when {
        error is CancellationException -> throw error
        error.message?.contains("401") == true || error.message?.contains("403") == true -> AgentFailure.BAD_KEY
        error is java.io.IOException -> AgentFailure.NETWORK
        else -> AgentFailure.LOOP_ERROR
    }
}

/** One model round's outcome: the streamed text, or the tool calls accumulated from the fragments. */
private class Round {
    var content: String = ""
    val toolCalls: MutableList<ToolCall> = mutableListOf()
}

private class ToolCall {
    var id: String = ""
    var name: String = ""
    var args: String = ""
}

internal const val CONTEXT_CHARS = 800

internal fun composeTurn(userText: String, memory: String, context: AgentPageContext?): String {
    val hasMemory = memory.isNotBlank()
    val hasContext = context != null
    if (!hasMemory && !hasContext) return userText
    val sb = StringBuilder()
    if (hasMemory) sb.append("[助手记忆] ").append(memory.trim()).append('\n')
    if (hasContext) {
        val block = StringBuilder("[页面上下文] ")
        if (context.subject != null) block.append("正在处理：${context.subject.title}。")
        for ((k, v) in context.facts) block.append("$k：$v。")
        if (block.length > CONTEXT_CHARS) { block.setLength(CONTEXT_CHARS); block.append("…已截断") }
        sb.append(block).append('\n')
    }
    return sb.append("玩家说：").append(userText).toString()
}
