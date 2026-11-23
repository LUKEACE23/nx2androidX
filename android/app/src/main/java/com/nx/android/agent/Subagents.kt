package com.nx.android.agent

import org.json.JSONObject

/**
 * The sub-agents the assistant spawned: what they were asked to do, how they went, and where
 * their own conversation lives.
 *
 * The engine's extension writes these into the session; the app only reads them.
 */
data class AgentSubagent(
    /** The sub-agent's own id (the extension's handle for that run). */
    val id: String,
    /** The kind of agent ("general-purpose"). */
    val type: String,
    /** The short task, in the model's own words. */
    val description: String,
    /** "running", "completed", "error", "aborted", "steered", "stopped". */
    val status: String,
    /** What it answered, when it answered. */
    val result: String? = null,
    val error: String? = null,
    val startedAt: Long = 0L,
    val completedAt: Long? = null,
    /** The sub-agent's own session file: its whole conversation. */
    val sessionFile: String? = null,
    /** The task it was given, read out of its own conversation. */
    val prompt: String? = null,
    /** What it generated on its own. */
    val tokens: Long = 0L,
    /** While it runs: the step it is at right now ("bash · log_triage.py"). */
    val step: String? = null,
) {
    /** It is still working. */
    val running: Boolean get() = status == "running"

    /** It stopped without an answer. */
    val failed: Boolean get() = status == "error" || status == "aborted"

    /** How long it took, in milliseconds (null while it still runs). */
    val durationMs: Long? get() = completedAt?.let { (it - startedAt).coerceAtLeast(0L) }
}

/**
 * The completion notice: a sub-agent is done, and here is what it says.
 *
 * The engine sends it into the conversation as a message of its own; the app turns it into a card.
 */
data class AgentNotification(
    /** The sub-agent's id (the notice names it). */
    val id: String,
    /** "Done", "Error: …", "Stopped". */
    val status: String,
    /** The one-line summary the engine wrote. */
    val summary: String,
    /** The answer, trimmed. */
    val result: String? = null,
    val totalTokens: Long = 0L,
    val toolUses: Long = 0L,
    val durationMs: Long = 0L,
    /** The tool call that spawned the sub-agent: the notice points back at it. */
    val toolCallId: String? = null,
)

/**
 * The sub-agent a tool call spawned: its type, the short task and the prompt it was given.
 *
 * The prompt is kept because it is the only exact link between a running sub-agent's transcript
 * and the call that started it.
 */
data class AgentSubagentCall(
    val type: String,
    val description: String,
    val prompt: String,
)

/** Reads the sub-agent out of a tool call's arguments. */
object AgentSubagentCallParser {

    /** The tool the assistant uses to spawn a sub-agent. */
    const val TOOL_NAME = "Agent"

    /** The call's own words, or null when this is not a sub-agent call. */
    fun of(name: String, arguments: JSONObject?): AgentSubagentCall? {
        if (name != TOOL_NAME) return null
        val args = arguments ?: return null
        val type = args.optString("subagent_type").trim()
        val description = args.optString("description").trim()
        val prompt = args.optString("prompt").trim()
        if (type.isEmpty() && description.isEmpty() && prompt.isEmpty()) return null
        return AgentSubagentCall(type, description, prompt)
    }
}
