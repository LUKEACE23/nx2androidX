package com.nx.android.data.agent

import android.util.Log
import com.nx.android.domain.AgentPackage
import com.nx.android.domain.UpdateFeed
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The packages the repository's newest release carries.
 *
 * The release's feed says which release is the newest one (a plain page: no API and no rate limit)
 * and that release's assets page says what it carries.
 */
object AgentPackageCatalog {

    private const val TAG = "Nx2aAgentPkg"

    /** The newest release's tag and the packages it carries. */
    fun latest(): Result<Pair<String, List<String>>> = runCatching {
        val feed = body(AgentPackage.RELEASES_FEED_URL) ?: throw IOException("the repository did not answer")
        val tag = UpdateFeed.parse(feed).firstOrNull()?.tag ?: throw IOException("the repository carries no release")
        val html = body(AgentPackage.assetsPage(tag)) ?: throw IOException("the release page did not answer")
        val names = AgentPackage.packageNamesIn(html)
        Log.i(TAG, "the newest release is $tag, it carries ${names.size} package(s): $names")
        tag to names
    }.onFailure { Log.e(TAG, "the repository could not be read: ${it.message}") }

    /** A page's body (the asset list is a small one); null when the server did not answer */
    private fun body(url: String): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "nx2android")
        }
        try {
            val code = conn.responseCode
            val text = if (code == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
            Log.i(TAG, "$url answered $code (${text?.length ?: 0} characters)")
            text
        } finally {
            conn.disconnect()
        }
    } catch (e: IOException) {
        Log.e(TAG, "$url did not answer: ${e.message}")
        null
    }
}
