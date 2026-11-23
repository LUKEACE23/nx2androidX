package com.nx.android.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversation's own steps: the words land, the answer streams into one message, the tools
 * fold into it, the turn ends and the failure takes the empty shell away. Plain Kotlin, JVM-fast.
 */
class AgentConversationTest {

    private fun card(id: String, state: AgentToolState = AgentToolState.RUNNING) =
        AgentToolCard(id = id, name = "bash", argsSummary = "ls -l", state = state)

    @Test
    fun `the player's words land as their own message`() {
        val c = AgentConversation()
        c.say("为啥闪退")
        assertEquals(1, c.messages.size)
        assertEquals(AgentRole.USER, c.messages[0].role)
        assertEquals("为啥闪退", c.messages[0].text)
    }

    @Test
    fun `the streamed words join the answer being written`() {
        val c = AgentConversation()
        c.say("为啥闪退")
        c.begin()
        c.apply(AgentEvent.TextDelta("先看"))
        c.apply(AgentEvent.TextDelta("日志"))
        assertEquals(2, c.messages.size)
        assertEquals(AgentRole.ASSISTANT, c.messages[1].role)
        assertEquals("先看日志", c.messages[1].text)
        assertEquals(c.messages[1].id, c.streamingId)
        assertTrue(c.running)
    }

    @Test
    fun `a batch of deltas is one answer`() {
        val c = AgentConversation()
        c.begin()
        c.appendText("看日志：")
        c.appendText("E-SHADER-010")
        assertEquals("看日志：E-SHADER-010", c.messages.last().text)
    }

    @Test
    fun `the tools fold into the answer`() {
        val c = AgentConversation()
        c.begin()
        c.apply(AgentEvent.ToolStarted(card("t1")))
        c.apply(AgentEvent.ToolFinished("t1", AgentToolState.DONE, "3 files"))
        val tools = c.messages.last().tools
        assertEquals(1, tools.size)
        assertEquals(AgentToolState.DONE, tools[0].state)
        assertEquals("3 files", tools[0].resultSummary)
    }

    @Test
    fun `the turn's end settles the answer`() {
        val c = AgentConversation()
        c.begin()
        c.apply(AgentEvent.TextDelta("看日志"))
        c.apply(AgentEvent.TurnDone("看日志：E-SHADER-010"))
        assertEquals("看日志：E-SHADER-010", c.messages.last().text)
        assertNull(c.streamingId)
        assertEquals(false, c.running)
    }

    @Test
    fun `the answer carries the tokens it cost`() {
        val c = AgentConversation()
        c.begin()
        c.apply(AgentEvent.TextDelta("看日志"))
        c.apply(AgentEvent.TurnDone("看日志：E-SHADER-010", tokens = 128L))
        assertEquals(128L, c.messages.last().tokens)
    }

    @Test
    fun `an older answer without tokens shows nothing`() {
        val c = AgentConversation()
        c.load(listOf(AgentMessage(3L, AgentRole.ASSISTANT, "上一轮")))
        assertEquals(0L, c.messages.last().tokens)
    }

    @Test
    fun `a failed turn takes the empty shell away`() {
        val c = AgentConversation()
        c.begin()
        c.apply(AgentEvent.Failed(AgentFailure.NETWORK))
        assertEquals(0, c.messages.size)
        assertEquals(AgentFailure.NETWORK, c.failure)
        assertEquals(false, c.running)
    }

    @Test
    fun `a failure after some words keeps them`() {
        val c = AgentConversation()
        c.begin()
        c.apply(AgentEvent.TextDelta("看日志"))
        c.apply(AgentEvent.Failed(AgentFailure.LOOP_ERROR))
        assertEquals(1, c.messages.size)
        assertEquals("看日志", c.messages[0].text)
        assertNull(c.streamingId)
    }

    @Test
    fun `no ghost answer once the turn is over`() {
        val c = AgentConversation()
        c.begin()
        c.apply(AgentEvent.TextDelta("看日志"))
        c.apply(AgentEvent.Failed(AgentFailure.LOOP_ERROR))
        // 迟到的字（缓冲里没刷出去的那一小段）：轮次已经结束，不许再补出新消息
        c.appendText("迟到的字")
        assertEquals(1, c.messages.size)
        assertEquals("看日志", c.messages[0].text)
    }

    @Test
    fun `the words that came out stay with the failure`() {
        val c = AgentConversation()
        c.begin()
        c.apply(AgentEvent.TextDelta("看日志"))
        c.apply(AgentEvent.Failed(AgentFailure.LOOP_ERROR))
        assertEquals(AgentFailure.LOOP_ERROR, c.failure)
        assertEquals(false, c.running)
        assertEquals(1, c.messages.size)
    }

    @Test
    fun `another conversation answers from here on`() {
        val c = AgentConversation()
        c.say("第一句")
        c.load(listOf(AgentMessage(7L, AgentRole.USER, "上一段对话")))
        assertEquals(1, c.messages.size)
        assertEquals("上一段对话", c.messages[0].text)
        c.say("这一句")
        assertTrue(c.messages.last().id > 7L)
    }

    @Test
    fun `the ids never collide with the history that came in`() {
        val c = AgentConversation()
        c.load(listOf(AgentMessage(7L, AgentRole.USER, "上一段对话")))
        c.say("这一句")
        assertEquals(8L, c.messages.last().id)
        assertNotNull(c.messages.last().id)
    }

    @Test
    fun `a stopped turn keeps the words and shows no failure`() {
        val c = AgentConversation()
        c.say("为啥闪退")
        c.begin()
        c.apply(AgentEvent.TextDelta("看日志"))
        c.end()   // 玩家按了停止
        assertEquals(2, c.messages.size)
        assertEquals("看日志", c.messages[1].text)
        assertNull(c.failure)
        assertEquals(false, c.running)
    }

    @Test
    fun `an interrupted turn keeps its mark`() {
        val c = AgentConversation()
        c.load(listOf(AgentMessage(1L, AgentRole.ASSISTANT, "看日志", pending = true)))
        assertTrue(c.messages.last().pending)
    }

    @Test
    fun `a sub-agent notice joins the answer it belongs to`() {
        val c = AgentConversation()
        c.say("派个活")
        c.begin()
        c.apply(AgentEvent.TextDelta("派了"))
        c.apply(AgentEvent.Notification(AgentNotification("t1", "Done", "Agent done")))
        c.end()
        assertEquals(2, c.messages.size)
        assertEquals(1, c.messages[1].notifications.size)
        assertEquals("t1", c.messages[1].notifications.first().id)
    }

    @Test
    fun `a notice after the turn joins the answer it is about`() {
        val c = AgentConversation()
        c.say("派个活")
        c.begin()
        c.apply(AgentEvent.TextDelta("派了"))
        c.end()
        c.apply(AgentEvent.Notification(AgentNotification("t1", "Done", "Agent done")))
        assertEquals(2, c.messages.size)
        assertEquals(1, c.messages.last().notifications.size)
        assertEquals("t1", c.messages.last().notifications.first().id)
    }

    @Test
    fun `a notice with no answer at all stands on its own`() {
        val c = AgentConversation()
        c.apply(AgentEvent.Notification(AgentNotification("t1", "Done", "Agent done")))
        assertEquals(1, c.messages.size)
        assertEquals("", c.messages.first().text)
        assertEquals(1, c.messages.first().notifications.size)
    }
}
