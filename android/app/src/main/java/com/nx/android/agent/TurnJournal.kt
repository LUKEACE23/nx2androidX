package com.nx.android.agent

/**
 * 这一轮的账：会话里有哪些消息、这一轮写到哪了、什么时候该落盘。
 *
 * 纯 Kotlin：不碰引擎进程、不碰 Android。落盘的内容与时机由它说了算，JVM 测试直接驱动。
 *
 * 「未收尾」是写进会话文件里的一个标记：轮次开始时打上，正常结束（或者玩家按了停止）时抹掉。
 * App 在这一轮中途没了，就没人来抹——重新打开时，那条消息上留着「已中断」，玩家看得见这一轮断在哪。
 */
class TurnJournal(
    initial: List<AgentMessage> = emptyList(),
    /** 落盘的节流：几秒写一次（写的是整个会话文件，手机上不构成压力） */
    private val flushIntervalMs: Long = 2_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val items: MutableList<AgentMessage> = initial.toMutableList()
    private var nextId: Long = (initial.maxOfOrNull { it.id } ?: -1L) + 1L
    private var draftId: Long? = null
    private var lastWriteAt: Long? = null   // 还没写过

    /** 换成另一段历史（切换会话时）。 */
    fun load(history: List<AgentMessage>) {
        items.clear()
        items.addAll(history)
        nextId = (history.maxOfOrNull { it.id } ?: -1L) + 1L
        draftId = null
    }

    /** 会话文件里该有的内容。 */
    fun snapshot(): List<AgentMessage> = items.toList()

    /** 这一轮还开着吗（草稿还在）。 */
    fun drafting(): Boolean = draftId != null

    /** 草稿里已经写出来的字。 */
    fun draftText(): String {
        val id = draftId ?: return ""
        return items.firstOrNull { it.id == id }?.text.orEmpty()
    }

    /**
     * 轮次开始：玩家的问题落下，助手的草稿也占好位（带「未收尾」标记）。
     *
     * 问过什么不该等这一轮结束才记上——App 在这一轮中途没了，那条问题也得还在。
     */
    fun beginTurn(userText: String) {
        if (draftId != null) finishTurn(0L)   // 上一轮没关干净就开了新的一轮：先把旧的收掉
        items.add(AgentMessage(nextId++, AgentRole.USER, userText))
        val draft = AgentMessage(nextId++, AgentRole.ASSISTANT, "", pending = true)
        items.add(draft)
        draftId = draft.id
    }

    /** 助手写出的字进了草稿。 */
    fun appendDelta(text: String) {
        if (text.isEmpty()) return
        val id = draftId ?: return
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items[i] = items[i].copy(text = items[i].text + text)
    }

    /** 这一轮正常结束：草稿成了正式的回答，标记抹掉。 */
    fun finishTurn(tokens: Long) {
        val id = draftId ?: return
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = items[i].copy(pending = false, tokens = tokens)
        draftId = null
    }

    /**
     * 玩家按了停止：已写出的部分算数（草稿留着），标记抹掉。
     *
     * 这是玩家的选择，不是意外——它不算「中断」。
     */
    fun stopTurn() {
        val id = draftId ?: return
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) items[i] = items[i].copy(pending = false)
        draftId = null
    }

    /**
     * 引擎中途死了，这一轮什么也没写出来：空壳丢掉。
     *
     * 已经写出来的字不算空壳——那种情况这一轮照常收尾。
     */
    fun failTurn() {
        val id = draftId ?: return
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0 && items[i].text.isEmpty()) items.removeAt(i)
        draftId = null
    }

    /**
     * 该落盘了吗（节流：几秒一次）。
     *
     * 判完就把计时归零——判了就算掉一次机会，免得连着两次都判yes。
     */
    fun dueForWrite(): Boolean {
        val now = clock()
        val last = lastWriteAt
        if (last != null && now - last < flushIntervalMs) return false
        lastWriteAt = now
        return true
    }

    /** 记下刚写过（不经过节流的那次写）。 */
    fun markWritten() {
        lastWriteAt = clock()
    }
}
