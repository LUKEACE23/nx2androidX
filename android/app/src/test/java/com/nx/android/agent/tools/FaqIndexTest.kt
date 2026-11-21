package com.nx.android.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The FAQ search's pure core: ranking, empties, the answer budget and broken input — all on the JVM, with a
 * trimmed-down copy of the FAQ file's own shape as the fixture.
 */
class FaqIndexTest {

    /** An answer past the budget, so the cut and its marker can be checked */
    private val longAnswer =
        "存档的原始文件在 App 的私有目录里，游戏库里每个游戏有一节存档，能导出成存档包，也能从存档包导回来。".repeat(7)

    /** The FAQ file's shape: groups, each with a title and question/answer pairs */
    private val json = """
        [
          {
            "group": "trouble",
            "title": "遇到问题",
            "items": [
              { "q": "移植中途无提示退出（exit 137）", "a": "这是 Android 的子进程数量限制把移植器停掉了，不是移植失败。" },
              { "q": "游戏卡顿、闪烁或黑屏", "a": "按这个顺序看：设置里的 Vulkan 驱动、画面帧生成、抽屉里的性能与 HUD。" }
            ]
          },
          {
            "group": "prepare",
            "title": "准备",
            "items": [
              { "q": "移植一个游戏要准备什么？", "a": "四样：自己导出的 prod.keys、自己的游戏文件、App 的移植环境、游戏档案包。" },
              { "q": "存档在哪，怎么备份？", "a": "${longAnswer}" }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `the entry that matches more comes first`() {
        val hits = FaqIndex.search(json, "移植 中途 退出 exit 137")
        assertEquals(2, hits.size)
        assertEquals("移植中途无提示退出（exit 137）", hits.first().question)
        // the weaker hit (only the shared word) is still there, behind it
        assertEquals("移植一个游戏要准备什么？", hits.last().question)
        // the group's title travels with the entry, so the model can cite it
        assertTrue(FaqIndex.render(hits).contains("「遇到问题」"))
    }

    @Test
    fun `chinese words match without spaces in the query`() {
        val hits = FaqIndex.search(json, "游戏卡顿怎么办")
        assertEquals("游戏卡顿、闪烁或黑屏", hits.first().question)
    }

    @Test
    fun `a query nothing matches gives no hits`() {
        assertTrue(FaqIndex.search(json, "夸克网盘上传").isEmpty())
    }

    @Test
    fun `an empty query gives no hits`() {
        assertTrue(FaqIndex.search(json, "").isEmpty())
        assertTrue(FaqIndex.search(json, "   ").isEmpty())
    }

    @Test
    fun `a long answer is cut and marked, a short one is not`() {
        val hit = FaqIndex.search(json, "存档 备份").single()
        assertTrue("the fixture's answer is past the budget", hit.answer.length > 200)
        val text = FaqIndex.render(listOf(hit))
        assertTrue(text.startsWith("命中 1 条："))
        assertTrue(text.contains("（截断）"))
        // the kept part is exactly the first 200 characters, and nothing more of the answer
        assertTrue(text.contains(hit.answer.take(200)))
        assertFalse(text.contains(hit.answer))
        // an answer inside the budget comes out whole, with no marker
        val short = FaqIndex.render(FaqIndex.search(json, "准备 什么"))
        assertFalse(short.contains("（截断）"))
    }

    @Test
    fun `no hits render as a plain no`() {
        assertTrue(FaqIndex.render(emptyList()).contains("没有找到"))
    }

    @Test
    fun `broken json gives no hits without throwing`() {
        assertTrue(FaqIndex.search("not json at all", "存档").isEmpty())
        assertTrue(FaqIndex.search("""[{"group": """, "存档").isEmpty())
        assertTrue(FaqIndex.search("", "存档").isEmpty())
        // an item missing either side is not an entry
        assertTrue(FaqIndex.search("""[{"group":"g","title":"t","items":[{"q":"存档在哪"}]}]""", "存档").isEmpty())
    }
}
