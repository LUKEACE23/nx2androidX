package com.nx.android.agent.runtime

import com.nx.android.agent.AgentPageContext

/** The page context's budget: ≤400 tokens ≈ 800 chars of CJK (页面上下文设计，ADR-005). */
internal const val CONTEXT_CHARS = 800

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
    if (hasDevice) sb.append("[本机状态] ").append(deviceState.trim()).append('\n')
    if (hasMemory) sb.append("[助手记忆] ").append(memory.trim()).append('\n')
    if (hasContext) {
        val block = StringBuilder("[页面上下文] ")
        if (context.subject != null) block.append("正在处理：${context.subject.title}。")
        for ((k, v) in context.facts) block.append("$k：$v。")
        if (block.length > CONTEXT_CHARS) { block.setLength(CONTEXT_CHARS); block.append("…已截断") }
        sb.append(block).append('\n')
    }
    return sb.append("玩家说：").append(userText).toString()
}
