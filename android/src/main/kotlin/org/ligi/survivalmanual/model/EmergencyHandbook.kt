package org.ligi.survivalmanual.model

import android.content.res.AssetManager
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

data class EmergencyArticle(
    val id: String,
    val group: String,
    val title: String,
    val summary: String,
    val keywords: String,
    val body: String,
    val sources: List<String>,
    val related: List<String>,
    val checkedAt: String
)

data class EmergencySearchResult(val articles: List<EmergencyArticle>, val approximate: Boolean)

class EmergencyHandbook(assets: AssetManager, val language: String) {
    private val root = assets.open("handbook/articles.json").bufferedReader().use { JSONObject(it.readText()) }
    val edition: String = root.getString("edition")
    private val aliases: Map<String, String> = root.getJSONArray("articles").let { list ->
        (0 until list.length()).associate { index ->
            val entry = list.getJSONObject(index)
            entry.getString("id") to listOf("en", "de").joinToString(" ") { lang ->
                val text = entry.getJSONObject(lang)
                "${text.getString("title")} ${text.getString("keywords")}"
            }
        }
    }
    val articles: List<EmergencyArticle> = root.getJSONArray("articles").let { list ->
        (0 until list.length()).map { index ->
            val entry = list.getJSONObject(index)
            val translation = entry.getJSONObject(language)
            val references = entry.getJSONArray("sources")
            EmergencyArticle(entry.getString("id"), entry.getString("group"),
                translation.getString("title"), translation.getString("summary"),
                translation.getString("keywords"), translation.getString("body"),
                (0 until references.length()).map { references.getString(it) },
                entry.optJSONArray("related")?.let { links -> (0 until links.length()).map { links.getString(it) } } ?: emptyList(),
                entry.getString("checked_at"))
        }
    }

    fun find(id: String) = articles.firstOrNull { it.id == id }

    fun search(query: String) = searchResult(query).articles

    fun searchResult(query: String): EmergencySearchResult {
        val words = normalise(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return EmergencySearchResult(articles, false)
        fun score(article: EmergencyArticle): Int {
            val title = normalise(article.title)
            val keywords = normalise(article.keywords + " " + aliases[article.id].orEmpty())
            return words.fold(0) { total, word -> total + when {
                title.contains(word) -> 100
                keywords.contains(word) -> 30
                normalise(article.summary).contains(word) -> 10
                else -> 1
            } } + if (title.contains(normalise(query))) 100 else 0
        }
        val exact = articles.filter { article ->
            val haystack = normalise("${article.title} ${article.summary} ${article.keywords} ${article.body} ${aliases[article.id].orEmpty()}")
            words.all { haystack.contains(it) }
        }
        if (exact.isNotEmpty()) return EmergencySearchResult(exact.sortedByDescending(::score), false)
        // Only offer near matches when exact search found nothing. Never silently
        // substitute a different medical procedure for an exact result.
        val approximate = articles.filter { article ->
            val terms = normalise("${article.title} ${article.keywords} ${aliases[article.id].orEmpty()}").split(Regex("[^\\p{L}\\p{N}]+"))
            words.all { word -> terms.any { term -> term.contains(word) || (word.length >= 4 && oneEditApart(word, term)) } }
        }
        return EmergencySearchResult(approximate.sortedByDescending(::score), approximate.isNotEmpty())
    }

    companion object {
        val helpNowIds = setOf("first-aid", "water", "power", "cyber", "earthquake", "fallout", "flood", "pandemic")

        private fun oneEditApart(a: String, b: String): Boolean {
            if (kotlin.math.abs(a.length - b.length) > 1) return false
            var i = 0; var j = 0; var edits = 0
            while (i < a.length && j < b.length) {
                if (a[i] == b[j]) { i++; j++; continue }
                if (++edits > 1) return false
                when {
                    a.length > b.length -> i++
                    b.length > a.length -> j++
                    else -> { i++; j++ }
                }
            }
            return edits + (a.length - i) + (b.length - j) <= 1
        }

        fun normalise(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT).replace("ß", "ss"), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
    }
}
