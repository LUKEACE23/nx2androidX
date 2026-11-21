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
        val entry = resolve(game) ?: return notFound(game)
        // packDirOf 只在条目带着 pack 时有值；按 titleId 扫 ports 目录的 packsOf 才是可靠的查找
        val pack = library.packDirOf(entry)
            ?: library.packsOf(entry).maxByOrNull { it.lastModified() }
            ?: return "${entry.label}（${entry.titleId}）在本机没有数据包——它还没有移植完成，因此没有运行日志。" +
                "不要再对它调用 game_log，直接告诉玩家它还未移植。"
        val f = when (which) {
            "prev" -> GameLog.previousInPack(pack)
            "rebuild" -> GameLog.rebuildInPack(pack)
            else -> GameLog.inPack(pack)
        } ?: return when (which.lowercase().trim()) {
            "prev" -> "${entry.label} 只运行过一次，没有上一次的日志。直接据此回答玩家。"
            "rebuild" -> "${entry.label} 没有做过 runtime 重编，没有这份日志。"
            else -> "${entry.label} 还没有运行日志（还没有成功运行过）。"
        }
        return "${entry.label} 日志（${f.name}）：\n" +
            f.readLines().takeLast(lines.coerceIn(1, 200)).joinToString("\n") { safeLine(it) }
    }

    /**
     * The miss is a signpost, not a dead end: the model cannot know the library's labels (they are
     * the dump file names), so the failure hands it the actual candidates to retry with.
     */
    private fun notFound(words: String?): String {
        val guess = words?.take(40) ?: "（未指定）"
        val list = library.visible().take(12)
            .joinToString("；") { "${it.label}（${it.titleId}）" }
        return "游戏库里没有名字能对上「$guess」的游戏。库里的游戏是：$list。" +
            "请从这些名字里挑最接近的，用它的完整名字或 titleId 再查一次。"
    }

    fun statusLabel(s: GameStatus): String = when (s) {
        GameStatus.NOT_PORTED -> "未移植"
        GameStatus.PORTING -> "移植中"
        GameStatus.DONE -> "已完成"
    }

    /** a Switch title id: 16 hex chars — file names carry it inside brackets, players paste it bare */
    private val TITLE_ID = Regex("""[0-9A-Fa-f]{16}""")

    private fun resolve(words: String?): GameEntry? {
        if (words.isNullOrBlank()) return null
        val w = words.trim()
        // a title id pasted bare, or inside a dump file name like [本体v1.0.0][0100...][中文].xci
        TITLE_ID.find(w)?.let { id ->
            library.visible().firstOrNull { it.titleId.equals(id.value, true) }?.let { return it }
        }
        return library.visible().firstOrNull { it.titleId.equals(w, true) }
            ?: library.visible().firstOrNull { it.label.contains(w, true) }
    }

    private fun safeLine(line: String): String =
        if (line.contains("key=") && line.length > 40) "[已隐藏]" else line.take(200)
}
