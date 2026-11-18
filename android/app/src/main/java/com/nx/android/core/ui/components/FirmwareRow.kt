package com.nx.android.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nx.android.R
import com.nx.android.core.ui.theme.NxPalette

/**
 * 固件那一行：整机级的一份（可选）。
 *
 * 状态点 + 现在是什么（导入的 zip 名，没导入时不写文字——状态点的颜色已经说了）+ 导入 / 重新导入。
 * 导入是设备级的事：环境清单、设置页的环境一节、引导与构建页都指向这一行，
 * 导入的东西在 App 的私有目录里，移植时统一带给移植器。
 *
 * @param onClear 清除按钮的动作；不传（null）就不显示那个按钮
 */
@Composable
fun FirmwareRow(
    imported: String,
    onImport: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    InfoRow(
        title = stringResource(R.string.fw_title),
        // 没导入时不写「没导入」：状态点已经用颜色说了，重复一遍只是噪音
        value = imported.takeIf { it.isNotBlank() },
        leading = { StatusDot(if (imported.isNotBlank()) NxPalette.Ready else NxPalette.Unbuilt, 8.dp) },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                QuietButton(
                    if (imported.isNotBlank()) stringResource(R.string.fw_reimport) else stringResource(R.string.fw_import),
                    onClick = onImport,
                )
                if (imported.isNotBlank() && onClear != null) {
                    QuietButton(stringResource(R.string.builds_clear), onClick = onClear)
                }
            }
        },
    )
}

/** 固件那一行下面的一句：它是可选的，什么时候才需要它 */
@Composable
fun FirmwareHint() {
    Text(
        stringResource(R.string.fw_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
    )
}
