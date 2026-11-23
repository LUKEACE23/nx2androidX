package com.nx.android.agent

import com.nx.android.agent.runtime.composeTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page context's ride into the model turn: the block is bounded, the player's words come last, and
 * without a context the message is just the words. Pure functions, JVM-fast.
 *
 * The injected blocks are English: the model works in English, and what the player reads is
 * translated at the end.
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
        assertTrue(composed.startsWith("[page context]"))
        assertTrue(composed.contains("游戏X"))
        assertTrue(composed.contains("E-SHADER-010"))
        assertTrue(composed.endsWith("player says: 为啥闪退"))
    }

    @Test
    fun `without a context the message is just the words`() {
        assertEquals("为啥闪退", composeTurn("为啥闪退", "", null))
    }

    @Test
    fun `memory rides ahead of everything`() {
        val composed = composeTurn("试试", "某设备上某游戏的修法", context)
        assertTrue(composed.startsWith("[memory] 某设备上某游戏的修法"))
        assertTrue(composed.indexOf("[memory]") < composed.indexOf("[page context]"))
        assertTrue(composed.endsWith("player says: 试试"))
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
        assertTrue(composed.length <= 800 + "...truncated".length + "怎么了".length + 20)
        assertTrue(composed.contains("truncated"))
        // the player's words survive the cut: they come after the block
        assertTrue(composed.contains("player says: 怎么了"))
    }

    @Test
    fun `a subject-less page still renders its facts`() {
        val bare = AgentPageContext(page = "settings", subject = null, facts = linkedMapOf("环境" to "就绪"))
        val composed = composeTurn("检查环境", "", bare)
        assertFalse(composed.contains("working on"))
        assertTrue(composed.contains("环境: 就绪"))
        assertTrue(composed.endsWith("player says: 检查环境"))
    }
}
