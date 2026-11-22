package com.nx.android.agent

import kotlinx.coroutines.flow.Flow

enum class AgentRole { USER, ASSISTANT }

data class AgentToolCard(
    val id: String,
    val name: String,
    val argsSummary: String,
    val state: AgentToolState,
    val resultSummary: String? = null,
)

enum class AgentToolState { RUNNING, DONE, FAILED }

data class AgentMessage(
    val id: Long,
    val role: AgentRole,
    val text: String,
    val tools: List<AgentToolCard> = emptyList(),
    /** 这一条回答自己生成的 token 数（0 = 引擎没报） */
    val tokens: Long = 0L,
    /**
     * 这一轮还没收尾：App 在这一轮中途没了，标记留在会话文件里。
     * 重新打开时它显示「已中断」——玩家看得见这一轮断在哪。
     */
    val pending: Boolean = false,
)

sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent
    data class ToolStarted(val card: AgentToolCard) : AgentEvent
    data class ToolFinished(val id: String, val state: AgentToolState, val resultSummary: String) : AgentEvent
    data class TurnDone(val assistantText: String, val tokens: Long = 0L) : AgentEvent
    data class Failed(val reason: AgentFailure) : AgentEvent

    /** The running context occupancy (tokens in the model's window) and the window itself. */
    data class UsageUpdated(val contextTokens: Long, val contextWindow: Long) : AgentEvent
}

enum class AgentFailure { NO_KEY, BAD_KEY, NETWORK, MODEL_NO_TOOLS, LOOP_ERROR }

data class AgentModelConfig(
    val providerId: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
) {
    val configured: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank()
}

interface AgentChat {
    val messages: List<AgentMessage>
    suspend fun send(userText: String, context: AgentPageContext? = null): Flow<AgentEvent>
    fun reset()

    /** 中止当前这一轮：引擎停下手里的活，进程留着给下一轮。 */
    fun abort()

    /**
     * 读回一段会话的历史：prefer 为空就是指针上的（或最新的一段），指定就是切到那一段再读。
     * 会话的真相在引擎那里——这是抽屉渲染历史的唯一来源。
     */
    suspend fun history(prefer: String? = null): Result<AgentHistory>
}

/** 一段会话的历史（从引擎读回的形状）：消息、会话文件的路径、标题、上下文占用。 */
data class AgentHistory(
    val messages: List<AgentMessage>,
    val sessionFile: String,
    val title: String,
    val contextTokens: Long,
    val contextWindow: Long,
)

/** 引擎还没装：历史读不了，面板要引导安装，而不是显示一段空历史。 */
class AgentEngineMissingException : IllegalStateException("引擎未安装")

data class AgentPageContext(
    val page: String,
    val subject: AgentSubject? = null,
    val facts: Map<String, String> = emptyMap(),
)

data class AgentSubject(val kind: String, val id: String, val title: String)

interface AgentUiActions {
    fun navigate(page: String, game: String? = null): String
}
