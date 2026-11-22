package com.nx.android.agent.runtime

import com.nx.android.core.storage.AppPaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * The engine's own place: the binary, the home it runs under, and the one-time copy of what an
 * earlier build kept in the folders the environment's reset throws away.
 *
 * The reset deletes the environment's own folders (rootfs, tool, nx2a, home, tmp) — the engine, its
 * home, the models and the conversations live in the assistant's own folder, which it does not touch.
 */
@Singleton
class AgentEngineStore @Inject constructor(
    private val paths: AppPaths,
) {

    /** The assistant's home: the engine runs with HOME pointed here, so its own tree lands in `<home>/.pi`. */
    val home: File get() = paths.agent

    /** Where the engine binary and the installed package's manifest live. */
    val runtimeDir: File get() = paths.agentRuntime

    /** The engine binary. */
    val binary: File get() = File(runtimeDir, AgentEngineLayout.BINARY)

    /** The manifest of the installed package: what is in, and what it needs. */
    val manifest: File get() = File(runtimeDir, AgentEngineLayout.MANIFEST)

    /** Whether the engine is there. */
    val installed: Boolean get() = binary.isFile

    /** The version of the installed package (empty when nothing is installed). */
    fun installedVersion(): String {
        val text = manifest.takeIf { it.isFile }?.readText().orEmpty()
        return runCatching { JSONObject(text).optString("version").trim() }.getOrDefault("")
    }

    /**
     * One-time: the engine and its own tree an earlier build kept in the environment's folders come
     * in here. The conversation pointers that name the old place are rewritten with them.
     *
     * @return whether anything moved
     */
    fun migrateLegacy(): Boolean {
        val legacyHome = File(paths.home, ".pi")
        val moved = AgentEngineLayout.migrate(
            legacyBinary = File(paths.nxHome, "agent/pi/pi"),
            legacyPiHome = legacyHome,
            runtimeDir = runtimeDir,
            home = home,
        )
        AgentEngineLayout.rewriteSessionPointers(
            metaFile = File(File(paths.agentWorkspace, "sessions"), "_meta.json"),
            oldPrefix = legacyHome.absolutePath,
            newPrefix = File(home, ".pi").absolutePath,
        )
        return moved
    }
}
