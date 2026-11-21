package com.nx.android.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nx.android.R
import com.nx.android.agent.AgentRole
import com.nx.android.agent.ui.AgentUiState
import com.nx.android.core.ui.components.CapsuleButton
import com.nx.android.core.ui.components.Hint
import com.nx.android.core.ui.theme.NxPalette

/**
 * The assistant's side drawer, landscape (ADR-007/009): three columns on a small screen where height is
 * the scarce resource — the rail (entries and quick questions), the conversation (all the vertical room),
 * and the control column (the input box standing tall, the model chip, new session and send). The drawer
 * is the screen; the chevron in the rail's top closes it.
 */
@Composable
fun AgentPanel(
    viewModel: AgentViewModel,
    chips: List<Int>,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showSetup = !state.configured
    val listState = rememberLazyListState()

    val last = state.messages.lastOrNull()
    LaunchedEffect(state.messages.size, last?.text?.length, last?.tools?.size, state.running) {
        if (state.messages.isNotEmpty() && !listState.isScrollInProgress) {
            listState.scrollToItem(state.messages.size - 1)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    1f to Color(0xF20E0E0F),
                ),
            )
            .clickable { onCollapse() },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(Modifier.fillMaxSize().imePadding()) {
            // ---- the rail: close, expand, and the context's quick questions -----------------------------
            Column(
                Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .background(NxPalette.Background)
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    stringResource(R.string.agent_panel_collapse),
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onCollapse)
                        .padding(4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    stringResource(R.string.agent_panel_expand),
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onExpand)
                        .padding(4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                // the context's quick questions: a tap sends the words as if the player had typed them
                chips.forEach { res ->
                    val words = stringResource(res)
                    RailChip(words) { viewModel.send(words) }
                }
            }
            // ---- the conversation: everything vertical goes here ----------------------------------------
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(NxPalette.Surface)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.error?.let { reason -> ErrorStrip(reason) { viewModel.dismissError() } }
                if (showSetup) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Hint(stringResource(R.string.agent_setup_prompt), size = 12.sp)
                        CapsuleButton(
                            stringResource(R.string.agent_menu_settings),
                            onOpenSettings,
                            Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        if (state.messages.isEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(top = 40.dp),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    Hint(stringResource(R.string.agent_empty), size = 12.sp)
                                }
                            }
                        }
                        items(state.messages, key = { it.id }) { message ->
                            MessageRow(message)
                        }
                        // the turn's first tokens are seconds away: say so instead of sitting silent
                        if (state.running && state.messages.lastOrNull()?.role != AgentRole.ASSISTANT) {
                            item(key = "agent-thinking") {
                                Hint(stringResource(R.string.agent_thinking), size = 12.sp)
                            }
                        }
                    }
                }
            }
            // ---- the control column: the model, the input standing tall, the actions --------------------
            AgentControlColumn(
                state = state,
                onDraftChange = { viewModel.updateDraft(it) },
                onSend = { viewModel.send(state.draft) },
                onStop = { viewModel.stop() },
                onNewSession = { viewModel.newSession() },
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .width(224.dp)
                    .fillMaxHeight()
                    .background(NxPalette.Background)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * The right column: the model chip on top (a tap is the settings), the input box filling the rest of
 * the height, and the two actions under it. Later controls (a model switcher, ...) join here.
 */
@Composable
private fun AgentControlColumn(
    state: AgentUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onNewSession: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = state.configured && (state.running || state.draft.isNotBlank())
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // the model chip: what answers now; a tap opens the settings
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(NxPalette.Surface)
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state.configured) NxPalette.Ready else NxPalette.Danger),
            )
            Text(
                if (state.configured) shortModel(state.config.modelName)
                else stringResource(R.string.agent_model_unset),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        // the input standing tall: the small-landscape layout spends its width, not its height
        OutlinedTextField(
            state.draft,
            onDraftChange,
            Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text(stringResource(R.string.agent_input_hint)) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NxPalette.Surface)
                    .clickable(onClick = onNewSession),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AddComment,
                    stringResource(R.string.agent_action_new_session),
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (canSend) NxPalette.Brand else NxPalette.Surface)
                    .clickable(enabled = canSend) {
                        if (state.running) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.running) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                    stringResource(if (state.running) R.string.agent_stop else R.string.agent_send),
                    Modifier.size(20.dp),
                    tint = if (canSend) NxPalette.Background else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One quick question on the rail: a tap sends the words as the player's own message. */
@Composable
private fun RailChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF26262A))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
}
