package com.nx.android.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 这一轮的账：问题先落盘、流出的字跟着长、结束抹掉标记、写盘有节流。
 *
 * 纯 Kotlin：JVM 上直接跑，不碰引擎进程。
 */
class TurnJournalTest {

    private var now = 0L

    private fun journal(flushMs: Long = 2_000L) =
        TurnJournal(flushIntervalMs = flushMs, clock = { now })

    @Test
    fun `the question lands the moment the turn starts`() {
        val j = journal()
        j.beginTurn("为啥闪退")
        val items = j.snapshot()
        assertEquals(2, items.size)
        assertEquals(AgentRole.USER, items[0].role)
        assertEquals("为啥闪退", items[0].text)
    }

    @Test
    fun `the draft takes its slot and waits for the words`() {
        val j = journal()
        j.beginTurn("为啥闪退")
        assertEquals("", j.draftText())
        assertTrue(j.drafting())
    }

    @Test
    fun `the draft is marked unfinished while the turn runs`() {
        val j = journal()
        j.beginTurn("为啥闪退")
        assertTrue(j.snapshot().last().pending)
    }

    @Test
    fun `the words join the draft`() {
        val j = journal()
        j.beginTurn("为啥闪退")
        j.appendDelta("看日志")
        j.appendDelta("：E-SHADER-010")
        assertEquals("看日志：E-SHADER-010", j.draftText())
        assertEquals("看日志：E-SHADER-010", j.snapshot().last().text)
    }

    @Test
    fun `a finished turn keeps the words and clears the mark`() {
        val j = journal()
        j.beginTurn("为啥闪退")
        j.appendDelta("看日志")
        j.finishTurn(128L)
        val last = j.snapshot().last()
        assertFalse(last.pending)
        assertEquals("看日志", last.text)
        assertEquals(128L, last.tokens)
        assertFalse(j.drafting())
    }

    @Test
    fun `a stopped turn keeps the words and clears the mark`() {
        val j = journal()
        j.beginTurn("为啥闪退")
        j.appendDelta("看日志")
        j.stopTurn()
        val last = j.snapshot().last()
        assertFalse(last.pending)
        assertEquals("看日志", last.text)
        assertFalse(j.drafting())
    }

    @Test
    fun `a turn that died with nothing written leaves no shell`() {
        val j = journal()
        j.beginTurn("为啥闪退")
        j.failTurn()
        val items = j.snapshot()
        assertEquals(1, items.size)
        assertEquals(AgentRole.USER, items[0].role)
    }

    @Test
    fun `a turn that died after some words keeps them`() {
        val j = journal()
        j.beginTurn("为啥闪退")
        j.appendDelta("看日志")
        j.failTurn()
        assertEquals("看日志", j.snapshot().last().text)
    }

    @Test
    fun `the write is throttled`() {
        val j = journal(flushMs = 2_000L)
        assertTrue(j.dueForWrite())      // 第一次：还没写过
        assertFalse(j.dueForWrite())     // 紧接着：太密了
        now += 2_000L
        assertTrue(j.dueForWrite())      // 够久了
    }

    @Test
    fun `a write outside the throttle resets the clock`() {
        val j = journal(flushMs = 2_000L)
        now += 10_000L
        j.markWritten()
        assertFalse(j.dueForWrite())
        now += 2_000L
        assertTrue(j.dueForWrite())
    }

    @Test
    fun `another conversation replaces the history`() {
        val j = journal()
        j.beginTurn("这一句")
        j.appendDelta("回答")
        j.load(listOf(AgentMessage(7L, AgentRole.USER, "上一段对话")))
        assertEquals(1, j.snapshot().size)
        assertEquals("上一段对话", j.snapshot()[0].text)
        assertFalse(j.drafting())
    }

    @Test
    fun `the ids continue above the history that came in`() {
        val j = journal()
        j.load(listOf(AgentMessage(7L, AgentRole.USER, "上一段对话")))
        j.beginTurn("这一句")
        val items = j.snapshot()
        assertEquals(3, items.size)
        // 问题是 8，助手的草稿占 9（界面上也是这个顺序：先落问题，再开草稿）
        assertEquals(8L, items[1].id)
        assertEquals(9L, items[2].id)
    }
}
