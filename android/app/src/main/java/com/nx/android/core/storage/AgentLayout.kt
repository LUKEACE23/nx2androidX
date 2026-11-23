package com.nx.android.core.storage

import java.io.File
import java.security.MessageDigest

/**
 * The assistant's folder layout: where its pieces live inside the player's nx2android folder.
 *
 * Cross-feature path logic, so it belongs with the app's paths ([AppPaths]) and not with the feature
 * that uses it. Free of Android, so the JVM tests drive it.
 */
object AgentLayout {

    /** The assistant's own folder, inside the player's nx2android folder. */
    const val DIR = "agent"

    /** The engine binary's name inside the runtime folder. */
    const val BINARY = "pi"

    /** The installed package's manifest: what is in, and what it needs. */
    const val MANIFEST = "manifest.json"

    /** The working agreement's name, in the folder the engine starts in. */
    const val AGREEMENT = "AGENTS.md"

    /** The assistant's folder: the engine, its own home, the models, the conversations. */
    fun agentDir(nxHome: File): File = File(nxHome, DIR)

    /** Where the engine binary and the installed package's manifest live. */
    fun runtimeDir(agent: File): File = File(agent, "runtime")

    /** The rendered conversations, the memory, the player's copy of the agreement. */
    fun workspaceDir(agent: File): File = File(agent, "workspace")

    /** The model profiles (the keys are encrypted). */
    fun modelsFile(agent: File): File = File(agent, "models.json")

    /**
     * The agreement the engine reads: the engine starts in the player's nx2android folder and loads
     * its context file from the working directory, so that is where the agreement has to be.
     */
    fun agreementFile(nxHome: File): File = File(nxHome, AGREEMENT)

    /**
     * A file we wrote, brought up to date: the agreement, a skill.
     *
     * A file that still carries one of our seeds is brought up to date; a file the player has
     * edited is left alone.
     *
     * How we tell our own text from the player's: beside the file we keep a mark with the digest of
     * the text we wrote. The file is ours while that digest still matches it; the moment the player
     * changes it, it is theirs. Without the mark (the seeds we wrote before it existed) the old
     * texts are recognized by hand, in [knownSeeds].
     */
    fun seedOwned(files: List<File>, seed: String, knownSeeds: Set<String>) {
        for (file in files) {
            val text = if (file.isFile) file.readText() else null
            val mark = File(file.parentFile, "${file.name}.seed")
            val ours = when {
                text == null -> true
                text == seed -> false
                text in knownSeeds -> true
                else -> mark.isFile && mark.readText().trim() == digest(text)
            }
            if (!ours) continue
            file.parentFile?.mkdirs()
            file.writeText(seed)
            runCatching { mark.writeText(digest(seed)) }
        }
    }

    /** A text's SHA-256, as hex. */
    fun digest(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * The mark beside a file we wrote: the digest of the text we wrote.
     *
     * The file is ours while that digest still matches it; the moment the player changes it, it is
     * theirs. Beside the agreement it is what keeps a changed seed flowing to the phone.
     */
    fun seedMark(file: File): File = File(file.parentFile, "${file.name}.seed")

    /** Is this file still the one we wrote (and the player has not touched since)? */
    fun isOurs(file: File, text: String): Boolean {
        val mark = seedMark(file)
        return mark.isFile && mark.readText().trim() == digest(text)
    }

    /** Write the text and the mark beside it. */
    fun writeSeeded(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
        runCatching { seedMark(file).writeText(digest(text)) }
    }
}
