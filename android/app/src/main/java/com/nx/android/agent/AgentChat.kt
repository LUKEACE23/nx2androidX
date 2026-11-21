package com.nx.android.agent

import kotlinx.coroutines.flow.Flow

/**
 * The built-in agent assistant's one contract: the feature layer renders it, the data layer implements it
 * on AgentScope Java (ADR-002). Nothing here touches Android, Compose or the framework — the shapes are the
 * chat page's vocabulary (80-交互设计.md: messages, tool cards, streaming).
 */

/** Which side of the conversation a message belongs to. */
enum class AgentRole { USER, ASSISTANT }

/** A tool invocation as the chat page shows it: one collapsible card (K1, collapsed form). */
data class AgentToolCard(
    val id: String,
    val name: String,
    val argsSummary: String,
    val state: AgentToolState,
    val resultSummary: String? = null,
)

enum class AgentToolState { RUNNING, DONE, FAILED }

/** One rendered message. The assistant message's [text] grows while streaming. */
data class AgentMessage(
    val id: Long,
    val role: AgentRole,
    val text: String,
    val tools: List<AgentToolCard> = emptyList(),
)

/** What one turn emits, in order. The ViewModel folds these into [AgentMessage]s. */
sealed interface AgentEvent {
    /** Append to the current assistant message's text. */
    data class TextDelta(val text: String) : AgentEvent

    /** A tool call started; its card joins the current assistant message. */
    data class ToolStarted(val card: AgentToolCard) : AgentEvent

    /** The tool finished; update its card by [AgentToolCard.id]. */
    data class ToolFinished(val id: String, val state: AgentToolState, val resultSummary: String) : AgentEvent

    /** The turn is over; [assistantText] is the final text ("" when the turn was all tool calls). */
    data class TurnDone(val assistantText: String) : AgentEvent

    /** The turn failed before producing anything usable. */
    data class Failed(val reason: AgentFailure) : AgentEvent
}

/**
 * Why a turn failed. The data layer reports codes, the UI layer gives the words
 * (my-docs/踩坑/01-界面语言.md §4 — the same rule the rest of the app follows).
 */
enum class AgentFailure { NO_KEY, BAD_KEY, NETWORK, MODEL_NO_TOOLS, LOOP_ERROR }

/** The BYOK model choice (ADR-003): the player's own key, one OpenAI-compatible endpoint. */
data class AgentModelConfig(
    /** One of [AgentModels] preset ids, or "custom". */
    val providerId: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
) {
    val configured: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank()
}

/** The conversation itself. v0: one live session in memory, resettable. */
interface AgentChat {
    /** The conversation so far, oldest first. */
    val messages: List<AgentMessage>

    /** Run one turn: send [userText], stream what the assistant does until the turn ends. */
    suspend fun send(userText: String): Flow<AgentEvent>

    /** Forget the conversation (a new session; the old one is simply dropped — v0 has no history list). */
    fun reset()
}
