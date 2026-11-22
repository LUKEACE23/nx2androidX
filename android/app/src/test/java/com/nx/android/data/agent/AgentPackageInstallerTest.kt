package com.nx.android.data.agent

import com.nx.android.domain.AgentPackageManifest
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The engine package is checked before it takes the place of the installed one: the digest of the
 * binary, the app version it needs. A package that fails either check changes nothing.
 */
class AgentPackageInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val runtime: File get() = File(tmp.root, "agent/runtime")

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, body) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(body)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun manifest(sha: String, minAppVersion: String = "0.3.0-alpha01"): String =
        """{"name":"pi-agent","version":"r0.5.11-1","sha256":"$sha","minAppVersion":"$minAppVersion","piVersion":"0.5.11"}"""

    @Test
    fun aPackageIsInstalledAndTheEngineTakesItsPlace() {
        val binary = "engine bytes".toByteArray()
        val digest = AgentPackageInstaller.digest(File(tmp.root, "x").apply { writeBytes(binary) })
        val archive = zip(mapOf("pi" to binary, "manifest.json" to manifest(digest).toByteArray()))

        val installed = AgentPackageInstaller.install(archive.inputStream(), runtime, "0.3.0-alpha15")

        assertTrue(installed.isSuccess)
        assertTrue(File(runtime, "pi").isFile)
        assertEquals("r0.5.11-1", installed.getOrThrow().version)
        assertEquals("r0.5.11-1", AgentPackageManifest.parse(File(runtime, "manifest.json").readText())?.version)
    }

    @Test
    fun aPackageWithTheWrongDigestIsRefused() {
        val archive = zip(
            mapOf(
                "pi" to "engine".toByteArray(),
                "manifest.json" to manifest("00".repeat(32)).toByteArray(),
            ),
        )
        File(runtime, "pi").apply { parentFile.mkdirs(); writeText("the old engine") }

        val installed = AgentPackageInstaller.install(archive.inputStream(), runtime, "0.3.0-alpha15")

        assertTrue(installed.isFailure)
        assertTrue(installed.exceptionOrNull() is AgentPackageInstaller.Refused)
        assertEquals(
            AgentPackageInstaller.Refusal.BAD_DIGEST,
            (installed.exceptionOrNull() as AgentPackageInstaller.Refused).refusal,
        )
        assertEquals("the installed engine is untouched", "the old engine", File(runtime, "pi").readText())
    }

    @Test
    fun aPackageThatNeedsANewerAppIsRefused() {
        val archive = zip(
            mapOf(
                "pi" to "engine".toByteArray(),
                "manifest.json" to manifest("00".repeat(32), minAppVersion = "9.9.9").toByteArray(),
            ),
        )

        val installed = AgentPackageInstaller.install(archive.inputStream(), runtime, "0.3.0-alpha15")

        assertTrue(installed.isFailure)
        assertEquals(
            AgentPackageInstaller.Refusal.TOO_NEW,
            (installed.exceptionOrNull() as AgentPackageInstaller.Refused).refusal,
        )
    }

    @Test
    fun anArchiveWithoutAManifestIsRefused() {
        val archive = zip(mapOf("pi" to "engine".toByteArray()))

        val installed = AgentPackageInstaller.install(archive.inputStream(), runtime, "0.3.0-alpha15")

        assertTrue(installed.isFailure)
        assertEquals(
            AgentPackageInstaller.Refusal.NO_MANIFEST,
            (installed.exceptionOrNull() as AgentPackageInstaller.Refused).refusal,
        )
    }

    @Test
    fun aManifestWithoutTheNeededWordsIsNoManifest() {
        assertNull(AgentPackageManifest.parse("""{"name":"pi-agent"}"""))
        assertNull(AgentPackageManifest.parse("not json"))
        assertEquals(
            "r1",
            AgentPackageManifest.parse("""{"name":"pi-agent","version":"r1","sha256":"AB"}""")?.version,
        )
    }

    @Test
    fun thePackageNamesAreReadOutOfTheAssetsPage() {
        val html = """<a href="/NixPort/pi-respack/releases/download/r1/pi-agent-r0.5.11-1.zip">pi-agent-r0.5.11-1.zip</a>"""

        val names = com.nx.android.domain.AgentPackage.packageNamesIn(html)

        assertEquals(listOf("pi-agent-r0.5.11-1.zip"), names)
        assertFalse(names.any { it.contains("other") })
    }
}
