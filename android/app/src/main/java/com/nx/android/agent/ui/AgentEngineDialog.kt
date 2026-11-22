package com.nx.android.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.core.ui.components.InfoRow
import com.nx.android.core.ui.theme.NxPalette

/**
 * The engine's own dialog: what is installed, and the two ways to get one — download it from the
 * repository, or import the file somebody gave you.
 *
 * Both actions show the app's own loading ring while they work, and they wait for each other.
 */
@Composable
fun AgentEngineDialog(
    state: AgentSettingsViewModel.UiState,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = state.busy
    AlertDialog(
        // 取消随时可以：正在做的事（下载）被叫停，弹框关掉
        onDismissRequest = { onDismiss() },
        title = {
            Text(stringResource(R.string.agent_engine_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow(
                    title = stringResource(R.string.agent_engine_current),
                    value = when {
                        !state.engineInstalled -> stringResource(R.string.agent_engine_missing)
                        state.engineVersion.isBlank() -> stringResource(R.string.agent_engine_ready)
                        else -> state.engineVersion
                    },
                )
                EngineAction(
                    label = stringResource(R.string.agent_engine_download),
                    busy = state.busyAction == AgentSettingsViewModel.ACTION_DOWNLOAD,
                    percent = state.progress,
                    enabled = !busy,
                    onClick = onDownload,
                )
                EngineAction(
                    label = stringResource(R.string.agent_engine_import),
                    busy = state.busyAction == AgentSettingsViewModel.ACTION_IMPORT,
                    percent = 0f,
                    enabled = !busy,
                    onClick = onImport,
                )
                if (state.noteRes != 0) {
                    Text(
                        stringResource(state.noteRes, state.noteArg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(stringResource(R.string.agent_model_cancel))
            }
        },
    )
}

/**
 * One action of the dialog: the app's own capsule, with the loading ring inside while it works.
 *
 * @param percent how far along it is, 0 to 1 (the download has a real one, the import does not)
 */
@Composable
private fun EngineAction(
    label: String,
    busy: Boolean,
    percent: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = NxPalette.Surface,
            disabledContentColor = NxPalette.Unbuilt,
        ),
        modifier = Modifier.fillMaxWidth().height(46.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
            if (percent > 0f) {
                Text("${(percent * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        } else {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
