package com.nx.android.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.agent.AgentPresets
import com.nx.android.agent.runtime.AgentModelProfile
import com.nx.android.core.ui.theme.NxPalette

/**
 * The model's own editor: first the provider, then the parameters.
 *
 * A new model starts at the provider (a named one brings its endpoint and its first model along);
 * an edited one starts at the parameters, with what it already had.
 */
@Composable
fun AgentModelDialog(
    existing: AgentModelProfile?,
    onDismiss: () -> Unit,
    onSave: (AgentModelProfile) -> Unit,
    onDelete: (() -> Unit)? = null,
    error: String = "",
) {
    var providerId by remember { mutableStateOf(existing?.providerId.orEmpty()) }
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var modelName by remember { mutableStateOf(existing?.modelName.orEmpty()) }
    var apiKey by remember { mutableStateOf(existing?.apiKey.orEmpty()) }
    var keyVisible by remember { mutableStateOf(false) }
    // the provider comes first for a new model; an edited one goes straight to its parameters
    var choosingProvider by remember { mutableStateOf(existing == null) }

    val complete = baseUrl.isNotBlank() && modelName.isNotBlank() && apiKey.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (choosingProvider) R.string.agent_model_pick_provider else R.string.agent_model_params,
                ),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (choosingProvider) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (p in AgentPresets.ALL) {
                        ProviderChoice(p.id, p.id == providerId) {
                            providerId = p.id
                            if (p.id != "custom") {
                                baseUrl = p.baseUrl
                                modelName = p.defaultModel
                            }
                            choosingProvider = false
                        }
                    }
                }
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        label,
                        { label = it },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.agent_model_label)) },
                    )
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
                    Text(
                        stringResource(providerHintRes(providerId)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 存不上的时候：错在哪里写在这里，框子不关
                    if (error.isNotBlank()) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    // the delete lives here: a model that is not wanted any more goes away from the
                    // place it was written
                    if (existing != null && onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text(stringResource(R.string.agent_models_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (choosingProvider) {
                TextButton(onClick = { choosingProvider = false }) {
                    Text(stringResource(R.string.agent_model_cancel))
                }
            } else {
                TextButton(
                    enabled = complete,
                    onClick = {
                        onSave(
                            AgentModelProfile(
                                id = existing?.id ?: "p-${System.currentTimeMillis()}",
                                providerId = providerId.ifBlank { "custom" },
                                label = label.trim(),
                                baseUrl = baseUrl.trim(),
                                modelName = modelName.trim(),
                                apiKey = apiKey.trim(),
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.agent_setup_save))
                }
            }
        },
        dismissButton = {
            if (!choosingProvider && existing != null) {
                TextButton(onClick = { choosingProvider = true }) {
                    Text(stringResource(R.string.agent_model_pick_provider))
                }
            }
        },
    )
}

/** One provider in the picker: the picked one says so in the brand's color. */
@Composable
private fun ProviderChoice(providerId: String, selected: Boolean, onPick: () -> Unit) {
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
            .clickable(onClick = onPick)
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
