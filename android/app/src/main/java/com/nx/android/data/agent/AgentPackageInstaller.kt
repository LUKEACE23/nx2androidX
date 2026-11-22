package com.nx.android.data.agent

import com.nx.android.agent.runtime.AgentEngineLayout
import com.nx.android.domain.AgentPackageManifest
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * The engine package's installation.
 *
 * The archive is unpacked into a staging folder and checked against its own manifest — the digest of
 * the binary, the app version it needs — before anything is put in place. The engine that is
 * installed keeps working until the new one is whole.
 *
 * Free of Android, so the JVM tests drive it.
 */
object AgentPackageInstaller {

    /** Why a package was refused. A code, not a sentence: the words live in the app's resources. */
    enum class Refusal { NO_MANIFEST, NO_BINARY, BAD_DIGEST, TOO_NEW }

    /** A package that was refused: the reason. */
    class Refused(val refusal: Refusal) : IOException(refusal.name)

    /**
     * Unpacks the archive into the staging folder: the manifest and the engine come out of it.
     *
     * The archive's own layout is kept (the engine looks for its own files beside it), but nothing may
     * point outside the staging folder.
     */
    fun unpack(archive: InputStream, stage: File): AgentPackageManifest {
        stage.mkdirs()
        var manifest: AgentPackageManifest? = null
        ZipInputStream(archive).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val safe = name.isNotEmpty() && !name.startsWith("/") && !name.contains("..") &&
                    !name.split('/').any { it == "." }
                if (!entry.isDirectory && safe) {
                    val out = File(stage, name)
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                    if (name == AgentEngineLayout.MANIFEST) {
                        manifest = AgentPackageManifest.parse(out.readText())
                    }
                }
                entry = zip.nextEntry
            }
        }
        return manifest ?: throw Refused(Refusal.NO_MANIFEST)
    }

    /**
     * Installs the package: the archive is checked in a staging folder, and only a package that
     * passes takes the place of the installed engine.
     *
     * The whole tree is swapped in one go (the folder is renamed), so the engine that is installed
     * keeps working until the new one is in place.
     *
     * @param appVersion the app's own version: the package may need a newer one
     */
    fun install(archive: InputStream, runtimeDir: File, appVersion: String): Result<AgentPackageManifest> = runCatching {
        val stage = File(runtimeDir.parentFile, ".install").apply { deleteRecursively(); mkdirs() }
        val manifest = unpack(archive, stage)
        if (!manifest.fits(appVersion)) throw Refused(Refusal.TOO_NEW)
        val binary = File(stage, AgentEngineLayout.BINARY)
        if (!binary.isFile) throw Refused(Refusal.NO_BINARY)
        if (digest(binary) != manifest.sha256) throw Refused(Refusal.BAD_DIGEST)
        // the engine has to be runnable: an archive carries no permissions
        binary.setExecutable(true, false)
        val old = File(runtimeDir.parentFile, ".runtime-old")
        old.deleteRecursively()
        val hadOld = runtimeDir.exists()
        if (hadOld && !runtimeDir.renameTo(old)) runtimeDir.deleteRecursively()
        if (!stage.renameTo(runtimeDir)) {
            if (old.exists()) old.renameTo(runtimeDir)
            stage.deleteRecursively()
            throw IOException("The engine could not be replaced")
        }
        old.deleteRecursively()
        manifest
    }

    /** The digest of a file (sha256, in hex). */
    fun digest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
