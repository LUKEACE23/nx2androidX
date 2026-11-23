package com.nx.android.agent.env

import com.nx.android.agent.AgentSubagent
import com.nx.android.agent.AgentSubagentCall
import com.nx.android.core.storage.AppPaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * The sub-agents of one session: the runs that finished and the ones still going.
 *
 * Both come from the engine's own files — the records it writes into the session, and the
 * transcripts it streams while a sub-agent works. Nothing here talks to the engine.
 */
@Singleton
class SubagentStore @Inject constructor(
    private val paths: AppPaths,
) {

    /** What a finished run's own conversation said: the file's stamp and what was read out of it. */
    private val details = mutableMapOf<String, Pair<Long, Pair<String?, Long>>>()

    /** The finished runs of one session, oldest first. */
    fun records(sessionFile: String?): List<AgentSubagent> =
        if (sessionFile.isNullOrBlank()) emptyList() else SubagentRecords.of(File(sessionFile))

    /** The runs that are still going: their transcripts, as they are being written. */
    fun live(sessionFile: String?): List<SubagentTranscripts.Transcript> {
        val id = sessionIdOf(sessionFile) ?: return emptyList()
        return SubagentTranscripts.of(paths.tmp, id)
    }

    /**
     * The list the player sees: the finished runs and the running ones, oldest first.
     *
     * The finished ones also carry what their own conversation says: the task they were given and
     * what they generated. That is how the run card in the conversation finds its run — the task
     * is the one thing both sides know.
     */
    fun list(sessionFile: String?, calls: List<AgentSubagentCall>): List<AgentSubagent> {
        val finished = records(sessionFile).map { record ->
            val (prompt, tokens) = detailsOf(record)
            record.copy(prompt = prompt, tokens = tokens)
        }
        return SubagentRuns.merge(finished, live(sessionFile), calls)
    }

    /**
     * A finished run's own words: the task it was given, and what it generated.
     *
     * Read out of its own session file, and remembered: the file does not change once the run is
     * over, and the list is read again and again while the player watches it.
     */
    private fun detailsOf(record: AgentSubagent): Pair<String?, Long> {
        val path = record.sessionFile ?: return null to 0L
        val file = File(path)
        if (!file.isFile) return null to 0L
        val stamp = file.lastModified()
        val cached = details[path]
        if (cached != null && cached.first == stamp) return cached.second
        val messages = SubagentTranscripts.messages(file)
        val prompt = messages.firstOrNull { it.optString("role") == "user" }
            ?.let { SubagentTranscripts.textOf(it) }
            ?.takeIf { it.isNotBlank() }
        val tokens = messages.filter { it.optString("role") == "assistant" }
            .sumOf { it.optJSONObject("usage")?.optLong("output", 0L) ?: 0L }
        val details = prompt to tokens
        this.details[path] = stamp to details
        return details
    }

    /**
     * The whole conversation of one sub-agent: its own session file when it has one, its
     * transcript while it is still running. Empty when neither can be read.
     */
    fun conversation(subagent: AgentSubagent, sessionFile: String?): List<JSONObject> {
        val own = subagent.sessionFile
        if (!own.isNullOrBlank()) {
            val messages = SubagentTranscripts.messages(File(own))
            if (messages.isNotEmpty()) return messages
        }
        val id = sessionIdOf(sessionFile) ?: return emptyList()
        return live(sessionFile).firstOrNull { it.agentId == subagent.id }?.messages.orEmpty()
    }

    /** The session id in a session file's name: `<time>_<id>.jsonl`. */
    private fun sessionIdOf(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val name = File(path).name.removeSuffix(".jsonl")
        val cut = name.lastIndexOf('_')
        if (cut < 0 || cut == name.length - 1) return null
        return name.substring(cut + 1)
    }
}

/**
 * How the finished runs and the running ones become one list.
 *
 * A finished run wins over a transcript of the same id (the record knows the outcome). A running
 * one takes its type and its task from the tool call that spawned it — the prompt is the exact
 * link between the two.
 */
object SubagentRuns {

    /** The runs the player sees, oldest first. */
    fun merge(
        finished: List<AgentSubagent>,
        live: List<SubagentTranscripts.Transcript>,
        calls: List<AgentSubagentCall>,
    ): List<AgentSubagent> {
        val done = finished.associateBy { it.id }
        val running = live.mapNotNull { transcript ->
            val id = transcript.agentId
            if (done.containsKey(id)) return@mapNotNull null
            val call = calls.firstOrNull { it.prompt.isNotBlank() && it.prompt == transcript.prompt }
            AgentSubagent(
                id = id,
                type = call?.type.orEmpty(),
                description = call?.description?.takeIf { it.isNotBlank() }
                    ?: transcript.prompt.lineSequence().firstOrNull()?.trim().orEmpty(),
                status = "running",
                startedAt = transcript.spawnedAt,
            )
        }
        return (done.values + running).sortedBy { it.startedAt }
    }
}
