package com.nx.android.agent.runtime

import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentRole
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/** One conversation in the list the session dropdown shows. */
data class AgentSessionMeta(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val contextTokens: Long = 0L,
    val contextWindow: Long = 0L,
)

/**
 * Multi-session persistence for the assistant's rendered conversation: one JSON file per session
 * under `sessions/`, an `_active` pointer, a `_meta.json` map for the dropdown's extras (context
 * occupancy per session), and a one-time migration from the v0 single `session.json`. The title is
 * derived from the first user words — it is what the dropdown shows.
 */
@Singleton
class AgentSessionStore @Inject constructor(private val workspace: AgentWorkspace) {

    private val dir: File get() = File(workspace.root, "sessions")
    private val activeFile: File get() = File(dir, "_active")
    private val metaFile: File get() = File(dir, "_meta.json")
    private fun file(id: String): File = File(dir, "$id.json")

    fun activeId(): String? {
        val id = activeFile.takeIf { it.isFile }?.readText()?.trim().orEmpty()
        return id.ifBlank { null }
    }

    fun list(): List<AgentSessionMeta> {
        val meta = readMeta()
        return dir.listFiles { f -> f.isFile && f.extension == "json" && f.name != "session.json" && f.name != "_meta.json" }
            ?.map { f ->
                val m = meta.optJSONObject(f.nameWithoutExtension)
                AgentSessionMeta(
                    f.nameWithoutExtension,
                    titleOf(f.readText()),
                    f.lastModified(),
                    m?.optLong("context") ?: 0L,
                    m?.optLong("window") ?: 0L,
                )
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun loadActive(): List<AgentMessage> {
        val id = activeId() ?: return emptyList()
        return load(id)
    }

    fun load(id: String): List<AgentMessage> = parse(file(id).takeIf { it.isFile }?.readText().orEmpty())

    /** Persists under the active id (creating it on first save), with the session's context state. */
    fun save(messages: List<AgentMessage>, context: Long = 0L, window: Long = 0L): String {
        dir.mkdirs()
        val id = activeId() ?: newId()
        val array = JSONArray()
        for (m in messages) array.put(JSONObject().put("id", m.id).put("role", m.role.name).put("text", m.text))
        file(id).writeText(array.toString(2))
        activeFile.writeText(id)
        writeMeta(id, context, window)
        return id
    }

    fun switch(id: String) {
        dir.mkdirs()
        activeFile.writeText(id)
    }

    fun create(): String {
        dir.mkdirs()
        val id = newId()
        File(dir, "$id.json").writeText("[]")
        activeFile.writeText(id)
        return id
    }

    fun clear() {
        dir.deleteRecursively()
    }

    private fun writeMeta(id: String, context: Long, window: Long) {
        val meta = readMeta()
        val entry = (meta.optJSONObject(id) ?: JSONObject()).put("context", context).put("window", window)
        meta.put(id, entry)
        metaFile.writeText(meta.toString(2))
    }

    private fun readMeta(): JSONObject = runCatching { JSONObject(metaFile.takeIf { it.isFile }?.readText().orEmpty()) }
        .getOrNull() ?: JSONObject()

    private fun newId(): String = "s-${System.currentTimeMillis()}"

    private fun parse(text: String): List<AgentMessage> {
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val role = if (o.optString("role") == "user") AgentRole.USER else AgentRole.ASSISTANT
            AgentMessage(o.optLong("id", i.toLong()), role, o.optString("text"))
        }
    }

    private fun titleOf(text: String): String {
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return "新会话"
        val first = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .firstOrNull { it.optString("role") == "user" }?.optString("text").orEmpty()
        return first.take(16).ifBlank { "新会话" }
    }

    companion object {
        /** One-time: a v0 single session.json becomes the first entry of the list. */
        fun migrateV0(dir: File) {
            val legacy = File(dir, "session.json")
            val target = File(dir, "s-legacy.json")
            if (legacy.isFile && !target.isFile) legacy.renameTo(target)
        }
    }
}
