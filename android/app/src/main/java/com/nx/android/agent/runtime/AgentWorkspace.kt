package com.nx.android.agent.runtime

import com.nx.android.core.storage.AgentLayout
import com.nx.android.core.storage.AppPaths
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The assistant's own working folder: the rendered conversations, the memory, and the working
 * agreement the player can read and edit.
 *
 * The agreement is seeded twice from one text: in the workspace (the player's copy) and in the folder
 * the engine starts in — the engine loads its context file from the working directory, so that is
 * where the agreement has to be for the engine to read it.
 */
@Singleton
class AgentWorkspace @Inject constructor(private val paths: AppPaths) {

    val root: File = paths.agentWorkspace
    private val agentsMd: File get() = File(root, "AGENTS.md")
    private val memoryMd: File get() = File(root, "MEMORY.md")
    private val memoryDir: File get() = File(root, "memory")
    private val knowledgeMd: File get() = File(File(root, "knowledge"), "KNOWLEDGE.md")

    fun ensure(): File {
        root.mkdirs()
        seedAgreement()
        if (!memoryMd.isFile) memoryMd.writeText("")
        if (!knowledgeMd.isFile) { knowledgeMd.parentFile?.mkdirs(); knowledgeMd.writeText(SEED_KNOWLEDGE) }
        return root
    }

    /**
     * The agreement, in the two places it is read from: the engine's working directory (the engine
     * loads its context file from there) and the workspace (the player's copy).
     *
     * A file the player has edited is left alone; a file still carrying one of our seeds is brought
     * up to date.
     */
    private fun seedAgreement() {
        AgentLayout.seedAgreement(
            files = listOf(paths.agentAgreement, agentsMd),
            seed = SEED_AGENTS,
            knownSeeds = SEEDS,
        )
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

        /** The seeds we know: a file carrying one of them is ours, not the player's. */
        private val SEEDS: Set<String> get() = setOf(SEED_AGENTS, SEED_AGENTS_V1, SEED_AGENTS_V2, SEED_AGENTS_V0)

        private val SEED_AGENTS_V0 = "你是 nx2androidX 的内置助手，用简体中文回答。"
        private val SEED_AGENTS_V1 = """
            # 助手的工作约定

            你是 nx2androidX 的内置助手。nx2androidX 把 Switch 游戏移植到 Android 手机上运行：
            玩家在这里管理游戏库、做移植、在手机上运行游戏。

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

        private val SEED_AGENTS_V2 = """
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

        private val SEED_AGENTS = """
            # 助手的工作约定

            你是 nx2androidX（把 Switch 游戏移植到 Android 手机上运行的应用）的内置助手，用简体中文回答玩家。

            ## 行为
            - 结论落在事实上：先用工具查，查不到就说查不到，不要猜。
            - 回答简洁，直接回答玩家的问题；能给步骤就给步骤。
            - 玩家的消息里可能带「[页面上下文]」和「[助手记忆]」块：它们是玩家此刻的页面状态和你的长期记忆，
              回答优先结合它们；它们不是玩家说的话，不要复述。
            - 值得记住的经验（某台设备上某个游戏的修法、某个坑、某个开关的疗效）写进 MEMORY.md
              （agent/workspace/MEMORY.md），一行一条，新的写在最前面。
            - 你的回答指向某个页面时，告诉玩家该打开哪一页（例：建议重新构建之后，让玩家打开构建页）。

            ## 环境事实
            - 你在玩家的 nx2android 文件夹里工作：数据包在 ../ports/<游戏>-data/，游戏档案在 ../profiles/<title ID>/，
              移植工作区在 ${'$'}NX2A_WORK，移植器的工具树在 ${'$'}NX2A_REPO。
            - 日志在数据包目录里：log.txt 是最近一次运行，log.prev.txt 是上一次——崩溃的回溯在 log.prev.txt 里。
            - 更细的领域知识在 agent/workspace/knowledge/KNOWLEDGE.md，要查就去读它。
            - 命令里用相对路径或 ${'$'}NX2A_WORK、${'$'}NX2A_REPO 这样的变量，不要手写 /data/user/0/... 这样的全路径。
            - bash 的环境已经配好（PATH、库路径、python3 都直接可用），不要在命令前写 export。
            - 诊断以只读为主：要改文件、改配置或删东西，先跟玩家说清楚再动手。

            ## 绝对不做
            - 读取或输出 prod.keys 的内容。
            - 读取游戏文件内容。
            - 编造读数、日志内容或玩家的确认。
        """.trimIndent()

        private val SEED_KNOWLEDGE = "host 每次启动把上一次日志改名：log.txt 最近、log.prev.txt 上一次。"
    }
}
