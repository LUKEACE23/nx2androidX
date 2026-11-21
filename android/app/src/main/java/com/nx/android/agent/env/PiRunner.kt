package com.nx.android.agent.env

import android.content.Context
import com.nx.android.agent.runtime.AgentSettingsStore
import com.nx.android.domain.environment.GuestProcessLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The env agent: pi — a full coding agent (read/write/edit/bash, ADR-009) living in the glibc
 * environment the porter runs in. This is the strong tier; the in-app ReAct runtime stays as the
 * light tier. One headless turn per call (`pi -p`): the panel's streaming RPC client is the next
 * step, this is the wiring proof.
 *
 * The work directory is the app's data root (NX2A_HOME): the AGENTS.md seeded there carries the
 * domain knowledge — where the packs live, which log carries a crash's backtrace, the read-only
 * discipline.
 */
@Singleton
class PiRunner @Inject constructor(
    private val guest: GuestProcessLauncher,
    private val proxy: GuestProxy,
    private val settings: AgentSettingsStore,
) {

    /** One headless pi turn; the answer's text, or a failure the caller shows verbatim. */
    suspend fun run(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val base = guest.guestEnv()
        val nx2a = File(requireNotNull(base["NX2A_HOME"]) { "NX2A_HOME is not set in the guest env" })
        val binary = File(nx2a, "agent/pi/pi")
        if (!binary.isFile) {
            return@withContext Result.failure(IllegalStateException("pi 没有安装（files/nx2a/agent/pi/pi 不存在）"))
        }
        val config = settings.config
        if (!config.configured) {
            return@withContext Result.failure(IllegalStateException("模型还没有配置"))
        }

        val proxyPort = proxy.ensure()
        val pb = ProcessBuilder(listOf(binary.absolutePath, "-p", "--model", fullModelId(config), prompt))
            .directory(nx2a)
        pb.redirectErrorStream(true)
        // stdin is /dev/null: an open pipe makes pi wait for an EOF that never comes
        // (0 cpu, no sockets, no session — verified once on device the hard way)
        pb.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
        pb.environment().putAll(base)
        pb.environment()["http_proxy"] = "http://127.0.0.1:$proxyPort"
        pb.environment()["https_proxy"] = pb.environment()["http_proxy"]
        pb.environment()["TERM"] = "dumb"
        val (keyName, keyValue) = keyEnv(config.providerId, config.apiKey)
        pb.environment()[keyName] = keyValue

        try {
            val process = pb.start()
            val out = StringBuilder()
            val reader = Thread {
                process.inputStream.use { stream ->
                    val buf = CharArray(4_096)
                    val r = stream.reader(Charsets.UTF_8)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        if (out.length < 100_000) out.appendRange(buf, 0, n)
                    }
                }
            }
            reader.start()
            val finished = process.waitFor(300, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                return@withContext Result.failure(IllegalStateException("超时：pi 跑了 5 分钟没有结束"))
            }
            reader.join(2_000)
            val text = out.toString().trim()
            if (process.exitValue() != 0) {
                return@withContext Result.failure(IllegalStateException("退出码 ${process.exitValue()}：${text.takeLast(400)}"))
            }
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** pi's model id: the catalog wants provider/model — the settings store keeps the bare name. */
    private fun fullModelId(config: com.nx.android.agent.AgentModelConfig): String =
        if (config.modelName.contains('/')) config.modelName
        else when (config.providerId) {
            "minimax" -> "minimax-cn/${config.modelName}"
            "glm" -> "zai/${config.modelName}"
            "deepseek" -> "deepseek/${config.modelName}"
            "kimi" -> "moonshot/${config.modelName}"
            else -> config.modelName
        }

    /** The provider's key travels as the env var pi reads; the app never writes it to a file. */
    private fun keyEnv(providerId: String, key: String): Pair<String, String> = when (providerId) {
        "minimax" -> "MINIMAX_CN_API_KEY" to key
        "glm" -> "ZAI_CODING_CN_API_KEY" to key
        "deepseek" -> "DEEPSEEK_API_KEY" to key
        "kimi" -> "MOONSHOT_API_KEY" to key
        else -> "OPENAI_API_KEY" to key
    }
}
