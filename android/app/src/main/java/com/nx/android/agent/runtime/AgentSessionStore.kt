package com.nx.android.agent.runtime

import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentRole
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class AgentSessionStore @Inject constructor(private val workspace: AgentWorkspace) {
    fun save(messages: List<AgentMessage>) {
        val dir = File(workspace.root, "sessions").apply { mkdirs() }
        val array = JSONArray()
        for (m in messages) array.put(JSONObject().put("id", m.id).put("role", m.role.name).put("text", m.text))
        File(dir, "session.json").writeText(array.toString(2))
    }

    fun load(): List<AgentMessage> {
        val f = File(workspace.root, "sessions/session.json")
        if (!f.isFile) return emptyList()
        val arr = runCatching { JSONArray(f.readText()) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val role = if (o.optString("role") == "user") AgentRole.USER else AgentRole.ASSISTANT
            AgentMessage(o.optLong("id", i.toLong()), role, o.optString("text"))
        }
    }

    fun clear() { File(workspace.root, "sessions/session.json").delete() }
}
