package com.nx.android.agent.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nx.android.R
import com.nx.android.agent.runtime.AgentEngineInstaller
import com.nx.android.agent.runtime.AgentEngineStore
import com.nx.android.agent.runtime.AgentModelProfile
import com.nx.android.agent.runtime.AgentModelStore
import com.nx.android.agent.runtime.AgentSetup
import com.nx.android.data.AppVersion
import com.nx.android.data.agent.AgentPackageDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The assistant's settings: the engine and the models.
 *
 * The facts live in [AgentSetup] and [AgentModelStore]; this holds them for the screen and does the
 * work the screen asks for (a download, an import, a model saved or thrown away).
 */
@HiltViewModel
class AgentSettingsViewModel @Inject constructor(
    private val setup: AgentSetup,
    private val engine: AgentEngineStore,
    private val installer: AgentEngineInstaller,
    private val models: AgentModelStore,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {

    /** What the section draws. */
    data class UiState(
        val engineInstalled: Boolean = false,
        val engineVersion: String = "",
        val profiles: List<AgentModelProfile> = emptyList(),
        val activeProfileId: String = "",
        val busy: Boolean = false,
        val busyAction: String = "",
        val progress: Float = 0f,

        /** What the dialog says about the last attempt (0: nothing to say). */
        val noteRes: Int = 0,
        val noteArg: String = "",

        /** What went wrong with the model the player was editing (empty when nothing did). */
        val modelError: String = "",
    )

    private var engineInstalled by mutableStateOf(setup.engineInstalled)
    private var engineVersion by mutableStateOf(engine.installedVersion())
    private var profiles by mutableStateOf(models.list())
    private var activeProfileId by mutableStateOf(models.activeId())
    private var busy by mutableStateOf(false)
    private var busyAction by mutableStateOf("")
    private var progress by mutableStateOf(0f)
    private var noteRes by mutableStateOf(0)
    private var noteArg by mutableStateOf("")
    private var modelError by mutableStateOf("")

    /** The download's stop flag: the player can call it off at any time. */
    private val downloadCancelled = AtomicBoolean(false)

    val uiState: StateFlow<UiState> = snapshotFlow {
        UiState(
            engineInstalled, engineVersion, profiles, activeProfileId,
            busy, busyAction, progress, noteRes, noteArg, modelError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /**
     * Downloads the newest package the repository carries and installs it.
     *
     * The download always happens: the button says "download the latest version", and a player who
     * wants the engine back (or a newer one) gets it.
     */
    fun downloadLatest() {
        if (busy) return
        busy = true
        busyAction = ACTION_DOWNLOAD
        progress = 0f
        noteRes = 0
        downloadCancelled.set(false)
        viewModelScope.launch {
            val result = installer.installLatest(
                AppVersion.of(ctx),
                { p -> progress = p },
                { downloadCancelled.get() },
            )
            finish(result)
        }
    }

    /**
     * The player called it off: the download stops at the next chunk, the package that was on its
     * way is thrown away, and the engine that is installed stays where it is.
     */
    fun cancelActive() {
        downloadCancelled.set(true)
    }

    /** Installs a package the player picked from a file. */
    fun importPackage(file: File) {
        if (busy) return
        busy = true
        busyAction = ACTION_IMPORT
        noteRes = 0
        viewModelScope.launch {
            // 解包、算摘要、补解释器：都是重活，不在主线程上做
            val result = withContext(Dispatchers.IO) {
                installer.installFrom(file, AppVersion.of(ctx))
            }
            finish(result)
        }
    }

    /** Saves a model (a new one or an edited one) and makes it the default when it is the only one. */
    fun saveModel(profile: AgentModelProfile): Boolean {
        val saved = models.save(profile)
        return saved.onSuccess {
            modelError = ""
            refreshModels()
        }.onFailure {
            // the keystore said no: say so in the box, keep it open, do not crash
            modelError = ctx.getString(R.string.agent_models_failed, it.message.orEmpty())
        }.isSuccess
    }

    /** Throws a model away; the default goes to another one. */
    fun removeModel(id: String) {
        models.remove(id)
        refreshModels()
    }

    /** Makes a model the default one. */
    fun setActiveModel(id: String) {
        models.setActive(id)
        refreshModels()
    }

    private fun refreshModels() {
        profiles = models.list()
        activeProfileId = models.activeId()
        setup.refresh()
    }

    private fun finish(result: Result<*>) {
        result.onSuccess {
            engineInstalled = engine.installed
            engineVersion = engine.installedVersion()
            setup.refresh()
            noteRes = R.string.agent_engine_installed
            noteArg = engineVersion
        }.onFailure {
            // 玩家自己叫停的：安静地过去，不道歉
            if (it is AgentPackageDownloader.Cancelled) {
                noteRes = 0
                noteArg = ""
            } else {
                noteRes = R.string.agent_engine_failed
                noteArg = it.message.orEmpty()
            }
        }
        busy = false
        busyAction = ""
        progress = 0f
    }

    companion object {
        const val ACTION_DOWNLOAD = "download"
        const val ACTION_IMPORT = "import"
    }
}
