package com.nx.android.agent.runtime

import com.nx.android.agent.AgentPageContext

/** The page context's budget: ≤400 tokens ≈ 800 chars of CJK (页面上下文设计，ADR-005). */
internal const val CONTEXT_CHARS = 800

/**
 * The device state block: the app's own version first, then the library summary.
 *
 * The version leads because nothing else in the turn tells the model which build is installed — an
 * old apk file name in a download folder once had it answer "0.3.0-alpha06" while the installed
 * app was 0.4.0. The line is written before the budget is spent on the library, so a long library
 * can never cut it off.
 *
 * The player's language rides along: the model works in English and translates for the player, so
 * it has to know which language the player reads.
 */
internal fun deviceState(version: String, library: String, budget: Int, language: String = ""): String {
    val head = buildString {
        append("app version: ").append(version)
        if (language.isNotBlank()) append("\nplayer language: ").append(language)
    }
    val room = (budget - head.length - 1).coerceAtLeast(0)
    return "$head\n${library.take(room)}"
}

/**
 * The player's words as the model reads them: the memory block and the page-context block ride
 * ahead, the words come last. Pure function — the JVM tests drive it.
 */
internal fun composeTurn(userText: String, memory: String, context: AgentPageContext?, deviceState: String = ""): String {
    val hasDevice = deviceState.isNotBlank()
    val hasMemory = memory.isNotBlank()
    val hasContext = context != null
    if (!hasDevice && !hasMemory && !hasContext) return userText
    val sb = StringBuilder()
    if (hasDevice) sb.append("[device] ").append(deviceState.trim()).append('\n')
    if (hasMemory) sb.append("[memory] ").append(memory.trim()).append('\n')
    if (hasContext) {
        val block = StringBuilder("[page context] ")
        if (context.subject != null) block.append("working on: ${context.subject.title}. ")
        for ((k, v) in context.facts) block.append("$k: $v. ")
        if (block.length > CONTEXT_CHARS) { block.setLength(CONTEXT_CHARS); block.append("...truncated") }
        sb.append(block).append('\n')
    }
    return sb.append("player says: ").append(userText).toString()
}
