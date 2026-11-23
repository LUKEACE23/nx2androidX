package com.nx.android.agent

import com.nx.android.agent.runtime.composeTurn
import com.nx.android.agent.runtime.deviceState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device state block the model reads: the app's own version leads it, and a long library
 * summary cannot push the version out of the block.
 *
 * The block is English, and it carries the player's language along: the model works in English
 * and translates for the player, so it has to know which language the player reads.
 */
class AgentDeviceStateTest {

    private val library = "the library has 12 game(s), 10 of them ported:\n" +
        (1..10).joinToString("\n") { "$it. 某游戏 (0100000000010000) - done" }

    @Test
    fun `the block leads with the app's version`() {
        val state = deviceState("0.4.0-alpha03", library, 600)

        assertTrue(state.startsWith("app version: 0.4.0-alpha03"))
        assertTrue(state.contains("the library has 12 game(s)"))
    }

    @Test
    fun `the version survives a chatty library`() {
        val chatty = library + "\n" + "11. 还有一个游戏 (0100000000020000) - done\n".repeat(40)
        val state = deviceState("0.4.0-alpha03", chatty, 600)

        assertTrue("the block stays within its budget", state.length <= 600)
        assertTrue("the version is still the first thing said", state.startsWith("app version: 0.4.0-alpha03"))
    }

    @Test
    fun `the composed turn carries the version first`() {
        val state = deviceState("0.4.0-alpha03", library, 600)
        val composed = composeTurn("我的 App 什么版本", "", null, state)

        assertTrue(composed.startsWith("[device] app version: 0.4.0-alpha03"))
        assertTrue(composed.contains("the library has 12 game(s)"))
        assertTrue(composed.endsWith("player says: 我的 App 什么版本"))
    }

    @Test
    fun `the player's language rides along`() {
        val state = deviceState("0.4.0-alpha03", library, 600, "Chinese (zh)")

        assertTrue(state.contains("player language: Chinese (zh)"))
    }

    @Test
    fun `the block tells the model what the player reads`() {
        // 模型不照约定走的时候，这一行是它每轮都看得见的：它自己写给玩家看的字
        val state = deviceState("0.4.0-alpha03", library, 600, "Chinese (zh)")

        assertTrue(state.contains("every word the player sees must be in that language"))
        assertTrue(state.contains("the label on a sub-agent card"))
    }

    @Test
    fun `without a language the block says nothing about one`() {
        val state = deviceState("0.4.0-alpha03", library, 600)

        assertTrue(!state.contains("player language"))
    }
}
