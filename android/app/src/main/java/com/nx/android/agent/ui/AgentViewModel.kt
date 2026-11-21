package com.nx.android.agent.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nx.android.agent.AgentChat
import com.nx.android.agent.AgentEvent
import com.nx.android.agent.AgentFailure
import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentModelConfig
import com.nx.android.agent.AgentRole
import com.nx.android.agent.AgentSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the chat page renders: the conversation, the running turn, the strip's failure, the model choice. */
data class AgentUiState(
    val messages: List<AgentMessage> = emptyList(),
    val running: Boolean = false,
    val error: AgentFailure? = null,
    val config: AgentModelConfig = AgentModelConfig("", "", "", ""),

    /** The capsule and the setup gate both read this: a blank key or endpoint means the assistant cannot run. */
    val configured: Boolean = false,
)

/**
 * The chat page's holder.
 *
 * The rendered list is the page's own (its own ids): the user's words land at once, the turn's events fold
 * into one assistant message while they stream. [AgentChat.messages] is read once at birth — after that the
 * chat's copy is the model's context and this list is what the player sees, so the two never need to agree
 * on numbering. The stop button is the turn's job cancelled: what streamed before the stop stays.
 */
@HiltViewModel
class AgentViewModel @Inject constructor(
    private val chat: AgentChat,
    private val settings: AgentSettings,
) : ViewModel() {

    private var messages by mutableStateOf(chat.messages)
    private var running by mutableStateOf(false)
    private var failure by mutableStateOf<AgentFailure?>(null)
    private var config by mutableStateOf(settings.config)

    /** The ids are this list's own: they start above whatever the chat brought along. */
    private var nextId = (chat.messages.maxOfOrNull { it.id } ?: 0L) + 1

    /** The assistant message the running turn's events land in; the turn's first event makes it. */
    private var draftId: Long? = null
    private var turn: Job? = null

    val uiState: StateFlow<AgentUiState> = snapshotFlow {
        AgentUiState(messages, running, failure, config, config.configured)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgentUiState())

    /** One turn: the words land at once, the answer streams in as the events arrive. */
    fun send(text: String, context: com.nx.android.agent.AgentPageContext? = null) {
        val words = text.trim()
        if (words.isEmpty() || running) return
        failure = null
        messages = messages + AgentMessage(nextId++, AgentRole.USER, words)
        running = true
        turn = viewModelScope.launch {
            try {
                chat.send(words, context).collect { fold(it) }
            } catch (e: CancellationException) {
                throw e   // the stop button: the cancel stays visible to the scope
            } catch (e: Exception) {
                failure = AgentFailure.LOOP_ERROR   // nothing the failure codes cover: the retry words
            } finally {
                running = false
                draftId = null
            }
        }
    }

    /** The square button while a turn runs: the turn's job is cancelled, the streamed part stays. */
    fun stop() {
        turn?.cancel()
        turn = null
    }

    /** The menu's new chat: the turn stops, the conversation and the strip are forgotten. */
    fun newSession() {
        stop()
        chat.reset()
        messages = emptyList()
        failure = null
    }

    /** The strip's way out (the next turn replaces the words anyway). */
    fun dismissError() {
        failure = null
    }

    /** The setup sheet's save: the choice lands where the data layer keeps it (the key never shows up here). */
    fun updateConfig(providerId: String, baseUrl: String, apiKey: String, modelName: String) {
        settings.update(AgentModelConfig(providerId, baseUrl, apiKey, modelName))
        config = settings.config
        failure = null
    }

    // ---- the turn's events → the rendered list ------------------------------------------------------------------

    private fun fold(event: AgentEvent) {
        when (event) {
            is AgentEvent.TextDelta -> {
                val draft = draft()
                update(draft.id) { it.copy(text = it.text + event.text) }
            }

            is AgentEvent.ToolStarted -> {
                val draft = draft()
                update(draft.id) { it.copy(tools = it.tools + event.card) }
            }

            is AgentEvent.ToolFinished -> updateLastAssistant { m ->
                m.copy(tools = m.tools.map { card ->
                    if (card.id == event.id) card.copy(state = event.state, resultSummary = event.resultSummary)
                    else card
                })
            }

            is AgentEvent.TurnDone -> {
                // the final text is the canonical one ("" meant the turn was all tool calls)
                if (event.assistantText.isNotEmpty()) updateLastAssistant { it.copy(text = event.assistantText) }
                draftId = null
            }

            is AgentEvent.Failed -> {
                failure = event.reason
                dropEmptyDraft()
            }
        }
    }

    /** The assistant message the events of the running turn land in; the turn's first event makes it. */
    private fun draft(): AgentMessage {
        val id = draftId
        val last = messages.lastOrNull()
        if (id != null && last?.id == id) return last
        val made = AgentMessage(nextId++, AgentRole.ASSISTANT, "")
        messages = messages + made
        draftId = made.id
        return made
    }

    /** A turn that failed before anything arrived leaves an empty shell behind: take it out. */
    private fun dropEmptyDraft() {
        val id = draftId
        val last = messages.lastOrNull()
        if (id != null && last?.id == id && last.text.isEmpty() && last.tools.isEmpty()) {
            messages = messages.dropLast(1)
        }
        draftId = null
    }

    private fun update(id: Long, change: (AgentMessage) -> AgentMessage) {
        messages = messages.map { if (it.id == id) change(it) else it }
    }

    private fun updateLastAssistant(change: (AgentMessage) -> AgentMessage) {
        val last = messages.lastOrNull() ?: return
        if (last.role != AgentRole.ASSISTANT) return
        update(last.id, change)
    }
}
