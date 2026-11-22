package com.nx.android.agent.env

import com.nx.android.agent.runtime.AgentEngineStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * 引擎会话目录的读法：一个 jsonl 文件就是一段会话，最新的在最上面。
 *
 * 引擎的 RPC 没有「列出会话」的命令——目录枚举是 App 必须自己做的少数几件事之一。
 * 耦合面控制在这里：只用文件名、时间与文件头部的会话名；消息正文一律走 RPC，不解析
 * 它的内部格式。
 */
@Singleton
class PiSessionCatalog internal constructor(private val home: File) {

    /** 注入用的那个：会话目录在引擎自己的 HOME 下面。 */
    @Inject
    constructor(engine: AgentEngineStore) : this(engine.home)

    /** 下拉列表的一行：会话文件的路径、标题、最后活动时间。 */
    data class Entry(val path: String, val title: String, val updatedAt: Long)

    /** 引擎的全部会话，最新的在最上面。目录不在（引擎没装/还没聊过）就是空列表。 */
    fun list(): List<Entry> {
        val sessions = File(home, ".pi/agent/sessions")
        val files = sessions.listFiles { f -> f.isDirectory }
            ?.flatMap { dir -> dir.listFiles { f -> f.isFile && f.extension == "jsonl" }?.toList() ?: emptyList() }
            .orEmpty()
        return files.map { f -> Entry(f.absolutePath, titleOf(f), f.lastModified()) }
            .sortedByDescending { it.updatedAt }
    }

    /**
     * 标题：会话名优先——我们自己建的会话在首轮就把名字写进了文件头部，读文件头一小段
     * 就够；没读到（别处来的老会话）退回文件名里的时间。
     */
    private fun titleOf(f: File): String {
        runCatching {
            f.inputStream().use { stream ->
                val head = ByteArray(64 * 1024)
                val n = stream.read(head)
                var name: String? = null
                for (line in String(head, 0, n, Charsets.UTF_8).split('\n')) {
                    if (!line.contains("\"session_info\"")) continue
                    runCatching { JSONObject(line).optString("name") }.getOrNull()
                        ?.takeIf { it.isNotBlank() }?.let { name = it }
                }
                name?.let { return it }
            }
        }
        return timeTitle(f.name)
    }

    /** 文件名里的时间戳（UTC）转成本地可读的样子。 */
    private fun timeTitle(name: String): String {
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(name.take(19))
        }.getOrNull()
        return if (parsed != null) {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(parsed)
        } else {
            "会话"
        }
    }
}
