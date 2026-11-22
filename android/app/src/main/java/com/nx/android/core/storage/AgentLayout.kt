package com.nx.android.core.storage

import java.io.File

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
     * The agreement, in the places it is read from. A file that still carries one of our seeds is
     * brought up to date; a file the player has edited is left alone.
     */
    fun seedAgreement(files: List<File>, seed: String, knownSeeds: Set<String>) {
        for (file in files) {
            val text = if (file.isFile) file.readText() else null
            when {
                text == null -> { file.parentFile?.mkdirs(); file.writeText(seed) }
                text in knownSeeds && text != seed -> file.writeText(seed)
            }
        }
    }
}
