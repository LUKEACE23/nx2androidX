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
        // the v0 seed was one thin line and the file is seeded once: a device that still carries it
        // verbatim never saw the domain rules, so lift it (a hand-edited file stays untouched)
        else if (agentsMd.readText() == SEED_AGENTS_V0) agentsMd.writeText(SEED_AGENTS)
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

        private val SEED_AGENTS_V0 = "你是 nx2androidX 的内置助手，用简体中文回答。"

        private val SEED_AGENTS = """
            # 助手的工作约定

            你是 nx2androidX 的内置助手。nx2androidX 把 Switch 游戏移植到 Android 手机上运行：
            玩家在这里管理游戏库、做移植、在手机上运行游戏。

            ## 行为
            - 玩家说「运行失败」「闪退」，指的是本机 app 里的运行：先查游戏库和 game_log（崩溃回溯在 prev 那份里），不要谈 PC / Steam / 主机版。
            - 结论落在事实上：先用工具查，查不到就说查不到，不编造。
            - 把值得记住的经验（某台设备某个游戏的修法、某个坑）用 memory_save 存下来。
        """.trimIndent()
        private val SEED_KNOWLEDGE = "host 每次启动把上一次日志改名：log.txt 最近、log.prev.txt 上一次。"
    }
}
