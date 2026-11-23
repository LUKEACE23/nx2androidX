package com.nx.android.agent.env

import java.io.File
import java.io.RandomAccessFile
import org.json.JSONObject

/**
 * The transcripts of the sub-agents that are running right now.
 *
 * The engine's extension streams every sub-agent's conversation into a JSONL file under the
 * scratch folder — one file per sub-agent, named after its id. The lines carry the engine's own
 * messages, so the app reads them with the same mapper the main conversation uses.
 *
 * The scratch folder's own shape (the uid, the encoded working directory) belongs to the engine;
 * the app only looks for the folder named after the parent session's id.
 */
object SubagentTranscripts {

    /** The scratch folders the engine writes into, under the app's tmp. */
    private const val ROOT = "pi-subagents-"

    /** How much of a transcript's tail is read: enough to see what it is doing. */
    private const val CHUNK = 128 * 1024

    /** How many lines of the tail are kept. */
    const val MAX_LINES = 200

    /** One running sub-agent's transcript. */
    data class Transcript(
        /** The sub-agent's id (the file's own name). */
        val agentId: String,
        /** The prompt it was given: its first message. */
        val prompt: String,
        /** The engine's messages, as they came in. */
        val messages: List<JSONObject>,
        /** When the transcript was last written: the sub-agent's own clock. */
        val spawnedAt: Long = 0L,
    )

    /** The tasks folder of one parent session, or null when there is none. */
    fun taskDir(scratch: File, parentSessionId: String): File? {
        if (!scratch.isDirectory || parentSessionId.isBlank()) return null
        val roots = scratch.listFiles { f -> f.isDirectory && f.name.startsWith(ROOT) } ?: return null
        for (root in roots) {
            // <root>/<the encoded working directory>/<the session's id>/tasks
            val dirs = root.listFiles { f -> f.isDirectory } ?: continue
            for (dir in dirs) {
                val hit = File(dir, parentSessionId)
                val tasks = File(hit, "tasks")
                if (tasks.isDirectory) return tasks
            }
        }
        return null
    }

    /** The transcripts of one parent session, in the order the sub-agents were spawned. */
    fun of(scratch: File, parentSessionId: String): List<Transcript> {
        val dir = taskDir(scratch, parentSessionId) ?: return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".output") } ?: return emptyList()
        return files.mapNotNull { file ->
            val messages = tail(file, MAX_LINES)
            if (messages.isEmpty()) return@mapNotNull null
            Transcript(
                agentId = file.name.removeSuffix(".output"),
                prompt = textOf(messages.first()),
                messages = messages,
                spawnedAt = file.lastModified(),
            )
        }.sortedBy { it.spawnedAt }
    }

    /**
     * The tail of a transcript: the last whole lines of the file.
     *
     * Only whole lines count — the first one may be cut in half when the file is bigger than the
     * chunk that was read. A line that does not parse is skipped: the file is being written while
     * it is read.
     */
    fun tail(file: File, maxLines: Int): List<JSONObject> {
        if (!file.isFile) return emptyList()
        val size = file.length()
        if (size <= 0L) return emptyList()
        val want = minOf(size, CHUNK.toLong()).toInt()
        val buffer = ByteArray(want)
        runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(size - want)
                raf.readFully(buffer)
            }
        }.onFailure { return emptyList() }
        val text = String(buffer, Charsets.UTF_8)
        val lines = text.split('\n')
        // a chunk that did not start at the file's beginning cut the first line in half
        val whole = if (want < size) lines.drop(1) else lines
        val parsed = whole.filter { it.isNotBlank() }.mapNotNull { line ->
            val entry = runCatching { JSONObject(line) }.getOrNull() ?: return@mapNotNull null
            entry.optJSONObject("message")
        }
        return if (parsed.size > maxLines) parsed.subList(parsed.size - maxLines, parsed.size) else parsed
    }

    /** Every message of a session file, in order. */
    fun messages(file: File): List<JSONObject> {
        if (!file.isFile) return emptyList()
        val out = mutableListOf<JSONObject>()
        runCatching {
            file.forEachLine { line ->
                val entry = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                entry.optJSONObject("message")?.let { out.add(it) }
            }
        }
        return out
    }

    /** The words of one message, whichever shape its content has. */
    fun textOf(message: JSONObject): String {
        val content = message.opt("content")
        if (content is String) return content
        val parts = message.optJSONArray("content") ?: return ""
        val out = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optString("type") == "text") out.append(part.optString("text"))
        }
        return out.toString()
    }
}
