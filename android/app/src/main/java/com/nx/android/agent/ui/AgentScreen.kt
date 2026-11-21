package com.nx.android.agent.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.mikepenz.markdown.m3.Markdown
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nx.android.R
import com.nx.android.core.ui.components.CapsuleButton
import com.nx.android.core.ui.components.Hint
import com.nx.android.core.ui.components.StatusDot
import com.nx.android.core.ui.nav.NavKey
import com.nx.android.core.ui.nav.OnNavKey
import com.nx.android.core.ui.theme.GlassPanel
import com.nx.android.core.ui.theme.NxPalette
import com.nx.android.agent.AgentFailure
import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentModelConfig
import com.nx.android.agent.AgentRole
import com.nx.android.agent.AgentToolCard
import com.nx.android.agent.AgentToolState

/** The user bubble's ground: the design spec's one off-palette neutral (a step above the palette's Surface). */
private val UserBubble = Color(0xFF26262A)

/**
 * The assistant's chat page: a full cover like the settings and the FAQ, but portrait (the activity turns
 * the screen while it is open).
 *
 * The top bar says who answers (a capsule: provider · model, or a gray word when nobody does); the flow is
 * the conversation — the player's bubbles on the right, the assistant's words running the full width while
 * they stream, the tools it used folded into one-line cards; the bottom bar sends, or stops while a turn
 * runs. Without a configured key the setup sheet stands in for the flow: the same form the ⋯ menu reopens.
 */
@Composable
fun AgentScreen(
    viewModel: AgentViewModel,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit = {},
    navActive: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler { onClose() }
    // the gamepad's back key: the same door as the system's (this page only knows how to close)
    OnNavKey(enabled = navActive) { key ->
        if (key == NavKey.Back) onClose()
    }
    // the ⋯ menu's "model settings" opens the settings page at the assistant's section; without a key the
    // page opens there by itself
    val showSetup = !state.configured
    val listState = rememberLazyListState()

    // new content arrives at the bottom: follow it (not while the player is scrolling up to read)
    val last = state.messages.lastOrNull()
    LaunchedEffect(state.messages.size, last?.text?.length, last?.tools?.size) {
        if (state.messages.isNotEmpty() && !listState.isScrollInProgress) {
            listState.scrollToItem(state.messages.size - 1)
        }
    }

    Box(Modifier.fillMaxSize().background(NxPalette.Background)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AgentTopBar(
                state.config,
                onBack = onClose,
                onNewSession = { viewModel.newSession() },
                onOpenSetup = onOpenSettings,
            )
            state.error?.let { reason ->
                ErrorStrip(reason) { viewModel.dismissError() }
            }
            if (showSetup) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.heightIn(min = 80.dp))
                    Hint(stringResource(R.string.agent_setup_prompt), size = 13.sp)
                    CapsuleButton(
                        stringResource(R.string.agent_menu_settings),
                        onOpenSettings,
                        Modifier.fillMaxWidth(0.6f),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    if (state.messages.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(top = 96.dp),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Hint(stringResource(R.string.agent_empty), size = 13.sp)
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

// ---- the top bar -----------------------------------------------------------------------------------------------

/** The page's header: the way back, the name, who answers, and the two things this page can redo. */
@Composable
private fun AgentTopBar(
    config: AgentModelConfig,
    onBack: () -> Unit,
    onNewSession: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onBack).padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.common_back),
                Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.agent_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        StatusCapsule(config)
        Spacer(Modifier.width(10.dp))
        Box {
            Box(
                Modifier.clip(RoundedCornerShape(50)).clickable { menuOpen = true }.padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    stringResource(R.string.agent_menu_more),
                    Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.agent_menu_new)) },
                    onClick = { menuOpen = false; onNewSession() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.agent_menu_settings)) },
                    onClick = { menuOpen = false; onOpenSetup() },
                )
            }
        }
    }
}

/** Who answers: provider · model in a capsule, or the gray word when nobody does. */
@Composable
private fun StatusCapsule(config: AgentModelConfig) {
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(50))
            // a faint white floats on the ground; a solid pill would read as a hole
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(if (config.configured) NxPalette.Ready else NxPalette.Unbuilt, size = 7.dp)
        Text(
            if (config.configured) {
                stringResource(providerLabelRes(config.providerId)) + " · " + shortModel(config.modelName)
            } else {
                stringResource(R.string.agent_status_unset)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (config.configured) MaterialTheme.colorScheme.onSurfaceVariant else NxPalette.Unbuilt,
            maxLines = 1,
        )
    }
}

// ---- the strip ---------------------------------------------------------------------------------------------------

/** A turn failed: one line under the top bar, the words say which kind (the code says nothing). */
@Composable
internal fun ErrorStrip(reason: AgentFailure, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NxPalette.Danger.copy(alpha = 0.14f))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(NxPalette.Danger, size = 7.dp)
        Text(
            stringResource(failureText(reason)),
            style = MaterialTheme.typography.bodySmall,
            color = NxPalette.Danger,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.common_close),
            style = MaterialTheme.typography.labelSmall,
            color = NxPalette.Danger,
        )
    }
}

/** Why a turn failed: the data layer reports codes, this page gives the words. */
@StringRes
private fun failureText(reason: AgentFailure): Int = when (reason) {
    AgentFailure.NO_KEY -> R.string.agent_error_no_key
    AgentFailure.BAD_KEY -> R.string.agent_error_bad_key
    AgentFailure.NETWORK -> R.string.agent_error_network
    AgentFailure.MODEL_NO_TOOLS -> R.string.agent_error_no_tools
    AgentFailure.LOOP_ERROR -> R.string.agent_error_loop
}

// ---- the flow ---------------------------------------------------------------------------------------------------

/** One rendered message: the player's bubble, or the assistant's block. */
@Composable
internal fun MessageRow(message: AgentMessage) {
    if (message.role == AgentRole.USER) UserBubbleText(message.text) else AssistantBlock(message)
}

/** The player's own words: a bubble on the right, at most 78% of the width. */
@Composable
private fun UserBubbleText(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.5.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(14.dp))
                .background(UserBubble)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

/** The assistant's answer: the running tool shows live, the finished ones fold into one summary row, then the words. */
@Composable
private fun AssistantBlock(message: AgentMessage) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // the call in flight: the live feedback the player watches
        message.tools.filter { it.state == AgentToolState.RUNNING }.forEach { ToolCard(it) }
        // everything finished folds into a single line — ten tool calls must not bury the question
        val finished = message.tools.filter { it.state != AgentToolState.RUNNING }
        if (finished.isNotEmpty()) ToolSummary(finished, message.id)
        if (message.text.isNotEmpty()) {
            Markdown(message.text)
        }
    }
}

/**
 * The finished tool calls, folded: one line says the count and the per-tool tallies; a tap unfolds
 * the full cards. The fold is per message, so it stays as the player left it across recompositions.
 */
@Composable
private fun ToolSummary(tools: List<AgentToolCard>, messageId: Long) {
    var expanded by remember(messageId) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "agent-tools-fold")
    if (expanded) {
        tools.forEach { ToolCard(it) }
    }
    val tallies = tools.groupingBy { it.name }.eachCount()
        .entries.sortedByDescending { it.value }
        .joinToString(" · ") { "${it.key}×${it.value}" }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.agent_tools_summary, tools.size) +
                if (tallies.isNotBlank()) " · $tallies" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = null,
            Modifier.size(16.dp).rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One tool call: collapsed it is a line (the name, a one-sentence result, the state's dot); a tap unfolds the details. */
@Composable
internal fun ToolCard(card: AgentToolCard) {
    var expanded by remember(card.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "agent-tool-arrow")
    val (tint, stateWord) = when (card.state) {
        AgentToolState.RUNNING -> NxPalette.Building to stringResource(R.string.agent_tool_running)
        AgentToolState.DONE -> NxPalette.Ready to stringResource(R.string.agent_tool_done)
        AgentToolState.FAILED -> NxPalette.Danger to stringResource(R.string.agent_tool_failed)
    }
    GlassPanel(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        contentPadding = 12.dp,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    card.resultSummary ?: stateWord,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    null,
                    Modifier.size(18.dp).rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusDot(tint, size = 8.dp)
            }
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.agent_tool_args),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        card.argsSummary,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (!card.resultSummary.isNullOrEmpty()) {
                        Text(
                            stringResource(R.string.agent_tool_result),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            card.resultSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---- the input bar ----------------------------------------------------------------------------------------------

/** The bottom bar: the box grows up to four lines; the round button sends, or stops while a turn runs. */
@Composable
internal fun AgentInputBar(running: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val canSend = !running && text.isNotBlank()
    Row(
        Modifier.fillMaxWidth().imePadding().padding(bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            text,
            { text = it },
            Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.agent_input_hint)) },
            maxLines = 4,
        )
        Box(
            Modifier
                .padding(bottom = 4.dp)
                .size(46.dp)
                .clip(RoundedCornerShape(50))
                .background(if (running || canSend) NxPalette.Brand else NxPalette.Surface)
                .clickable(enabled = running || canSend) {
                    if (running) onStop()
                    else {
                        onSend(text)
                        text = ""
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (running) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                stringResource(if (running) R.string.agent_stop else R.string.agent_send),
                Modifier.size(20.dp),
                tint = if (running || canSend) NxPalette.Background else NxPalette.Unbuilt,
            )
        }
    }
}
