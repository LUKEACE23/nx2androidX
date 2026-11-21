package com.nx.android.agent.runtime

import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentRole
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The rendered conversation, persisted as JSON in the workspace (ADR-007: the agent keeps its state in
 * its own directory). What survives a restart is what the page renders — the user's words and the
 * assistant's answers with their tool cards' summary line; the tool args/results are transient by design
 * (the model's own context is rebuilt from the restored history on the next send, see AgentChatImpl).
 *
 * The encode/decode pair is pure (strings in, strings out) so the JVM tests can drive the round trip
 * without Android.
 */
@Singleton
class AgentSessionStore @Inject constructor(private val workspace: AgentWorkspace) {

    private companion object {
        const val SESSION = "sessions/session.json"

        internal fun decode(json: String): List<AgentMessage> {
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            val out = ArrayList<AgentMessage>(array.length())
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optLong("id", i.toLong())
                val role = if (o.optString("role") == "user") AgentRole.USER else AgentRole.ASSISTANT
                val text = o.optString("text")
                if (text.isEmpty() && role == AgentRole.USER) continue
                out.add(AgentMessage(id, role, text))
            }
            return out
        }
    }

    /** Write the conversation (oldest first). */
    fun save(messages: List<AgentMessage>) {
        val array = JSONArray()
        for (m in messages) {
            array.put(
                JSONObject()
                    .put("id", m.id)
                    .put("role", if (m.role == AgentRole.USER) "user" else "assistant")
                    .put("text", m.text)
                    .put("tools", JSONArray().apply { for (t in m.tools) put(t.name) }),
            )
        }
        val f = File(workspace.root, "sessions").apply { mkdirs() }
        File(f, "session.json").writeText(array.toString(2))
    }

    /** The conversation (oldest first); an unreadable or missing file is an empty history. */
    fun load(): List<AgentMessage> {
        val f = File(workspace.root, "sessions/session.json")
        if (!f.isFile) return emptyList()
        return decode(f.readText())
    }

    /** Forget the conversation: the file goes, the next session starts clean. */
    fun clear() {
        File(workspace.root, "sessions/session.json").delete()
    }
}
