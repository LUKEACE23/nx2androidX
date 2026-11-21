package com.nx.android.agent

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

data class AgentPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
)

object AgentPresets {
    val ALL: List<AgentPreset> = listOf(
        AgentPreset("glm", "GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4.6"),
        AgentPreset("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
        AgentPreset("doubao", "豆包", "https://ark.cn-beijing.volces.com/api/v3", ""),
        AgentPreset("kimi", "Kimi", "https://api.moonshot.cn/v1", "kimi-latest"),
        AgentPreset("minimax", "MiniMax", "https://api.minimax.chat/v1", "MiniMax-M2.7"),
        AgentPreset("custom", "自定义", "", ""),
    )

    fun byId(id: String): AgentPreset = ALL.firstOrNull { it.id == id } ?: ALL.last()
}

interface AgentSettings {
    val config: AgentModelConfig
    fun update(config: AgentModelConfig)
}
