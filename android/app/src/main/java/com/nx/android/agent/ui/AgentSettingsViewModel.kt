package com.nx.android.agent.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nx.android.R
import com.nx.android.agent.runtime.AgentEngineStore
import com.nx.android.agent.runtime.AgentModelProfile
import com.nx.android.agent.runtime.AgentModelStore
import com.nx.android.agent.runtime.AgentSetup
import com.nx.android.agent.env.PiModelList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The assistant's settings: the engine and the models.
 *
 * The facts live in [AgentSetup] and [AgentModelStore]; this holds them for the screen and does the
 * work the screen asks for (a model saved or thrown away).
 */
@HiltViewModel
class AgentSettingsViewModel @Inject constructor(
    private val setup: AgentSetup,
    private val engine: AgentEngineStore,
    private val models: AgentModelStore,
    private val modelList: PiModelList,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {

    /** What the section draws. */
    data class UiState(
        val engineInstalled: Boolean = false,
        val engineVersion: String = "",
        val profiles: List<AgentModelProfile> = emptyList(),
        val activeProfileId: String = "",

        /** What went wrong with the model the player was editing (empty when nothing did). */
        val modelError: String = "",
    )

    private var profiles by mutableStateOf(models.list())
    private var activeProfileId by mutableStateOf(models.activeId())
    private var modelError by mutableStateOf("")

    val uiState: StateFlow<UiState> = snapshotFlow {
        UiState(
            // the engine comes with the environment: what is in it is read fresh
            setup.engineInstalled, engine.installedVersion(), profiles, activeProfileId, modelError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

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

    /**
     * Asks the engine which models a provider has.
     *
     * The engine knows the providers it carries — the endpoint and the model list are its business.
     */
    suspend fun listModels(providerId: String, apiKey: String): Result<PiModelList.Listing> =
        modelList.list(providerId, apiKey)

    private fun refreshModels() {
        profiles = models.list()
        activeProfileId = models.activeId()
        setup.refresh()
    }
}
