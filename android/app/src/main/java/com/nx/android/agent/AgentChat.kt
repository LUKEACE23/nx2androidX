package com.nx.android.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File

enum class AgentRole { USER, ASSISTANT }

data class AgentToolCard(
    val id: String,
    val name: String,
    val argsSummary: String,
    val state: AgentToolState,
    val resultSummary: String? = null,
    /** The sub-agent this call spawned (null for an ordinary tool). */
    val subagent: AgentSubagentCall? = null,
)

enum class AgentToolState { RUNNING, DONE, FAILED }

data class AgentMessage(
    val id: Long,
    val role: AgentRole,
    val text: String,
    val tools: List<AgentToolCard> = emptyList(),
    /**
     * 这一轮的思考过程（引擎的 thinking）。
     *
     * 它不是回答，是模型自己想的过程：默认折起来，点开才看。
     */
    val thinking: String = "",
    /** 这一条回答自己生成的 token 数（0 = 引擎没报） */
    val tokens: Long = 0L,
    /**
     * 这一轮还没收尾：App 在这一轮中途没了，标记留在会话文件里。
     * 重新打开时它显示「已中断」——玩家看得见这一轮断在哪。
     */
    val pending: Boolean = false,
    /** 这一轮里，子 Agent 完成时回来的通知（一张卡片一张通知）。 */
    val notifications: List<AgentNotification> = emptyList(),
)

sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent
    /** 引擎的思考过程：模型自己想的那一段（不是回答） */
    data class ThinkingDelta(val text: String) : AgentEvent
    data class ToolStarted(val card: AgentToolCard) : AgentEvent
    data class ToolFinished(val id: String, val state: AgentToolState, val resultSummary: String) : AgentEvent
    data class TurnDone(val assistantText: String, val tokens: Long = 0L) : AgentEvent
    data class Failed(val reason: AgentFailure) : AgentEvent

    /** The running context occupancy (tokens in the model's window) and the window itself. */
    data class UsageUpdated(val contextTokens: Long, val contextWindow: Long) : AgentEvent

    /** A sub-agent finished: the notice that says so. */
    data class Notification(val notification: AgentNotification) : AgentEvent

    /**
     * 轮外的一轮写完了：后台子 Agent 跑完之后，主 Agent 自己写下的那段下文。
     *
     * 它到的时候没有玩家在问——引擎把子 Agent 的通知注入会话，主 Agent 随即接一段话。
     * 这段话是给玩家看的，所以它自己是一条消息。
     */
    data class FollowUp(val text: String, val tokens: Long = 0L) : AgentEvent
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

    /**
     * 轮外的事件：子 Agent 跑完的通知、主 Agent 在轮外写下的下文、上下文占用。
     *
     * 它们是引擎在两轮之间送来的（后台子 Agent 完成后，引擎把通知注入会话，主 Agent
     * 随即写一段总结）——这些事件到的时候没有轮在跑，App 照样得看得见。
     */
    val backgroundEvents: Flow<AgentEvent> get() = emptyFlow()

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

/** 页面上下文里那个键：那一局的攻关档案在哪儿 */
const val CASE_DIR_FACT = "攻关档案"

/**
 * 玩家点了「按方案修复」之后送去主会话的话。
 *
 * 攻关子 Agent 看到「玩家已确认」才动代码——这句是那道门。
 */
const val CONFIRM_WORDS = "玩家已确认，按档案里的方案执行修复"

data class AgentSubject(val kind: String, val id: String, val title: String)

/**
 * The context a page hands the assistant: which game the player is in, and where that game's
 * case file is.
 *
 * The game page says "game"; the library page says "library" — there the player is looking at the
 * card they picked (right after a crash, that is the game that crashed).
 */
fun gameContext(
    titleId: String,
    name: String,
    caseDir: File,
    page: String = "game",
): AgentPageContext = AgentPageContext(
    page = page,
    subject = AgentSubject("game", titleId, name),
    facts = mapOf(CASE_DIR_FACT to caseDir.absolutePath),
)

interface AgentUiActions {
    fun navigate(page: String, game: String? = null): String
}
