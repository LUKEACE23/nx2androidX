package com.nx.android.agent.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The playbooks the assistant can load: they sit in the folder the engine finds them in, and a
 * playbook the player has written for the assistant is left alone.
 */
class AgentSkillsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun skillFile(home: File) = File(File(File(home, ".agents/skills"), "card-unpack"), "SKILL.md")

    @Test
    fun `the skill is written where the engine looks for it`() {
        val home = File(tmp.root, "agent")

        AgentSkills(home).ensure()

        assertTrue("the engine's home holds the skill", skillFile(home).isFile)
    }

    @Test
    fun `the skill says where the unpacked files go`() {
        val home = File(tmp.root, "agent")

        AgentSkills(home).ensure()

        val text = skillFile(home).readText()
        assertTrue("the skill is named", text.contains("name: card-unpack"))
        assertTrue("the output folder is stated", text.contains("/storage/emulated/0/Download/"))
        assertTrue("the tool is named", text.contains("card_split.py"))
        assertTrue("the player's own card is protected", text.contains("不要动玩家自己的卡带"))
    }

    @Test
    fun `a skill the player edited is left alone`() {
        val home = File(tmp.root, "agent")
        val file = skillFile(home)
        file.parentFile.mkdirs()
        file.writeText("我自己写的拆包步骤")

        AgentSkills(home).ensure()

        assertEquals("我自己写的拆包步骤", file.readText())
    }
}
