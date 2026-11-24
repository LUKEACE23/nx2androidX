package com.nx.android.agent

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

data class AgentPreset(
    val id: String,
    val baseUrl: String,
    val defaultModel: String,
)

object AgentPresets {
    /**
     * The providers the player may choose.
     *
     * The three the engine knows by itself carry no endpoint here: the engine knows where they
     * live, and the player brings the key. The rest are ours to describe.
     *
     * The label the player reads is in the text tables (agent_provider_*), not here: the table
     * carries it in the player's language, and this file carries the wiring.
     */
    val ALL: List<AgentPreset> = listOf(
        AgentPreset("glm", "https://open.bigmodel.cn/api/paas/v4", "glm-4.6"),
        AgentPreset("deepseek", "", ""),
        AgentPreset("doubao", "https://ark.cn-beijing.volces.com/api/v3", ""),
        AgentPreset("kimi", "", ""),
        AgentPreset("minimax", "", ""),
        AgentPreset("custom", "", ""),
    )

    fun byId(id: String): AgentPreset = ALL.firstOrNull { it.id == id } ?: ALL.last()
}

interface AgentSettings {
    val config: AgentModelConfig
    fun update(config: AgentModelConfig)
}
