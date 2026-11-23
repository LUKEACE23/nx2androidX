package com.nx.android.agent

import com.nx.android.agent.runtime.composeTurn
import com.nx.android.agent.runtime.deviceState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device state block the model reads: the app's own version leads it, and a long library
 * summary cannot push the version out of the block.
 */
class AgentDeviceStateTest {

    private val library = "游戏库共 12 个，已移植 10 个：\n" +
        (1..10).joinToString("\n") { "$it. 某游戏（0100000000010000）— 已完成" }

    @Test
    fun `the block leads with the app's version`() {
        val state = deviceState("0.4.0-alpha03", library, 600)

        assertTrue(state.startsWith("App 版本：0.4.0-alpha03"))
        assertTrue(state.contains("游戏库共 12 个"))
    }

    @Test
    fun `the version survives a chatty library`() {
        val chatty = library + "\n" + "11. 还有一个游戏（0100000000020000）— 已完成\n".repeat(40)
        val state = deviceState("0.4.0-alpha03", chatty, 600)

        assertTrue("the block stays within its budget", state.length <= 600)
        assertTrue("the version is still the first thing said", state.startsWith("App 版本：0.4.0-alpha03"))
    }

    @Test
    fun `the composed turn carries the version first`() {
        val state = deviceState("0.4.0-alpha03", library, 600)
        val composed = composeTurn("我的 App 什么版本", "", null, state)

        assertTrue(composed.startsWith("[本机状态] App 版本：0.4.0-alpha03"))
        assertTrue(composed.contains("游戏库共 12 个"))
        assertTrue(composed.endsWith("玩家说：我的 App 什么版本"))
    }
}
