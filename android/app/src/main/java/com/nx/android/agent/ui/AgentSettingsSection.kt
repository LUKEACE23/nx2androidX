package com.nx.android.agent.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nx.android.R
import com.nx.android.agent.AgentPresets
import com.nx.android.core.ui.components.CapsuleButton
import com.nx.android.core.ui.components.Hint
import com.nx.android.core.ui.components.Section
import com.nx.android.core.ui.theme.NxPalette

/**
 * The assistant's settings section: the BYOK form (ADR-003) — the player's own key, one OpenAI-compatible
 * endpoint of their choice. Lives here because it is global configuration; the assistant's surfaces (the
 * panel, the full page) only send the player here when no key is set yet.
 */
@Composable
fun AgentSettingsSection(viewModel: AgentViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // the form follows the saved choice: after a save it re-reads what the data layer kept
    var providerId by remember(state.config) { mutableStateOf(state.config.providerId.ifEmpty { AgentPresets.ALL.first().id }) }
    var baseUrl by remember(state.config) { mutableStateOf(state.config.baseUrl) }
    var modelName by remember(state.config) { mutableStateOf(state.config.modelName) }
    var apiKey by remember(state.config) { mutableStateOf(state.config.apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    val preset = AgentPresets.byId(providerId)
    val complete = baseUrl.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()

    Section(stringResource(R.string.agent_setup_title)) {
        Hint(stringResource(R.string.agent_setup_intro), size = 13.sp)

        Text(
            stringResource(R.string.agent_setup_provider),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in AgentPresets.ALL) {
                ProviderRow(p.id, p.id == providerId, onSelect = {
                    providerId = p.id
                    if (p.id != "custom") {
                        // a named preset brings its own endpoint and first model along
                        baseUrl = p.baseUrl
                        modelName = p.defaultModel
                    }
                })
            }
        }

        OutlinedTextField(
            baseUrl,
            { baseUrl = it },
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.agent_setup_base_url)) },
        )
        OutlinedTextField(
            modelName,
            { modelName = it },
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.agent_setup_model)) },
        )
        OutlinedTextField(
            apiKey,
            { apiKey = it },
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.agent_setup_api_key)) },
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        stringResource(R.string.agent_setup_key_toggle),
                        Modifier.size(18.dp),
                    )
                }
            },
        )
        // the provider's own words for where the key comes from
        Hint(stringResource(providerHintRes(preset.id)), size = 11.sp)

        CapsuleButton(
            stringResource(R.string.agent_setup_save),
            { viewModel.updateConfig(providerId, baseUrl.trim(), apiKey.trim(), modelName.trim()) },
            Modifier.fillMaxWidth(),
            enabled = complete,
        )
    }
}

/** One provider option: a row that takes the choice, the picked one says so in the brand's color. */
@Composable
private fun ProviderRow(providerId: String, selected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) NxPalette.Brand.copy(alpha = 0.14f) else NxPalette.Surface, shape)
            .border(
                1.dp,
                if (selected) NxPalette.Brand else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(providerLabelRes(providerId)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) NxPalette.Brand else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = NxPalette.Brand)
        }
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
