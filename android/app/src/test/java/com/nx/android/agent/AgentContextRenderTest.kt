package com.nx.android.agent

import com.nx.android.agent.runtime.composeTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page context's ride into the model turn: the block is bounded, the player's words come last, and
 * without a context the message is just the words. Pure functions, JVM-fast.
 */
class AgentContextRenderTest {

    private val context = AgentPageContext(
        page = "builds",
        subject = AgentSubject("build", "b-2041", "游戏X"),
        facts = linkedMapOf(
            "阶段" to "着色器切分（3/6）",
            "失败码" to "E-SHADER-010",
            "日志尾行" to "[RED] alias budget exceeded",
        ),
    )

    @Test
    fun `with a context the block rides ahead of the words`() {
        val composed = composeTurn("为啥闪退", "", context)
        assertTrue(composed.startsWith("[页面上下文]"))
        assertTrue(composed.contains("游戏X"))
        assertTrue(composed.contains("E-SHADER-010"))
        assertTrue(composed.endsWith("玩家说：为啥闪退"))
    }

    @Test
    fun `without a context the message is just the words`() {
        assertEquals("为啥闪退", composeTurn("为啥闪退", "", null))
    }

    @Test
    fun `memory rides ahead of everything`() {
        val composed = composeTurn("试试", "某设备上某游戏的修法", context)
        assertTrue(composed.startsWith("[助手记忆] 某设备上某游戏的修法"))
        assertTrue(composed.indexOf("[助手记忆]") < composed.indexOf("[页面上下文]"))
        assertTrue(composed.endsWith("玩家说：试试"))
    }

    @Test
    fun `the block stays within the budget even for a chatty page`() {
        val chatty = context.copy(
            facts = linkedMapOf(
                "日志尾行" to "[RED] " + "alias budget exceeded ".repeat(80),
                "细节" to "说明文字".repeat(200),
            ),
        )
        val composed = composeTurn("怎么了", "", chatty)
        assertTrue(composed.length <= 800 + "…（上下文超预算，已截断）".length + "怎么了".length + 20)
        assertTrue(composed.contains("已截断"))
        // the player's words survive the cut: they come after the block
        assertTrue(composed.contains("玩家说：怎么了"))
    }

    @Test
    fun `a subject-less page still renders its facts`() {
        val bare = AgentPageContext(page = "settings", subject = null, facts = linkedMapOf("环境" to "就绪"))
        val composed = composeTurn("检查环境", "", bare)
        assertFalse(composed.contains("正在处理"))
        assertTrue(composed.contains("环境：就绪"))
        assertTrue(composed.endsWith("玩家说：检查环境"))
    }
}
