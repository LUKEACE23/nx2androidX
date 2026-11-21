package com.nx.android.agent.tools

import com.nx.android.appstate.LibraryState
import com.nx.android.data.GameLog
import com.nx.android.domain.GameEntry
import com.nx.android.domain.GameStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentTools @Inject constructor(private val library: LibraryState) {

    fun kbSearch(query: String): String = "FAQ 检索：$query（待接入 faq_zh.json）"

    fun libraryStatus(): String {
        val games = library.visible()
        if (games.isEmpty()) return "游戏库是空的。"
        val done = games.count { library.statusOf(it) == GameStatus.DONE }
        val out = StringBuilder("游戏库共 ${games.size} 个，已移植 $done 个：")
        for ((i, e) in games.take(10).withIndex()) {
            out.append('\n').append(i + 1).append(". ").append(e.label).append("—").append(statusLabel(library.statusOf(e)))
        }
        return out.toString()
    }

    fun buildLogTail(lines: Int): String {
        val n = lines.coerceIn(1, 100)
        return "构建日志尾部（待接入 PortState）：$n 行"
    }

    fun gameLog(game: String?, which: String, lines: Int): String {
        val entry = resolve(game) ?: return "没有找到游戏。"
        val pack = library.packDirOf(entry) ?: return "${entry.label} 的数据包不在本机。"
        val f = when (which) {
            "prev" -> GameLog.previousInPack(pack)
            "rebuild" -> GameLog.rebuildInPack(pack)
            else -> GameLog.inPack(pack)
        } ?: return "${entry.label} 没有这份日志。"
        return "${entry.label} 日志（${f.name}）：\n" + f.readLines().takeLast(lines.coerceIn(1, 200)).joinToString("\n") { safeLine(it) }
    }

    fun statusLabel(s: GameStatus): String = when (s) {
        GameStatus.NOT_PORTED -> "未移植"
        GameStatus.PORTING -> "移植中"
        GameStatus.DONE -> "已完成"
    }

    private fun resolve(words: String?): GameEntry? {
        if (words.isNullOrBlank()) return null
        return library.visible().firstOrNull { it.titleId.equals(words, true) }
            ?: library.visible().firstOrNull { it.label.contains(words, true) }
    }

    private fun safeLine(line: String): String =
        if (line.contains("key=") && line.length > 40) "[已隐藏]" else line.take(200)
}
