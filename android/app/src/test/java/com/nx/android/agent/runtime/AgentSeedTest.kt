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
 * it says who does what — the concierge answers, the case sub-agent does the case work, and the
 * player confirms before anything is changed.
 *
 * The prompts are English: the upstream, the engine and the case file are all English.
 */
class AgentSeedTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `the current seed states the lineage, the version and what a tree change needs`() {
        val seed = AgentWorkspace.SEED_AGENTS

        assertTrue("the lineage is stated", seed.contains("shirai91/nx2android"))
        assertTrue("the version's home is stated", seed.contains("manifest.json"))
        assertTrue("what a change to the engine tree needs is stated", seed.contains("re-run the port"))
    }

    @Test
    fun `the current seed names the split tool and where its output goes`() {
        val seed = AgentWorkspace.SEED_AGENTS

        assertTrue("the split tool is named", seed.contains("card_split.py"))
        assertTrue("where the unpacked files go is stated", seed.contains("/storage/emulated/0/Download/"))
    }

    @Test
    fun `the current seed says the work is done in English and the answer in the player's language`() {
        val seed = AgentWorkspace.SEED_AGENTS

        assertTrue("the work language is stated", seed.contains("Work in English"))
        assertTrue("the player's language decides the answer", seed.contains("player language"))
    }

    @Test
    fun `the current seed states the gate the player confirms`() {
        val seed = AgentWorkspace.SEED_AGENTS

        // the split: case work goes to the sub-agent, the concierge does not dig
        assertTrue("the split is stated", seed.contains("case sub-agent"))
        assertTrue("the concierge does not do the case work", seed.contains("Do not do that work yourself"))
        // the gate: the player confirms with one tap
        assertTrue("the player confirms with one tap", seed.contains("Fix it as planned"))
        assertTrue("the fix only happens after that", seed.contains("Only after the player has tapped it"))
    }

    @Test
    fun `the current seed says a status question is answered at once`() {
        val seed = AgentWorkspace.SEED_AGENTS

        // 玩家问「咋样了」：立刻答，不许等着子 Agent 跑完
        assertTrue("the status question is answered at once", seed.contains("answer at once"))
        assertTrue("blocking on a running sub-agent is forbidden", seed.contains("Never block on a running sub-agent"))
        assertTrue("the query does not wait", seed.contains("wait option left off"))
    }

    @Test
    fun `the player's own agreement is left alone`() {
        val agreement = File(tmp.root, "AGENTS.md").apply { writeText("my own agreement") }

        AgentLayout.seedOwned(listOf(agreement), AgentWorkspace.SEED_AGENTS, AgentWorkspace.SEEDS)

        assertEquals("my own agreement", agreement.readText())
    }

    @Test
    fun `a file carrying our own seed is refreshed`() {
        val agreement = File(tmp.root, "AGENTS.md").apply { writeText(AgentWorkspace.SEED_AGENTS) }

        AgentLayout.seedOwned(listOf(agreement), AgentWorkspace.SEED_AGENTS, AgentWorkspace.SEEDS)

        assertEquals(AgentWorkspace.SEED_AGENTS, agreement.readText())
    }
}
