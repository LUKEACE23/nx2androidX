package com.nx.android.agent.tools

import com.nx.android.appstate.LibraryState
import com.nx.android.domain.GameStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one piece of app state the agent's turns still carry as context: the library summary.
 * Everything else pi does itself — read files, run commands — through its own tools.
 */
@Singleton
class AgentTools @Inject constructor(private val library: LibraryState) {

    fun libraryStatus(): String {
        val games = library.visible()
        if (games.isEmpty()) return "游戏库是空的。"
        val done = games.count { library.statusOf(it) == GameStatus.DONE }
        val out = StringBuilder("游戏库共 ${games.size} 个，已移植 $done 个：")
        for ((i, e) in games.take(10).withIndex()) {
            out.append('\n').append(i + 1).append(". ").append(e.label)
                .append("（").append(e.titleId).append("）— ").append(statusLabel(library.statusOf(e)))
        }
        val rest = games.size - 10
        if (rest > 0) out.append("\n……还有 $rest 个未列出。")
        return out.toString()
    }

    private fun statusLabel(s: GameStatus): String = when (s) {
        GameStatus.NOT_PORTED -> "未移植"
        GameStatus.PORTING -> "移植中"
        GameStatus.DONE -> "已完成"
    }
}
