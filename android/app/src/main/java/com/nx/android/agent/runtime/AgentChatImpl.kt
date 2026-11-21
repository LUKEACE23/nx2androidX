package com.nx.android.agent.runtime

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class AgentChatImpl @Inject constructor(
    private val settings: AgentSettingsStore,
    private val tools: AgentTools,
    private val memoryTools: MemoryTools,
    private val workspace: AgentWorkspace,
    private val sessionStore: AgentSessionStore,
) : AgentChat {

    override val messages: List<AgentMessage> get() = list
    private var nextId: Long = 0
    private var list: MutableList<AgentMessage> = mutableListOf()

    init {
        workspace.ensure()
        list = sessionStore.load().toMutableList()
        nextId = (list.maxOfOrNull { it.id } ?: -1L) + 1L
    }

    override fun reset() { list = mutableListOf(); sessionStore.clear() }

    override suspend fun send(userText: String, context: AgentPageContext?): Flow<AgentEvent> = flow {
        val config = settings.config
        if (!config.configured) { emit(AgentEvent.Failed(AgentFailure.NO_KEY)); return@flow }
        list.add(AgentMessage(nextId++, AgentRole.USER, userText))
        val composed = composeTurn(userText, workspace.readMemory(), context)

        val text = try {
            withContext(Dispatchers.IO) { runReAct(config, composed) }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { emit(AgentEvent.Failed(classify(e))); return@flow }

        list.add(AgentMessage(nextId++, AgentRole.ASSISTANT, text.trim()))
        sessionStore.save(list)
        emit(AgentEvent.TurnDone(text.trim()))
    }

    private suspend fun runReAct(config: AgentModelConfig, message: String): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", message))
        for (round in 0 until MAX_ROUNDS) {
            val resp = callModel(config, messages) ?: return "网络不可用。"
            val calls = resp.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) return resp.optString("content") ?: ""
            messages.put(resp)
            for (i in 0 until calls.length()) {
                val fn = calls.optJSONObject(i)?.optJSONObject("function") ?: continue
                val result = dispatch(fn.optString("name", ""), fn.optString("arguments", "{}"))
                messages.put(JSONObject().put("role", "tool").put("content", result.take(2000)))
            }
        }
        return "经过 $MAX_ROUNDS 轮仍未得出最终回答。"
    }

    private suspend fun callModel(config: AgentModelConfig, messages: JSONArray): JSONObject? =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("model", config.modelName).put("messages", messages)
            val conn = URL(config.baseUrl.trimEnd('/') + "/chat/completions")
                .openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                conn.doOutput = true; conn.connectTimeout = 15_000; conn.readTimeout = 120_000
                conn.outputStream.write(body.toString().toByteArray(Charsets.UTF_8))
                if (conn.responseCode !in 200..299) throw RuntimeException("HTTP ${conn.responseCode}")
                JSONObject(conn.inputStream.bufferedReader().readText())
                    .optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            } finally { conn.disconnect() }
        }

    private suspend fun dispatch(name: String, args: String): String = when (name) {
        "kb_search" -> tools.kbSearch(extractStr(args, "query") ?: args)
        "library_status" -> tools.libraryStatus()
        "build_log_tail" -> tools.buildLogTail(extractInt(args, "lines") ?: 40)
        "game_log" -> tools.gameLog(extractStr(args, "game"), extractStr(args, "which") ?: "last", extractInt(args, "lines") ?: 60)
        "memory_save" -> memoryTools.save(extractStr(args, "lesson") ?: args)
        else -> "未知工具"
    }

    private fun extractStr(args: String, key: String): String? = try {
        val o = JSONObject(args); if (o.has(key) && !o.isNull(key)) o.optString(key) else null
    } catch (e: Exception) { null }

    private fun extractInt(args: String, key: String): Int? = try {
        val o = JSONObject(args); if (o.has(key) && !o.isNull(key)) o.optInt(key) else null
    } catch (e: Exception) { null }

    internal fun classify(error: Throwable): AgentFailure = when {
        error is CancellationException -> throw error
        error.message?.contains("401") == true || error.message?.contains("403") == true -> AgentFailure.BAD_KEY
        error is java.io.IOException -> AgentFailure.NETWORK
        else -> AgentFailure.LOOP_ERROR
    }

    private companion object {
        const val MAX_ROUNDS = 6
        const val SYSTEM_PROMPT = "你是 nx2androidX 的内置助手，用简体中文回答。"
    }
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
