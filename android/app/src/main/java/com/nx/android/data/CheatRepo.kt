package com.nx.android.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The community cheat library (switch-cheats-db), fetched over the network.
 *
 * The library holds one JSON file per game (named by its title ID), and inside it one section per build: the keys are
 * the cheat names ("[Inf HP]"), the values the text of the cheat. A cheat file belongs to one build, so the app picks
 * the section of the build it runs and writes it out as the Atmosphère-format file the host reads.
 *
 * The file lands in the app's own folder (files/cheats/<title ID>/<build ID>.txt): a rebuilt data pack does not touch
 * that folder, so the cheats the player downloaded stay where they are.
 */
object CheatRepo {

    /** The library's CDN: the community's files, one JSON per game */
    const val CDN = "https://cdn.jsdelivr.net/gh/HamletDuFromage/switch-cheats-db@master/cheats"

    /**
     * The CDN's own edges: the same file, three networks. One network is often blocked (a reset TLS handshake
     * on some carriers), so the download walks the list and takes the first that answers.
     */
    private val EDGES = listOf("cdn.jsdelivr.net", "fastly.jsdelivr.net", "gcore.jsdelivr.net")

    /** The library's file of one game (its title ID, upper case: the library names its files that way) */
    fun url(titleId: String): String = "$CDN/${titleId.uppercase()}.json"

    /** The same file on every edge (the download tries them in turn) */
    fun urls(titleId: String): List<String> =
        EDGES.map { "https://$it/gh/HamletDuFromage/switch-cheats-db@master/cheats/${titleId.uppercase()}.json" }

    /** The folder the app keeps the cheats of one game in (app-private: a rebuilt data pack does not touch it) */
    fun dir(filesDir: File, titleId: String): File = File(filesDir, "cheats/$titleId")

    /** The cheat file of one build (the name the host looks for) */
    fun file(dir: File, build: String): File = File(dir, "${build.uppercase()}.txt")

    /** The line of credit beside the file (what the library says about it) */
    fun creditFile(dir: File, build: String): File = File(dir, "${build.uppercase()}.credit")

    /**
     * The toggles of the cheats (the host writes them beside the file): the names of the cheats that are on, one a
     * line. Deleting that file turns them all off.
     *
     * Returns whether there was one (nothing to turn off when there was none).
     */
    fun clearToggles(dir: File, build: String): Boolean {
        val file = File(dir, "${build.uppercase()}.enabled")
        if (!file.isFile) return false
        return file.delete()
    }

    /**
     * The build ID the cheat files are named by, read from the data pack's rewriter table (its head line:
     * `# BUILD_ID <hex>`). The host knows the same value once it has loaded the modules; this is how to know it
     * before that. The first 16 hex digits, upper case: the name the cheat files carry.
     */
    fun buildIdOf(pack: File): String? {
        val file = File(pack, "mods/main.fixups.tsv")
        if (!file.isFile) return null
        val line = runCatching {
            file.useLines { lines -> lines.firstOrNull { it.trimStart().startsWith("#") && it.contains("BUILD_ID") } }
        }.getOrNull() ?: return null
        val id = line.trim().split(Regex("\\s+")).lastOrNull() ?: return null
        return id.takeIf { it.length >= 16 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
            ?.take(16)?.uppercase()
    }

    /**
     * The Atmosphère text of the build's cheats, or null when the library holds no file for it.
     *
     * The section of the build is a map of cheat names to their text; the file is those texts one after another (each
     * one starts with its own [Name] header, so the file keeps the order the library lists them in).
     */
    fun pick(json: String, build: String): String? {
        val root = runCatching { OrderedJson.parse(json) }.getOrNull() ?: return null
        val want = build.trim().uppercase()
        // the library's credit is a member of its own, not a build
        val section = root.entries.firstOrNull { it.key.uppercase() == want && it.key.uppercase() != "ATTRIBUTION" }
            ?.value as? Map<*, *> ?: return null
        val out = StringBuilder()
        for ((_, body) in section) {
            val text = body as? String ?: continue
            if (text.isEmpty()) continue
            out.append(text)
            if (!text.endsWith("\n")) out.append('\n')
        }
        return out.toString().ifEmpty { null }
    }

    /**
     * The library's line of credit for the file: the first line of each file it names (the rest is the author's own
     * notes), or "" when it has none.
     */
    fun credit(json: String): String {
        val root = runCatching { OrderedJson.parse(json) }.getOrNull() ?: return ""
        val attribution = root["attribution"] as? Map<*, *> ?: return ""
        val lines = attribution.values.mapNotNull { it as? String }
            .map { it.trim().lines().firstOrNull()?.trim().orEmpty() }
            .filter { it.isNotEmpty() }
        return lines.joinToString(" · ")
    }

    /**
     * Downloads the file of the build into [dir]: the library's JSON, the section of the build, written out as the
     * file the host reads (with the library's line of credit beside it).
     *
     * Fails with [NoSuchBuild] when the library holds no file for the build (the game is not in the library, or it has
     * another build of it: a cheat file of another build would write the wrong places).
     */
    fun download(titleId: String, build: String, dir: File): Result<File> =
        downloadFrom(urls(titleId), build, dir)

    /** The same, from URLs of one's own (the test uses it with a local server) */
    internal fun downloadFrom(url: String, build: String, dir: File): Result<File> =
        downloadFrom(listOf(url), build, dir)

    /** The same, from a list of URLs (the edges of the CDN, one after another) */
    internal fun downloadFrom(urls: List<String>, build: String, dir: File): Result<File> = runCatching {
        val text = fetch(urls)
        val section = pick(text, build) ?: throw NoSuchBuild("the library has no file for this build")
        val line = credit(text)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("the folder could not be made")
        val file = file(dir, build)
        val tmp = File(file.path + ".part")
        tmp.writeText(section, Charsets.UTF_8)
        if (file.exists() && !file.delete()) throw IOException("the old file could not be replaced")
        if (!tmp.renameTo(file)) throw IOException("the file could not be put in place")
        // the credit is a note, not the file: a failure to write it must not fail the download
        runCatching { creditFile(dir, build).writeText(line, Charsets.UTF_8) }
        file
    }

    /** The library holds no file for this build (the game is not in it, or it has another build of it) */
    class NoSuchBuild(message: String) : Exception(message)

    /**
     * The library's answer: the first URL that answers. A network that fails is tried again on the next edge;
     * an answer that says "no file of this game" is final (it is not a network problem).
     */
    private fun fetch(urls: List<String>): String {
        var last: IOException = IOException("the library did not answer")
        for (u in urls) {
            try {
                return body(u)
            } catch (e: NoSuchBuild) {
                throw e
            } catch (e: IOException) {
                last = e
            }
        }
        throw last
    }

    /** The library's file (its JSON): the body of the answer */
    private fun body(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "nx2android")
        }
        try {
            val code = conn.responseCode
            // 404: the library has no file of this game at all (it is not in the library)
            if (code == HttpURLConnection.HTTP_NOT_FOUND) throw NoSuchBuild("the library has no file of this game")
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } finally {
            conn.disconnect()
        }
    }
}
