package com.nx.android.data

import org.json.JSONObject
import java.io.File

/**
 * 每个游戏的攻关档案：`gamespec/<title ID>/` 在玩家的 nx2android 文件夹里（环境重建不动它）。
 *
 * 档案里的文件各有各的写者：App 只写运行事故的流水（events.jsonl，出事那一刻追加一行），
 * 攻关子 Agent 写攻关文档（spec.md）与修过的文件（files/ + fix.md）。谁也不跟谁抢同一个文件。
 *
 * 玩家没有委托过攻关的游戏没有这个目录：档案是攻关的产物，不是每个游戏的默认零件。
 */
object Gamespec {

    /** 事件的三种：进程异常退出、运行库重建、启动失败 */
    const val EXIT = "process_exit"
    const val REBUILD = "runtime_rebuild"
    const val LAUNCH_FAILED = "launch_failed"

    /** 流水的文件名 */
    const val EVENTS_FILE = "events.jsonl"

    /** 攻关文档的文件名（子 Agent 写它，app 只读） */
    const val SPEC_FILE = "spec.md"

    /**
     * 方案待确认的标记。
     *
     * 子 Agent 诊断完写下它（连同建议修法），玩家点了「按方案修复」之后它把状态改成已修/未修。
     * app 读这个标记才知道该不该给玩家那个按钮——app 不写档案，只读。
     *
     * 标记是英文的：档案整个是英文的（它要交给上游看）。
     */
    const val PLAN_PENDING = "(status: pending approval)"

    /**
     * 玩家语言的那几行：档案里 `Player ` 开头的行。
     *
     * 档案整个是英文的（它要交给上游看），但玩家读的是自己的语言——子 Agent 在英文那几行
     * 旁边再写几行玩家语言的，app 显示时优先用它们，没有就回退英文那几行。
     *
     * 字段名是英文的（那是 app 与子 Agent 之间的约定），值用玩家读的那种语言。
     */
    const val PLAYER_HEADLINE = "Player headline:"
    const val PLAYER_CAUSE = "Player cause:"
    const val PLAYER_FIX = "Player fix:"

    /** 那局的攻关档案里待确认的方案：问题、根因、建议修法 */
    data class Plan(
        /** 那一条的标题（「### 2026-11-23 cross-chunk copy dropped (status: pending approval)」里的前半句） */
        val headline: String,
        /** 根因 */
        val cause: String,
        /** 建议修法 */
        val fix: String,
        /** 玩家语言的那几行（档案里可选的 `Player ` 行） */
        val playerHeadline: String? = null,
        val playerCause: String? = null,
        val playerFix: String? = null,
    ) {
        /** 给玩家看的标题：玩家语言的那行优先，没有就用英文的 */
        val shownHeadline: String get() = playerHeadline?.takeIf { it.isNotBlank() } ?: headline

        /** 给玩家看的根因：玩家语言的那行优先，没有就用英文的 */
        val shownCause: String get() = playerCause?.takeIf { it.isNotBlank() } ?: cause

        /** 给玩家看的修法：玩家语言的那行优先，没有就用英文的 */
        val shownFix: String get() = playerFix?.takeIf { it.isNotBlank() } ?: fix
    }

    /** 那局档案的文件夹；[titleId] 是空的（这局说不清是哪个游戏）时返回的文件夹不要用 */
    fun dir(nxHome: File, titleId: String): File = File(File(nxHome, "gamespec"), titleId)

    /**
     * 追加一行事故（时间、种类、细节）。只追加：已有的行原样留着，后来的排在后面。
     * 写不进去就放着——记录事故这件事本身不该把谁绊倒。
     */
    fun appendEvent(
        nxHome: File,
        titleId: String,
        kind: String,
        detail: String = "",
        at: Long = System.currentTimeMillis(),
    ) {
        if (titleId.isBlank()) return
        val line = JSONObject().apply {
            put("ts", at)
            put("kind", kind)
            if (detail.isNotBlank()) put("detail", detail.take(300))
        }.toString()
        runCatching {
            val dir = dir(nxHome, titleId)
            dir.mkdirs()
            File(dir, EVENTS_FILE).appendText(line + "\n")
        }
    }

    /** 一局的事故流水（一行一个事件，按时间先后），读不出来（没攻关过）就是空的 */
    fun events(nxHome: File, titleId: String): List<JSONObject> {
        val file = File(dir(nxHome, titleId), EVENTS_FILE)
        if (!file.isFile) return emptyList()
        val out = mutableListOf<JSONObject>()
        runCatching {
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                runCatching { JSONObject(line) }.getOrNull()?.let { out.add(it) }
            }
        }
        return out
    }

    /**
     * 那局档案里待确认的方案：子 Agent 诊断完写下的那一条。
     *
     * 没有就是没有（没攻关过，或者方案已经修过了）。只读档案，app 不写它。
     */
    fun pendingPlan(caseDir: File): Plan? {
        val spec = File(caseDir, SPEC_FILE)
        if (!spec.isFile) return null
        val text = runCatching { spec.readText() }.getOrNull() ?: return null
        return parsePlan(text)
    }

    /**
     * 最近动过的那一局档案：面板上「修好了，去跑一下」说的是它。
     *
     * 最近写过的档案就是刚刚被攻关的那一局（子 Agent 写完 spec.md 才算数）。
     */
    fun recentCaseDir(nxHome: File): File? {
        val root = File(nxHome, "gamespec")
        val dirs = runCatching { root.listFiles()?.filter { it.isDirectory } }.getOrNull() ?: return null
        return dirs
            .filter { File(it, SPEC_FILE).isFile }
            .maxByOrNull { runCatching { File(it, SPEC_FILE).lastModified() }.getOrDefault(0L) }
    }

    /** 档案里最后一条待确认的方案（解析 spec.md 的正文） */
    fun parsePlan(text: String): Plan? {
        var plan: Plan? = null
        var headline = ""
        var cause = ""
        var fix = ""
        var playerHeadline = ""
        var playerCause = ""
        var playerFix = ""
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("### ")) {
                // 一条结束了：它带着待确认标记的话，它就是一条方案（后一条覆盖前一条）
                if (headline.contains(PLAN_PENDING)) {
                    plan = Plan(
                        headline.substringBefore(PLAN_PENDING).trim(), cause, fix,
                        playerHeadline, playerCause, playerFix,
                    )
                }
                headline = line.removePrefix("### ").trim()
                cause = ""
                fix = ""
                playerHeadline = ""
                playerCause = ""
                playerFix = ""
                continue
            }
            if (headline.isBlank()) continue
            when {
                line.startsWith(PLAYER_HEADLINE) -> playerHeadline = line.removePrefix(PLAYER_HEADLINE).trim()
                line.startsWith(PLAYER_CAUSE) -> playerCause = line.removePrefix(PLAYER_CAUSE).trim()
                line.startsWith(PLAYER_FIX) -> playerFix = line.removePrefix(PLAYER_FIX).trim()
                line.startsWith("Cause:") -> cause = line.removePrefix("Cause:").trim()
                line.startsWith("Proposed fix:") -> fix = line.removePrefix("Proposed fix:").trim()
                line.startsWith("Fix:") && fix.isBlank() -> fix = line.removePrefix("Fix:").trim()
            }
        }
        if (headline.contains(PLAN_PENDING)) {
            plan = Plan(
                headline.substringBefore(PLAN_PENDING).trim(), cause, fix,
                playerHeadline, playerCause, playerFix,
            )
        }
        return plan?.takeIf { it.headline.isNotBlank() }
    }

    /**
     * 档案时间线上的最新一条：子代理自己记下的进展。
     *
     * 它是玩家看得见的进度通道——子代理干活时往时间线上追加一行，这里把它读出来，
     * 玩家不用发消息问「咋样」。
     */
    fun latestTimeline(caseDir: File): String? {
        val spec = File(caseDir, SPEC_FILE)
        if (!spec.isFile) return null
        val text = runCatching { spec.readText() }.getOrNull() ?: return null
        return latestTimelineOf(text)
    }

    /** 正文时间线上的最后一行（没有就是没有）。 */
    fun latestTimelineOf(text: String): String? {
        var inTimeline = false
        var latest: String? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("## ")) {
                inTimeline = line.equals("## Timeline", ignoreCase = true)
                continue
            }
            if (inTimeline && line.startsWith("- ")) latest = line.removePrefix("- ").trim()
        }
        return latest
    }

}
