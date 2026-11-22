package com.nx.android.agent

import kotlinx.coroutines.flow.Flow

enum class AgentRole { USER, ASSISTANT }

data class AgentToolCard(
    val id: String,
    val name: String,
    val argsSummary: String,
    val state: AgentToolState,
    val resultSummary: String? = null,
)

enum class AgentToolState { RUNNING, DONE, FAILED }

data class AgentMessage(
    val id: Long,
    val role: AgentRole,
    val text: String,
    val tools: List<AgentToolCard> = emptyList(),
)

sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent
    data class ToolStarted(val card: AgentToolCard) : AgentEvent
    data class ToolFinished(val id: String, val state: AgentToolState, val resultSummary: String) : AgentEvent
    data class TurnDone(val assistantText: String) : AgentEvent
    data class Failed(val reason: AgentFailure) : AgentEvent

    /** The running totals of the session, re-emitted as usage lands (ADR-009's usage row). */
    data class UsageUpdated(val tokens: Long, val cost: Double) : AgentEvent
}

enum class AgentFailure { NO_KEY, BAD_KEY, NETWORK, MODEL_NO_TOOLS, LOOP_ERROR }

data class AgentModelConfig(
    val providerId: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
) {
    val configured: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank()
}

interface AgentChat {
    val messages: List<AgentMessage>
    suspend fun send(userText: String, context: AgentPageContext? = null): Flow<AgentEvent>
    fun reset()
}

data class AgentPageContext(
    val page: String,
    val subject: AgentSubject? = null,
    val facts: Map<String, String> = emptyMap(),
)

data class AgentSubject(val kind: String, val id: String, val title: String)

interface AgentUiActions {
    fun navigate(page: String, game: String? = null): String
}
