package com.nx.android.data

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * The cheat files: the community's, in the Atmosphère format.
 *
 * They are not ours, and the app does not fetch them: the player imports a zip of them into the app's own
 * folder (files/cheats/<title ID>/<build ID>.txt), the same way the game profiles are imported. One file per
 * build: the host reads the file named after the build it runs (the first 16 hex digits of main's build ID),
 * so a file of another build would write the wrong places and is never used.
 *
 * The zip that is imported can be laid out in any of the shapes the community uses, as long as the game's
 * title ID is a folder name in the path and the file is named after the build:
 *
 *     titles/<title ID>/cheats/<build ID>.txt      (the Atmosphère standard, EdiZon and Breeze)
 *     atmosphere/titles/<title ID>/cheats/<build>.txt
 *     cheats/<title ID>/<build ID>.txt              (the layout of our own cheat repository)
 *
 * The resolution patches are skipped: the app has its own render scale (the drawer), and a community patch
 * of the same setting would fight it. A file that holds nothing else is not written.
 */
object CheatRepo {

    /** One cheat entry: its name (without the brackets) and its text (the block it belongs to). */
    data class Entry(val name: String, val text: String)

    /**
     * The entries we skip: the resolution patches. The app has its own render scale (the drawer), and the two
     * change the same thing in the game - with both on, the result is not the one the player expects.
     */
    private val RESOLUTION = Regex("""\b\d{3,4}\s*p\b|downscale|upscale|resolution""", RegexOption.IGNORE_CASE)

    /** A title ID: 16 hex digits (the folders the libraries name the games by) */
    private val TITLE_ID = Regex("""^[0-9A-Fa-f]{16}$""")

    /** A build ID: 16 hex digits (the files are named by it) */
    private val BUILD_ID = Regex("""^[0-9A-Fa-f]{16}$""")

    /** The folder the app keeps the cheats of one game in (app-private: a rebuilt data pack does not touch it) */
    fun dir(filesDir: File, titleId: String): File = File(filesDir, "cheats/$titleId")

    /** The cheat file of one build (the name the host looks for) */
    fun file(dir: File, build: String): File = File(dir, "${build.uppercase()}.txt")

    /** The line of credit beside the file (what the cheat's author says about it) */
    fun creditFile(dir: File, build: String): File = File(dir, "${build.uppercase()}.credit")

    /** The folder the app keeps all the imported cheats in */
    fun root(filesDir: File): File = File(filesDir, "cheats")

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
     * The entries of an Atmosphère file: the blocks that start with a [Name] or {Name} header. The text before
     * the first header is prose (the host ignores it, so it is dropped here too).
     *
     * The header is read the way the host reads it (the line starts with [ or {, the name is what lies before
     * the first closing bracket), and a block runs from its header to the next one: the text of the file is
     * kept as it is, so what is written back is the community's file byte for byte.
     */
    fun fileEntries(text: String): List<Entry> {
        val out = ArrayList<Entry>()
        var name: String? = null
        var start = 0
        var offset = 0
        for (line in text.split("\n")) {
            val header = headerOf(line)
            if (header != null) {
                if (name != null) out.add(Entry(name!!, text.substring(start, offset)))
                name = header
                start = offset
            }
            offset += line.length + 1
        }
        if (name != null) out.add(Entry(name!!, text.substring(start)))
        return out
    }

    /** Is this entry a resolution patch (the ones we skip: the app's own render scale does that)? */
    fun isResolution(name: String): Boolean = RESOLUTION.containsMatchIn(name)

    /** What an import brought in. */
    data class Import(
        /** The games it brought files for: the title ID and the number of files */
        val games: Map<String, Int>,
        /** The files it wrote */
        val files: Int,
        /** The files it left out (only resolution patches, or nothing usable in them) */
        val skipped: Int,
        /** The files whose game it could not tell (no title ID in the path) */
        val unknown: Int,
    ) {
        /** The number of games it brought files for */
        val gameCount: Int get() = games.size
    }

    /**
     * Imports a zip of cheat files into the app's own folder (files/cheats/<title ID>/<build ID>.txt).
     *
     * The files of the zip are matched by their names: the file is named after the build (16 hex digits) and
     * the game's title ID is a folder name in the path. The resolution patches are left out, and a file that
     * holds nothing else is not written at all.
     *
     * The files that are already there are replaced by the new ones; the files of other games are left alone
     * (the player can import another game's pack later).
     */
    fun importZip(zip: File, filesDir: File): Import {
        if (!zip.isFile) throw IOException("the file is not there")
        val found = LinkedHashMap<String, String>()      // "<title ID>/<build ID>" -> the file's text
        val credits = LinkedHashMap<String, String>()    // the same key -> the line of credit
        var skipped = 0
        var unknown = 0
        ZipFile(zip).use { z ->
            val entries = z.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                val path = e.name
                if (path.startsWith("/") || path.split('/').any { it == ".." }) continue
                val base = path.substringAfterLast('/')
                if (base.endsWith(".credit")) {
                    val build = base.removeSuffix(".credit")
                    val title = titleIdOf(path) ?: continue
                    if (!BUILD_ID.matches(build)) continue
                    val text = z.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) }.trim()
                    if (text.isNotEmpty()) credits["$title/$build"] = text
                    continue
                }
                if (!base.endsWith(".txt")) continue
                val build = base.removeSuffix(".txt")
                if (!BUILD_ID.matches(build)) continue
                val title = titleIdOf(path)
                if (title == null) { unknown++; continue }
                val text = z.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) }
                // 只有分辨率类的条目：这条不管用，不写
                val kept = fileEntries(text).filterNot { isResolution(it.name) }
                if (kept.isEmpty()) { skipped++; continue }
                found["$title/${build.uppercase()}"] = kept.joinToString("") { it.text }
            }
        }
        val games = LinkedHashMap<String, Int>()
        for ((key, text) in found) {
            val (title, build) = key.split('/')
            val dir = dir(filesDir, title)
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("the folder could not be made")
            val out = file(dir, build)
            out.writeText(text, Charsets.UTF_8)
            credits[key]?.let { runCatching { creditFile(dir, build).writeText(it, Charsets.UTF_8) } }
            games[title] = (games[title] ?: 0) + 1
        }
        return Import(games, found.size, skipped, unknown)
    }

    /** The game's title ID in a zip entry's path: the last folder name that is 16 digits. */
    internal fun titleIdOf(path: String): String? {
        val parts = path.split('/')
        // the last part is the file's own name; the title ID is a folder above it
        for (i in parts.size - 2 downTo 0) {
            if (i >= 0 && TITLE_ID.matches(parts[i])) return parts[i].uppercase()
        }
        return null
    }

    /** The name of an entry's header ([Name] / {Name}), or null when the line is not a header. */
    private fun headerOf(line: String): String? {
        val trimmed = line.trimStart()
        if (trimmed.length < 3) return null
        val c = trimmed[0]
        if (c != '[' && c != '{') return null
        val end = if (c == '[') ']' else '}'
        val close = trimmed.indexOf(end, 1)
        if (close < 2) return null
        return trimmed.substring(1, close)
    }
}
