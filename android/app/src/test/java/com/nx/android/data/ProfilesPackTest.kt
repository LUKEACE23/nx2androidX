package com.nx.android.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 档案包的时效：这一版的档案包里有、玩家那一份里没有 —— 提示去导入；玩家自己已经有了，或者是压根没有的游戏，
 * 什么都不说。
 */
class ProfilesPackTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val tid = "0100000000010000"

    private fun pack() = ProfilesPack(setOf(tid), folder.root)

    @Test
    fun `the package has the game and the player's layer does not - the player's package is older`() {
        assertTrue(pack().stale(tid))
    }

    @Test
    fun `the player's layer has the game - nothing to say`() {
        File(folder.root, tid).mkdirs()
        assertFalse(pack().stale(tid))
    }

    @Test
    fun `a game the package does not have is not stale`() {
        assertFalse(pack().stale("01000FFFFFFFF000"))
    }

    @Test
    fun `no game read yet is not stale`() {
        assertFalse(pack().stale(""))
    }
}