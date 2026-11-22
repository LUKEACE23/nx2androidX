package com.nx.android.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nx.android.R
import com.nx.android.agent.AgentPresets
import com.nx.android.agent.AgentRole
import com.nx.android.agent.runtime.AgentSessionMeta

/**
 * The assistant's side drawer, phone-landscape (ADR-009, the mobile draft): a near-opaque panel over
 * the dimmed home hero — the conversation on the left (no header, the whole height), the tool column
 * on the right (close + title + session tokens, the model chip, quick asks, the composer, and the
 * session switcher beside the send button at the very bottom).
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
                .imePadding(),
        ) {
            // ---- the conversation: no header, the whole height -------------------------------------------
            Column(
                Modifier
                    .width(460.dp)
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
                        MessageRow(message)
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
            // ---- the tool column --------------------------------------------------------------------------
            Column(
                Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(AgentPalette.Ground)
                    .padding(start = 0.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // the head: close the drawer, the title, and the session's tokens on the right
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onCollapse),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            stringResource(R.string.agent_panel_collapse),
                            Modifier.size(16.dp),
                            tint = AgentPalette.Ink2,
                        )
                    }
                    Text(
                        stringResource(R.string.agent_title),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = AgentPalette.Ink,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.agent_label_session),
                        style = MaterialTheme.typography.labelSmall,
                        color = AgentPalette.Ink2,
                    )
                    Text(
                        formatTokens(state.usageTokens),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AgentPalette.Ink,
                    )
                }
                // the model chip: what answers now; the menu lists every preset
                var modelMenu by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(RoundedCornerShape(50))
                            .background(AgentPalette.Panel)
                            .border(1.dp, AgentPalette.Hairline)
                            .clickable { modelMenu = !modelMenu }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
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
                    DropdownMenu(
                        expanded = modelMenu,
                        onDismissRequest = { modelMenu = false },
                        modifier = Modifier.background(AgentPalette.Panel),
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
                            onClick = {
                                modelMenu = false
                                onOpenSettings()
                            },
                        )
                    }
                }
                // quick asks: the flexible middle, scrollable
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        stringResource(R.string.agent_label_quick_ops),
                        style = MaterialTheme.typography.labelSmall,
                        color = AgentPalette.Ink2,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    var sessionMenu by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AgentPalette.Panel)
                                .border(1.dp, AgentPalette.Hairline)
                                .clickable { sessionMenu = true }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
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
                                Icons.Filled.ArrowDropUp,
                                null,
                                Modifier.size(14.dp),
                                tint = AgentPalette.Ink2,
                            )
                        }
                        DropdownMenu(
                            expanded = sessionMenu,
                            onDismissRequest = { sessionMenu = false },
                            modifier = Modifier
                                .width(292.dp)
                                .background(AgentPalette.Panel),
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
                                            if (meta.tokens > 0) {
                                                Text(
                                                    formatTokens(meta.tokens),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                    ),
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
        }
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

/** The session button with its dropdown: past conversations above, cream new-chat at the bottom. */
@Composable
private fun SessionSwitcher(
    sessions: List<AgentSessionMeta>,
    activeTitle: String,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AgentPalette.Panel)
                .border(1.dp, AgentPalette.Hairline)
                .clickable { open = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                activeTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AgentPalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                null,
                Modifier.size(16.dp),
                tint = AgentPalette.Ink2,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(AgentPalette.Panel),
        ) {
            sessions.take(6).forEach { meta ->
                DropdownMenuItem(
                    text = {
                        Text(
                            meta.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = AgentPalette.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        open = false
                        onSwitch(meta.id)
                    },
                )
            }
            HorizontalDivider(color = AgentPalette.Hairline)
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.agent_action_new_session),
                        style = MaterialTheme.typography.bodySmall,
                        color = AgentPalette.Ink,
                    )
                },
                onClick = {
                    open = false
                    onNew()
                },
            )
        }
    }
}

/** The usage card: tokens left in ink, cost right in ink-2. */
@Composable
private fun UsageCard(tokens: Long, cost: Double) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(AgentPalette.Panel)
            .border(1.dp, AgentPalette.Hairline)
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
            .clip(RoundedCornerShape(50))
            .background(AgentPalette.Panel)
            .border(1.dp, AgentPalette.Hairline)
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
            .clip(RoundedCornerShape(12.dp))
            .background(AgentPalette.Panel)
            .border(1.dp, AgentPalette.Hairline)
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
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, AgentPalette.Hairline)
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

/** 45.6k style: under a thousand stays exact, above it one decimal and a k. */
private fun formatTokens(tokens: Long): String =
    if (tokens < 1_000) "$tokens tok"
    else "%.1fk tok".format(tokens / 1_000.0)
