package com.nx.android.feature.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nx.android.R
import com.nx.android.core.ui.components.Hint
import com.nx.android.core.ui.theme.NxPalette

/**
 * The assistant's side panel: the landscape answer to "where does the assistant live" (ADR-007). It docks
 * on the right edge over whatever page is beneath, hosts the pending decisions and the last exchange, and
 * takes taps only — typing happens here too on the tablets, but the long conversations go to the portrait
 * page through the expand arrow.
 */
@Composable
fun AgentPanel(
    viewModel: AgentViewModel,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var setupOpen by remember { mutableStateOf(false) }
    val showSetup = !state.configured || setupOpen
    val listState = rememberLazyListState()

    val last = state.messages.lastOrNull()
    LaunchedEffect(state.messages.size, last?.text?.length, last?.tools?.size) {
        if (state.messages.isNotEmpty() && !listState.isScrollInProgress) {
            listState.scrollToItem(state.messages.size - 1)
        }
    }

    Column(
        modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(
                NxPalette.Surface,
                RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            )
            .imePadding()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.agent_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
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
            Icon(
                Icons.Filled.Close,
                stringResource(R.string.agent_panel_collapse),
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onCollapse)
                    .padding(4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.error?.let { reason -> ErrorStrip(reason) { viewModel.dismissError() } }
        if (showSetup) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AgentSetupSheet(
                    config = state.config,
                    cancelable = state.configured,
                    onSave = { providerId, baseUrl, apiKey, modelName ->
                        viewModel.updateConfig(providerId, baseUrl, apiKey, modelName)
                        setupOpen = false
                    },
                    onCancel = { setupOpen = false },
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
                }            }
            AgentInputBar(
                running = state.running,
                onSend = { viewModel.send(it) },
                onStop = { viewModel.stop() },
            )
        }
    }
}
