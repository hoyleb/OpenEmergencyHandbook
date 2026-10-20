package org.ligi.survivalmanual.model

import android.content.res.AssetManager
import org.json.JSONObject
import org.json.JSONArray
import java.security.MessageDigest
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

class EmergencyHandbook(private val source: (String) -> String, val language: String) {
    constructor(assets: AssetManager, language: String) : this({ path -> assets.open(path).bufferedReader().use { it.readText() } }, language)
    private val loaded = loadContent(source)
    private val root = loaded.root
    val recoveryMode = loaded.recovery
    val missingGuideCount = loaded.missing
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

    private data class Loaded(val root: JSONObject, val recovery: Boolean, val missing: Int)

    companion object {
        val helpNowIds = setOf("first-aid", "water", "power", "cyber", "earthquake", "fallout", "flood", "pandemic")

        private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private fun validateArticle(entry: JSONObject) {
            require(entry.getString("id").matches(Regex("[a-z0-9-]{1,80}")))
            require(entry.getString("group") in setOf("urgent", "disaster", "essentials", "about"))
            for (lang in listOf("en", "de")) for (field in listOf("title", "summary", "keywords", "body"))
                require(entry.getJSONObject(lang).getString(field).isNotBlank())
            entry.getJSONArray("sources"); entry.getString("checked_at")
        }
        private fun validateRoot(root: JSONObject) {
            require(root.getInt("schema_version") == 1 && root.getString("edition").matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
            val list = root.getJSONArray("articles")
            require(list.length() in 1..500)
            val ids = mutableSetOf<String>()
            for (i in 0 until list.length()) {
                val entry = list.getJSONObject(i); validateArticle(entry); require(ids.add(entry.getString("id")))
            }
            require(ids.containsAll(helpNowIds))
        }
        private fun loadContent(read: (String) -> String): Loaded {
            val index = try { JSONObject(read("handbook/recovery/index.json")) } catch (_: Exception) { null }
            try {
                val text = read("handbook/articles.json")
                val root = JSONObject(text); validateRoot(root)
                val expected = index?.optString("primary_sha256").orEmpty()
                if (expected.matches(Regex("[a-f0-9]{64}"))) require(digest(text) == expected)
                return Loaded(root, false, 0)
            } catch (_: Exception) { /* Try independent copies, never invent instructions. */ }
            val recovered = JSONArray()
            var expectedCount = 0
            var edition = "unavailable"
            try {
                require(index != null && index.getInt("schema_version") == 1)
                edition = index.getString("edition")
                require(edition.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
                val entries = index.getJSONArray("articles")
                require(entries.length() in 1..500); expectedCount = entries.length()
                val seen = mutableSetOf<String>()
                for (i in 0 until entries.length()) try {
                    val part = entries.getJSONObject(i)
                    val id = part.getString("id")
                    require(id.matches(Regex("[a-z0-9-]{1,80}")) && seen.add(id))
                    val text = read("handbook/recovery/$id.json")
                    require(part.getString("sha256").matches(Regex("[a-f0-9]{64}")) && digest(text) == part.getString("sha256"))
                    val article = JSONObject(text); validateArticle(article); require(article.getString("id") == id)
                    recovered.put(article)
                } catch (_: Exception) { /* Other undamaged guide files stay readable. */ }
            } catch (_: Exception) { }
            val ids = (0 until recovered.length()).map { recovered.getJSONObject(it).getString("id") }.toSet()
            for (i in 0 until recovered.length()) {
                val article = recovered.getJSONObject(i)
                val links = article.optJSONArray("related") ?: JSONArray()
                article.put("related", JSONArray((0 until links.length()).map { links.optString(it) }.filter { it in ids }))
            }
            return Loaded(JSONObject().put("edition", edition).put("articles", recovered), true, (expectedCount - recovered.length()).coerceAtLeast(0))
        }

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
