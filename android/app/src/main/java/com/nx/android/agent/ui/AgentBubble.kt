package com.nx.android.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nx.android.core.ui.theme.NxPalette
import kotlin.math.roundToInt

/**
 * The assistant's floating ball: the entry that lives on every page (ADR-007). Draggable anywhere, it
 * snaps to the nearer side edge when let go, and remembers where the player left it for this session.
 *
 * The drag lives on the ball itself — never on a full-screen layer: a layer that listens everywhere would
 * eat the taps meant for the page underneath (that bug shipped once and the library went dead).
 */
@Composable
fun AgentBubble(onClick: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val ball = with(density) { 44.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()

        // the ball's top-left corner, in the overlay's pixels; it starts on the right edge, a third down
        var x by remember { mutableFloatStateOf(-1f) }
        var y by remember { mutableFloatStateOf(-1f) }
        // a drag that passed the touch slop suppresses the click that would otherwise fire on release
        var dragged by remember { mutableStateOf(false) }

        LaunchedEffect(maxW, maxH) {
            if (x < 0f && maxW > 0f) {
                x = maxW - ball - margin
                y = maxH * 0.30f
            }
        }

        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(44.dp)
                .clip(CircleShape)
                .background(NxPalette.Surface)
                .border(2.dp, NxPalette.Brand, CircleShape)
                .pointerInput(maxW, maxH) {
                    detectDragGestures(
                        onDragStart = { dragged = false },
                        onDrag = { change, drag ->
                            change.consume()
                            dragged = true
                            x = (x + drag.x).coerceIn(0f, maxW - ball)
                            y = (y + drag.y).coerceIn(0f, maxH - ball)
                        },
                        onDragEnd = {
                            // let go: the ball slides to whichever side edge it is nearer to
                            x = if (x + ball / 2 < maxW / 2) margin else maxW - ball - margin
                            y = y.coerceIn(0f, maxH - ball)
                        },
                    )
                }
                .clickable {
                    // a tap that slid past the slop counted as a drag and swallowed its click —
                    // nothing reset the flag (only a real drag did), so every later tap died too
                    val wasDragged = dragged
                    dragged = false
                    if (!wasDragged) onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.SmartToy,
                contentDescription = null,
                Modifier.size(20.dp),
                tint = NxPalette.Brand,
            )
        }
    }
}
