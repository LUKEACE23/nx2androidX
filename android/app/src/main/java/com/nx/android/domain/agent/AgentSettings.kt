package com.nx.android.domain.agent

/**
 * The BYOK presets (ADR-003) and the settings the chat page needs to read and write.
 * Pure data plus an interface — the data layer persists it, the feature layer renders it.
 */

/** A BYOK provider preset: an OpenAI-compatible endpoint the player may bring a key for. */
data class AgentPreset(
    val id: String,
    /** The debug/log name (English by rule); the picker's shown name comes from the resource tables. */
    val label: String,
    val baseUrl: String,
    /** Suggested first model; the player can type any model name the endpoint serves. */
    val defaultModel: String,
)

/** The presets the setup sheet offers; "custom" is the escape hatch (any OpenAI-compatible base URL). */
object AgentPresets {
    val ALL: List<AgentPreset> = listOf(
        AgentPreset("glm", "GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4.6"),
        AgentPreset("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
        AgentPreset("doubao", "Doubao", "https://ark.cn-beijing.volces.com/api/v3", ""),
        AgentPreset("kimi", "Kimi", "https://api.moonshot.cn/v1", "kimi-latest"),
        AgentPreset("minimax", "MiniMax", "https://api.minimax.chat/v1", "MiniMax-M2.7"),
        AgentPreset("custom", "Custom", "", ""),
    )

    fun byId(id: String): AgentPreset = ALL.firstOrNull { it.id == id } ?: ALL.last()
}

/**
 * Reads and writes the agent's model choice. The API key is a secret: it is stored by the data layer
 * (Keystore-backed prefs) and must never appear in logs, tool results or diagnostics (ADR-004).
 */
interface AgentSettings {
    /** The current choice; [AgentModelConfig.configured] says whether the assistant can run. */
    val config: AgentModelConfig

    /** Replace the choice. */
    fun update(config: AgentModelConfig)
}
