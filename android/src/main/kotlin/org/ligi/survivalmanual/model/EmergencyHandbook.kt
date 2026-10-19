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
    val sources: List<String>
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
                (0 until references.length()).map { references.getString(it) })
        }
    }

    fun find(id: String) = articles.firstOrNull { it.id == id }

    fun search(query: String): List<EmergencyArticle> {
        val words = normalise(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return articles
        return articles.filter { article ->
            val haystack = normalise("${article.title} ${article.summary} ${article.keywords} ${article.body}")
            words.all { haystack.contains(it) }
        }
    }

    companion object {
        fun normalise(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT).replace("ß", "ss"), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
    }
}
