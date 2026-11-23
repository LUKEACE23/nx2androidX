package com.nx.android.core.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The assistant's folder layout and the agreement's seeding: the assistant lives in the player's
 * nx2android folder, the agreement the engine reads sits in the folder the engine starts in.
 */
class AgentLayoutTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun theAssistantLivesInThePlayersFolder() {
        val nxHome = File(tmp.root, "nx2a")
        val agent = AgentLayout.agentDir(nxHome)

        assertEquals(File(nxHome, "agent"), agent)
        assertTrue("the assistant belongs to the player's folder", agent.path.startsWith(nxHome.path))
        assertEquals(File(agent, "runtime"), AgentLayout.runtimeDir(agent))
        assertEquals(File(agent, "workspace"), AgentLayout.workspaceDir(agent))
        assertEquals(File(agent, "models.json"), AgentLayout.modelsFile(agent))
    }

    @Test
    fun theAgreementSitsInTheEngineWorkingDirectory() {
        val nxHome = File(tmp.root, "nx2a")

        // the engine starts in the player's nx2android folder and loads its context file from the
        // working directory: the agreement has to be right there
        assertEquals(File(nxHome, "AGENTS.md"), AgentLayout.agreementFile(nxHome))
    }

    @Test
    fun theAgreementIsSeededWhereItIsMissing() {
        val agreement = File(tmp.root, "nx2a/AGENTS.md")
        val copy = File(tmp.root, "nx2a/agent/workspace/AGENTS.md")

        AgentLayout.seedOwned(listOf(agreement, copy), "the rules", setOf("the rules"))

        assertEquals("the rules", agreement.readText())
        assertEquals("the rules", copy.readText())
    }

    @Test
    fun anOldSeedIsBroughtUpToDate() {
        val agreement = File(tmp.root, "nx2a/AGENTS.md").apply { parentFile.mkdirs(); writeText("the old rules") }

        AgentLayout.seedOwned(listOf(agreement), "the rules", setOf("the rules", "the old rules"))

        assertEquals("the rules", agreement.readText())
    }

    @Test
    fun thePlayersOwnFileIsLeftAlone() {
        val agreement = File(tmp.root, "nx2a/AGENTS.md").apply { parentFile.mkdirs(); writeText("my own rules") }

        AgentLayout.seedOwned(listOf(agreement), "the rules", setOf("the rules"))

        assertEquals("my own rules", agreement.readText())
    }

    @Test
    fun aSeedWeWroteIsUpdatedWithoutKnowingItsText() {
        // the mark beside the file says it is ours: a later seed replaces it, and the old text does
        // not have to be listed by hand (the tool catalog grows with every change)
        val agreement = File(tmp.root, "nx2a/AGENTS.md")
        AgentLayout.seedOwned(listOf(agreement), "the rules of the day", setOf("the rules of the day"))
        assertEquals("the rules of the day", agreement.readText())

        AgentLayout.seedOwned(listOf(agreement), "the rules of tomorrow", setOf("the rules of tomorrow"))

        assertEquals("the rules of tomorrow", agreement.readText())
    }

    @Test
    fun aFileThePlayerChangedAfterWeWroteItIsLeftAlone() {
        val agreement = File(tmp.root, "nx2a/AGENTS.md")
        AgentLayout.seedOwned(listOf(agreement), "the rules of the day", setOf("the rules of the day"))
        agreement.writeText("the rules of the day, plus mine")

        AgentLayout.seedOwned(listOf(agreement), "the rules of tomorrow", setOf("the rules of tomorrow"))

        assertEquals("the rules of the day, plus mine", agreement.readText())
    }

    @Test
    fun theEngineAndItsManifestAreNamed() {
        assertEquals("pi", AgentLayout.BINARY)
        assertEquals("manifest.json", AgentLayout.MANIFEST)
        assertFalse("the engine's binary name is not a path", AgentLayout.BINARY.contains('/'))
    }
}
