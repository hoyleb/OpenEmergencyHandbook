package org.ligi.survivalmanual.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.ligi.survivalmanual.BuildConfig
import org.ligi.survivalmanual.functions.convertMarkdownToHtml
import org.ligi.survivalmanual.model.EmergencyArticle
import org.ligi.survivalmanual.model.EmergencyHandbook
import org.ligi.survivalmanual.model.HouseholdPlan

/** Offline dashboard and reader. No network, location or account permissions. */
class EmergencyActivity : BaseActivity() {
    private val preferences by lazy { getSharedPreferences("emergency-handbook", MODE_PRIVATE) }
    private var language = "en"
    private var route = "home"
    private var query = ""
    private var articleScroll = 0
    private var category = "now"
    private var readerScale = 1f
    private lateinit var handbook: EmergencyHandbook
    private lateinit var page: LinearLayout
    private lateinit var scroll: ScrollView
    private var pageBackground = 0
    private var textColour = 0
    private var muted = 0
    private var surface = 0
    private var accent = 0
    private var pendingExport: String? = null
    private val exportPlan = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val snapshot = pendingExport
        pendingExport = null
        if (uri != null && snapshot != null) {
            try {
                val output = contentResolver.openOutputStream(uri) ?: throw java.io.IOException("No output stream")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
                Toast.makeText(this, tr("Plan saved. Keep a printed copy too.", "Plan gespeichert. Auch auf Papier aufbewahren."), Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this, tr("Could not save. Try another local folder.", "Speichern fehlgeschlagen. Anderen lokalen Ordner versuchen."), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        language = savedInstanceState?.getString("language") ?: preferences.getString("language", "en") ?: "en"
        route = savedInstanceState?.getString("route") ?: "home"
        query = savedInstanceState?.getString("query") ?: ""
        articleScroll = savedInstanceState?.getInt("scroll") ?: 0
        category = savedInstanceState?.getString("category") ?: "now"
        readerScale = preferences.getFloat("reader-scale", 1f).coerceIn(1f, 1.8f)
        pendingExport = savedInstanceState?.getString("pending-export")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        pageBackground = Color.parseColor(if (dark) "#101E22" else "#F2F6F4")
        textColour = Color.parseColor(if (dark) "#F1F8F5" else "#152D2A")
        muted = Color.parseColor(if (dark) "#B7CCC5" else "#47635B")
        surface = Color.parseColor(if (dark) "#1B3034" else "#FFFFFF")
        accent = Color.parseColor(if (dark) "#8BE1CA" else "#086850")
        handbook = EmergencyHandbook(assets, language)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (route != "home") { query = ""; navigate("home") }
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("language", language)
        outState.putString("route", route)
        outState.putString("query", query)
        outState.putInt("scroll", scroll.scrollY)
        outState.putString("category", category)
        outState.putString("pending-export", pendingExport)
        super.onSaveInstanceState(outState)
    }

    private fun tr(en: String, de: String) = if (language == "de") de else en
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(colour: Int, radius: Int = 18) = GradientDrawable().apply {
        setColor(colour); cornerRadius = dp(radius).toFloat()
    }

    private fun text(value: String, size: Float = 17f, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(textColour)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(8), 0, dp(8))
        setLineSpacing(dp(3).toFloat(), 1f)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f; minHeight = dp(52)
        setTextColor(accent); setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
    }

    private fun navigate(destination: String) {
        route = destination; articleScroll = 0; render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(pageBackground)
            // Selectable article text must not steal initial focus and scroll
            // past the action summary when an older Android recreates the view.
            isFocusableInTouchMode = true; requestFocus()
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(4))
        }
        val home = button(tr("Home", "Start")) { query = ""; navigate("home") }
        home.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        bar.addView(home)
        val plan = button("Plan") { navigate("plan") }.apply {
            contentDescription = tr("Open household plan", "Haushaltsplan öffnen")
        }
        plan.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        bar.addView(plan)
        val lang = button(if (language == "en") "Deutsch" else "English") {
            articleScroll = scroll.scrollY
            language = if (language == "en") "de" else "en"
            preferences.edit().putString("language", language).apply()
            handbook = EmergencyHandbook(assets, language); render()
        }
        lang.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        bar.addView(lang); root.addView(bar)
        scroll = ScrollView(this).apply { isFillViewport = true }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), dp(32))
        }
        scroll.addView(page); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
        // Android 16's controller needs a real DecorView, created by setContentView.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            val light = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
            isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light
        }
        ViewCompat.requestApplyInsets(root)
        when (route) {
            "home" -> dashboard()
            "plan" -> household()
            else -> handbook.find(route)?.let { read(it) } ?: run { route = "home"; dashboard() }
        }
        val restoreY = articleScroll
        scroll.post { scroll.scrollTo(0, restoreY) }
    }

    private fun dashboard() {
        page.addView(text("OPEN EMERGENCY", 13f, true).apply { setTextColor(accent) })
        page.addView(text(tr("Ready when the\nnetwork isn’t.", "Bereit, auch\nohne Netz."), 32f, true))
        page.addView(text(tr("Free • Open source • All guides stored on this phone", "Kostenlos • Quelloffen • Alle Anleitungen auf diesem Handy"), 14f).apply { setTextColor(muted) })
        val notice = text(tr("DEVELOPMENT PREVIEW\nTest the app. Content and translations have not been professionally reviewed; coverage is incomplete. Follow emergency services and current official instructions.", "ENTWICKLUNGSVERSION\nZum Testen. Inhalte und Übersetzungen sind noch nicht fachlich geprüft; die Abdeckung ist unvollständig. Rettungsdiensten und aktuellen amtlichen Anweisungen folgen."), 14f).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12)); background = shape(surface, 12)
        }
        page.addView(notice)
        page.addView(button(tr("112 · Open emergency dialler", "112 · Notruf-Wählfeld öffnen")) { dialEmergency() })
        page.addView(text(tr("Help now", "Jetzt Hilfe finden"), 23f, true))
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val choices = listOf("now" to tr("Help now", "Soforthilfe"), "aid" to tr("First aid", "Erste Hilfe"), "prepare" to tr("Prepare", "Vorsorge"), "all" to tr("All guides", "Alle Anleitungen"))
        choices.forEach { (key, label) ->
            filters.addView(button(if (category == key) "✓ $label" else label) {
                category = key; query = ""; articleScroll = scroll.scrollY; render()
            }.apply {
                contentDescription = when (key) {
                    "aid" -> tr("Show first-aid guides", "Erste-Hilfe-Anleitungen anzeigen")
                    "prepare" -> tr("Show preparation guides", "Vorsorge-Anleitungen anzeigen")
                    "all" -> tr("Show all guides", "Alle Anleitungen anzeigen")
                    else -> tr("Show immediate help", "Soforthilfe anzeigen")
                }
                layoutParams = LinearLayout.LayoutParams(-2, -2)
            })
        }
        page.addView(HorizontalScrollView(this).apply { addView(filters) })
        val search = EditText(this).apply {
            id = View.generateViewId(); hint = tr("Search: water, blackout, bleeding…", "Suchen: Wasser, Stromausfall, Blutung…")
            textSize = 16f; setTextColor(textColour); setHintTextColor(muted); setSingleLine(true)
            setPadding(dp(14), dp(10), dp(14), dp(10)); background = shape(surface, 12)
            contentDescription = tr("Search all offline guides", "Alle Offline-Anleitungen durchsuchen")
            setText(query)
        }
        page.addView(search, LinearLayout.LayoutParams(-1, -2).apply { search.minHeight = dp(58) })
        val count = text("", 14f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        page.addView(count)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(results)
        fun updateResults() {
            results.removeAllViews()
            val matches = if (query.isNotBlank()) handbook.search(query) else handbook.articles.filter {
                when (category) {
                    "now" -> it.id in EmergencyHandbook.helpNowIds
                    "aid" -> it.group == "urgent"
                    "prepare" -> it.group == "essentials"
                    else -> true
                }
            }
            count.text = tr("${matches.size} guides · search checks the whole handbook", "${matches.size} Anleitungen · Suche im gesamten Handbuch")
            if (matches.isEmpty()) results.addView(text(tr("No matches. Try a simpler word or another language.", "Keine Treffer. Ein einfacheres Wort oder die andere Sprache versuchen.")))
            matches.forEach { article -> results.addView(card(article)) }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); updateResults() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateResults()
        page.addView(button(tr("My household · checklist & notes", "Mein Haushalt · Checkliste & Notizen")) { navigate("plan") })
        page.addView(text(tr("Edition ${handbook.edition} · ${BuildConfig.VERSION_NAME}\nNo live alerts or verified shelter map. External source links need internet. 112 is the emergency number in Germany/EU.", "Ausgabe ${handbook.edition} · ${BuildConfig.VERSION_NAME}\nKeine Live-Warnungen oder geprüfte Schutzraumkarte. Quellenlinks benötigen Internet. 112 ist der Notruf in Deutschland/EU."), 12f).apply { setTextColor(muted) })
    }

    private fun card(article: EmergencyArticle): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10))
            background = shape(surface); isClickable = true; isFocusable = true
            contentDescription = "${article.title}. ${article.summary}"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
            setOnClickListener { navigate(article.id) }
        }
        card.addView(text(article.title, 20f, true).apply { setTextColor(accent) })
        card.addView(text(article.summary, 14f).apply { setTextColor(muted) })
        return card
    }

    private fun read(article: EmergencyArticle) {
        page.addView(text(article.title, 30f, true))
        page.addView(text(tr("OFFLINE GUIDE · UNREVIEWED PREVIEW", "OFFLINE-ANLEITUNG · UNGEPRÜFTER ENTWURF"), 12f, true).apply { setTextColor(accent) })
        val sizeControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(-0.2f to tr("A− Smaller", "A− Kleiner"), 0.2f to tr("A+ Larger", "A+ Größer")).forEach { (delta, label) ->
            sizeControls.addView(button(label) {
                readerScale = (readerScale + delta).coerceIn(1f, 1.8f)
                preferences.edit().putFloat("reader-scale", readerScale).apply()
                articleScroll = scroll.scrollY; render()
            }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        }
        page.addView(sizeControls)
        page.addView(text(article.summary, 18f * readerScale))
        page.addView(text("").apply {
            text = HtmlCompat.fromHtml(convertMarkdownToHtml(article.body), HtmlCompat.FROM_HTML_MODE_LEGACY)
            textSize = 18f * readerScale; setTextIsSelectable(true); movementMethod = LinkMovementMethod.getInstance()
            contentDescription = null
        })
        if (article.related.isNotEmpty()) {
            page.addView(text(tr("Related offline guides", "Weitere Offline-Anleitungen"), 21f, true))
            article.related.mapNotNull { handbook.find(it) }.forEach { link ->
                page.addView(button(link.title) { navigate(link.id) })
            }
        }
        page.addView(text(tr("Sources & limitations", "Quellen & Grenzen"), 21f, true))
        page.addView(text(tr("Original summary and translation. Sources checked ${article.checkedAt}; no professional review recorded. Source links open outside this app and require internet. This app is not affiliated with the cited organisations.", "Eigene Zusammenfassung und Übersetzung. Quellen geprüft am ${article.checkedAt}; keine fachliche Prüfung dokumentiert. Quellenlinks öffnen außerhalb der App und benötigen Internet. Keine Verbindung zu den genannten Organisationen."), 13f))
        article.sources.forEachIndexed { index, url ->
            page.addView(button(tr("Source ${index + 1} · ${Uri.parse(url).host}", "Quelle ${index + 1} · ${Uri.parse(url).host}")) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                catch (_: ActivityNotFoundException) { Toast.makeText(this, tr("No browser installed", "Kein Browser installiert"), Toast.LENGTH_SHORT).show() }
            })
        }
        page.addView(button(tr("Back to all guides", "Zurück zu allen Anleitungen")) { query = ""; navigate("home") })
    }

    private fun household() {
        page.addView(text(tr("My household", "Mein Haushalt"), 30f, true))
        page.addView(text(tr("Saved only on this device. Do not rely on a phone as your only copy.", "Nur auf diesem Gerät gespeichert. Wichtige Angaben zusätzlich auf Papier aufbewahren."), 15f))
        val items = listOf(
            "water" to tr("Stored drinking water & clean containers", "Trinkwasservorrat & saubere Behälter"),
            "food" to tr("Shelf-stable food & manual tin opener", "Haltbare Lebensmittel & Hand-Dosenöffner"),
            "medicine" to tr("Medication plan, first-aid kit & prescriptions", "Medikamentenplan, Verbandkasten & Rezepte"),
            "light" to tr("Torch, batteries, charged power bank", "Taschenlampe, Batterien, geladene Powerbank"),
            "radio" to tr("Battery radio & written emergency contacts", "Batterieradio & Notfallkontakte auf Papier"),
            "warmth" to tr("Warm layers, blankets & safe shelter", "Warme Kleidung, Decken & sicherer Schutz"),
            "hygiene" to tr("Hygiene, waste bags & emergency toilet plan", "Hygiene, Abfallbeutel & Nottoilettenplan"),
            "documents" to tr("Documents, cash, meeting point & evacuation bag", "Dokumente, Bargeld, Treffpunkt & Notgepäck")
        )
        items.forEach { (key, label) ->
            page.addView(CheckBox(this).apply {
                text = label; textSize = 17f; setTextColor(textColour); minHeight = dp(56)
                isChecked = preferences.getBoolean("check-$key", false)
                setOnCheckedChangeListener { _, checked -> preferences.edit().putBoolean("check-$key", checked).apply() }
            })
        }
        page.addView(text(tr("Drinking-water starting estimate", "Ausgangspunkt für den Trinkwasservorrat"), 21f, true))
        page.addView(text(tr("BBK starting point: 2 L per person per day, including 0.5 L for cooking. Add water for heat, exertion, health needs, hygiene and pets. Build toward 10 days; even 3 days helps. This is not a guarantee of sufficient total water.", "BBK-Ausgangspunkt: 2 L pro Person und Tag, davon 0,5 L zum Kochen. Zusätzlich Wasser für Hitze, Anstrengung, Gesundheit, Hygiene und Tiere einplanen. Möglichst 10 Tage anstreben; schon 3 Tage helfen. Keine Garantie für den gesamten Wasserbedarf."), 15f))
        val people = numberInput(tr("People (1–50)", "Personen (1–50)"), "people", "1")
        val days = numberInput(tr("Days (1–90)", "Tage (1–90)"), "days", "3")
        val result = text("", 22f, true)
        fun calculate() {
            val p = people.text.toString().toIntOrNull(); val d = days.text.toString().toIntOrNull()
            result.text = if (p == null || d == null || p !in 1..50 || d !in 1..90)
                tr("Enter valid people and days.", "Gültige Personen- und Tageszahl eingeben.")
            else tr("Starting drinking/cooking supply: ${p * d * 2} L", "Trinken/Kochen als Ausgangspunkt: ${p * d * 2} L")
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { calculate() }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        people.addTextChangedListener(watcher); days.addTextChangedListener(watcher); page.addView(result); calculate()
        page.addView(text(tr("Contacts, meeting point & personal needs", "Kontakte, Treffpunkt & persönliche Bedürfnisse"), 21f, true))
        page.addView(EditText(this).apply {
            id = View.generateViewId(); setTextColor(textColour); setHintTextColor(muted)
            hint = tr("Emergency contact, medicines, meeting point…", "Notfallkontakt, Medikamente, Treffpunkt…")
            minLines = 5; gravity = android.view.Gravity.TOP
            setText(preferences.getString("notes", ""))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { preferences.edit().putString("notes", s.toString()).apply() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        })
        page.addView(button(tr("Export household plan…", "Haushaltsplan exportieren…")) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(tr("This creates an unencrypted text file containing your checklist and personal notes. Choose a local folder for offline storage; cloud providers may upload it. Keep exported copies private.", "Erstellt eine unverschlüsselte Textdatei mit Checkliste und persönlichen Notizen. Für Offline-Speicherung einen lokalen Ordner wählen; Cloud-Anbieter können die Datei hochladen. Kopien vertraulich aufbewahren."))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(tr("Choose file", "Datei wählen")) { _, _ ->
                    pendingExport = HouseholdPlan.export(preferences, language, handbook.edition)
                    try { exportPlan.launch("OpenEmergency-Household.txt") }
                    catch (_: ActivityNotFoundException) {
                        pendingExport = null
                        Toast.makeText(this, tr("No file picker available", "Keine Dateiauswahl verfügbar"), Toast.LENGTH_LONG).show()
                    }
                }.show()
        })
        page.addView(button(tr("Clear household data…", "Haushaltsdaten löschen…")) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(tr("Clear your checklist, estimate and personal notes? This cannot be undone.", "Checkliste, Berechnung und persönliche Notizen löschen? Dies kann nicht rückgängig gemacht werden."))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    HouseholdPlan.clear(preferences); articleScroll = 0; render()
                }.show()
        })
    }

    private fun numberInput(label: String, key: String, fallback: String): EditText {
        page.addView(text(label, 15f, true))
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(textColour)
            contentDescription = label; setText(preferences.getString(key, fallback)); minHeight = dp(52)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { preferences.edit().putString(key, s.toString()).apply() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        page.addView(input); return input
    }

    private fun dialEmergency() {
        try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, tr("No dialler available. Ask someone to call 112.", "Kein Wählfeld verfügbar. Jemanden bitten, 112 anzurufen."), Toast.LENGTH_LONG).show() }
    }
}
