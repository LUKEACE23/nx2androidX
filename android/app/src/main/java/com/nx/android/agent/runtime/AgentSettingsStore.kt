package com.nx.android.agent.runtime

import android.content.Context
import com.nx.android.agent.AgentModelConfig
import com.nx.android.agent.AgentSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentSettingsStore @Inject constructor(@ApplicationContext ctx: Context) : AgentSettings {

    private val prefs = ctx.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)

    override val config: AgentModelConfig
        get() = AgentModelConfig(
            providerId = prefs.getString("providerId", "") ?: "",
            baseUrl = prefs.getString("baseUrl", "") ?: "",
            apiKey = prefs.getString("apiKey", "") ?: "",
            modelName = prefs.getString("modelName", "") ?: "",
        )

    override fun update(config: AgentModelConfig) {
        prefs.edit()
            .putString("providerId", config.providerId)
            .putString("baseUrl", config.baseUrl)
            .putString("modelName", config.modelName)
            .putString("apiKey", config.apiKey)
            .apply()
    }
}
