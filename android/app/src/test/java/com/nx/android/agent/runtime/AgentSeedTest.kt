package com.nx.android.agent.runtime

import com.nx.android.core.storage.AgentLayout
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The agreement the assistant works by: it states the facts the assistant kept getting wrong, and
 * the texts the phones already carry stay recognized — otherwise their agreement would be taken
 * for the player's own and never refreshed.
 */
class AgentSeedTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `the current seed states the lineage, the version and what a tree change needs`() {
        val seed = AgentWorkspace.SEED_AGENTS

        assertTrue("the lineage is stated", seed.contains("shirai91/nx2android"))
        assertTrue("the version's home is stated", seed.contains("manifest.json"))
        assertTrue("what a change to the engine tree needs is stated", seed.contains("重跑移植"))
    }

    @Test
    fun `a phone carrying the previous build's text gets the new seed`() {
        val agreement = File(tmp.root, "AGENTS.md").apply { writeText(AgentWorkspace.SEED_AGENTS_V5) }

        AgentLayout.seedAgreement(listOf(agreement), AgentWorkspace.SEED_AGENTS, AgentWorkspace.SEEDS)

        assertEquals(AgentWorkspace.SEED_AGENTS, agreement.readText())
    }

    @Test
    fun `a phone carrying the build before that one gets the new seed too`() {
        val agreement = File(tmp.root, "AGENTS.md").apply { writeText(AgentWorkspace.SEED_AGENTS_V4) }

        AgentLayout.seedAgreement(listOf(agreement), AgentWorkspace.SEED_AGENTS, AgentWorkspace.SEEDS)

        assertEquals(AgentWorkspace.SEED_AGENTS, agreement.readText())
    }

    @Test
    fun `the player's own agreement is left alone`() {
        val agreement = File(tmp.root, "AGENTS.md").apply { writeText("我自己写的约定") }

        AgentLayout.seedAgreement(listOf(agreement), AgentWorkspace.SEED_AGENTS, AgentWorkspace.SEEDS)

        assertEquals("我自己写的约定", agreement.readText())
    }
}
