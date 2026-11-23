package com.nx.android.agent.env

import com.nx.android.agent.AgentSubagent
import com.nx.android.agent.AgentSubagentCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跑完的和在跑的合成一份清单：谁压谁、类型和任务从哪儿来。
 */
class SubagentRunsTest {

    private fun finished(
        id: String,
        startedAt: Long,
        status: String = "completed",
    ) = AgentSubagent(
        id = id,
        type = "general-purpose",
        description = "Run marker echo",
        status = status,
        startedAt = startedAt,
        completedAt = startedAt + 100,
    )

    private fun transcript(id: String, prompt: String, spawnedAt: Long) =
        SubagentTranscripts.Transcript(id, prompt, emptyList(), spawnedAt)

    @Test
    fun `a finished run wins over its transcript`() {
        val merged = SubagentRuns.merge(
            finished = listOf(finished("a", 10L)),
            live = listOf(transcript("a", "run echo", 10L)),
            calls = emptyList(),
        )
        assertEquals(1, merged.size)
        assertEquals("completed", merged.first().status)
    }

    @Test
    fun `a running run takes its type and its task from the call`() {
        val merged = SubagentRuns.merge(
            finished = emptyList(),
            live = listOf(transcript("b", "run echo MARKER_TEST", 20L)),
            calls = listOf(
                AgentSubagentCall("general-purpose", "Run marker echo", "run echo MARKER_TEST"),
            ),
        )
        assertEquals(1, merged.size)
        assertEquals("running", merged.first().status)
        assertEquals("general-purpose", merged.first().type)
        assertEquals("Run marker echo", merged.first().description)
    }

    @Test
    fun `a running run without a call falls back to its prompt`() {
        val merged = SubagentRuns.merge(
            finished = emptyList(),
            live = listOf(transcript("c", "check the log\nand tell me", 30L)),
            calls = emptyList(),
        )
        assertEquals("check the log", merged.first().description)
        assertEquals("", merged.first().type)
    }

    @Test
    fun `the runs come out oldest first`() {
        val merged = SubagentRuns.merge(
            finished = listOf(finished("a", 10L), finished("b", 40L)),
            live = listOf(transcript("c", "x", 20L)),
            calls = emptyList(),
        )
        assertEquals(listOf("a", "c", "b"), merged.map { it.id })
    }

    @Test
    fun `nothing at all is an empty list`() {
        assertTrue(SubagentRuns.merge(emptyList(), emptyList(), emptyList()).isEmpty())
    }
}
