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
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import org.ligi.survivalmanual.BuildConfig
import org.ligi.survivalmanual.functions.convertMarkdownToHtml
import org.ligi.survivalmanual.model.EmergencyArticle
import org.ligi.survivalmanual.model.EmergencyHandbook
import org.ligi.survivalmanual.model.HouseholdPlan
import org.ligi.survivalmanual.model.HouseholdBackup
import org.ligi.survivalmanual.model.HouseholdTransfer
import org.ligi.survivalmanual.model.EncryptedHouseholdBackup
import org.ligi.survivalmanual.model.TransferState
import org.ligi.survivalmanual.model.safeString
import org.ligi.survivalmanual.model.safeBoolean
import org.ligi.survivalmanual.model.safeFloat
import org.ligi.survivalmanual.model.safeStrings
import java.util.Locale

/** Entire reading, search, navigation and household flow works offline. */
class EmergencyActivity : BaseActivity() {
    private val preferences by lazy { getSharedPreferences("emergency-handbook", MODE_PRIVATE) }
    private var language = "en"
    private var route = "home"
    private var query = ""
    private var category = "all"
    private var articleScroll = 0
    private var activeTab = "home"
    private var readerScale = 1f
    private var keepReadingOn = false
    private lateinit var handbook: EmergencyHandbook
    private lateinit var page: LinearLayout
    private lateinit var scroll: ScrollView
    private var pageBackground = 0
    private var textColour = 0
    private var muted = 0
    private var surface = 0
    private var accent = 0
    private val history = mutableListOf<Place>()
    private val tabPlaces = mutableMapOf<String, Place>()
    private val roots = setOf("home", "search", "saved", "plan")
    private val readerSections = linkedMapOf<String, View>()
    private var pendingExport: String? = null
    private var feedback: Toast? = null
    private val transfer by lazy { ViewModelProvider(this).get(HouseholdTransfer::class.java) }
    private var transferStatus: TextView? = null
    private var restoreDialog: androidx.appcompat.app.AlertDialog? = null
    private var passwordDialog: androidx.appcompat.app.AlertDialog? = null
    private var passwordFields = emptyList<EditText>()

    private data class Place(val route: String, val query: String, val category: String, val scroll: Int, val tab: String) {
        fun json() = JSONObject().put("route", route).put("query", query).put("category", category).put("scroll", scroll).put("tab", tab)
        companion object {
            fun from(data: JSONObject) = Place(data.getString("route"), data.optString("query"), data.optString("category", "all"), data.optInt("scroll"), data.optString("tab", "home"))
        }
    }

    private val exportPlan = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val snapshot = pendingExport; pendingExport = null
        if (uri != null) {
            if (snapshot != null) transfer.write(applicationContext.contentResolver, uri, snapshot)
            else transferResult(tr("Export interrupted. The selected file may be empty; save the plan again.", "Export unterbrochen. Gewählte Datei eventuell leer; Plan erneut speichern."))
        }
    }
    private val backupPlan = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val data = transfer.pendingBackup; transfer.pendingBackup = null
        if (uri != null) {
            if (data != null) transfer.write(applicationContext.contentResolver, uri, data)
            else transferResult(tr("Backup interrupted. The selected file may be empty; save a new backup.", "Sicherung unterbrochen. Gewählte Datei eventuell leer; Sicherung erneut speichern."))
        }
    }
    private val encryptedBackupPlan = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val data = transfer.pendingBackup; transfer.pendingBackup = null
        if (uri != null) {
            if (data != null) transfer.protect(uri, data)
            else transferResult(tr("Backup interrupted. The selected file may be empty; save a new backup.", "Sicherung unterbrochen. Gewählte Datei eventuell leer; Sicherung erneut speichern."))
        }
    }
    private val importPlan = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) transfer.read(applicationContext.contentResolver, uri, handbook.articles.map { it.id }.toSet().takeIf { it.isNotEmpty() })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceLanguage = if (Locale.getDefault().language == "de") "de" else "en"
        language = savedInstanceState?.getString("language") ?: preferences.safeString("language", deviceLanguage)
        if (language !in setOf("en", "de")) language = "en"
        readerScale = preferences.safeFloat("reader-scale", 1f).coerceIn(1f, 1.8f)
        keepReadingOn = savedInstanceState?.getBoolean("keep-reading-on") ?: false
        pendingExport = savedInstanceState?.getString("pending-export")
        savedInstanceState?.getString("place")?.let { restore(Place.from(JSONObject(it))) }
        savedInstanceState?.getString("history")?.let { encoded ->
            val list = JSONArray(encoded)
            for (i in 0 until list.length()) history.add(Place.from(list.getJSONObject(i)))
        }
        savedInstanceState?.getString("tab-places")?.let { encoded ->
            val list = JSONArray(encoded)
            for (i in 0 until list.length()) Place.from(list.getJSONObject(i)).let { tabPlaces[it.tab] = it }
        }
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
                if (!goBack()) { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
        render()
        transfer.state.observe(this) { state ->
            transferStatus?.text = if (state == TransferState.Busy) tr("Working with the selected file…", "Ausgewählte Datei wird verarbeitet…") else ""
            when (state) {
                is TransferState.Preview -> showRestorePreview(state)
                is TransferState.Protect, is TransferState.Unlock -> showPasswordPrompt(state)
                TransferState.Saved -> transferResult(tr("File saved. Keep the backup private and a printed plan too.", "Datei gespeichert. Sicherung vertraulich und Plan auch auf Papier aufbewahren."))
                TransferState.Restored -> {
                    transfer.dismiss()
                    if (route == "plan" || route == "saved") { articleScroll = 0; render() }
                    showFeedback(tr("Household plan and saved guides restored", "Haushaltsplan und Merkliste wiederhergestellt"), true)
                }
                TransferState.ReadFailed -> transferResult(tr("Could not read this backup. Choose this app's JSON or encrypted backup (up to 512 KiB of plan data). Current data is unchanged.", "Sicherung nicht lesbar. JSON- oder verschlüsselte Sicherung dieser App wählen (bis 512 KiB Plandaten). Bisherige Daten unverändert."))
                TransferState.WriteFailed -> transferResult(tr("Could not finish saving. Check the selected file and available storage. Your plan is kept in the app.", "Speichern nicht abgeschlossen. Datei und freien Speicher prüfen. Der Plan bleibt in der App."))
                TransferState.RestoreFailed -> transferResult(tr("Could not store the restored plan. Previous values were restored in memory. Keep your backup and free storage before retrying.", "Wiederherstellung nicht gespeichert. Vorherige Werte im Arbeitsspeicher wiederhergestellt. Sicherung behalten und Speicher freigeben."))
                else -> Unit
            }
        }
    }

    private fun showFeedback(message: String, long: Boolean = true) {
        feedback?.cancel()
        feedback = Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).also { it.show() }
    }

    private fun transferResult(message: String) { transfer.dismiss(); showFeedback(message, true) }

    private fun showPasswordPrompt(state: TransferState) {
        if (passwordDialog?.isShowing == true) return
        feedback?.cancel()
        val protecting = state is TransferState.Protect
        val title = if (protecting) tr("Protect backup", "Sicherung schützen") else tr("Unlock backup", "Sicherung öffnen")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)); setBackgroundColor(surface)
        }
        ViewCompat.setAccessibilityPaneTitle(content, title)
        content.addView(heading(title, 20f))
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val details = ScrollView(this).apply { isFillViewport = true; addView(fields) }
        content.addView(details, LinearLayout.LayoutParams(-1, 0, 1f))
        val explanation = if (protecting) tr(
            "Use a unique passphrase of 12–128 characters. Keep it separately: there is no password reset. This protects the backup file only; the plan in this app is unchanged. Cancelling may leave an empty selected file.",
            "Einzigartige Passphrase mit 12–128 Zeichen verwenden. Separat aufbewahren: kein Zurücksetzen möglich. Schützt nur die Sicherungsdatei; der Plan in der App bleibt unverändert. Abbrechen kann eine leere gewählte Datei hinterlassen.")
        else if ((state as TransferState.Unlock).failed) tr("Wrong passphrase or damaged backup. Try again, or cancel and choose another file. Your plan is unchanged.", "Falsche Passphrase oder beschädigte Sicherung. Erneut versuchen oder abbrechen und andere Datei wählen. Der Plan ist unverändert.")
        else tr("Enter the exact backup passphrase. Spaces and upper/lower case matter. Unlocking shows a preview before replacing anything. There is no password reset.", "Die genaue Passphrase eingeben. Leerzeichen und Groß-/Kleinschreibung zählen. Vor dem Ersetzen erscheint eine Vorschau. Kein Zurücksetzen möglich.")
        fields.addView(text(explanation, 15f))
        fun input(label: String, tag: String): EditText {
            val edit = EditText(this).apply {
                id = View.generateViewId(); this.tag = tag; isSaveEnabled = false; minHeight = dp(52)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                setTextColor(textColour); contentDescription = label
                if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
            fields.addView(text(label, 15f, true).apply { labelFor = edit.id })
            fields.addView(edit); return edit
        }
        val first = input(tr("Passphrase", "Passphrase"), "backup-passphrase")
        val confirmation = if (protecting) input(tr("Repeat passphrase", "Passphrase wiederholen"), "backup-passphrase-confirm") else null
        passwordFields = listOfNotNull(first, confirmation)
        fields.addView(CheckBox(this).apply {
            text = tr("Show passphrase", "Passphrase anzeigen"); setTextColor(textColour); minHeight = dp(52)
            setOnCheckedChangeListener { _, show -> passwordFields.forEach { edit ->
                edit.transformationMethod = if (show) null else android.text.method.PasswordTransformationMethod.getInstance()
                edit.setSelection(edit.length())
            } }
        })
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(content)
            .setOnCancelListener { transfer.dismiss() }.create()
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        actions.addView(button(if (protecting) tr("Encrypt", "Verschlüsseln") else tr("Unlock", "Öffnen")) {
            val validLength = if (protecting) first.length() in EncryptedHouseholdBackup.MIN_PASSPHRASE..EncryptedHouseholdBackup.MAX_PASSPHRASE else first.length() in 1..EncryptedHouseholdBackup.MAX_PASSPHRASE
            if (!validLength) { first.error = if (protecting) tr("Use 12–128 characters", "12–128 Zeichen verwenden") else tr("Use 1–128 characters", "1–128 Zeichen verwenden"); first.requestFocus() }
            else {
                val password = CharArray(first.length()).also { android.text.TextUtils.getChars(first.text, 0, first.length(), it, 0) }
                val repeated = confirmation?.let { edit ->
                    if (edit.length() <= EncryptedHouseholdBackup.MAX_PASSPHRASE) CharArray(edit.length()).also { android.text.TextUtils.getChars(edit.text, 0, edit.length(), it, 0) } else charArrayOf()
                }
                val valid = (!protecting || password.any { !it.isWhitespace() }) && (repeated == null || password.contentEquals(repeated))
                repeated?.fill('\u0000')
                if (!valid) {
                    password.fill('\u0000'); confirmation?.error = tr("Passphrases must match and cannot be blank", "Passphrasen müssen übereinstimmen und dürfen nicht leer sein")
                } else {
                    passwordFields.forEach { it.text?.clear() }
                    if (protecting) transfer.encryptAndWrite(applicationContext.contentResolver, password) else transfer.unlock(password)
                    dialog.dismiss()
                }
            }
        }, LinearLayout.LayoutParams(-1, -2))
        actions.addView(button(getString(android.R.string.cancel)) { transfer.dismiss(); dialog.dismiss() }, LinearLayout.LayoutParams(-1, -2))
        content.addView(actions)
        dialog.setOnDismissListener { passwordFields.forEach { it.text?.clear() }; passwordFields = emptyList() }
        dialog.setOnShowListener {
            val metrics = resources.displayMetrics
            val bars = ViewCompat.getRootWindowInsets(window.decorView)?.getInsets(WindowInsetsCompat.Type.systemBars())
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            dialog.window?.setLayout((metrics.widthPixels * 0.94f).toInt(), ((metrics.heightPixels - (bars?.top ?: 0) - (bars?.bottom ?: 0)) * 0.9f).toInt())
        }
        passwordDialog = dialog; dialog.show()
    }

    private fun showRestorePreview(state: TransferState.Preview) {
        if (restoreDialog?.isShowing == true) return
        feedback?.cancel()
        val data = state.snapshot
        val preview = tr("Replace the current household plan and saved guides with this backup? Language, text size and reading history stay unchanged.", "Aktuellen Haushaltsplan und Merkliste durch diese Sicherung ersetzen? Sprache, Schriftgröße und Verlauf bleiben erhalten.") +
            "\n\n" + tr("${data.checks.values.count { it }} of 8 checklist items · ${data.bookmarks.size} saved guides", "${data.checks.values.count { it }} von 8 Punkten · ${data.bookmarks.size} gemerkte Anleitungen") +
            "\n" + tr("People: ${data.people.ifEmpty { "—" }} · Days: ${data.days.ifEmpty { "—" }}", "Personen: ${data.people.ifEmpty { "—" }} · Tage: ${data.days.ifEmpty { "—" }}") +
            "\n\n" + tr("Notes preview (${data.notes.length} characters):", "Notizvorschau (${data.notes.length} Zeichen):") + "\n" + data.notes.take(300) + if (data.notes.length > 300) "…" else ""
        hideKeyboard()
        val title = tr("Review backup before replacing", "Sicherung vor Ersetzen prüfen")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12)); setBackgroundColor(surface)
        }
        ViewCompat.setAccessibilityPaneTitle(content, title)
        content.addView(heading(title, 20f))
        val details = ScrollView(this).apply { addView(text(preview, 16f)) }
        content.addView(details, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply {
            orientation = if (resources.configuration.screenWidthDp >= 480) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(content)
            .setOnCancelListener { transfer.dismiss() }.create()
        val replace = button(tr("Replace plan", "Ersetzen")) { transfer.restore(preferences, data); dialog.dismiss() }
        val cancel = button(getString(android.R.string.cancel)) { transfer.dismiss(); dialog.dismiss() }
        for (action in listOf(replace, cancel)) actions.addView(action, if (actions.orientation == LinearLayout.HORIZONTAL)
            LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2))
        content.addView(actions)
        dialog.setOnShowListener {
            val metrics = resources.displayMetrics
            val bars = ViewCompat.getRootWindowInsets(window.decorView)?.getInsets(WindowInsetsCompat.Type.systemBars())
            val height = metrics.heightPixels - (bars?.top ?: 0) - (bars?.bottom ?: 0)
            dialog.window?.setLayout((metrics.widthPixels * 0.94f).toInt(), (height * 0.9f).toInt())
        }
        restoreDialog = dialog
        dialog.show()
    }

    override fun onDestroy() { feedback?.cancel(); passwordDialog?.dismiss(); restoreDialog?.dismiss(); super.onDestroy() }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("language", language)
        outState.putString("place", snapshot().json().toString())
        outState.putString("history", JSONArray(history.map { it.json() }).toString())
        outState.putString("tab-places", JSONArray(tabPlaces.values.map { it.json() }).toString())
        outState.putString("pending-export", pendingExport)
        outState.putBoolean("keep-reading-on", keepReadingOn)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() { super.onResume(); applyScreenOn() }
    override fun onPause() { feedback?.cancel(); passwordFields.forEach { it.text?.clear() }; window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); super.onPause() }

    private fun applyScreenOn() {
        if (::handbook.isInitialized && keepReadingOn && handbook.find(route) != null)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun snapshot() = Place(route, query, category, if (::scroll.isInitialized) scroll.scrollY else articleScroll, activeTab)
    private fun restore(place: Place) { route = place.route; query = place.query; category = place.category; articleScroll = place.scroll; activeTab = place.tab }
    private fun tr(en: String, de: String) = if (language == "de") de else en
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(colour: Int, radius: Int = 14) = GradientDrawable().apply { setColor(colour); cornerRadius = dp(radius).toFloat() }

    private fun text(value: String, size: Float = 17f, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(textColour)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(6)); setLineSpacing(dp(3).toFloat(), 1f)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun heading(value: String, size: Float = 23f) = text(value, size, true).apply { ViewCompat.setAccessibilityHeading(this, true) }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f; minHeight = dp(52)
        minWidth = 0; minimumWidth = 0
        setPadding(dp(10), dp(8), dp(10), dp(8)); setTextColor(accent)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
    }

    private fun hideKeyboard() {
        val token = currentFocus?.windowToken ?: window.decorView.windowToken
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(token, 0)
    }
    private fun navigate(destination: String) {
        hideKeyboard()
        if (destination == route) return
        history.add(snapshot()); if (history.size > 30) history.removeAt(0)
        route = destination; articleScroll = 0; render()
    }

    private fun switchTab(destination: String) {
        hideKeyboard()
        if (route in roots) tabPlaces[route] = snapshot()
        else history.lastOrNull { it.route == activeTab }?.let { tabPlaces[activeTab] = it }
        history.clear()
        restore(tabPlaces[destination] ?: Place(destination, "", "all", 0, destination))
        activeTab = destination; render()
    }

    private fun goBack(): Boolean {
        if (history.isNotEmpty()) { restore(history.removeAt(history.lastIndex)); render(); return true }
        if (route != "home") { switchTab("home"); return true }
        return false
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(pageBackground)
            isFocusableInTouchMode = true; requestFocus()
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom); insets
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(2), dp(12), 0); setBackgroundColor(surface)
        }
        if (route != "home") bar.addView(button(if (resources.configuration.fontScale >= 1.3f) "←" else tr("Back", "Zurück")) { goBack() }.apply {
            contentDescription = tr("Go back", "Zurückgehen")
            textSize = 14f; layoutParams = LinearLayout.LayoutParams(dp(76), -2)
        })
        if (handbook.find(route) != null) bar.addView(button(if (resources.configuration.fontScale >= 1.3f) "⋮" else tr("More", "Mehr")) { readerOptions() }.apply {
            contentDescription = tr("Reader options", "Leseoptionen")
            textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }) else bar.addView(text("OPEN EMERGENCY", 12f, true).apply {
            setTextColor(accent); maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        bar.addView(button("112") { dialEmergency() }.apply {
            contentDescription = tr("Open 112 emergency dialler", "Notruf-Wählfeld 112 öffnen")
            textSize = 15f; layoutParams = LinearLayout.LayoutParams(dp(72), -2)
        })
        bar.addView(button(if (language == "en") "DE" else "EN") {
            articleScroll = scroll.scrollY
            language = if (language == "en") "de" else "en"
            preferences.edit().putString("language", language).apply()
            handbook = EmergencyHandbook(assets, language); render()
        }.apply {
            contentDescription = tr("Switch to German", "Zu Englisch wechseln")
            textSize = 14f; layoutParams = LinearLayout.LayoutParams(dp(if (resources.configuration.fontScale >= 1.3f) 64 else 52), -2)
        })
        root.addView(bar)
        scroll = ScrollView(this).apply { isFillViewport = true }
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        scroll.addView(page); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomNavigation())
        setContentView(root)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            val light = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
            isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light
        }
        ViewCompat.requestApplyInsets(root)
        if (handbook.recoveryMode && handbook.articles.isNotEmpty()) page.addView(text(tr(
            "Recovery mode: ${handbook.articles.size} guides readable; ${handbook.missingGuideCount} unavailable. Back up your plan before reinstalling.",
            "Wiederherstellungsmodus: ${handbook.articles.size} Anleitungen lesbar; ${handbook.missingGuideCount} fehlen. Vor Neuinstallation Plan sichern."), 14f).apply { setTextColor(muted) })
        when (route) {
            "home" -> dashboard()
            "search" -> searchPage()
            "saved" -> savedPage()
            "plan" -> household()
            "aid" -> {
                val priority = listOf("first-aid", "adult-cpr", "child-cpr", "adult-choking", "child-choking", "bleeding", "anaphylaxis", "stroke-heart", "burns", "seizures", "diabetes", "poisoning")
                guideList(tr("First aid", "Erste Hilfe"), handbook.articles.filter { it.group == "urgent" }.sortedBy { priority.indexOf(it.id).let { index -> if (index < 0) Int.MAX_VALUE else index } })
            }
            "prepare" -> guideList(tr("Prepare & recover", "Vorsorge & Rückkehr"), handbook.articles.filter { it.group == "essentials" })
            else -> handbook.find(route)?.let { read(it) } ?: run { route = "home"; activeTab = "home"; dashboard() }
        }
        val restoreY = articleScroll
        val targetScroll = scroll
        targetScroll.post { targetScroll.scrollTo(0, restoreY) }
        applyScreenOn()
    }

    private fun bottomNavigation(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), 0); setBackgroundColor(surface) }
        val options = listOf("home" to tr("Help", "Hilfe"), "search" to tr("Search", "Suche"), "saved" to tr("Saved", "Gemerkt"), "plan" to tr("Plan", "Plan"))
        val columns = if (resources.configuration.fontScale >= 1.3f) 2 else 4
        options.chunked(columns).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { (destination, label) -> row.addView(button(label) { switchTab(destination) }.apply {
                tag = "tab-$destination"; textSize = 14f; isSelected = activeTab == destination
                contentDescription = when (destination) {
                    "home" -> tr("Help now tab", "Soforthilfe öffnen")
                    "search" -> tr("Search guides tab", "Anleitungen durchsuchen")
                    "saved" -> tr("Saved guides tab", "Merkliste öffnen")
                    else -> tr("Open household plan", "Haushaltsplan öffnen")
                }
                ViewCompat.setStateDescription(this, if (isSelected) tr("Selected", "Ausgewählt") else null)
                if (isSelected) { setTypeface(typeface, Typeface.BOLD); background = shape(pageBackground, 10) }
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(2); rightMargin = dp(2) }
            }) }
            container.addView(row)
        }
        return container
    }

    /** Two-column actions become one column for larger system fonts. */
    private fun actionGrid(actions: List<Triple<String, String, () -> Unit>>) {
        val columns = if (resources.configuration.fontScale >= 1.3f) 1 else 2
        actions.chunked(columns).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { (label, description, action) -> row.addView(button(label, action).apply {
                minHeight = dp(64); contentDescription = description
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(3); rightMargin = dp(3) }
            }) }
            page.addView(row)
        }
    }

    private fun dashboard() {
        if (handbook.articles.isEmpty()) {
            page.addView(heading(tr("Guides unavailable", "Anleitungen nicht verfügbar")))
            page.addView(text(tr("The bundled handbook could not be read. Your household plan is still available. Back it up before reinstalling, and keep the printable handbook as a separate copy.", "Das enthaltene Handbuch ist nicht lesbar. Der Haushaltsplan ist weiter verfügbar. Vor Neuinstallation sichern und Druckausgabe getrennt aufbewahren.")))
            page.addView(button(tr("Open household plan", "Haushaltsplan öffnen")) { switchTab("plan") })
            page.addView(button(tr("Retry loading guides", "Anleitungen erneut laden")) { handbook = EmergencyHandbook(assets, language); articleScroll = 0; render() })
            return
        }
        page.addView(heading(tr("Help now", "Jetzt Hilfe finden")))
        page.addView(text(tr("Offline · Unreviewed development preview", "Offline · Ungeprüfter Entwicklungsentwurf"), 12f).apply { setTextColor(muted) })
        actionGrid(listOf(
            Triple(tr("First aid", "Erste Hilfe"), tr("Show first-aid guides", "Erste-Hilfe-Anleitungen anzeigen")) { navigate("aid") },
            Triple(tr("Safe water", "Sicheres Wasser"), tr("Open safe water guide", "Trinkwasser-Anleitung öffnen")) { navigate("water") }
        ).filterIndexed { index, _ -> if (index == 0) handbook.articles.any { it.group == "urgent" } else handbook.find("water") != null })
        val labels = mapOf(
            "power" to tr("Power outage", "Stromausfall"), "flood" to tr("Flood", "Hochwasser"),
            "earthquake" to tr("Earthquake", "Erdbeben"), "fallout" to tr("Nuclear fallout", "Nuklearer Fallout"),
            "cyber" to tr("Cyberattack", "Cyberangriff"), "pandemic" to tr("Pandemic", "Pandemie")
        )
        actionGrid(labels.filterKeys { handbook.find(it) != null }.map { (id, label) -> Triple(label, label) { navigate(id) } })
        page.addView(button(tr("Prepare & recover", "Vorsorge & Rückkehr")) { navigate("prepare") })
        page.addView(button(tr("All guides & search", "Alle Anleitungen & Suche")) { switchTab("search") })
        page.addView(button(tr("Unreviewed preview · About & sources", "Ungeprüfter Entwurf · Über die App & Quellen")) { navigate("about") }.apply { textSize = 14f })
        page.addView(text(tr("All ${handbook.articles.size} available guides are on this phone. Incomplete, unreviewed content; follow emergency services. No live alerts. Edition ${handbook.edition} · ${BuildConfig.VERSION_NAME}", "Alle ${handbook.articles.size} verfügbaren Anleitungen auf diesem Handy. Inhalte unvollständig und ungeprüft; Rettungsdiensten folgen. Keine Live-Warnungen. Ausgabe ${handbook.edition} · ${BuildConfig.VERSION_NAME}"), 13f).apply { setTextColor(muted) })
    }

    private fun guideList(title: String, articles: List<EmergencyArticle>) {
        page.addView(heading(title))
        page.addView(text(tr("Offline guides · unreviewed development preview", "Offline-Anleitungen · ungeprüfter Entwurf"), 13f).apply { setTextColor(muted) })
        articles.forEach { page.addView(card(it)) }
    }

    private fun searchPage() {
        page.addView(heading(tr("Search all guides", "Alle Anleitungen durchsuchen")))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val search = EditText(this).apply {
            hint = tr("Water, blackout, bleeding…", "Wasser, Stromausfall, Blutung…")
            contentDescription = tr("Search all offline guides", "Alle Offline-Anleitungen durchsuchen")
            setSingleLine(true); filters = arrayOf(android.text.InputFilter.LengthFilter(200)); textSize = 17f; setTextColor(textColour); setHintTextColor(muted)
            minHeight = dp(56); setPadding(dp(12), dp(8), dp(12), dp(8)); background = shape(surface, 10)
            setText(query)
        }
        row.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("×") { search.setText("") }.apply {
            contentDescription = tr("Clear search", "Suche löschen"); layoutParams = LinearLayout.LayoutParams(dp(52), -2)
        })
        page.addView(row)
        val filters = listOf("all", "aid", "disaster", "prepare")
        val filterLabels = listOf(tr("All guides", "Alle Anleitungen"), tr("First aid", "Erste Hilfe"), tr("Disasters", "Katastrophen"), tr("Preparation", "Vorsorge"))
        page.addView(text(tr("Filter results", "Ergebnisse filtern"), 14f))
        val filter = Spinner(this).apply {
            contentDescription = tr("Filter search results", "Suchergebnisse filtern")
            minimumHeight = dp(52)
            adapter = ArrayAdapter(this@EmergencyActivity, android.R.layout.simple_spinner_dropdown_item, filterLabels)
            setSelection(filters.indexOf(category).coerceAtLeast(0))
        }
        page.addView(filter)
        val count = text("", 14f).apply { ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE) }
        page.addView(count)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(results)
        fun updateResults() {
            results.removeAllViews()
            val found = handbook.searchResult(query)
            val matches = found.articles.filter { when (category) {
                "aid" -> it.group == "urgent"; "disaster" -> it.group == "disaster"; "prepare" -> it.group == "essentials"; else -> true
            } }
            count.text = when {
                found.approximate && matches.isNotEmpty() -> tr("${matches.size} possible matches · check the guide title", "${matches.size} mögliche Treffer · Titel prüfen")
                matches.isEmpty() -> tr("No matches. Clear the filter or try a simpler word.", "Keine Treffer. Filter löschen oder einfacheres Wort versuchen.")
                else -> tr("${matches.size} guides · English & German search terms work", "${matches.size} Anleitungen · deutsche & englische Suchwörter möglich")
            }
            matches.forEach { results.addView(card(it)) }
            if (matches.isEmpty()) {
                if (category != "all") results.addView(button(tr("Search all categories", "In allen Kategorien suchen")) { category = "all"; filter.setSelection(0); updateResults() })
                results.addView(button(tr("Browse first aid", "Erste Hilfe durchsuchen")) { navigate("aid") })
            }
        }
        filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { category = filters[position]; updateResults() }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); updateResults() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateResults()
    }

    private fun bookmarks() = preferences.safeStrings("bookmarks")
    private fun recentIds(): List<String> = try {
        val list = JSONArray(preferences.safeString("recent-guides", "[]"))
        (0 until minOf(list.length(), 6)).mapNotNull { list.optString(it).takeIf { id -> handbook.find(id) != null } }
    } catch (_: Exception) { emptyList() }

    private fun savedPage() {
        page.addView(heading(tr("Saved guides", "Merkliste")))
        page.addView(text(tr("Bookmarks and reading history stay on this device.", "Merkliste und Verlauf bleiben auf diesem Gerät."), 14f).apply { setTextColor(muted) })
        val saved = handbook.articles.filter { it.id in bookmarks() }.sortedBy { it.title }
        if (saved.isEmpty()) {
            page.addView(text(tr("No saved guides yet. Open a guide and choose Save guide.", "Noch keine gemerkten Anleitungen. Eine Anleitung öffnen und Merken wählen.")))
            page.addView(button(tr("Browse all guides", "Alle Anleitungen ansehen")) { switchTab("search") })
        } else saved.forEach { page.addView(card(it)) }
        val recent = recentIds().filterNot { it in bookmarks() }.mapNotNull { handbook.find(it) }
        if (recent.isNotEmpty()) {
            page.addView(heading(tr("Recently opened", "Zuletzt geöffnet"), 20f))
            recent.forEach { page.addView(card(it)) }
        }
        if (recentIds().isNotEmpty()) page.addView(button(tr("Clear reading history…", "Leseverlauf löschen…")) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(tr("Clear recently opened guides? Your bookmarks and household plan stay saved.", "Zuletzt geöffnete Anleitungen löschen? Merkliste und Haushaltsplan bleiben gespeichert."))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> preferences.edit().remove("recent-guides").apply(); articleScroll = 0; render() }.show()
        })
    }

    private fun card(article: EmergencyArticle): View {
        val card = object : LinearLayout(this) {
            override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(info)
                info.className = Button::class.java.name
            }
        }.apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10))
            background = shape(surface); isClickable = true; isFocusable = true; minimumHeight = dp(72)
            tag = "guide-${article.id}"; contentDescription = "${article.title}. ${article.summary}"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            setOnClickListener { navigate(article.id) }
            ViewCompat.setScreenReaderFocusable(this, true)
        }
        card.addView(text(article.title, 19f, true).apply { setTextColor(accent); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
        card.addView(text(article.summary, 14f).apply { setTextColor(muted); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
        return card
    }

    private fun showSections() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(tr("On this page", "Auf dieser Seite"))
            .setItems(readerSections.keys.toTypedArray()) { _, index ->
                val target = readerSections.values.elementAt(index)
                scroll.scrollTo(0, (target.top - dp(6)).coerceAtLeast(0))
            }.show()
    }

    private fun readerOptions() {
        val article = handbook.find(route) ?: return
        val items = arrayOf(
            if (article.id in bookmarks()) tr("Saved · remove bookmark", "Gemerkt · entfernen") else tr("Save guide", "Anleitung merken"),
            tr("Text size · ${(readerScale * 100).toInt()}%", "Schriftgröße · ${(readerScale * 100).toInt()}%"),
            tr("Keep screen on · ${if (keepReadingOn) "on" else "off"}", "Bildschirm anlassen · ${if (keepReadingOn) "an" else "aus"}"),
            tr("Jump to section", "Zum Abschnitt")
        )
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(tr("Reader options", "Leseoptionen"))
            .setItems(items) { _, item -> when (item) {
                0 -> {
                    val saved = bookmarks().toMutableSet()
                    if (!saved.add(article.id)) saved.remove(article.id)
                    preferences.edit().putStringSet("bookmarks", saved).apply()
                    showFeedback(if (article.id in saved) tr("Guide saved", "Anleitung gemerkt") else tr("Bookmark removed", "Eintrag entfernt"), false)
                }
                1 -> {
                    val scales = listOf(1f, 1.2f, 1.4f, 1.6f, 1.8f)
                    androidx.appcompat.app.AlertDialog.Builder(this).setTitle(tr("Reader text size", "Leseschriftgröße"))
                        .setSingleChoiceItems(scales.map { "${(it * 100).toInt()}%" }.toTypedArray(), scales.indexOfFirst { kotlin.math.abs(it - readerScale) < 0.01f }) { dialog, index ->
                            readerScale = scales[index]; preferences.edit().putFloat("reader-scale", readerScale).apply()
                            articleScroll = scroll.scrollY; dialog.dismiss(); render()
                        }.show()
                }
                2 -> {
                    keepReadingOn = !keepReadingOn; applyScreenOn()
                    showFeedback(if (keepReadingOn) tr("Screen stays on only while reading here. Uses extra battery.", "Bildschirm bleibt nur hier beim Lesen an. Braucht mehr Akku.") else tr("Normal screen timeout restored", "Normale Bildschirmabschaltung aktiv"), true)
                }
                3 -> showSections()
            } }.show()
    }

    private fun read(article: EmergencyArticle) {
        val recents = (listOf(article.id) + recentIds().filterNot { it == article.id }).take(6)
        preferences.edit().putString("recent-guides", JSONArray(recents).toString()).apply()
        page.addView(heading(article.title, 25f))
        page.addView(text(tr("Unreviewed draft", "Ungeprüfter Entwurf"), 12f, true).apply { setTextColor(accent) })
        page.addView(text(article.summary, 18f * readerScale))
        readerSections.clear()
        page.addView(button(tr("Jump to section", "Zum Abschnitt")) { showSections() }.apply { textSize = 14f })
        if (article.id == "about") {
            page.addView(heading(tr("Backup privacy in this build", "Sicherungsdatenschutz dieser Version"), 21f))
            page.addView(text(tr("Plan offers optional passphrase-encrypted backup files alongside plain JSON/text exports. This does not encrypt the plan stored inside the app. There is no passphrase reset; keep it separately. File providers may upload selected files under their own policies.", "Der Haushaltsplan bietet passphrasenverschlüsselte Sicherungen neben unverschlüsselten JSON-/Textexporten. Der in der App gespeicherte Plan wird dadurch nicht verschlüsselt. Kein Zurücksetzen der Passphrase; separat aufbewahren. Dateianbieter können gewählte Dateien nach eigenen Regeln hochladen."), 16f))
            page.addView(button(tr("Bouncy Castle licence · offline", "Bouncy-Castle-Lizenz · offline")) {
                val licence = assets.open("licenses/bouncycastle.html").bufferedReader().use { it.readText() }
                val body = ScrollView(this).apply { addView(text("").apply { text = HtmlCompat.fromHtml(licence, HtmlCompat.FROM_HTML_MODE_LEGACY) }) }
                androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Bouncy Castle 1.86").setView(body).setPositiveButton(android.R.string.ok, null).show()
            })
        }
        article.body.split(Regex("(?m)(?=^## )")).filter { it.isNotBlank() }.forEach { block ->
            val trimmed = block.trim()
            val body: String
            if (trimmed.startsWith("## ")) {
                val label = trimmed.substringAfter("## ").substringBefore('\n')
                val title = heading(label, 21f)
                readerSections[label] = title; page.addView(title)
                body = trimmed.substringAfter('\n', "")
            } else body = trimmed
            if (body.isNotBlank()) page.addView(text("").apply {
                text = HtmlCompat.fromHtml(convertMarkdownToHtml(body), HtmlCompat.FROM_HTML_MODE_LEGACY)
                textSize = 18f * readerScale; setTextIsSelectable(true); movementMethod = LinkMovementMethod.getInstance()
            })
        }
        if (article.related.isNotEmpty()) {
            page.addView(heading(tr("Related offline guides", "Weitere Offline-Anleitungen"), 21f))
            article.related.mapNotNull { handbook.find(it) }.forEach { link ->
                page.addView(button(link.title) { navigate(link.id) }.apply { tag = "related-${link.id}" })
            }
        }
        val sources = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val sourcesToggle = button(tr("Sources & limitations (${article.sources.size})", "Quellen & Grenzen (${article.sources.size})")) { }
        ViewCompat.setStateDescription(sourcesToggle, tr("Collapsed", "Eingeklappt"))
        sourcesToggle.setOnClickListener {
            val expanded = sources.visibility != View.VISIBLE
            sources.visibility = if (expanded) View.VISIBLE else View.GONE
            ViewCompat.setStateDescription(sourcesToggle, if (expanded) tr("Expanded", "Ausgeklappt") else tr("Collapsed", "Eingeklappt"))
        }
        page.addView(sourcesToggle)
        sources.addView(text(tr("Original summary and translation. Sources checked ${article.checkedAt}; no professional review recorded. Links open your browser and require internet. No affiliation with the cited organisations.", "Eigene Zusammenfassung und Übersetzung. Quellenabgleich: ${article.checkedAt}; keine fachliche Prüfung dokumentiert. Links öffnen den Browser und benötigen Internet. Keine Verbindung zu den Quellenorganisationen."), 13f))
        article.sources.forEachIndexed { index, url -> sources.addView(button(tr("Source ${index + 1} · ${Uri.parse(url).host}", "Quelle ${index + 1} · ${Uri.parse(url).host}")) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (_: ActivityNotFoundException) { showFeedback(tr("No browser installed", "Kein Browser installiert"), false) }
        }) }
        page.addView(sources)
        page.addView(button(tr("Back to previous page", "Zurück zur vorherigen Seite")) { goBack() })
    }
    private fun household() {
        page.addView(heading(tr("My household", "Mein Haushalt"), 30f))
        page.addView(button(tr("Backup / restore…", "Sichern / wiederherstellen…")) { householdTransfers() })
        transferStatus = text(if (transfer.state.value == TransferState.Busy) tr("Working with the selected file…", "Ausgewählte Datei wird verarbeitet…") else "", 14f).apply {
            ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        }
        page.addView(transferStatus)
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
                isChecked = preferences.safeBoolean("check-$key", false)
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
            contentDescription = tr("Household notes", "Haushaltsnotizen")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5; gravity = android.view.Gravity.TOP
            setText(preferences.safeString("notes", ""))
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
                        showFeedback(tr("No file picker available", "Keine Dateiauswahl verfügbar"), true)
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

    private fun householdTransfers() {
        hideKeyboard()
        if (transfer.state.value == TransferState.Busy) {
            showFeedback(tr("A file operation is still running", "Eine Datei wird noch verarbeitet"), false); return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(tr("Household backup", "Haushaltssicherung"))
            .setItems(arrayOf(tr("Save encrypted backup", "Verschlüsselte Sicherung speichern"), tr("Save JSON backup", "JSON-Sicherung speichern"), tr("Restore backup", "Sicherung wiederherstellen"))) { _, action ->
                if (action == 2) {
                    try { importPlan.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                    catch (_: ActivityNotFoundException) { transferResult(tr("No file picker available", "Keine Dateiauswahl verfügbar")) }
                } else if (action == 0) {
                    try {
                        val ids = handbook.articles.map { it.id }.toSet() + preferences.safeStrings("bookmarks")
                        val encoded = HouseholdBackup.encode(HouseholdBackup.snapshot(preferences))
                        transfer.pendingBackup = HouseholdBackup.encode(HouseholdBackup.parse(encoded, ids))
                        encryptedBackupPlan.launch("OpenEmergency-Household-backup.oeh")
                    } catch (_: Exception) { transfer.pendingBackup = null; transferResult(tr("Could not start backup. Check saved data and file-picker availability.", "Sicherung nicht gestartet. Gespeicherte Daten und Dateiauswahl prüfen.")) }
                } else androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(tr("Creates an unencrypted JSON file with your checklist, notes and saved guides. It can restore these after reinstalling. Choose a local folder; cloud providers may upload it. Keep it private. Text exports are for reading and cannot be restored automatically.", "Erstellt eine unverschlüsselte JSON-Datei mit Checkliste, Notizen und Merkliste. Zur Wiederherstellung nach Neuinstallation. Lokalen Ordner wählen; Cloud-Anbieter können hochladen. Vertraulich aufbewahren. Textexporte sind nur zum Lesen und nicht automatisch einlesbar."))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(tr("Choose file", "Datei wählen")) { _, _ ->
                        try {
                            val ids = handbook.articles.map { it.id }.toSet() + preferences.safeStrings("bookmarks")
                            val encoded = HouseholdBackup.encode(HouseholdBackup.snapshot(preferences))
                            transfer.pendingBackup = HouseholdBackup.encode(HouseholdBackup.parse(encoded, ids))
                            backupPlan.launch("OpenEmergency-Household-backup.json")
                        } catch (_: Exception) { transfer.pendingBackup = null; transferResult(tr("Could not create backup. Check notes length and saved data.", "Sicherung nicht erstellt. Notizlänge und gespeicherte Daten prüfen.")) }
                    }.show()
            }.show()
    }

    private fun numberInput(label: String, key: String, fallback: String): EditText {
        page.addView(text(label, 15f, true))
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(textColour)
            contentDescription = label; setText(preferences.safeString(key, fallback)); minHeight = dp(52)
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
        catch (_: ActivityNotFoundException) { showFeedback(tr("No dialler available. Ask someone to call 112.", "Kein Wählfeld verfügbar. Jemanden bitten, 112 anzurufen."), true) }
    }
}
