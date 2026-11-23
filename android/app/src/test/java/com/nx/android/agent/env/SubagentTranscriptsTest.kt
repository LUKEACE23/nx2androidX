package com.nx.android.agent.env

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * transcript 的尾巴：只读最后几行完整的，半行不算，坏行跳过。
 */
class SubagentTranscriptsTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** 一行 transcript 的样子：外层是壳，里面是引擎自己的消息。 */
    private fun line(role: String, text: String) = JSONObject().apply {
        put("isSidechain", true)
        put("agentId", "abc")
        put("type", role)
        put("message", JSONObject().put("role", role).put("content", text))
    }.toString()

    @Test
    fun `an empty file gives nothing`() {
        val file = folder.newFile("a.output")
        assertTrue(SubagentTranscripts.tail(file, 10).isEmpty())
    }

    @Test
    fun `a file that is not there gives nothing`() {
        assertTrue(SubagentTranscripts.tail(File(folder.root, "absent.output"), 10).isEmpty())
    }

    @Test
    fun `the whole lines come back`() {
        val file = folder.newFile("a.output")
        file.writeText(listOf(line("user", "hello"), line("assistant", "hi")).joinToString("\n") + "\n")
        val messages = SubagentTranscripts.tail(file, 10)
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].optString("role"))
        assertEquals("assistant", messages[1].optString("role"))
    }

    @Test
    fun `a broken line in the middle is skipped`() {
        val file = folder.newFile("a.output")
        file.writeText(
            listOf(line("user", "hello"), "{ not json", line("assistant", "hi")).joinToString("\n") + "\n",
        )
        val messages = SubagentTranscripts.tail(file, 10)
        assertEquals(2, messages.size)
    }

    @Test
    fun `only the last lines are kept`() {
        val file = folder.newFile("a.output")
        file.writeText((1..50).joinToString("\n") { line("user", "n$it") } + "\n")
        val messages = SubagentTranscripts.tail(file, 5)
        assertEquals(5, messages.size)
        assertEquals("n50", messages.last().optString("content"))
    }

    @Test
    fun `a half line at the front is not read`() {
        val file = folder.newFile("a.output")
        // 比一块还大：读文件头的时候第一行被切开了
        file.writeText(line("user", "x".repeat(200_000)) + "\n" + line("assistant", "hi") + "\n")
        val messages = SubagentTranscripts.tail(file, 10)
        assertEquals(1, messages.size)
        assertEquals("assistant", messages[0].optString("role"))
    }

    @Test
    fun `the tasks folder is found by the session's id`() {
        val scratch = folder.newFolder("tmp")
        val tasks = File(scratch, "pi-subagents-10333/data-user-0-x/parent-1/tasks")
        tasks.mkdirs()
        File(tasks, "agent-1.output").writeText(line("user", "hello") + "\n")

        val found = SubagentTranscripts.taskDir(scratch, "parent-1")
        assertTrue(found != null)
        assertEquals(tasks.absolutePath, found!!.absolutePath)
        assertTrue(SubagentTranscripts.taskDir(scratch, "other") == null)
    }

    @Test
    fun `the transcripts of a session come back with their prompt`() {
        val scratch = folder.newFolder("tmp")
        val tasks = File(scratch, "pi-subagents-10333/data-user-0-x/parent-1/tasks")
        tasks.mkdirs()
        File(tasks, "agent-1.output").writeText(line("user", "do the thing") + "\n")

        val transcripts = SubagentTranscripts.of(scratch, "parent-1")
        assertEquals(1, transcripts.size)
        assertEquals("agent-1", transcripts[0].agentId)
        assertEquals("do the thing", transcripts[0].prompt)
    }

    @Test
    fun `the messages of a session file come back in order`() {
        val file = folder.newFile("session.jsonl")
        file.writeText(
            listOf(
                """{"type":"session","version":3,"id":"abc"}""",
                """{"type":"message","message":{"role":"user","content":"hi"}}""",
                """{"type":"custom","customType":"subagents:record","data":{}}""",
                """{"type":"message","message":{"role":"assistant","content":"yo"}}""",
            ).joinToString("\n"),
        )
        val messages = SubagentTranscripts.messages(file)
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].optString("role"))
        assertEquals("assistant", messages[1].optString("role"))
    }
}
