package com.nx.android.data

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress

/** 社区库（switch-cheats-db）的金手指文件：按 build 编号挑出来，拼成 Atmosphère 格式的文本 */
class CheatRepoTest {

    /** The servers the tests started (they are stopped when the test is over) */
    private val servers = ArrayList<HttpServer>()

    /** How many folders the test made (their names must not collide) */
    private var counter = 0

    @After
    fun stopServers() {
        for (s in servers) s.stop(0)
        servers.clear()
    }

    /** The library's file of one game, in the shape it has: the builds it knows, each with its cheats, and the credit */
    private val library = """
        {
          "5A797A3A5DA6C4F3": {
            "[Old Build]": "[Old Build]\n04000000 003BE784 94378D5F\n\n"
          },
          "DF3766A2BB651A3E": {
            "[Inf HP]": "[Inf HP]\n04000000 011A1D00 52800108\n04000000 011A1D04 3909DC08\n\n",
            "[Human 99]": "[Human 99]\n04000000 004C095C 52800C69\n\n",
            "[Aura]": "[Aura]\n04000000 0024F7CC 1E2E1008\n\n"
          },
          "attribution": {
            "DARK SOULS REMASTERED.txt": "By masagrator\nhttps://gbatemp.net/threads/dark-souls-60-fps-mod.557760/\n"
          }
        }
    """.trimIndent()

    @Test
    fun `the build's cheats become one Atmosphere file`() {
        assertEquals(
            "[Inf HP]\n04000000 011A1D00 52800108\n04000000 011A1D04 3909DC08\n\n" +
                "[Human 99]\n04000000 004C095C 52800C69\n\n" +
                "[Aura]\n04000000 0024F7CC 1E2E1008\n\n",
            CheatRepo.pick(library, "DF3766A2BB651A3E"),
        )
    }

    @Test
    fun `the cheats keep the order the library lists them in`() {
        val text = CheatRepo.pick(library, "DF3766A2BB651A3E")!!
        assertTrue("Inf HP comes first", text.indexOf("[Inf HP]") < text.indexOf("[Human 99]"))
        assertTrue("Aura comes last", text.indexOf("[Human 99]") < text.indexOf("[Aura]"))
    }

    @Test
    fun `the build is found in either case`() {
        assertEquals(CheatRepo.pick(library, "DF3766A2BB651A3E"), CheatRepo.pick(library, "df3766a2bb651a3e"))
    }

    @Test
    fun `a build the library does not have gives nothing`() {
        assertNull(CheatRepo.pick(library, "040AD650120B583A"))
    }

    @Test
    fun `the credit is not a build`() {
        assertNull(CheatRepo.pick(library, "attribution"))
        assertNull(CheatRepo.pick(library, "ATTRIBUTION"))
    }

    @Test
    fun `the credit is the first line of the library's note`() {
        assertEquals("By masagrator", CheatRepo.credit(library))
    }

    @Test
    fun `a file without a credit gives none`() {
        val noCredit = """{"DF3766A2BB651A3E": {"[Inf HP]": "[Inf HP]\n04000000 011A1D00 52800108\n\n"}}"""
        assertEquals("", CheatRepo.credit(noCredit))
    }

    @Test
    fun `the escapes of the library's text are read`() {
        val text = """{"DF3766A2BB651A3E": {"[A]": "[A]\n04000000 004C095C \"quoted\" \\ back \u00e9\n\n"}}"""
        assertEquals("[A]\n04000000 004C095C \"quoted\" \\ back \u00e9\n\n", CheatRepo.pick(text, "DF3766A2BB651A3E"))
    }

    @Test
    fun `a broken file gives nothing`() {
        assertNull(CheatRepo.pick("not json at all", "DF3766A2BB651A3E"))
        assertNull(CheatRepo.pick("""{"DF3766A2BB651A3E": }""", "DF3766A2BB651A3E"))
    }

    @Test
    fun `the file lands in the app's own folder, named by the build`() {
        val dir = CheatRepo.dir(File("/data/data/com.nx.android/files"), "01004AB00A260000")
        assertEquals("/data/data/com.nx.android/files/cheats/01004AB00A260000", dir.path)
        assertEquals("DF3766A2BB651A3E.txt", CheatRepo.file(dir, "df3766a2bb651a3e").name)
    }

    @Test
    fun `the library's file of one game is fetched by its title id`() {
        assertEquals(
            "https://cdn.jsdelivr.net/gh/HamletDuFromage/switch-cheats-db@master/cheats/01004AB00A260000.json",
            CheatRepo.url("01004ab00a260000"),
        )
    }

    // ---- the download itself (a local server stands in for the library) ----

    @Test
    fun `the download writes the file of the build and the credit beside it`() {
        val dir = folder()
        val result = CheatRepo.downloadFrom(server(library), "DF3766A2BB651A3E", dir)
        assertTrue(result.isSuccess)
        val file = CheatRepo.file(dir, "DF3766A2BB651A3E")
        assertTrue(file.isFile)
        assertEquals(
            "[Inf HP]\n04000000 011A1D00 52800108\n04000000 011A1D04 3909DC08\n\n" +
                "[Human 99]\n04000000 004C095C 52800C69\n\n" +
                "[Aura]\n04000000 0024F7CC 1E2E1008\n\n",
            file.readText(),
        )
        assertEquals("By masagrator", CheatRepo.creditFile(dir, "DF3766A2BB651A3E").readText())
    }

    @Test
    fun `a build the library does not have fails as such`() {
        val result = CheatRepo.downloadFrom(server(library), "040AD650120B583A", folder())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CheatRepo.NoSuchBuild)
    }

    @Test
    fun `a game the library does not have fails as such`() {
        val result = CheatRepo.downloadFrom(server("", code = 404), "DF3766A2BB651A3E", folder())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CheatRepo.NoSuchBuild)
    }

    @Test
    fun `a broken answer fails as a failure`() {
        val result = CheatRepo.downloadFrom(server("", code = 500), "DF3766A2BB651A3E", folder())
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is CheatRepo.NoSuchBuild)
    }

    /** A server that answers with the text (the code is 200 unless the test says otherwise) */
    private fun server(text: String, code: Int = 200): String {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = text.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        servers += server
        return "http://127.0.0.1:${server.address.port}/cheats/01004AB00A260000.json"
    }

    /** A folder of the test's own (the app's files folder is not there) */
    private fun folder(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "nx2a-cheats-test-${counter++}")
        dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }
}
