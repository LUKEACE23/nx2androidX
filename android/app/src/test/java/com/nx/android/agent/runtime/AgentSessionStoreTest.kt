package com.nx.android.agent.runtime

import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentRole
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 会话文件：这一轮没收尾的标记写得进、读得出；老文件没有这个字段也照常读。
 */
class AgentSessionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): AgentSessionStore = AgentSessionStore(folder.newFolder("sessions"))

    @Test
    fun `a session with an unfinished turn comes back marked`() {
        val store = store()
        store.save(
            listOf(
                AgentMessage(0L, AgentRole.USER, "为啥闪退"),
                AgentMessage(1L, AgentRole.ASSISTANT, "看日志", pending = true),
            ),
        )
        val back = store.loadActive()
        assertEquals(2, back.size)
        assertTrue(back[1].pending)
        assertEquals("看日志", back[1].text)
    }

    @Test
    fun `a finished turn comes back unmarked`() {
        val store = store()
        store.save(listOf(AgentMessage(1L, AgentRole.ASSISTANT, "看日志")))
        assertFalse(store.loadActive().last().pending)
    }

    @Test
    fun `an older session file without the mark still loads`() {
        val dir = folder.newFolder("sessions")
        File(dir, "s-1.json").writeText(
            """[{"id":0,"role":"user","text":"为啥闪退"},{"id":1,"role":"assistant","text":"看日志"}]""",
        )
        File(dir, "_active").writeText("s-1")
        val back = AgentSessionStore(dir).loadActive()
        assertEquals(2, back.size)
        assertEquals("看日志", back[1].text)
        assertFalse(back[1].pending)
    }

    @Test
    fun `the title is still the first words of the question`() {
        val store = store()
        store.save(listOf(AgentMessage(0L, AgentRole.USER, "为啥这个游戏闪退")))
        assertEquals("为啥这个游戏闪退", store.list().first().title)
    }

    @Test
    fun `the context occupancy survives the write`() {
        val store = store()
        store.save(listOf(AgentMessage(1L, AgentRole.ASSISTANT, "看日志")), 64_000L, 200_000L)
        val meta = store.list().first()
        assertEquals(64_000L, meta.contextTokens)
        assertEquals(200_000L, meta.contextWindow)
    }
}
