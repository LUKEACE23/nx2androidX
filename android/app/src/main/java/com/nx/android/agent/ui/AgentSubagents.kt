package com.nx.android.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.agent.AgentMessage
import com.nx.android.agent.AgentNotification
import com.nx.android.agent.AgentSubagent
import com.nx.android.agent.AgentToolCard
import com.nx.android.agent.AgentToolState
import com.nx.android.core.ui.components.CapsuleButton
import com.nx.android.data.Gamespec
import kotlinx.coroutines.delay

/**
 * The sub-agent's run, as a card in the conversation.
 *
 * The generic tool card folds a call into one line; a sub-agent's run deserves its own face: what
 * kind of agent it was, what it was asked to do, and whether it is still working.
 *
 * The numbers come from the engine's notice when there is one, and from the run's own record
 * otherwise (the model sometimes takes the result itself, and then no notice is sent).
 */
@Composable
internal fun AgentRunCard(
    card: AgentToolCard,
    notification: AgentNotification? = null,
    record: AgentSubagent? = null,
) {
    val call = card.subagent ?: return
    val (tint, stateWord) = when (card.state) {
        AgentToolState.RUNNING -> AgentPalette.Working to stringResource(R.string.agent_tool_running)
        AgentToolState.DONE -> AgentPalette.Ready to stringResource(R.string.agent_tool_done)
        AgentToolState.FAILED -> AgentPalette.Danger to stringResource(R.string.agent_tool_failed)
    }
    val duration = notification?.durationMs?.takeIf { it > 0 } ?: record?.durationMs
    val tokens = notification?.totalTokens?.takeIf { it > 0 } ?: record?.tokens?.takeIf { it > 0 }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AgentPalette.Panel)
            .border(1.dp, AgentPalette.Hairline)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubagentBadge(call.type)
            Text(
                stateWord,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = tint,
            )
            if (duration != null && duration > 0) {
                Text(
                    "· ${formatDuration(duration)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = AgentPalette.Ink2,
                )
            }
            if (tokens != null) {
                Text(
                    "· ${formatTokens(tokens)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = AgentPalette.Ink2,
                )
            }
        }
        if (call.description.isNotBlank()) {
            Text(
                call.description,
                style = MaterialTheme.typography.bodyMedium,
                color = AgentPalette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 还在跑：它当前这一步（面板上要看得见它在干啥，不只是「在跑」）
        if (card.state == AgentToolState.RUNNING) {
            record?.step?.takeIf { it.isNotBlank() }?.let { step ->
                Text(
                    step,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = AgentPalette.Quick,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 结果的预览：跑完的那次，它自己的答案；还在跑的就是工具回的那段
        val preview = record?.result?.takeIf { it.isNotBlank() } ?: card.resultSummary
        preview?.takeIf { it.isNotBlank() }?.let { result ->
            Text(
                previewOf(result),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = AgentPalette.Ink2,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The result as a preview: a fenced block shows its own words, not its fence. */
internal fun previewOf(result: String): String {
    val text = result.trim()
    if (!text.startsWith("```")) return text
    val end = text.lastIndexOf("```")
    if (end < 3) return text
    return text.substring(3, end).trim()
}

/**
 * The completion notice, as a card: what the sub-agent did, not the markup it arrived in.
 *
 * The paths the notice carries are left out on purpose — the player reads the conversation, not
 * the engine's scratch folder.
 */
@Composable
internal fun AgentNotificationCard(notification: AgentNotification) {
    val failed = notification.status.startsWith("Error") || notification.status == "Stopped"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AgentPalette.Panel)
            .border(1.dp, AgentPalette.Hairline)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (failed) AgentPalette.Danger else AgentPalette.Ready),
            )
            Text(
                notification.status.ifBlank { stringResource(R.string.agent_tool_done) },
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (failed) AgentPalette.Danger else AgentPalette.Ink,
            )
            if (notification.durationMs > 0) {
                Text(
                    "· ${formatDuration(notification.durationMs)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = AgentPalette.Ink2,
                )
            }
            if (notification.totalTokens > 0) {
                Text(
                    "· ${formatTokens(notification.totalTokens)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = AgentPalette.Ink2,
                )
            }
            if (notification.toolUses > 0) {
                Text(
                    "· ${notification.toolUses} " + stringResource(R.string.agent_subagents_tool_uses),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = AgentPalette.Ink2,
                )
            }
        }
        if (notification.summary.isNotBlank()) {
            Text(
                notification.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = AgentPalette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        notification.result?.takeIf { it.isNotBlank() }?.let { result ->
            Text(
                result,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = AgentPalette.Ink2,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The gate: the case file's plan is waiting for the player.
 *
 * The sub-agent diagnosed and wrote the plan; nothing is changed until the player taps. The card
 * says what the plan is (the problem, the cause, what the fix would be) so the tap is informed.
 */
@Composable
internal fun PlanConfirmCard(
    plan: Gamespec.Plan,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AgentPalette.Panel)
            .border(1.dp, AgentPalette.Hairline)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.agent_plan_waiting),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = AgentPalette.Quick,
        )
        if (plan.headline.isNotBlank()) {
            Text(
                plan.headline,
                style = MaterialTheme.typography.bodyMedium,
                color = AgentPalette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (plan.cause.isNotBlank()) {
            PlanLine(stringResource(R.string.agent_plan_cause), plan.cause)
        }
        if (plan.fix.isNotBlank()) {
            PlanLine(stringResource(R.string.agent_plan_fix), plan.fix)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CapsuleButton(
                stringResource(R.string.agent_plan_confirm),
                onConfirm,
                Modifier,
            )
            Text(
                stringResource(R.string.agent_plan_hint),
                style = MaterialTheme.typography.labelSmall,
                color = AgentPalette.Ink2,
            )
        }
    }
}

/** One line of the plan: the label in mono, the words under it. */
@Composable
private fun PlanLine(label: String, text: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = AgentPalette.Ink2,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = AgentPalette.Ink,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The kind of agent, in a little mono badge. */
@Composable
internal fun SubagentBadge(type: String) {
    if (type.isBlank()) return
    Text(
        type,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.4.sp,
        ),
        color = AgentPalette.Quick,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AgentPalette.Bubble)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * The sub-agents of this chat: the list, and the one that is open.
 *
 * The list is what the player sees first; tapping a run opens its own conversation. There is
 * nothing to press in there — the view is read-only.
 */
@Composable
internal fun AgentsOverlay(
    subagents: List<AgentSubagent>,
    running: Int,
    loadConversation: suspend (AgentSubagent) -> List<AgentMessage>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf<AgentSubagent?>(null) }
    val current = open
    // consume taps: the overlay covers the whole panel, so a tap on its blank face must not
    // reach the chips underneath (the model chip answered the player through it)
    Column(
        modifier
            .background(AgentPalette.DrawerBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (current != null) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.agent_subagents_back),
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable { open = null }
                        .padding(3.dp),
                    tint = AgentPalette.Ink2,
                )
            }
            Text(
                if (current != null) {
                    stringResource(R.string.agent_subagents_viewer_title)
                } else {
                    stringResource(R.string.agent_subagents_title)
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AgentPalette.Ink,
                modifier = Modifier.weight(1f),
            )
            if (current == null && subagents.isNotEmpty()) {
                Text(
                    stringResource(R.string.agent_subagents_count, subagents.size),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = AgentPalette.Ink2,
                )
            }
            if (current == null && running > 0) {
                Text(
                    stringResource(R.string.agent_subagents_running, running),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = AgentPalette.Working,
                )
            }
            Icon(
                Icons.Filled.Close,
                stringResource(R.string.agent_close),
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(4.dp),
                tint = AgentPalette.Ink2,
            )
        }
        HorizontalDivider(color = AgentPalette.Hairline)
        if (current == null) {
            SubagentList(subagents, onOpen = { open = it })
        } else {
            SubagentConversation(current, loadConversation)
        }
    }
}

/** The runs of this chat, oldest first. */
@Composable
private fun SubagentList(subagents: List<AgentSubagent>, onOpen: (AgentSubagent) -> Unit) {
    if (subagents.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.agent_subagents_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = AgentPalette.Ink2,
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(subagents, key = { it.id }) { subagent ->
            SubagentRow(subagent) { onOpen(subagent) }
        }
    }
}

/** One run: its kind, its task, how it went. */
@Composable
private fun SubagentRow(subagent: AgentSubagent, onOpen: () -> Unit) {
    val (tint, word) = when {
        subagent.running -> AgentPalette.Working to stringResource(R.string.agent_subagents_state_running)
        subagent.failed -> AgentPalette.Danger to stringResource(R.string.agent_tool_failed)
        else -> AgentPalette.Ready to stringResource(R.string.agent_tool_done)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AgentPalette.Panel)
            .border(1.dp, AgentPalette.Hairline)
            .clickable(enabled = subagent.sessionFile != null || subagent.running, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubagentBadge(subagent.type)
            Text(
                word,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = tint,
            )
            subagent.durationMs?.let { ms ->
                Text(
                    "· ${formatDuration(ms)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = AgentPalette.Ink2,
                )
            }
        }
        if (subagent.description.isNotBlank()) {
            Text(
                subagent.description,
                style = MaterialTheme.typography.bodyMedium,
                color = AgentPalette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        subagent.error?.takeIf { it.isNotBlank() }?.let { error ->
            Text(
                error,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = AgentPalette.Danger,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The sub-agent's own conversation, read-only.
 *
 * There is no input here on purpose: the player cannot steer a sub-agent from here (that is for
 * a later version), they can only watch what it did.
 */
@Composable
private fun SubagentConversation(
    subagent: AgentSubagent,
    loadConversation: suspend (AgentSubagent) -> List<AgentMessage>,
) {
    var messages by remember(subagent.id) { mutableStateOf<List<AgentMessage>?>(null) }
    LaunchedEffect(subagent.id) { messages = loadConversation(subagent) }
    val loaded = messages
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubagentBadge(subagent.type)
            Text(
                subagent.description.ifBlank { subagent.type },
                style = MaterialTheme.typography.labelMedium,
                color = AgentPalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        when {
            loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.agent_loading_history),
                    style = MaterialTheme.typography.bodySmall,
                    color = AgentPalette.Ink2,
                )
            }

            loaded.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.agent_subagents_gone),
                    style = MaterialTheme.typography.bodySmall,
                    color = AgentPalette.Ink2,
                )
            }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(loaded, key = { it.id }) { message ->
                    MessageRow(message)
                }
            }
        }
    }
}

/** How long it took: seconds, or minutes and seconds. */
internal fun formatDuration(ms: Long): String {
    if (ms < 1000) return "${ms}ms"
    val seconds = ms / 1000.0
    if (seconds < 60) return "%.1fs".format(seconds)
    val minutes = (ms / 60_000).toInt()
    val rest = (ms % 60_000) / 1000
    return "${minutes}m ${rest}s"
}
