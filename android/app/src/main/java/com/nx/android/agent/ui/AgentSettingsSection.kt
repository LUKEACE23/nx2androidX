package com.nx.android.agent.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nx.android.R
import com.nx.android.agent.runtime.AgentModelProfile
import com.nx.android.core.ui.components.InfoRow
import com.nx.android.core.ui.theme.NxPalette
import com.nx.android.feature.filepicker.PickerMode
import com.nx.android.feature.filepicker.PickerRequest

/**
 * The assistant's settings: the engine and the models.
 *
 * Two things, no cards and no explanations: the engine (one row — the dialog it opens holds the two
 * ways to get one) and the models (one row each). The rest of the settings page is made of rows like
 * these, so this section is made of rows like these too.
 */
@Composable
fun AgentSettingsSection(viewModel: AgentSettingsViewModel, onRequest: (PickerRequest) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var engineDialog by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AgentModelProfile?>(null) }
    var deleting by remember { mutableStateOf<AgentModelProfile?>(null) }
    val pickTitle = stringResource(R.string.agent_engine_pick)

    // the engine: one row. The dialog it opens does the work (download, import)
    InfoRow(
        title = stringResource(R.string.agent_engine_label),
        value = when {
            !state.engineInstalled -> stringResource(R.string.agent_engine_missing)
            state.engineVersion.isBlank() -> stringResource(R.string.agent_engine_ready)
            else -> state.engineVersion
        },
        onClick = { engineDialog = true },
    )

    // the models: one row each. The row picks the model; the pencil edits it.
    state.profiles.forEach { profile ->
        val active = profile.id == state.activeProfileId
        InfoRow(
            title = profile.label.ifBlank { profile.modelName },
            value = profile.modelName.takeIf { it != profile.label }.orEmpty(),
            leading = {
                Icon(
                    Icons.Filled.Check,
                    null,
                    Modifier.size(16.dp),
                    tint = if (active) NxPalette.Brand else Color.Transparent,
                )
            },
            trailing = {
                IconButton(onClick = { editing = profile }) {
                    Icon(
                        Icons.Filled.Edit,
                        stringResource(R.string.agent_models_edit),
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            onClick = { viewModel.setActiveModel(profile.id) },
        )
    }
    InfoRow(
        title = stringResource(R.string.agent_models_add),
        value = "+",
        onClick = { adding = true },
    )

    if (engineDialog) {
        AgentEngineDialog(
            state = state,
            onDownload = { viewModel.downloadLatest() },
            onImport = {
                // 弹框是它自己的窗口：它会盖在文件选择器上面。所以先把弹框关掉，选完再开回来。
                engineDialog = false
                onRequest(
                    PickerRequest(
                        mode = PickerMode.FILE,
                        extensions = setOf("zip"),
                        initial = null,
                        title = pickTitle,
                        onPicked = { file ->
                            viewModel.importPackage(file)
                            engineDialog = true
                        },
                    ),
                )
            },
            onDismiss = {
                // 取消随时可用：正在做的动作被叫停，弹框关掉
                viewModel.cancelActive()
                engineDialog = false
            },
        )
    }

    if (adding) {
        AgentModelDialog(
            existing = null,
            error = state.modelError,
            engineReady = state.engineInstalled,
            listModels = { providerId, apiKey -> viewModel.listModels(providerId, apiKey) },
            onDismiss = { adding = false },
            onSave = {
                // 存不上就留在框里：玩家看得见哪里出了错，不用重新填一遍
                if (viewModel.saveModel(it)) adding = false
            },
        )
    }
    editing?.let { profile ->
        AgentModelDialog(
            existing = profile,
            error = state.modelError,
            engineReady = state.engineInstalled,
            listModels = { providerId, apiKey -> viewModel.listModels(providerId, apiKey) },
            onDismiss = { editing = null },
            onSave = {
                if (viewModel.saveModel(it)) editing = null
            },
            onDelete = {
                deleting = profile
                editing = null
            },
        )
    }

    // the delete asks once: a key that goes with the model cannot be read back
    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.agent_models_delete), fontWeight = FontWeight.Bold) },
            text = {
                Text(stringResource(R.string.agent_models_delete_confirm, profile.label.ifBlank { profile.modelName }))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeModel(profile.id)
                    deleting = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.agent_model_cancel)) }
            },
        )
    }
}

/**
 * The picker's words: the preset's id is the stable thing, the label is the UI's to say — in the app's own
 * language.
 */
@StringRes
internal fun providerLabelRes(providerId: String): Int = when (providerId) {
    "glm" -> R.string.agent_provider_glm
    "deepseek" -> R.string.agent_provider_deepseek
    "doubao" -> R.string.agent_provider_doubao
    "kimi" -> R.string.agent_provider_kimi
    "minimax" -> R.string.agent_provider_minimax
    else -> R.string.agent_provider_custom
}

/** The capsule's model word: the endpoints' ids are long, the ends carry the meaning. */
internal fun shortModel(name: String): String =
    if (name.length <= 20) name else name.take(10) + "…" + name.takeLast(6)

/** The picker's per-provider hint under the fields: where the key comes from, in the app's own language. */
@StringRes
internal fun providerHintRes(providerId: String): Int = when (providerId) {
    "glm" -> R.string.agent_provider_hint_glm
    "deepseek" -> R.string.agent_provider_hint_deepseek
    "doubao" -> R.string.agent_provider_hint_doubao
    "kimi" -> R.string.agent_provider_hint_kimi
    "minimax" -> R.string.agent_provider_hint_minimax
    else -> R.string.agent_provider_hint_custom
}
