package com.nx.android.agent.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nx.android.R
import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentPresets
import com.nx.android.agent.AgentRole
import com.nx.android.agent.runtime.AgentSessionMeta
import kotlin.math.roundToInt

/**
 * The assistant's side drawer, phone-landscape (ADR-009, the mobile draft) — the app's own glass
 * language: two columns (conversation 460dp, tools 240dp) over a near-opaque ground, the dimmed
 * home hero showing through on the left. The conversation has no header; the tool column carries
 * the head (title + context occupancy), the model chip, quick asks, the composer, and the session
 * switcher beside the send button at the very bottom. The tool-call overlay covers the tool column.
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
    val listState = rememberLazyListState()
    var toolsOverlay by remember { mutableStateOf<AgentMessage?>(null) }

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
                    0.34f to Color(0x75141416),
                    1f to Color(0xF5141416),
                ),
            )
            .clickable { onCollapse() },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .width(700.dp)
                .background(AgentPalette.DrawerBg)
                .statusBarsPadding()
                .imePadding()
                // consume taps: the drawer is not the scrim (a tap in the flow used to close it)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
        ) {
            // ---- the conversation: no header, the whole height -------------------------------------------
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(AgentPalette.Ground),
            ) {
                state.error?.let { reason -> ErrorStrip(reason) { viewModel.dismissError() } }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.messages.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(top = 32.dp)) {
                                Text(
                                    stringResource(R.string.agent_empty),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AgentPalette.Ink,
                                )
                                Text(
                                    stringResource(R.string.agent_empty_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AgentPalette.Ink2,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                    items(state.messages, key = { it.id }) { message ->
                        MessageRow(message, onToolsClick = { toolsOverlay = it })
                    }
                }
                if (state.running) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AgentPalette.Working),
                        )
                        Text(
                            stringResource(R.string.agent_state_working),
                            style = MaterialTheme.typography.labelSmall,
                            color = AgentPalette.Ink2,
                        )
                    }
                }
            }
            // ---- the tool column, covered by the tool-call overlay when open -------------------------------
            Box(Modifier.width(240.dp).fillMaxHeight()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 0.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // the head: the title and the session's context occupancy (opencode style)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.agent_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = AgentPalette.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatTokens(state.usageTokens) + usagePct(state.usageTokens, state.usageWindow),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = AgentPalette.Ink,
                        )
                    }
                    // the model chip: what answers now; the menu lists every preset
                    var modelMenu by remember { mutableStateOf(false) }
                    GlassChip(
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        onClick = { modelMenu = !modelMenu },
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.configured) AgentPalette.Ready else AgentPalette.Idle),
                        )
                        Text(
                            state.config.modelName.ifBlank { stringResource(R.string.agent_model_unset) },
                            style = MaterialTheme.typography.labelMedium,
                            color = AgentPalette.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            null,
                            Modifier.size(13.dp),
                            tint = AgentPalette.Ink2,
                        )
                    }
                    GlassMenu(
                        expanded = modelMenu,
                        onDismiss = { modelMenu = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AgentPresets.ALL.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        preset.label + (if (preset.defaultModel.isNotBlank()) " · ${preset.defaultModel}" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AgentPalette.Ink,
                                    )
                                },
                                onClick = {
                                    modelMenu = false
                                    viewModel.selectModel(preset.id)
                                },
                            )
                        }
                        HorizontalDivider(color = AgentPalette.Hairline)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.agent_menu_settings),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AgentPalette.Ink2,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Settings,
                                    null,
                                    Modifier.size(14.dp),
                                    tint = AgentPalette.Ink2,
                                )
                            },
                            onClick = {
                                modelMenu = false
                                onOpenSettings()
                            },
                        )
                    }
                    // the flexible middle: quick asks before the player talks, the context ring after
                    val hasConversation = state.messages.any { it.role == AgentRole.USER }
                    if (hasConversation) {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(AgentPalette.Panel)
                                .border(1.dp, AgentPalette.Hairline)
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            ContextRing(state.usageTokens, state.usageWindow)
                            Text(
                                stringResource(R.string.agent_usage_total),
                                style = MaterialTheme.typography.labelSmall,
                                color = AgentPalette.Ink2,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    } else {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                stringResource(R.string.agent_label_quick_ops),
                                style = MaterialTheme.typography.labelSmall,
                                color = AgentPalette.Ink2,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                            chips.forEach { res ->
                                val words = stringResource(res)
                                QuickCard(words) { viewModel.send(words) }
                            }
                        }
                    }
                    // the composer: the input alone, then the session switcher beside the send
                    OutlinedTextField(
                        state.draft,
                        viewModel::updateDraft,
                        Modifier.fillMaxWidth().height(60.dp),
                        maxLines = 2,
                        placeholder = { Text(stringResource(R.string.agent_input_hint), style = MaterialTheme.typography.bodyMedium) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = AgentPalette.Ink),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AgentPalette.Hairline,
                            unfocusedBorderColor = AgentPalette.Hairline,
                            focusedContainerColor = AgentPalette.Panel,
                            unfocusedContainerColor = AgentPalette.Panel,
                            focusedTextColor = AgentPalette.Ink,
                            unfocusedTextColor = AgentPalette.Ink,
                            cursorColor = AgentPalette.Orange,
                            focusedPlaceholderColor = AgentPalette.Idle,
                            unfocusedPlaceholderColor = AgentPalette.Idle,
                        ),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        var sessionMenu by remember { mutableStateOf(false) }
                        Box(Modifier.weight(1f)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(AgentPalette.Panel, RoundedCornerShape(12.dp))
                                    .border(1.dp, AgentPalette.Hairline, RoundedCornerShape(12.dp))
                                    .clickable { sessionMenu = true }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Icon(
                                    Icons.Filled.AddComment,
                                    null,
                                    Modifier.size(14.dp),
                                    tint = AgentPalette.Ink2,
                                )
                                Text(
                                    state.sessions.firstOrNull { it.id == state.activeSessionId }?.title
                                        ?: stringResource(R.string.agent_action_new_session),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AgentPalette.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    null,
                                    Modifier.size(14.dp),
                                    tint = AgentPalette.Ink2,
                                )
                            }
                            GlassMenu(
                                expanded = sessionMenu,
                                onDismiss = { sessionMenu = false },
                                alignment = Alignment.BottomEnd,
                                offset = IntOffset(0, with(LocalDensity.current) { -(52.dp.roundToPx()) }),
                                modifier = Modifier.width(292.dp),
                            ) {
                                state.sessions.take(6).forEach { meta ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    meta.title,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = AgentPalette.Ink,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                if (meta.contextTokens > 0) {
                                                    Text(
                                                        formatTokens(meta.contextTokens),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                        color = AgentPalette.Idle,
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            sessionMenu = false
                                            viewModel.switchSession(meta.id)
                                        },
                                    )
                                }
                                HorizontalDivider(color = AgentPalette.Hairline)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.agent_action_new_session),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = AgentPalette.Cream,
                                            ),
                                        )
                                    },
                                    onClick = {
                                        sessionMenu = false
                                        viewModel.newSession()
                                    },
                                )
                            }
                        }
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.running || state.draft.isNotBlank()) AgentPalette.Orange else AgentPalette.Panel,
                                )
                                .clickable(enabled = state.running || state.draft.isNotBlank()) {
                                    if (state.running) viewModel.stop() else viewModel.send(state.draft)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (state.running) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                                stringResource(if (state.running) R.string.agent_stop else R.string.agent_send),
                                Modifier.size(20.dp),
                                tint = if (state.running || state.draft.isNotBlank()) AgentPalette.CreamInk else AgentPalette.Idle,
                            )
                        }
                    }
                }
                // the tool-call overlay: covers the tool column, closes with the X
                toolsOverlay?.let { msg ->
                    ToolOverlay(
                        message = msg,
                        onDismiss = { toolsOverlay = null },
                        modifier = Modifier
                            .matchParentSize()
                            .padding(top = 12.dp, end = 12.dp, bottom = 14.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xEE1A1A1C))
                            .border(1.dp, AgentPalette.Hairline),
                    )
                }
            }
        }
    }
}

/** The tool-call overlay: every call of one conversation turn, in the repair-ticket record style. */
@Composable
private fun ToolOverlay(message: AgentMessage, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.agent_tool_overlay_title),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AgentPalette.Ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    null,
                    Modifier.size(14.dp),
                    tint = AgentPalette.Ink2,
                )
            }
        }
        HorizontalDivider(color = AgentPalette.Hairline)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            message.tools.forEach { card ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        card.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp,
                        ),
                        color = AgentPalette.Idle,
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
            if (message.tools.isEmpty()) {
                Text(
                    stringResource(R.string.agent_tool_overlay_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = AgentPalette.Ink2,
                )
            }
        }
    }
}

/** A glass chip: the base card at pill size with the white cap and the hairline. */
@Composable
private fun GlassChip(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    onClick: () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)),
                RoundedCornerShape(50),
            )
            .background(AgentPalette.Panel, RoundedCornerShape(50))
            .border(1.dp, AgentPalette.Hairline, RoundedCornerShape(50))
            .padding(contentPadding)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        content = content,
    )
}

/** The context ring: the session's occupancy of the model's window, opencode-style but round. */
@Composable
private fun ContextRing(context: Long, window: Long, modifier: Modifier = Modifier) {
    val pct = if (window > 0) (context * 100.0 / window).coerceIn(0.0, 100.0) else 0.0
    val color = when {
        pct >= 85 -> AgentPalette.Danger
        pct >= 50 -> AgentPalette.Working
        else -> AgentPalette.Ready
    }
    Box(modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            drawArc(
                AgentPalette.Hairline.copy(alpha = 0.6f),
                -90f, 360f, false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (pct > 0) {
                drawArc(
                    color,
                    -90f, (pct * 3.6).toFloat(), false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatTokens(context),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = AgentPalette.Ink,
            )
            Text(
                "${pct.roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = AgentPalette.Ink2,
            )
        }
    }
}

/**
 * A glass dropdown: the M3 menu's shape is square-ish and its surface can't take the white cap —
 * this popup carries the full glass recipe at a 20dp radius. [alignment]/[offset] pick where it
 * opens (the session menu rises from the bottom row; the model menu drops from its chip).
 */
@Composable
private fun GlassMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopStart,
    offset: IntOffset = IntOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    Popup(
        alignment = alignment,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                    ),
                    RoundedCornerShape(20.dp),
                )
                .background(AgentPalette.Panel.copy(alpha = 0.94f), RoundedCornerShape(20.dp))
                .border(1.dp, AgentPalette.Hairline, RoundedCornerShape(20.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/** The 11px section caption between the tool column's blocks. */
@Composable
private fun ToolLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
        color = AgentPalette.Ink2,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** The usage card: context occupancy left in ink, the window's cost right in ink-2. */
@Composable
private fun UsageCard(tokens: Long, cost: Double) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(AgentPalette.Panel, RoundedCornerShape(13.dp))
            .border(1.dp, AgentPalette.Hairline, RoundedCornerShape(13.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            formatTokens(tokens),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = AgentPalette.Ink,
        )
        if (cost > 0.0) {
            Text(
                "¥%.2f".format(cost),
                style = MaterialTheme.typography.bodyMedium,
                color = AgentPalette.Ink2,
            )
        }
    }
}

/** The model pill: 36dp, glass, the readiness dot on the left, the settings on a tap. */
@Composable
private fun ModelPill(model: String, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(AgentPalette.Panel, RoundedCornerShape(50))
            .border(1.dp, AgentPalette.Hairline, RoundedCornerShape(50))
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(AgentPalette.Ready))
        Text(
            model,
            style = MaterialTheme.typography.bodyMedium,
            color = AgentPalette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.KeyboardArrowDown,
            null,
            Modifier.size(14.dp),
            tint = AgentPalette.Ink2,
        )
    }
}

/** A quick ask: a glass card, the words coming from the page context. */
@Composable
private fun QuickCard(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = AgentPalette.Quick,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentPalette.Panel, RoundedCornerShape(12.dp))
            .border(1.dp, AgentPalette.Hairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/** The composer: a one-line glass input, the ghost new-chat and the orange send under it. */
@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    running: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onNewSession: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            text,
            onTextChange,
            Modifier.fillMaxWidth().height(44.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.agent_input_hint), style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = AgentPalette.Ink),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AgentPalette.Hairline,
                unfocusedBorderColor = AgentPalette.Hairline,
                focusedContainerColor = AgentPalette.Panel,
                unfocusedContainerColor = AgentPalette.Panel,
                focusedTextColor = AgentPalette.Ink,
                unfocusedTextColor = AgentPalette.Ink,
                cursorColor = AgentPalette.Orange,
                focusedPlaceholderColor = AgentPalette.Idle,
                unfocusedPlaceholderColor = AgentPalette.Idle,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(AgentPalette.Panel, RoundedCornerShape(13.dp))
                    .border(1.dp, AgentPalette.Hairline, RoundedCornerShape(13.dp))
                    .clickable(onClick = onNewSession)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.AddComment,
                    null,
                    Modifier.size(14.dp),
                    tint = AgentPalette.Ink2,
                )
                Text(
                    stringResource(R.string.agent_action_new_session),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AgentPalette.Ink2,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AgentPalette.Orange)
                    .clickable { if (running) onStop() else onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (running) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                    stringResource(if (running) R.string.agent_stop else R.string.agent_send),
                    Modifier.size(20.dp),
                    tint = AgentPalette.CreamInk,
                )
            }
        }
    }
}

/** opencode style: 642.2k above a thousand gets one decimal and a k. */
internal fun formatTokens(tokens: Long): String =
    if (tokens < 1_000) "$tokens"
    else "%.1fk".format(tokens / 1_000.0)

/** The occupancy percent — empty when the window is unknown. */
internal fun usagePct(context: Long, window: Long): String =
    if (window > 0 && context > 0) "(${(context * 100 / window).coerceAtMost(999)}%)" else ""
