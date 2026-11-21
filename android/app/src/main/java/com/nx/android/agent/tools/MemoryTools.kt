package com.nx.android.agent.tools

import com.nx.android.agent.runtime.AgentWorkspace
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryTools @Inject constructor(private val workspace: AgentWorkspace) {
    fun save(lesson: String): String {
        if (lesson.isBlank()) return "空经验不存。"
        workspace.remember(lesson)
        return "已记下。"
    }
}
