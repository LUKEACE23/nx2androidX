package com.nx.android.agent.runtime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentWorkspace @Inject constructor(@ApplicationContext ctx: Context) {
    val root: File = File(ctx.filesDir, "agent/workspace")
    private val agentsMd: File get() = File(root, "AGENTS.md")
    val memoryMd: File get() = File(root, "MEMORY.md")
    private val memoryDir: File get() = File(root, "memory")
    private val knowledgeMd: File get() = File(File(root, "knowledge"), "KNOWLEDGE.md")

    fun ensure(): File {
        root.mkdirs()
        if (!agentsMd.isFile) agentsMd.writeText(SEED_AGENTS)
        if (!memoryMd.isFile) memoryMd.writeText("")
        if (!knowledgeMd.isFile) { knowledgeMd.parentFile?.mkdirs(); knowledgeMd.writeText(SEED_KNOWLEDGE) }
        return root
    }

    fun readMemory(): String = memoryMd.takeIf { it.isFile }?.readText() ?: ""
    fun readAgents(): String = agentsMd.takeIf { it.isFile }?.readText() ?: ""

    fun remember(entry: String) {
        memoryDir.mkdirs()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        File(memoryDir, "${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.md")
            .appendText("\n## ${fmt.format(Date())}\n$entry\n")
        memoryMd.writeText(curate(readMemory(), entry, MEMORY_MAX))
    }

    internal companion object {
        const val MEMORY_MAX = 4000
        const val MEMORY_INJECT = 1200

        internal fun curate(existing: String, entry: String, max: Int): String {
            val line = "- $entry"
            val merged = if (existing.isBlank()) line else "$line\n\n$existing"
            if (merged.length <= max) return merged
            val cut = merged.take(max)
            val keep = cut.lastIndexOf("\n\n")
            return if (keep > 0) cut.take(keep).trim() else cut.trim()
        }

        private val SEED_AGENTS = "你是 nx2androidX 的内置助手，用简体中文回答。"
        private val SEED_KNOWLEDGE = "host 每次启动把上一次日志改名：log.txt 最近、log.prev.txt 上一次。"
    }
}
