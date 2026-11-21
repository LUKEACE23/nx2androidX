package com.nx.android.agent.tools

import android.content.Context
import com.nx.android.data.AppLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray

/**
 * The FAQ the assistant answers from: the same JSON files the FAQ screen shows, ranked by keyword.
 *
 * The search is deliberately plain — words and Chinese character pairs, counted where they appear (a hit in
 * the question is worth three in the answer). The core is pure — JSON text plus a query in, hits out, hits to
 * text — so the JVM tests drive it without Android. Reading the asset is the thin wrapper on top: it picks the
 * file by the app's language and yields an empty index when the file cannot be read, so a missing or broken
 * FAQ never throws.
 */
@Singleton
class FaqIndex @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locale: AppLocale,
) {

    /** One entry the assistant can answer with: the FAQ's own text and the score that ranked it */
    data class Hit(val group: String, val question: String, val answer: String, val score: Int)

    /** The answer to the player's question: the top entries, as text for the model */
    fun ask(query: String): String = render(search(load(), query))

    /** The FAQ file of the app's current language ('' when it cannot be read, which searches to nothing) */
    private fun load(): String = runCatching {
        context.assets.open(fileFor(locale.mode)).bufferedReader().use { it.readText() }
    }.getOrDefault("")

    companion object {
        const val MAX_RESULTS = 5
        const val MAX_ANSWER_CHARS = 200

        /** The FAQ file of a language mode: the picked one, or the phone's own language when it follows the system */
        fun fileFor(mode: AppLocale.Mode): String = when (mode) {
            AppLocale.Mode.ZH -> "faq/faq_zh.json"
            AppLocale.Mode.EN -> "faq/faq_en.json"
            AppLocale.Mode.SYSTEM ->
                if (Locale.getDefault().language == "zh") "faq/faq_zh.json" else "faq/faq_en.json"
        }

        /**
         * The pure core: the FAQ's JSON text and a query, in; the best entries, out.
         *
         * An entry scores one point per keyword occurrence in its answer and three per occurrence in its
         * question; entries that score nothing are left out, the rest come best-first (ties keep the file's
         * own order), at most [limit] of them. Text that is not the FAQ's shape gives an empty list, not an
         * exception.
         */
        fun search(json: String, query: String, limit: Int = MAX_RESULTS): List<Hit> {
            val keywords = keywordsOf(query)
            if (keywords.isEmpty()) return emptyList()
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
            val hits = ArrayList<Hit>()
            for (i in 0 until array.length()) {
                val group = array.optJSONObject(i) ?: continue
                val items = group.optJSONArray("items") ?: continue
                val title = group.optString("title").trim()
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    val q = item.optString("q").trim()
                    val a = item.optString("a").trim()
                    // an entry missing either side is not an entry: skip it, same as the FAQ screen does
                    if (q.isEmpty() || a.isEmpty()) continue
                    val score = score(q, a, keywords)
                    if (score > 0) hits.add(Hit(title, q, a, score))
                }
            }
            return hits.sortedByDescending { it.score }.take(limit)
        }

        /**
         * The hits as text for the model: how many came back, then each question with its answer under the
         * answer budget — an answer past it is cut and says so.
         */
        fun render(hits: List<Hit>, maxAnswer: Int = MAX_ANSWER_CHARS): String {
            if (hits.isEmpty()) return "没有找到相关条目。"
            val out = StringBuilder("命中 ${hits.size} 条：\n")
            for ((i, hit) in hits.withIndex()) {
                out.append(i + 1).append(". ")
                if (hit.group.isNotEmpty()) out.append("「").append(hit.group).append("」")
                out.append(hit.question).append('\n')
                out.append("答：")
                out.append(if (hit.answer.length > maxAnswer) hit.answer.take(maxAnswer) + "（截断）" else hit.answer)
                out.append('\n')
            }
            return out.toString().trim()
        }

        // ---- the scoring ----

        /**
         * The query's keywords: its words, and for Chinese — which has no spaces to split on — the character
         * pairs of its runs. A question that shares a phrase with the query shares its pairs; a single
         * Chinese character stands alone. Latin words of one character are dropped: they match too much.
         */
        private fun keywordsOf(query: String): List<String> {
            val out = LinkedHashSet<String>()
            for (token in query.split(Regex("[^0-9A-Za-z\u4e00-\u9fff]+"))) {
                val word = token.lowercase()
                if (word.isEmpty()) continue
                val cjk = word.any { it.code in 0x4e00..0x9fff }
                if (cjk) {
                    if (word.length == 1) out.add(word)
                    else for (k in 0 until word.length - 1) out.add(word.substring(k, k + 2))
                } else if (word.length >= 2) {
                    out.add(word)
                }
            }
            return out.toList()
        }

        /** A hit in the question is worth three in the answer: the question is what the entry is about */
        /**
         * An entry scores three per keyword occurrence in its question and one per keyword its answer merely
         * contains. The answer counts once per keyword on purpose: a long answer that repeats a word would
         * otherwise outrank the entry whose question is the match (the tests catch exactly that).
         */
        private fun score(question: String, answer: String, keywords: List<String>): Int {
            val q = question.lowercase()
            val a = answer.lowercase()
            var total = 0
            for (k in keywords) total += 3 * occurrences(q, k) + if (k in a) 1 else 0
            return total
        }

        private fun occurrences(text: String, keyword: String): Int {
            var count = 0
            var at = text.indexOf(keyword)
            while (at >= 0) {
                count++
                at = text.indexOf(keyword, at + keyword.length)
            }
            return count
        }
    }
}
