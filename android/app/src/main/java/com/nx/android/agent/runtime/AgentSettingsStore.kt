package com.nx.android.agent.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.data.PrefsStore
import com.nx.android.agent.AgentModelConfig
import com.nx.android.agent.AgentSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The assistant's BYOK choice, kept in the app's own settings file (ADR-003).
 *
 * The API key lands in the app's private storage like the rest of the settings: private to the app,
 * never logged, never part of any diagnostic (ADR-004). Sealing it inside Android's Keystore is a
 * planned hardening, not a v0 behaviour.
 */
@Singleton
class AgentSettingsStore @Inject constructor(
    private val prefs: PrefsStore,
) : AgentSettings {

    private companion object {
        const val PROVIDER = "agent.provider"
        const val BASE_URL = "agent.baseUrl"
        const val MODEL = "agent.model"
        const val API_KEY = "agent.apiKey"
    }

    /** The choice as observable state: a write goes to disk at once, a read is a Compose read. */
    private var storedProvider by mutableStateOf(prefs.str(PROVIDER))
    private var storedBaseUrl by mutableStateOf(prefs.str(BASE_URL))
    private var storedModel by mutableStateOf(prefs.str(MODEL))
    private var storedKey by mutableStateOf(prefs.str(API_KEY))

    override val config: AgentModelConfig
        get() = AgentModelConfig(storedProvider, storedBaseUrl, storedKey, storedModel)

    override fun update(config: AgentModelConfig) {
        storedProvider = config.providerId
        storedBaseUrl = config.baseUrl
        storedModel = config.modelName
        storedKey = config.apiKey
        prefs.put(PROVIDER, config.providerId)
        prefs.put(BASE_URL, config.baseUrl)
        prefs.put(MODEL, config.modelName)
        prefs.put(API_KEY, config.apiKey)
    }
}
