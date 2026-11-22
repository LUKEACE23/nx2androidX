package com.nx.android.agent.ui

import androidx.compose.ui.graphics.Color

/**
 * The assistant's palette — the app's own language, one-to-one with the new design draft: near-black
 * ground, deep glass panels over a hairline, warm-white ink, cream for the primary action, the brand
 * orange only as an accent, one color per status. No foreign styles — the drawer must look like it
 * grew inside the app.
 */
object AgentPalette {
    val Ground = Color(0xFF0E0E0F)
    val Panel = Color(0xFF1A1A1C)
    val Hairline = Color(0xFF2A2A2E)
    val Ink = Color(0xFFEDEDF0)
    val Ink2 = Color(0xFFA9A9B2)
    val Cream = Color(0xFFFFF0D6)
    val CreamInk = Color(0xFF141416)
    val Orange = Color(0xFFFF8A00)
    val Ready = Color(0xFF40D678)
    val Working = Color(0xFFFFC73D)
    val Idle = Color(0xFF91949B)
    val Danger = Color(0xFFFF6B66)
    val CodeBg = Color(0xFF141416)
    val CodeInk = Color(0xFFD8DADE)
    val Cmd = Color(0xFFC9CCD4)
    val Quick = Color(0xFFD6D7DC)

    /** The drawer's ground: near-black at 93% — the page's dimming shows through just enough. */
    val DrawerBg = Color(0xEB0E0E0F)
}
