package com.nx.android.game

import org.json.JSONArray

/**
 * 一条金手指（宿主那边的一段程序，改写游戏内存里的一个数值）。
 *
 * 名字来自社区那份 Atmosphère 格式的文件；why 是它不能跑的原因（要改游戏的代码之类），空着就是能跑。
 */
data class Cheat(val name: String, val on: Boolean, val why: String) {
    /** 它不能跑（这一版只写数据，不改游戏的代码） */
    val broken: Boolean get() = why.isNotEmpty()
}

/** 宿主给的金手指列表（JSON 数组）：每条 {name, on, why} */
fun parseCheats(json: String): List<Cheat> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val a = JSONArray(json)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Cheat(o.optString("name"), o.optBoolean("on"), o.optString("why"))
        }
    }.getOrDefault(emptyList())
}

/** 金手指页的状态：App 那边这个游戏有什么 */
data class CheatStatus(
    /** 手机上导入过金手指（不管哪个游戏） */
    val anyImported: Boolean = false,
    /** 这个游戏这个 build 的文件在（宿主加载的就是它） */
    val hasBuild: Boolean = false,
    /** 出处：导入的那份文件自己写的 */
    val credit: String = "",
)
