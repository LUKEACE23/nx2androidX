package com.nx.android.agent.tools

import com.nx.android.agent.runtime.AgentWorkspace
import io.agentscope.core.tool.Tool
import io.agentscope.core.tool.ToolParam
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The agent's way to grow (ADR-007's memory channel): record a lesson into the long-term memory. The
 * workspace keeps the dated daily file and a curated MEMORY.md whose newest entries are injected every
 * turn — so a lesson written today is available in every conversation from now on.
 *
 * Writing to the workspace is the one benign write the assistant is allowed: it is the agent's own
 * directory, it carries no player data, and the memory is what makes the assistant grow. Everything else
 * stays read-only.
 */
@Singleton
class MemoryTools @Inject constructor(
    private val workspace: AgentWorkspace,
) {

    @Tool(
        name = "memory_save",
        description = "把一条经验教训写进长期记忆（跨会话保留，之后的对话都能用到）。" +
            "值得存的是：某台设备上某个游戏的修法、某个开关的疗效、某个报错的真正原因。" +
            "不要存临时的状态，也不要存玩家的个人信息。",
        readOnly = false,
        concurrencySafe = false,
    )
    fun memorySave(
        @ToolParam(name = "lesson", description = "一句话的经验，写清楚条件（设备/游戏/症状）与结果（用什么方法解决了什么）")
        lesson: String,
    ): String {
        val clean = lesson.trim()
        if (clean.isEmpty()) return "空的经验没有存。"
        workspace.ensure()
        workspace.remember(clean)
        return "已记下：${clean.take(60)}${if (clean.length > 60) "…" else ""}"
    }
}
