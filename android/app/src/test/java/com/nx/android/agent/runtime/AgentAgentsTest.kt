package com.nx.android.agent.runtime

import com.nx.android.core.storage.AgentLayout
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 攻关子 Agent 的定义：App 装它，玩家改过的那份不动。
 *
 * 提示词是英文的：上游的代码、issue、日志都是英文，档案也要交给上游看。
 */
class AgentAgentsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun file(home: File): File = File(AgentAgents.dir(home), AgentAgents.GAMESPEC_FILE)

    @Test
    fun `the definition is installed in the engine's home`() {
        val home = folder.newFolder("agent")
        AgentAgents.install(home)
        assertTrue(file(home).isFile)
    }

    @Test
    fun `the definition declares its type and its instructions`() {
        val home = folder.newFolder("agent")
        AgentAgents.install(home)
        val text = file(home).readText()
        assertTrue(text.take(40), text.startsWith("---"))
        assertTrue(text.take(200), text.contains("name: gamespec"))
        assertTrue(text.take(200), text.contains("description:"))
        // the body: the two phases, the upstream search, the archive, the lines it must not cross
        assertTrue(text.contains("Diagnosis phase"))
        assertTrue(text.contains("Fix phase"))
        assertTrue(text.contains("upstream_pr.py"))
        assertTrue(text.contains("spec.md"))
        assertTrue(text.contains("Red lines"))
    }

    @Test
    fun `installing twice leaves the same file`() {
        val home = folder.newFolder("agent")
        AgentAgents.install(home)
        val first = file(home).readText()
        AgentAgents.install(home)
        assertEquals(first, file(home).readText())
    }

    @Test
    fun `a definition the player wrote is left alone`() {
        val home = folder.newFolder("agent")
        AgentAgents.dir(home).mkdirs()
        file(home).writeText("---\nname: gamespec\ndescription: my own\n---\n\nmy own words.\n")
        AgentAgents.install(home)
        assertTrue(file(home).readText().contains("my own"))
    }

    @Test
    fun `our own definition is refreshed`() {
        val home = folder.newFolder("agent")
        AgentAgents.dir(home).mkdirs()
        // 我们自己写的那份：认得出来 → 装新的
        file(home).writeText(AgentAgents.GAMESPEC)
        AgentAgents.install(home)
        assertEquals(AgentAgents.GAMESPEC, file(home).readText())
    }

    @Test
    fun `a definition we wrote before is refreshed when the prompt changes`() {
        val home = folder.newFolder("agent")
        AgentAgents.dir(home).mkdirs()
        // 上一版提示词：带着我们写的标记 → 换成新的（改了提示词要能到手机上）
        val old = "---\nname: gamespec\ndescription: the old one\n---\n\nthe old body.\n"
        file(home).writeText(old)
        AgentLayout.writeSeeded(file(home), old)
        AgentAgents.install(home)
        assertEquals(AgentAgents.GAMESPEC, file(home).readText())
    }

    @Test
    fun `a definition the player edited is left alone`() {
        val home = folder.newFolder("agent")
        AgentAgents.dir(home).mkdirs()
        // 玩家在我们那份上改过：标记对不上了 → 那是他的，不碰
        val ours = "---\nname: gamespec\ndescription: ours\n---\n\nour body.\n"
        file(home).writeText(ours)
        AgentLayout.writeSeeded(file(home), ours)
        file(home).writeText(ours + "\nthe player added this.\n")
        AgentAgents.install(home)
        assertTrue(file(home).readText().contains("the player added this"))
    }

    @Test
    fun `the description tells the main agent when to delegate`() {
        val home = folder.newFolder("agent")
        AgentAgents.install(home)
        val text = file(home).readText()
        val description = text.lines().first { it.startsWith("description:") }
        // 触发条件写明：构建失败、闪退、掉帧、黑屏
        assertTrue(description, description.contains("build failures"))
        assertTrue(description, description.contains("crashes"))
        assertTrue(description, description.contains("low fps"))
        assertTrue(description, description.contains("black screen"))
        // 边界写明：用法与 FAQ 不派
        assertTrue(description, description.contains("FAQ"))
        // 两段式写明：诊断不改代码，玩家确认之后才修
        assertTrue(description, description.contains("does not change any code until the player"))
    }

    @Test
    fun `the definition says the fix waits for the player`() {
        val home = folder.newFolder("agent")
        AgentAgents.install(home)
        val text = file(home).readText()
        // 修复阶段只在玩家确认之后
        assertTrue(text.contains("the player has confirmed"))
        // 诊断阶段只出方案
        assertTrue(text.contains("No code changes without the player's confirmation"))
        // 方案在档案里的标记：app 读它才知道要给玩家那个按钮
        assertTrue(text.contains("(status: pending approval)"))
    }

    @Test
    fun `the definition opens the case file before the investigation`() {
        val home = folder.newFolder("agent")
        AgentAgents.install(home)
        val text = file(home).readText()
        // 玩家马上要看得见「档案开在这」：先建档，再查
        assertTrue(text.contains("If it does not exist, create it"))
        assertTrue(text.contains("Write that before you investigate"))
    }
}
