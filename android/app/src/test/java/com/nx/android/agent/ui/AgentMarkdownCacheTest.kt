package com.nx.android.agent.ui

import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The markdown is parsed ahead of time, so the answers reach the screen with their final height:
 * the list does not jump while the player reads it, and the first open lands at the true bottom.
 *
 * The parsing lives in the conversation state, not in the panel: the panel is not composed on a
 * cold start, so a prewarm there would only start when the player opens the drawer — too late.
 */
class AgentMarkdownCacheTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun cache() = AgentMarkdownCache(scope)

    @Test
    fun `the answers are parsed ahead of the screen`() = runBlocking {
        val cache = cache()
        val messages = listOf(
            AgentMessage(1L, AgentRole.USER, "看日志"),
            AgentMessage(2L, AgentRole.ASSISTANT, "## 原因\n\n着色器切分失败"),
        )
        cache.ensureAll(messages, streamingId = null)
        withTimeout(10_000) { cache.ready.first { it.containsKey(2L) } }
        assertTrue(cache.ready.value.containsKey(2L))
    }

    @Test
    fun `the message being written waits for the turn to end`() = runBlocking {
        val cache = cache()
        val messages = listOf(
            AgentMessage(1L, AgentRole.ASSISTANT, "正在写"),
            AgentMessage(2L, AgentRole.ASSISTANT, "写完了"),
        )
        cache.ensureAll(messages, streamingId = 1L)
        withTimeout(10_000) { cache.ready.first { it.containsKey(2L) } }
        // 正在生成的那条不排版：这一轮结束后再排
        assertFalse(cache.ready.value.containsKey(1L))
    }

    @Test
    fun `the player's own words are not parsed`() = runBlocking {
        val cache = cache()
        cache.ensureAll(listOf(AgentMessage(7L, AgentRole.USER, "看日志")), streamingId = null)
        // 用户那侧是原样显示的，不用排版
        assertFalse(cache.ready.value.containsKey(7L))
    }

    @Test
    fun `a message is parsed once`() = runBlocking {
        val cache = cache()
        val message = AgentMessage(3L, AgentRole.ASSISTANT, "## 标题")
        cache.ensureAll(listOf(message), streamingId = null)
        withTimeout(10_000) { cache.ready.first { it.containsKey(3L) } }
        val first = cache.ready.value[3L]
        // 再备一遍：还是那一份，解析不重跑
        cache.ensureAll(listOf(message), streamingId = null)
        assertEquals(first, cache.ready.value[3L])
    }
}

/**
 * The first open lands at the conversation's true bottom — and it gets exactly one chance:
 * marking it done before the messages are there means the drawer never lands at all.
 */
class AgentFirstOpenTest {

    @Test
    fun `the first open follows the bottom`() {
        assertTrue(shouldFollowToBottom(open = true, followed = false, messageCount = 3))
    }

    @Test
    fun `a closed drawer does not follow`() {
        assertFalse(shouldFollowToBottom(open = false, followed = false, messageCount = 3))
    }

    @Test
    fun `the second open keeps the place the player left`() {
        assertFalse(shouldFollowToBottom(open = true, followed = true, messageCount = 3))
    }

    @Test
    fun `no messages yet means no chance is spent`() {
        assertFalse(shouldFollowToBottom(open = true, followed = false, messageCount = 0))
    }
}
