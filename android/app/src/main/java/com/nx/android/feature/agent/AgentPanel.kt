package com.nx.android.feature.agent

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nx.android.R
import com.nx.android.core.ui.components.CapsuleButton
import com.nx.android.core.ui.components.Hint
import com.nx.android.core.ui.theme.NxPalette

/**
 * The assistant's side drawer: the landscape answer to "where does the assistant live" (ADR-007). Two
 * columns — a tool rail (the entry marks, the expand arrow, and the context's quick questions) beside the
 * chat; the page beneath stays visible on the left and darkens toward them, a tap there closes.
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
    LaunchedEffect(state.messages.size, last?.text?.length, last?.tools?.size) {
        if (state.messages.isNotEmpty() && !listState.isScrollInProgress) {
            listState.scrollToItem(state.messages.size - 1)
        }
    }

    // The game drawer's shape: the page stays visible on the left and darkens toward the panel; a tap on
    // the dark part closes, a tap on the panel itself does not fall through
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.28f to Color(0x8C0E0E0F),
                    0.44f to Color(0xD90E0E0F),
                    1f to Color(0xF20E0E0F),
                ),
            )
            .clickable { onCollapse() },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(Modifier.fillMaxHeight()) {
            // ---- the tool rail: the entry marks and the context's quick questions ----------------------
            Column(
                Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .background(NxPalette.Background)
                    .padding(vertical = 14.dp, horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.SmartToy, null, Modifier.size(22.dp), tint = NxPalette.Brand)
                Text(
                    stringResource(R.string.agent_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            // ---- the chat ---------------------------------------------------------------------------------
            Column(
                Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(NxPalette.Surface)
                    .clickable(enabled = false) {}
                    .imePadding()
                    .padding(horizontal = 12.dp),
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
                        Spacer(Modifier.size(24.dp))
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
                    }
                    AgentInputBar(
                        running = state.running,
                        onSend = { viewModel.send(it) },
                        onStop = { viewModel.stop() },
                    )
                }
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
