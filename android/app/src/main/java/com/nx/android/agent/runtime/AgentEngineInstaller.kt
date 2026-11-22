package com.nx.android.agent.runtime

import android.util.Log
import com.nx.android.core.storage.AgentLayout
import com.nx.android.core.storage.AppPaths
import com.nx.android.data.agent.AgentPackageCatalog
import com.nx.android.data.agent.AgentPackageDownloader
import com.nx.android.data.agent.AgentPackageInstaller
import com.nx.android.domain.AgentPackage
import com.nx.android.domain.AgentPackageManifest
import com.nx.android.domain.environment.EnvironmentSetup
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Getting the engine: the package comes from the repository or from a file the player picked, and
 * both go through the same check and the same installation.
 *
 * The installed engine lives in the assistant's own folder, so the environment's reset does not take
 * it away.
 */
@Singleton
class AgentEngineInstaller @Inject constructor(
    private val paths: AppPaths,
    private val env: EnvironmentSetup,
) {

    /** Where a downloaded package waits for the installer. */
    private val downloads: File get() = File(paths.agent, ".download")

    /** The packages the repository's newest release carries. */
    suspend fun available(): Result<List<String>> = withContext(Dispatchers.IO) {
        AgentPackageCatalog.latest().map { it.second }
    }

    /**
     * Downloads the newest package the repository carries and installs it.
     *
     * @param onProgress how far along the download is, 0 to 1
     */
    suspend fun installLatest(
        appVersion: String,
        onProgress: (Float) -> Unit,
        cancelled: () -> Boolean,
    ): Result<AgentPackageManifest> = withContext(Dispatchers.IO) {
        val latest = AgentPackageCatalog.latest().getOrElse { return@withContext Result.failure(it) }
        val name = latest.second.firstOrNull()
            ?: return@withContext Result.failure(IOException("the release carries no package"))
        installFromUrl(AgentPackage.assetUrl(latest.first, name), appVersion, onProgress, cancelled)
    }

    /**
     * Downloads a package and installs it.
     *
     * @param onProgress how far along the download is, 0 to 1
     */
    suspend fun installFromUrl(
        url: String,
        appVersion: String,
        onProgress: (Float) -> Unit,
        cancelled: () -> Boolean,
    ): Result<AgentPackageManifest> = withContext(Dispatchers.IO) {
        downloads.mkdirs()
        val dest = File(downloads, "package.zip")
        val downloaded = AgentPackageDownloader.download(url, dest, onProgress, cancelled)
        downloaded.fold(
            onSuccess = { installFrom(it, appVersion) },
            onFailure = { Result.failure(it) },
        ).also { dest.delete() }
    }

    /**
     * Installs a package the player picked from a file.
     */
    fun installFrom(file: File, appVersion: String): Result<AgentPackageManifest> {
        val result = file.inputStream().use {
            AgentPackageInstaller.install(it, paths.agentRuntime, appVersion)
        }
        result.onSuccess {
            Log.i(TAG, "the engine package is in: $it")
            // the engine runs in the guest: its interpreter has to point at this phone's loader
            env.patchInterpreter(File(paths.agentRuntime, AgentLayout.BINARY))
        }
        return result
    }

    private companion object {
        const val TAG = "Nx2aAgentPkg"
    }
}
