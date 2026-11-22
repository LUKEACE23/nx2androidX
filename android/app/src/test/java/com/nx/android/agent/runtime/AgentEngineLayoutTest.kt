package com.nx.android.agent.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The engine and its own tree used to live in the folders the environment's reset throws away.
 * They come over once — as a copy, so an older build still finds them.
 */
class AgentEngineLayoutTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun theLegacyBinaryIsCopiedIntoTheRuntimeFolder() {
        val legacy = tmp.newFile("pi").apply { writeText("engine") }
        val runtime = File(tmp.root, "agent/runtime")
        val home = File(tmp.root, "agent")

        val moved = AgentEngineLayout.migrate(legacy, File(tmp.root, "no-pi-home"), runtime, home)

        assertTrue(moved)
        assertTrue(File(runtime, "pi").isFile)
        assertEquals("engine", File(runtime, "pi").readText())
        assertTrue("the engine has to be runnable", File(runtime, "pi").canExecute())
    }

    @Test
    fun theMigrationIsIdempotent() {
        val legacy = tmp.newFile("pi").apply { writeText("engine") }
        val runtime = File(tmp.root, "agent/runtime")
        val home = File(tmp.root, "agent")

        AgentEngineLayout.migrate(legacy, File(tmp.root, "no-pi-home"), runtime, home)
        File(runtime, "pi").writeText("newer")

        val again = AgentEngineLayout.migrate(legacy, File(tmp.root, "no-pi-home"), runtime, home)

        assertFalse("a second run finds the new place filled", again)
        assertEquals("newer", File(runtime, "pi").readText())
    }

    @Test
    fun theEngineOwnTreeIsCopiedUnderTheNewHome() {
        val legacyHome = File(tmp.root, "home/.pi").apply { mkdirs() }
        File(legacyHome, "agent/models.json").apply { parentFile.mkdirs(); writeText("{}") }
        val runtime = File(tmp.root, "agent/runtime")
        val home = File(tmp.root, "agent")

        val moved = AgentEngineLayout.migrate(File(tmp.root, "no-binary"), legacyHome, runtime, home)

        assertTrue(moved)
        assertTrue(File(home, ".pi/agent/models.json").isFile)
    }

    @Test
    fun theSessionPointersFollowTheEngineHome() {
        val meta = File(tmp.root, "sessions/_meta.json").apply { parentFile.mkdirs() }
        meta.writeText("""{"s-1":{"context":10,"piFile":"/data/home/.pi/agent/sessions/a.json"}}""")

        val rewritten = AgentEngineLayout.rewriteSessionPointers(
            metaFile = meta,
            oldPrefix = "/data/home/.pi",
            newPrefix = "/data/agent/.pi",
        )

        assertTrue(rewritten)
        assertTrue(meta.readText().contains("/data/agent/.pi/agent/sessions/a.json"))
        assertFalse(meta.readText().contains("/data/home/.pi"))
    }

    @Test
    fun aMetaFileWithoutTheOldPrefixIsLeftAlone() {
        val meta = File(tmp.root, "sessions/_meta.json").apply { parentFile.mkdirs() }
        val text = """{"s-1":{"context":10,"piFile":"/data/agent/.pi/agent/sessions/a.json"}}"""
        meta.writeText(text)

        val rewritten = AgentEngineLayout.rewriteSessionPointers(meta, "/data/home/.pi", "/data/agent/.pi")

        assertFalse(rewritten)
        assertEquals(text, meta.readText())
    }
}
