package com.nx.android.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nx.android.R
import com.nx.android.core.ui.theme.NxPalette

/**
 * The assistant's entry: the floating button that opens the side panel.
 *
 * It belongs to the page, not to a layer above it: the page knows what sits in its own corners
 * (the key hints at the library's bottom right, the port progress along the bottom), so the entry
 * is pinned to the page's bottom right and the page yields the space to it. Nothing drags it —
 * the hand always knows where it is, and it never floats over another page's content.
 *
 * It reads as floating, not as a button lying on the page: a disc, lifted off the ground by a
 * shadow. The ground here is near black, so a plain black shadow would be invisible — the shadow
 * carries the brand orange instead and reads as the disc's own light (colored shadows are API 28+;
 * below that it falls back to a plain one, and the disc alone still reads).
 */
@Composable
fun AgentEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(52.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                clip = true,
                ambientColor = NxPalette.Brand.copy(alpha = 0.35f),
                spotColor = NxPalette.Brand.copy(alpha = 0.45f),
            )
            .clip(CircleShape)
            .background(NxPalette.Brand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.SmartToy,
            // 圆盘上放不下「助手」两个字，它躲 TalkBack 里
            contentDescription = stringResource(R.string.agent_entry),
            modifier = Modifier.size(24.dp),
            tint = Color(0xFF141416),
        )
    }
}
