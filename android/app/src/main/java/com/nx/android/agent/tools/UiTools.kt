package com.nx.android.agent.tools

import com.nx.android.agent.runtime.AgentUiBridge
import io.agentscope.core.tool.Tool
import io.agentscope.core.tool.ToolParam
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The agent's way to act on the UI: bring the player to the page where the answer lives. Navigation is a
 * benign action (it changes what the player sees, never data), so it is who=ai — the tool announces the
 * jump in its result and the model relays it. When the UI has not registered its actions yet, the tool
 * says so instead of pretending (ADR-004: no invented results).
 */
@Singleton
class UiTools @Inject constructor(
    private val bridge: AgentUiBridge,
) {

    companion object {
        /** The pages navigate() accepts, and the words the model sees for each */
        private val PAGES = mapOf(
            "library" to "游戏库",
            "builds" to "构建页",
            "settings" to "设置页",
        )
    }

    @Tool(
        name = "navigate",
        description = "带玩家去应用的某个页面（游戏库/构建/设置）。当你的回答指向某个页面时用这个工具把玩家带过去，" +
            "例如建议重新构建之后就打开构建页。",
        readOnly = true,
        concurrencySafe = false,
    )
    fun navigate(
        @ToolParam(name = "page", description = "目标页面：library（游戏库）/ builds（构建）/ settings（设置）")
        page: String,
        @ToolParam(
            name = "game",
            description = "可选：要选中的游戏名或 title ID。仅构建页支持直接选中该游戏",
            required = false,
        )
        game: String? = null,
    ): String {
        val target = PAGES[page.lowercase().trim()]
            ?: return "unknown page: $page（可用：library / builds / settings）"
        return bridge.navigate(target, game?.takeIf { it.isNotBlank() })
    }
}
