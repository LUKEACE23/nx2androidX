package com.nx.android.agent.runtime

import java.io.File

/**
 * Where the engine's pieces live, and how the ones an earlier build left behind come over.
 *
 * Free of Android, so the JVM tests drive it.
 */
object AgentEngineLayout {

    /** The engine binary's name inside the runtime folder. */
    const val BINARY = "pi"

    /** The installed package's manifest: what is in, and what it needs. */
    const val MANIFEST = "manifest.json"

    /**
     * Copies the engine and its own tree out of the folders the environment's reset throws away.
     *
     * Copy, not move: an older build must still find them (a rollback keeps working), and the
     * copies are a few megabytes. Idempotent — a second run finds the new place already filled.
     *
     * @return whether anything was copied
     */
    fun migrate(legacyBinary: File, legacyPiHome: File, runtimeDir: File, home: File): Boolean {
        var moved = false
        if (legacyBinary.isFile && !File(runtimeDir, BINARY).isFile) {
            runtimeDir.mkdirs()
            runCatching {
                legacyBinary.copyTo(File(runtimeDir, BINARY), overwrite = false)
                File(runtimeDir, BINARY).setExecutable(true, false)
            }.onSuccess { moved = true }
        }
        val piHome = File(home, ".pi")
        if (legacyPiHome.isDirectory && !piHome.isDirectory) {
            val stage = File(home, ".pi.new")
            runCatching {
                stage.deleteRecursively()
                legacyPiHome.copyRecursively(stage, overwrite = true)
                stage.renameTo(piHome)
            }.onSuccess { moved = true }
            stage.deleteRecursively()
        }
        return moved
    }

    /**
     * The engine's session files moved with the home, so the stored pointers still name the old
     * place. Rewrite them in place: a conversation the player comes back to must still open.
     *
     * @return whether anything was rewritten
     */
    fun rewriteSessionPointers(metaFile: File, oldPrefix: String, newPrefix: String): Boolean {
        if (!metaFile.isFile || oldPrefix.isBlank() || newPrefix.isBlank()) return false
        val text = metaFile.readText()
        if (!text.contains(oldPrefix)) return false
        val rewritten = text.replace(oldPrefix, newPrefix)
        if (rewritten == text) return false
        runCatching { metaFile.writeText(rewritten) }
        return true
    }
}
