package com.nx.android.agent.runtime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The assistant's workspace (ADR-007's growth channel): the directory where the agent's persona, long-term
 * memory and domain knowledge live as plain markdown — the layout mirrors AgentScope's harness workspace so
 * the files can move to a fuller implementation later without a rewrite.
 *
 * Three lifecycles share the tree, on purpose:
 * - AGENTS.md — the working conventions, seeded once, **editable by hand**: edits shape the assistant
 *   without a rebuild (the system prompt keeps only the identity line).
 * - MEMORY.md + memory/YYYY-MM-DD.md — the lessons learned, written by the agent through memory_save;
 *   MEMORY.md is what gets injected every turn, newest first, capped.
 * - knowledge/KNOWLEDGE.md — the facts we learned the hard way (where the crash backtrace lives, ...).
 *
 * The session JSON (the rendered conversation) is *not* in here — see [AgentSessionStore].
 */
@Singleton
class AgentWorkspace @Inject constructor(@ApplicationContext ctx: Context) {

    val root: File = File(ctx.filesDir, "agent/workspace")

    val agentsMd: File get() = File(root, "AGENTS.md")
    val memoryMd: File get() = File(root, "MEMORY.md")
    private val memoryDir: File get() = File(root, "memory")
    private val knowledgeMd: File get() = File(File(root, "knowledge"), "KNOWLEDGE.md")

    /** Create the tree and seed the files that are missing. Cheap enough to call on every turn. */
    fun ensure(): File {
        root.mkdirs()
        seedIfAbsent(agentsMd, SEED_AGENTS_MD)
        seedIfAbsent(memoryMd, "")
        seedIfAbsent(knowledgeMd, SEED_KNOWLEDGE)
        return root
    }

    /** The working conventions as they are on disk ("" when the file went missing). */
    fun readAgents(): String = read(agentsMd)

    /** The curated long-term memory as it is on disk ("" when nothing was learned yet). */
    fun readMemory(): String = read(memoryMd)

    /**
     * Record a lesson: the dated daily file takes it appended, and the curated MEMORY.md takes it
     * prepended (newest first) — trimmed to [MEMORY_MAX_CHARS] from the tail, so the oldest lessons go
     * first when the cap bites. The pure part is [curateMemory]; this wrapper only does the files.
     */
    fun remember(entry: String) {
        memoryDir.mkdirs()
        val daily = File(memoryDir, dailyName())
        daily.appendText("\n## ${timestamp()}\n$entry\n")
        memoryMd.writeText(curateMemory(readMemory(), entry, MEMORY_MAX_CHARS))
    }

    private fun read(f: File): String = f.takeIf { it.isFile }?.readText() ?: ""

    private fun seedIfAbsent(f: File, content: String) {
        if (!f.isFile) {
            f.parentFile?.mkdirs()
            f.writeText(content)
        }
    }

    companion object {
        const val MEMORY_MAX_CHARS = 4000
        const val MEMORY_INJECT_CHARS = 1200

        private fun dailyName(): String {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return format.format(Date())
        }

        private fun timestamp(): String {
            val format = SimpleDateFormat("HH:mm", Locale.US)
            return format.format(Date())
        }

        /**
         * The curated memory after one more lesson: the entry goes to the top (newest first), the file
         * keeps at most [maxChars] — the tail is cut at the last paragraph boundary that fits, so a
         * lesson is never cut mid-sentence (that pure part is what the tests drive).
         */
        internal fun curateMemory(existing: String, entry: String, maxChars: Int): String {
            val line = "- $entry"
            val merged = if (existing.isBlank()) line else "$line\n\n$existing"
            if (merged.length <= maxChars) return merged
            val cut = merged.take(maxChars)
            val keep = cut.lastIndexOf("\n\n")
            return if (keep > 0) cut.take(keep).trim() else cut.trim()
        }

        private val SEED_AGENTS_MD = """
            # 助手的工作约定

            你是 nx2androidX（把 Switch 游戏移植到 Android 的应用）的内置助手，用简体中文回答玩家。

            ## 行为
            - 结论落在事实上：先用工具查，查不到就说查不到，不要猜。
            - 回答简洁，直接回答玩家的问题；能给步骤就给步骤。
            - 玩家的消息里可能带「[页面上下文]」和「[助手记忆]」块：它们是玩家此刻的页面状态和你的长期记忆，
              回答优先结合它们；它们不是玩家说的话，不要复述。
            - 把值得记住的经验用 memory_save 存下来：某台设备上某个游戏的修法、某个坑、某个开关的疗效。
            - 你的回答指向某个页面时，用 navigate 把玩家带过去（例：建议重新构建之后，打开构建页）。

            ## 绝对不做
            - 读取或输出 prod.keys 的内容。
            - 读取游戏文件内容。
            - 修改任何设置或文件——你没有任何写工具，不要假装有，也不要建议玩家手动改文件。
            - 编造读数、日志内容或玩家的确认。
        """.trimIndent()

        private val SEED_KNOWLEDGE = """
            # 领域知识

            ## 崩溃回溯在哪份日志里
            host 每次启动都会把上一次的运行日志改名：`log.txt` 是最近一次，`log.prev.txt` 是上一次——
            **崩溃的回溯（backtrace）在 log.prev.txt 里**，因为崩的那次结束后没有新的运行来覆盖它。

            ## exit 137 不是移植失败
            移植中途「退出 137」是 Android 的子进程数量限制把移植器停掉了（Android 12+ 默认 32 个），
            处理入口在顶栏环境胶囊的「子进程数量限制」。
        """.trimIndent()
    }
}
