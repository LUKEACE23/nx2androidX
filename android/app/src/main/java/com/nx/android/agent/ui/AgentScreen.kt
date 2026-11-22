package com.nx.android.agent.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.snapshotFlow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
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
import com.nx.android.agent.AgentFailure
import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentModelConfig
import com.nx.android.agent.AgentRole
import com.nx.android.agent.AgentToolCard
import com.nx.android.agent.AgentToolState

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
    // every (re)open lands at the true bottom — after the first layout, not before it exists
    LaunchedEffect(Unit) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        listState.scrollToBottom()
    }
    LaunchedEffect(state.messages.size, last?.text?.length, last?.tools?.size) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()
        val atEnd = lastVisible == null ||
            (lastVisible.index >= info.totalItemsCount - 1 &&
                lastVisible.offset + lastVisible.size - info.viewportEndOffset < 400)
        if (atEnd) listState.scrollToBottom()
    }

    // the light sheet: everything inside resolves ink-on-white, whatever the app's dark theme says
    MaterialTheme(colorScheme = AgentLightScheme, typography = AgentTypography) {
    Box(Modifier.fillMaxSize().background(AgentPalette.Ground)) {
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
                    text = state.draft,
                    onTextChange = { viewModel.updateDraft(it) },
                    running = state.running,
                    onSend = { viewModel.send(it) },
                    onStop = { viewModel.stop() },
                )
            }
        }
    }
    } // MaterialTheme(agent light)
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
                tint = AgentPalette.Ink,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.agent_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = AgentPalette.Ink,
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
                    tint = AgentPalette.Ink,
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
            .background(AgentPalette.Panel)
            .border(1.dp, AgentPalette.Hairline, RoundedCornerShape(50))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(if (config.configured) AgentPalette.Ready else AgentPalette.Idle, size = 7.dp)
        Text(
            if (config.configured) {
                stringResource(providerLabelRes(config.providerId)) + " · " + shortModel(config.modelName)
            } else {
                stringResource(R.string.agent_status_unset)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (config.configured) AgentPalette.Ink2 else AgentPalette.Idle,
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
            .background(AgentPalette.Danger.copy(alpha = 0.10f))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(AgentPalette.Danger, size = 7.dp)
        Text(
            stringResource(failureText(reason)),
            style = MaterialTheme.typography.bodySmall,
            color = AgentPalette.Danger,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.common_close),
            style = MaterialTheme.typography.labelSmall,
            color = AgentPalette.Danger,
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
internal fun MessageRow(message: AgentMessage, onToolsClick: ((AgentMessage) -> Unit)? = null) {
    if (message.role == AgentRole.USER) UserBubbleText(message.text) else AssistantBlock(message, onToolsClick)
}

/** The player's bubble: the soft navy wash with the hairline, 13sp — the size the answers match. */
@Composable
private fun UserBubbleText(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = AgentPalette.Ink,
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .background(AgentPalette.Bubble, RoundedCornerShape(14.dp))
                .border(1.dp, AgentPalette.BubbleBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** The assistant's answer: the running tool shows live, the finished ones fold, then the words. */
@Composable
private fun AssistantBlock(message: AgentMessage, onToolsClick: ((AgentMessage) -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.tools.isNotEmpty()) {
            ToolSummary(message.tools, message.id, onToolsClick?.let { cb -> { cb(message) } })
        }
        if (message.text.isNotEmpty()) {
            Markdown(message.text, typography = agentMarkdownTypography())
        }
    }
}

/** The answers' voice: serif headings, 13sp body on 22 (the bubble's size), mono code. */
@Composable
private fun agentMarkdownTypography(): MarkdownTypography = markdownTypography(
    h1 = MaterialTheme.typography.titleLarge.copy(
        fontFamily = AgentHeading, fontSize = 16.sp, lineHeight = 22.sp, color = AgentPalette.Ink,
    ),
    h2 = MaterialTheme.typography.titleMedium.copy(
        fontFamily = AgentHeading, fontSize = 15.sp, lineHeight = 21.sp, color = AgentPalette.Ink,
    ),
    h3 = MaterialTheme.typography.titleSmall.copy(
        fontFamily = AgentHeading, fontSize = 14.sp, lineHeight = 19.sp, color = AgentPalette.Ink,
    ),
    text = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 13.sp, lineHeight = 22.sp, color = AgentPalette.Ink,
    ),
    code = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = AgentPalette.Ink,
    ),
)

/**
 * The fold: a mono row — the count left, the state right. With an [onOpen] the tap hands the turn
 * to the tool overlay (the panel's tool column); without one it expands the records in place.
 */
@Composable
private fun ToolSummary(
    tools: List<AgentToolCard>,
    messageId: Long,
    onOpen: (() -> Unit)? = null,
) {
    var expanded by remember(messageId) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp, horizontal = 11.dp)
                .clickable { if (onOpen != null) onOpen() else expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val tallies = tools.groupingBy { it.name }.eachCount()
                .entries.sortedByDescending { it.value }
                .joinToString(" · ") { "${it.key}×${it.value}" }
            Text(
                stringResource(R.string.agent_tools_summary, tools.size) +
                    if (tallies.isNotBlank()) " · $tallies" else "",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = AgentPalette.Ink2,
                ),
                modifier = Modifier.weight(1f),
            )
            val live = tools.any { it.state == AgentToolState.RUNNING }
            Text(
                stringResource(if (live) R.string.agent_state_working else R.string.agent_state_ready),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.4.sp,
                    color = if (live) AgentPalette.Ink else AgentPalette.Ink2,
                ),
            )
        }
        if (onOpen == null && expanded) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                tools.forEach { card ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(
                            card.name.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp,
                            ),
                            color = AgentPalette.Ink2,
                        )
                        if (card.argsSummary.isNotBlank()) {
                            Text(
                                card.argsSummary,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = AgentPalette.Cmd,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        card.resultSummary?.let { result ->
                            Text(
                                "→ $result",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = AgentPalette.Ink2,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AgentPalette.Hairline),
        )
    }
}

@Composable
internal fun ToolCard(card: AgentToolCard) {
    var expanded by remember(card.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "agent-tool-arrow")
    val (tint, stateWord) = when (card.state) {
        AgentToolState.RUNNING -> AgentPalette.Working to stringResource(R.string.agent_tool_running)
        AgentToolState.DONE -> AgentPalette.Ready to stringResource(R.string.agent_tool_done)
        AgentToolState.FAILED -> AgentPalette.Danger to stringResource(R.string.agent_tool_failed)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AgentPalette.Panel)
            .border(1.dp, AgentPalette.Hairline)
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 11.dp),
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
                    color = AgentPalette.Ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    null,
                    Modifier.size(18.dp).rotate(rotation),
                    tint = AgentPalette.Ink2,
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
                        color = AgentPalette.Ink2,
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
                            color = AgentPalette.Ink2,
                        )
                        Text(
                            card.resultSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = AgentPalette.Ink2,
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
internal fun AgentInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    running: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    val canSend = !running && text.isNotBlank()
    Row(
        Modifier.fillMaxWidth().imePadding().padding(bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            text,
            { onTextChange(it) },
            Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.agent_input_hint)) },
            maxLines = 4,
        )
        Box(
            Modifier
                .padding(bottom = 4.dp)
                .size(46.dp)
                .clip(RoundedCornerShape(50))
                .background(if (running || canSend) AgentPalette.Orange else AgentPalette.Panel)
                .clickable(enabled = running || canSend) {
                    if (running) onStop()
                    else onSend(text)   // the ViewModel clears the draft as the words land
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (running) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                stringResource(if (running) R.string.agent_stop else R.string.agent_send),
                Modifier.size(20.dp),
                tint = if (running || canSend) Color.White else AgentPalette.Idle,
            )
        }
    }
}
