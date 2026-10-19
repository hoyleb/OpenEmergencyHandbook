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

class EmergencyHandbook(assets: AssetManager, val language: String) {
    private val root = assets.open("handbook/articles.json").bufferedReader().use { JSONObject(it.readText()) }
    val edition: String = root.getString("edition")
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

    fun search(query: String): List<EmergencyArticle> {
        val words = normalise(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return articles
        val exact = articles.filter { article ->
            val haystack = normalise("${article.title} ${article.summary} ${article.keywords} ${article.body}")
            words.all { haystack.contains(it) }
        }
        if (exact.isNotEmpty()) return exact
        // Only offer near matches when exact search found nothing. Never silently
        // substitute a different medical procedure for an exact result.
        return articles.filter { article ->
            val terms = normalise("${article.title} ${article.keywords}").split(Regex("[^\\p{L}\\p{N}]+"))
            words.all { word -> terms.any { term -> term.contains(word) || (word.length >= 4 && oneEditApart(word, term)) } }
        }
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
