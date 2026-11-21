package com.nx.android.agent.runtime

import android.os.Handler
import android.os.Looper
import com.nx.android.agent.AgentUiActions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one-way bridge from the agent's tools (singletons, running on tool threads) to the UI's actions
 * (screen-scoped, main thread). The screen registers its actions on composition and unregisters on
 * dispose; until then the tools report that navigation is unavailable instead of pretending.
 *
 * The wait is bounded on purpose: a tool call is a blocking step in the agent's loop, and a UI that
 * never answers must not hang the turn.
 */
@Singleton
class AgentUiBridge @Inject constructor() {

    private val main = Handler(Looper.getMainLooper())

    /** The screen's actions; volatile because the tools read it from their own threads */
    @Volatile
    var actions: AgentUiActions? = null

    /** Run a UI navigation on the main thread and wait (bounded) for its result text. */
    fun navigate(page: String, game: String?): String {
        val actions = actions ?: return UNAVAILABLE
        val latch = CountDownLatch(1)
        var result = ""
        main.post {
            try {
                result = actions.navigate(page, game)
            } catch (e: Exception) {
                result = "打开页面时出错：${e.message ?: e.javaClass.simpleName}"
            } finally {
                latch.countDown()
            }
        }
        latch.await(3, TimeUnit.SECONDS)
        return result.ifBlank { "已执行导航。" }
    }

    companion object {
        const val UNAVAILABLE = "导航暂不可用（助手尚未挂接到界面）。请告诉玩家手动前往对应页面。"
    }
}
