package com.nx.android.agent

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 游戏页给助手的上下文：这局是什么、它的攻关档案在哪。
 */
class GameContextTest {

    @Test
    fun `the game page tells the assistant which game it is`() {
        val context = gameContext("0100A3501946E000", "八方旅人2", File("/data/user/0/x/files/nx2a/gamespec/0100A3501946E000"))
        assertEquals("game", context.page)
        assertEquals("0100A3501946E000", context.subject?.id)
        assertEquals("八方旅人2", context.subject?.title)
    }

    @Test
    fun `the game page tells the assistant where the case file is`() {
        val dir = File("/data/user/0/x/files/nx2a/gamespec/0100A3501946E000")
        val context = gameContext("0100A3501946E000", "八方旅人2", dir)
        val where = context.facts.values.first()
        assertEquals(dir.absolutePath, where)
        assertTrue(context.facts.keys.first().contains("攻关档案"))
    }

    @Test
    fun `no game means no context`() {
        // 不在游戏页时助手没有当前游戏：档案位置由它自己按 title ID 推
        assertNull(AgentPageContext(page = "library").subject)
    }

    @Test
    fun `the library page says which card the player picked`() {
        val dir = File("/data/user/0/x/files/nx2a/gamespec/010055D009F78000")
        val context = gameContext("010055D009F78000", "Fire Emblem: Three Houses", dir, page = "library")
        assertEquals("library", context.page)
        assertEquals("010055D009F78000", context.subject?.id)
        assertEquals("Fire Emblem: Three Houses", context.subject?.title)
        assertEquals(dir.absolutePath, context.facts.values.first())
    }
}
