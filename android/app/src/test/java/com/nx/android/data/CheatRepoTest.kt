package com.nx.android.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 金手指文件：玩家导入的 zip（社区的 Atmosphère 文件），落到 App 自己的目录里 */
class CheatRepoTest {

    /** The folders the test made (their names must not collide) */
    private var counter = 0

    @After
    fun cleanUp() {
        // nothing to stop: the files are in the temp folder
    }

    // ---- the file layout: the game is a folder, the build is the file's name ----

    @Test
    fun `the game is the folder of 16 digits in the path`() {
        assertEquals("01004AB00A260000", CheatRepo.titleIdOf("titles/01004AB00A260000/cheats/DF3766A2BB651A3E.txt"))
        assertEquals("01004AB00A260000", CheatRepo.titleIdOf("atmosphere/titles/01004AB00A260000/cheats/DF3766A2BB651A3E.txt"))
        assertEquals("01004AB00A260000", CheatRepo.titleIdOf("cheats/01004AB00A260000/DF3766A2BB651A3E.txt"))
        assertEquals("0100F43008C44000", CheatRepo.titleIdOf("Pokemon Legends Z-A/0100F43008C44000/cheats/B1F12FD919EAE86A.txt"))
    }

    @Test
    fun `a path without a game folder is not a cheat file`() {
        assertNull(CheatRepo.titleIdOf("cheats/DF3766A2BB651A3E.txt"))
        assertNull(CheatRepo.titleIdOf("readme.txt"))
        assertNull(CheatRepo.titleIdOf("titles/Dark Souls/cheats/DF3766A2BB651A3E.txt"))
    }

    // ---- the import ----

    @Test
    fun `the import writes the file of the build where the host looks for it`() {
        val files = folder()
        val zip = zip(
            "titles/01004AB00A260000/cheats/DF3766A2BB651A3E.txt" to "[Inf HP]\n04000000 011A1D00 52800108\n\n",
        )
        val result = CheatRepo.importZip(zip, files)
        assertEquals(1, result.files)
        assertEquals(1, result.gameCount)
        assertEquals(mapOf("01004AB00A260000" to 1), result.games)
        val out = CheatRepo.file(CheatRepo.dir(files, "01004AB00A260000"), "DF3766A2BB651A3E")
        assertTrue(out.isFile)
        assertEquals("[Inf HP]\n04000000 011A1D00 52800108\n\n", out.readText())
    }

    @Test
    fun `the import takes the layouts the community uses`() {
        val files = folder()
        val zip = zip(
            "titles/01004AB00A260000/cheats/DF3766A2BB651A3E.txt" to "[A]\n04000000 011A1D00 52800108\n",
            "atmosphere/titles/01001F5010DFA000/cheats/AEE8F150DDA1B5A8.txt" to "[B]\n04000000 011A1D00 52800108\n",
            "cheats/0100F43008C44000/B1F12FD919EAE86A.txt" to "[C]\n04000000 011A1D00 52800108\n",
        )
        val result = CheatRepo.importZip(zip, files)
        assertEquals(3, result.files)
        assertEquals(3, result.gameCount)
        assertTrue(CheatRepo.file(CheatRepo.dir(files, "01004AB00A260000"), "DF3766A2BB651A3E").isFile)
        assertTrue(CheatRepo.file(CheatRepo.dir(files, "01001F5010DFA000"), "AEE8F150DDA1B5A8").isFile)
        assertTrue(CheatRepo.file(CheatRepo.dir(files, "0100F43008C44000"), "B1F12FD919EAE86A").isFile)
    }

    @Test
    fun `the resolution patches are not imported`() {
        val files = folder()
        val zip = zip(
            "cheats/01001F5010DFA000/AEE8F150DDA1B5A8.txt" to
                "[60 FPS]\n04000000 0397E8D8 00FE502B\n\n[Downscale Handheld 504p / Docked 756p]\n580F0000 042ECFC8\n",
        )
        val result = CheatRepo.importZip(zip, files)
        assertEquals(1, result.files)
        val out = CheatRepo.file(CheatRepo.dir(files, "01001F5010DFA000"), "AEE8F150DDA1B5A8")
        assertTrue(out.readText().contains("60 FPS"))
        assertFalse(out.readText().contains("504p"))
    }

    @Test
    fun `a file with only resolution patches is not imported at all`() {
        val files = folder()
        val zip = zip(
            "cheats/01001F5010DFA000/AEE8F150DDA1B5A8.txt" to "[Downscale Handheld 504p / Docked 756p]\n580F0000 042ECFC8\n",
        )
        val result = CheatRepo.importZip(zip, files)
        assertEquals(0, result.files)
        assertEquals(1, result.skipped)
        assertEquals(0, result.gameCount)
        assertFalse(CheatRepo.file(CheatRepo.dir(files, "01001F5010DFA000"), "AEE8F150DDA1B5A8").exists())
    }

    @Test
    fun `the credit beside the file is imported too`() {
        val files = folder()
        val zip = zip(
            "cheats/01006B601380E000/FA24EA3C3A512C07.txt" to "[移動速度X2]\n580F0000 01652508\n",
            "cheats/01006B601380E000/FA24EA3C3A512C07.credit" to "vgtxc/switch-cheat\n",
        )
        CheatRepo.importZip(zip, files)
        val credit = CheatRepo.creditFile(CheatRepo.dir(files, "01006B601380E000"), "FA24EA3C3A512C07")
        assertTrue(credit.isFile)
        assertEquals("vgtxc/switch-cheat", credit.readText().trim())
    }

    @Test
    fun `the files that are not cheat files are left out`() {
        val files = folder()
        val zip = zip(
            "readme.txt" to "hello",
            "cheats/01004AB00A260000/notes.txt" to "notes",
            "cheats/DF3766A2BB651A3E.txt" to "[A]\n04000000 011A1D00 52800108\n",
        )
        val result = CheatRepo.importZip(zip, files)
        assertEquals(0, result.files)
        assertEquals(1, result.unknown)   // the one without a game folder
    }

    @Test
    fun `a second import replaces the file of the same build`() {
        val files = folder()
        CheatRepo.importZip(zip("cheats/01004AB00A260000/DF3766A2BB651A3E.txt" to "[A]\n04000000 011A1D00 52800108\n"), files)
        CheatRepo.importZip(zip("cheats/01004AB00A260000/DF3766A2BB651A3E.txt" to "[B]\n04000000 011A1D04 3909DC08\n"), files)
        val out = CheatRepo.file(CheatRepo.dir(files, "01004AB00A260000"), "DF3766A2BB651A3E")
        assertEquals("[B]\n04000000 011A1D04 3909DC08\n", out.readText())
        assertEquals(1, CheatRepo.root(files).listFiles()!!.size)
    }

    // ---- the file itself ----

    @Test
    fun `an Atmosphere file is read into its entries`() {
        val text = """
            Pokemon Legends Z-A v393216 TID=0100F43008C44000 BID=B1F12FD919EAE86A
            [Item/Misc]
            [1. Disable Autosave]
            040A0000 002D4B40 00000001

            [2. Max Money]
            040A0000 00321A00 F4FFFFFF
        """.trimIndent()
        val entries = CheatRepo.fileEntries(text)
        assertEquals(3, entries.size)
        assertEquals("Item/Misc", entries[0].name)
        assertEquals("1. Disable Autosave", entries[1].name)
        assertTrue("the code stays with its entry", entries[1].text.contains("002D4B40"))
    }

    @Test
    fun `the resolution patches are skipped`() {
        assertTrue(CheatRepo.isResolution("[Downscale Handheld 504p / Docked 756p]"))
        assertTrue(CheatRepo.isResolution("[Remain Handheld 720p / Docked 1080p]"))
        assertTrue(CheatRepo.isResolution("[Downscale]"))
        assertTrue(CheatRepo.isResolution("[Resolution]"))
        assertFalse(CheatRepo.isResolution("[Inf HP]"))
        assertFalse(CheatRepo.isResolution("[60 FPS]"))
        assertFalse(CheatRepo.isResolution("[Default 30 FPS]"))
        assertFalse(CheatRepo.isResolution("[Max/Inf Money]"))
    }

    // ---- the toggles and the build ID ----

    @Test
    fun `the toggles of the cheats are cleared when the game starts again`() {
        val dir = folder()
        val toggles = File(dir, "DF3766A2BB651A3E.enabled")
        toggles.writeText("Inf HP\n")
        assertTrue(CheatRepo.clearToggles(dir, "df3766a2bb651a3e"))
        assertFalse(toggles.exists())
        // 没有开关文件时没什么可清的
        assertFalse(CheatRepo.clearToggles(dir, "df3766a2bb651a3e"))
    }

    @Test
    fun `the build id comes from the pack's rewriter table`() {
        val pack = folder()
        val mods = File(pack, "mods")
        mods.mkdirs()
        File(mods, "main.fixups.tsv").writeText(
            "# BUILD_ID\tdf3766a2bb651a3e822828abf10b6f7b\n" +
                "# PATCH_OFF\t-0x2000000\n" +
                "0x277d838\t280080d2\t080080d2\tdisplay settings\n"
        )
        assertEquals("DF3766A2BB651A3E", CheatRepo.buildIdOf(pack))
    }

    @Test
    fun `a pack without that table gives no build id`() {
        assertNull(CheatRepo.buildIdOf(folder()))
    }

    // ---- the helpers ----

    /** A zip with the given entries (the path and its text) */
    private fun zip(vararg entries: Pair<String, String>): File {
        val file = File(System.getProperty("java.io.tmpdir"), "nx2a-cheats-test-${counter++}.zip")
        file.delete()
        ZipOutputStream(FileOutputStream(file)).use { out ->
            for ((path, text) in entries) {
                out.putNextEntry(ZipEntry(path))
                out.write(text.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    /** A folder of the test's own (the app's files folder is not there) */
    private fun folder(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "nx2a-cheats-test-${counter++}")
        dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }
}
