package com.nx.android.agent.tools

import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.PortState
import com.nx.android.data.GameLog
import com.nx.android.data.porting.BuildSessionRepository
import com.nx.android.domain.GameEntry
import com.nx.android.domain.GameStatus
import com.nx.android.domain.porting.PortPhase
import io.agentscope.core.tool.Tool
import io.agentscope.core.tool.ToolParam
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The assistant's read-only window on the app: the FAQ it answers from, the library it describes, and the
 * build log it reads. Every tool returns text for the model (Chinese, like the words around it); none of
 * them writes anything — the agent cannot start, stop or change a thing, it can only look.
 *
 * One rule is absolute here: nothing that might carry the player's keys leaves the app. A log line that
 * looks like one is replaced whole before the model sees it.
 */
@Singleton
class AgentTools @Inject constructor(
    private val faq: FaqIndex,
    private val library: LibraryState,
    private val port: PortState,
    private val sessions: BuildSessionRepository,
) {

    @Tool(
        name = "kb_search",
        description = "在 App 的常见问题（FAQ）知识库里检索。玩家问怎么用、出了什么状况时先查这里。",
        readOnly = true,
        concurrencySafe = true,
    )
    fun kbSearch(
        @ToolParam(name = "query", description = "检索词：玩家的原话，或从中取出的关键词")
        query: String,
    ): String = faq.ask(query)

    @Tool(
        name = "library_status",
        description = "查看游戏库摘要：有几个游戏、每个的标题与状态（未移植/移植中/已完成），以及当前构建在做什么。",
        readOnly = true,
        concurrencySafe = true,
    )
    fun libraryStatus(): String {
        val games = library.visible()
        if (games.isEmpty()) {
            return "游戏库是空的：还没有游戏被移植或导入。\n环境：${environment()}"
        }
        val done = games.count { library.statusOf(it) == GameStatus.DONE }
        val out = StringBuilder("游戏库共 ${games.size} 个游戏，其中已移植 ${done} 个：")
        for ((i, e) in games.take(LISTED_GAMES).withIndex()) {
            out.append('\n').append(i + 1).append(". ").append(e.label)
                .append("（").append(e.titleId)
            if (e.isImported) out.append("，导入")
            out.append("）— ").append(statusLabel(library.statusOf(e)))
        }
        val rest = games.size - LISTED_GAMES
        if (rest > 0) out.append('\n').append("……还有 ").append(rest).append(" 个未列出。")
        out.append('\n').append("环境：").append(environment())
        return out.toString()
    }

    @Tool(
        name = "build_log_tail",
        description = "读取最近一次构建（移植）日志的尾部，用来排查构建失败或被中断的原因。",
        readOnly = true,
        concurrencySafe = true,
    )
    fun buildLogTail(
        @ToolParam(name = "lines", description = "要读取的行数，1-100，默认 40")
        lines: Int,
    ): String {
        val n = (if (lines <= 0) DEFAULT_TAIL_LINES else lines).coerceIn(1, MAX_TAIL_LINES)
        val newest = port.history.firstOrNull()
        // the live buffer first (it is the running build, already in memory), the session's file second
        // (it survives a restart); both empty means nothing was ever built, or the log is gone — said as is
        val live = port.log.takeLast(n)
        val tail = if (live.isNotEmpty()) live
        else newest?.takeIf { it.logAvailable }?.let { sessions.tail(it.id, n) } ?: emptyList()
        if (tail.isEmpty()) {
            return if (newest == null) "没有构建记录。"
            else "最近一次构建（${File(newest.game).name}）没有留存日志。"
        }
        val head = StringBuilder("最近一次构建：")
        head.append(
            if (newest != null) "${File(newest.game).name}（${phaseLabel(newest.phase)}）"
            else port.gameFile.takeIf { it.isNotEmpty() }?.let { File(it).name } ?: "未知游戏"
        )
        head.append("，日志最后 ${tail.size} 行：")
        val out = StringBuilder(head)
        for (line in tail) out.append('\n').append(safeLine(line))
        return out.toString()
    }

    @Tool(
        name = "game_log",
        description = "读取某款游戏的运行日志（host 把自己的 stderr 写在游戏数据包里）。" +
            "排查闪退、起不来、黑屏时用这个。注意：崩溃的回溯通常在上一次运行的日志里" +
            "（host 每次启动都会把日志改名，所以崩的那次的日志留在 log.prev.txt）；" +
            "游戏重编过 runtime 的话，起不来的原因在 rebuild.log。",
        readOnly = true,
        concurrencySafe = true,
    )
    fun gameLog(
        @ToolParam(name = "game", description = "游戏名或 title ID；不给则取最近运行过的那个游戏")
        game: String?,
        @ToolParam(
            name = "which",
            description = "读哪份日志：last（最近一次运行）/ prev（上一次运行，崩溃回溯在这里）/ rebuild（runtime 重编日志）",
            required = false,
        )
        which: String = "last",
        @ToolParam(name = "lines", description = "要读取的行数，默认 60，上限 200", required = false)
        lines: Int = 60,
    ): String {
        val n = lines.coerceIn(1, MAX_GAME_LOG_LINES)
        val entry = resolveGame(game) ?: run {
            val recent = mostRecentlyRun()
                ?: return "没有找到游戏（给的是「$game」）。游戏库里现有的游戏见 library_status。"
            recent
        }
        val pack = library.packDirOf(entry) ?: return "游戏 ${entry.label} 的数据包不在本机。"
        val file = when (which.lowercase().trim()) {
            "prev" -> GameLog.previousInPack(pack)
            "rebuild" -> GameLog.rebuildInPack(pack)
            else -> GameLog.inPack(pack)
        } ?: return when (which.lowercase().trim()) {
            "rebuild" -> "${entry.label} 没有做过 runtime 重编，没有 rebuild.log。"
            "prev" -> "${entry.label} 只运行过一次，没有上一次的日志。"
            else -> "${entry.label} 还没有运行日志（游戏没启动过）。"
        }
        val tail = file.readLines().takeLast(n)
        if (tail.isEmpty()) return "${entry.label} 的日志是空的（${file.name}）。"
        val head = "${entry.label} 的${when (which.lowercase().trim()) {
            "prev" -> "上一次运行"
            "rebuild" -> "runtime 重编"
            else -> "最近一次运行"
        }}日志（${file.name}，最后 ${tail.size} 行）："
        val out = StringBuilder(head)
        for (line in tail) out.append('\n').append(safeLine(line))
        if (which == "prev") out.append("\n（崩溃回溯通常在这份里：找 backtrace、FATAL 或 signal 开头的行。）")
        return out.toString()
    }

    /** The entry a game word points at: by exact title ID, or by the label containing the words. */
    private fun resolveGame(game: String?): GameEntry? {
        val words = game?.trim() ?: return null
        if (words.isEmpty()) return null
        val byId = library.visible().firstOrNull { it.titleId.equals(words, ignoreCase = true) }
        if (byId != null) return byId
        return library.visible().firstOrNull { it.label.contains(words, ignoreCase = true) }
    }

    /** The game whose log was written most recently — the one the player just tried, most likely. */
    private fun mostRecentlyRun(): GameEntry? {
        var best: GameEntry? = null
        var bestAt = Long.MIN_VALUE
        for (e in library.visible()) {
            val pack = library.packDirOf(e) ?: continue
            val at = GameLog.inPack(pack)?.lastModified() ?: continue
            if (at > bestAt) {
                bestAt = at
                best = e
            }
        }
        return best
    }

    // ---- the words ----

    /** One honest sentence about what the machine is doing, from the states the tools already hold */
    private fun environment(): String {
        val scanning = if (library.scanning) "，正在扫描游戏库" else ""
        return when {
            port.running -> {
                val name = File(port.gameFile).name
                val stage = if (port.stages > 0) "（阶段 ${port.stageOf}/${port.stages}）" else ""
                "当前正在构建 $name$stage$scanning。"
            }
            port.history.isNotEmpty() -> {
                val last = port.history.first()
                "当前没有正在进行的构建；最近一次构建是 ${File(last.game).name}（${phaseLabel(last.phase)}）$scanning。"
            }
            else -> "当前没有正在进行的构建，也没有构建记录$scanning。"
        }
    }

    private fun statusLabel(s: GameStatus): String = when (s) {
        GameStatus.NOT_PORTED -> "未移植"
        GameStatus.PORTING -> "移植中"
        GameStatus.DONE -> "已完成"
    }

    private fun phaseLabel(p: PortPhase): String = when (p) {
        PortPhase.IDLE -> "未开始"
        PortPhase.RUNNING -> "进行中"
        PortPhase.DONE -> "成功"
        PortPhase.FAILED -> "失败"
        PortPhase.CANCELLED -> "已取消"
        PortPhase.INTERRUPTED -> "被系统中断"
    }

    /**
     * A line that looks like it carries a key (a "key=" in a long line — the porter's own lines name their
     * arguments, a dumped key would not be short) is replaced whole; any other line is only cut to the
     * line budget.
     */
    private fun safeLine(line: String): String =
        if (line.contains("key=") && line.length > SECRET_LINE_MIN) "[已隐藏]"
        else line.take(MAX_LINE_CHARS)

    companion object {
        private const val LISTED_GAMES = 10
        private const val DEFAULT_TAIL_LINES = 40
        private const val MAX_TAIL_LINES = 100
        private const val MAX_LINE_CHARS = 200
        private const val SECRET_LINE_MIN = 40
        private const val MAX_GAME_LOG_LINES = 200
    }
}
