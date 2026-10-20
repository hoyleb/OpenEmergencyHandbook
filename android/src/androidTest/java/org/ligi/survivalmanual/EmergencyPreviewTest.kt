package org.ligi.survivalmanual

import android.Manifest
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ligi.survivalmanual.model.EmergencyHandbook
import org.ligi.survivalmanual.model.HouseholdPlan
import org.ligi.survivalmanual.ui.EmergencyActivity

@RunWith(AndroidJUnit4::class)
class EmergencyPreviewTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("emergency-handbook", Context.MODE_PRIVATE)
    private fun guide(id: String) = onView(withTagValue(equalTo("guide-$id")))
    private fun tab(id: String) = onView(withTagValue(equalTo("tab-$id")))
    private fun search(value: String) {
        tab("search").perform(click())
        onView(withContentDescription("Search all offline guides")).perform(replaceText(value), closeSoftKeyboard())
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun capture(name: String) {
        // Toasts are system windows and can outlive the previous test/activity.
        Thread.sleep(3500)
        instrumentation.waitForIdleSync()
        shell("screencap -p /sdcard/Download/open-emergency-$name.png")
    }

    @Before fun resetPlan() { prefs.edit().clear().putString("language", "en").commit() }

    @Test fun bothLanguagesAndAllDisastersAreBundled() {
        for (language in listOf("en", "de")) {
            val book = EmergencyHandbook(context.assets, language)
            assertTrue(book.articles.size >= 30)
            for (id in listOf("power", "cyber", "earthquake", "fallout", "flood", "pandemic", "first-aid", "water")) assertTrue(book.find(id)!!.body.isNotBlank())
            for (article in book.articles) for (related in article.related) assertNotNull(book.find(related))
        }
        val en = EmergencyHandbook(context.assets, "en")
        assertTrue(en.search("blackout").any { it.id == "power" })
        assertEquals("bleeding", en.search("bleeding").first().id)
        assertTrue(en.search("Stromausfall").any { it.id == "power" })
        assertFalse(en.searchResult("blackout").approximate)
        assertTrue(en.searchResult("blackot").approximate)
        assertTrue(en.search("nonsensewordxyz").isEmpty())
        assertTrue(EmergencyHandbook(context.assets, "de").search("uberschwemmung").any { it.id == "flood" })
        assertTrue(EmergencyHandbook(context.assets, "de").search("stromausfal").any { it.id == "power" })
        assertEquals(PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET))
    }

    @Test fun sharedSearchAcceptanceVectorsMatchPortableEdition() {
        val cases = org.json.JSONArray(context.assets.open("handbook/search-cases.json").bufferedReader().use { it.readText() })
        for (index in 0 until cases.length()) {
            val test = cases.getJSONObject(index)
            val result = EmergencyHandbook(context.assets, test.getString("language")).searchResult(test.getString("query"))
            assertEquals(test.optString("first", ""), result.articles.firstOrNull()?.id.orEmpty())
            assertEquals(test.getBoolean("approximate"), result.approximate)
        }
    }

    @Test fun urgentHomeActionsAreVisibleWithoutScrolling() {
        ActivityScenario.launch(EmergencyActivity::class.java).use {
            capture("home")
            onView(withContentDescription("Show first-aid guides")).check(matches(isCompletelyDisplayed()))
            onView(withContentDescription("Open safe water guide")).check(matches(isCompletelyDisplayed()))
            onView(withContentDescription("Open 112 emergency dialler")).check(matches(isCompletelyDisplayed()))
            onView(withText("Nuclear fallout")).check(matches(isCompletelyDisplayed()))
            tab("search").check(matches(isCompletelyDisplayed()))
        }
    }

    @Test fun returningHomeDismissesSearchKeyboard() {
        ActivityScenario.launch(EmergencyActivity::class.java).use {
            tab("search").perform(click())
            onView(withContentDescription("Search all offline guides")).perform(click(), typeText("water"))
            tab("home").perform(click())
            onView(withText("Nuclear fallout")).check(matches(isCompletelyDisplayed()))
        }
    }

    @Test fun landscapeKeepsEmergencyAndNavigationControlsReachable() {
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            fun awaitOrientation(expected: Int) {
                val deadline = System.currentTimeMillis() + 5000
                var current = 0
                while (current != expected && System.currentTimeMillis() < deadline) {
                    scenario.onActivity { current = it.resources.configuration.orientation }
                    if (current != expected) Thread.sleep(100)
                }
                assertEquals(expected, current)
            }
            scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            awaitOrientation(Configuration.ORIENTATION_LANDSCAPE)
            onView(withContentDescription("Open 112 emergency dialler")).check(matches(isCompletelyDisplayed()))
            tab("plan").check(matches(isCompletelyDisplayed()))
            onView(withContentDescription("Open safe water guide")).perform(scrollTo(), click())
            onView(withContentDescription("Reader options")).check(matches(isCompletelyDisplayed()))
            onView(withContentDescription("Switch to German")).perform(click())
            capture("landscape")
            scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            awaitOrientation(Configuration.ORIENTATION_PORTRAIT)
            onView(withText("Sicheres Trinkwasser")).check(matches(isDisplayed()))
        }
    }

    @Test fun guideCardsAndSourceDisclosureExposeAccessibleRolesAndStates() {
        ActivityScenario.launch(EmergencyActivity::class.java).use {
            onView(withText("Help now")).check { view, _ -> assertTrue(androidx.core.view.ViewCompat.isAccessibilityHeading(view)) }
            search("water")
            guide("water").check { view, _ -> assertEquals("android.widget.Button", view.createAccessibilityNodeInfo().className.toString()) }
            guide("water").perform(scrollTo(), click())
            val toggle = onView(withText(org.hamcrest.CoreMatchers.startsWith("Sources & limitations")))
            toggle.perform(scrollTo(), click())
            toggle.check { view, _ -> assertEquals("Expanded", androidx.core.view.ViewCompat.getStateDescription(view).toString()) }
            toggle.perform(click())
            toggle.check { view, _ -> assertEquals("Collapsed", androidx.core.view.ViewCompat.getStateDescription(view).toString()) }
        }
    }

    @Test fun launchSearchOpenGuideAndSwitchLanguage() {
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            search("nuclear")
            guide("fallout").perform(scrollTo(), click())
            onView(withText("Unreviewed draft")).check(matches(isDisplayed()))
            onView(withText("Immediate protection")).check(matches(isDisplayed()))
            onView(withContentDescription("Switch to German")).perform(click())
            onView(withText("Nuklearer Fallout & Strahlung")).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withText("Nuklearer Fallout & Strahlung")).check(matches(isDisplayed()))
        }
    }

    @Test fun householdChecklistPersistsAfterRecreation() {
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            tab("plan").perform(click())
            onView(withText("Stored drinking water & clean containers")).perform(scrollTo(), click())
            scenario.recreate()
            onView(withText("Stored drinking water & clean containers")).check(matches(isChecked()))
        }
    }

    @Test fun firstAidIsReachableInTwoTapsAndRelatedGuidesWork() {
        ActivityScenario.launch(EmergencyActivity::class.java).use {
            onView(withContentDescription("Show first-aid guides")).perform(click())
            guide("child-choking").perform(scrollTo(), click())
            onView(withText("Unreviewed draft")).check(matches(isDisplayed()))
            onView(withTagValue(equalTo("related-child-cpr"))).perform(scrollTo(), click())
            onView(withText("CPR: babies & children")).check(matches(isDisplayed()))
        }
    }

    @Test fun exportSnapshotContainsPlanAndClearPreservesSettings() {
        prefs.edit().putBoolean("check-water", true).putString("notes", "Meeting point: library\nContact: Example")
            .putString("people", "2").putString("days", "3").putString("language", "de").putFloat("reader-scale", 1.4f).commit()
        val exported = HouseholdPlan.export(prefs, "en", "2026-10-19")
        assertTrue(exported.contains("[x] Drinking water")); assertTrue(exported.contains("12 L"))
        assertTrue(exported.contains("Meeting point: library\nContact: Example")); assertFalse(exported.contains("reader-scale"))
        assertTrue(HouseholdPlan.export(prefs, "de", "2026-10-19").contains("12 L"))
        prefs.edit().putString("days", "9999999999999999").commit()
        assertTrue(HouseholdPlan.export(prefs, "en", "2026-10-19").contains("invalid input"))
        HouseholdPlan.clear(prefs)
        assertFalse(prefs.contains("notes")); assertFalse(prefs.contains("check-water"))
        assertEquals("de", prefs.getString("language", "")); assertEquals(1.4f, prefs.getFloat("reader-scale", 1f), 0.01f)
    }

    @Test fun cancelledExportLeavesPlanIntact() {
        prefs.edit().putString("notes", "Keep this note").commit()
        ActivityScenario.launch(EmergencyActivity::class.java).use {
            tab("plan").perform(click())
            onView(withText("Export household plan…")).perform(scrollTo(), click())
            onView(withText(android.R.string.cancel)).perform(click())
            assertEquals("Keep this note", prefs.getString("notes", ""))
        }
    }

    @Test fun largeReaderTextAndLanguageSurviveRecreation() {
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            onView(withContentDescription("Open safe water guide")).perform(click())
            onView(withContentDescription("Reader options")).perform(click())
            onView(withText("Text size · 100%")).perform(click())
            onView(withText("140%")).perform(click())
            assertEquals(1.4f, prefs.getFloat("reader-scale", 1f), 0.01f)
            onView(withContentDescription("Switch to German")).perform(click())
            scenario.recreate()
            onView(withText("Sicheres Trinkwasser")).check(matches(isDisplayed()))
            assertEquals(1.4f, prefs.getFloat("reader-scale", 1f), 0.01f)
        }
    }

    @Test fun backRestoresRelatedGuideThenOriginalSearch() {
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            search("nuclear")
            guide("fallout").perform(scrollTo(), click())
            onView(withTagValue(equalTo("related-water"))).perform(scrollTo(), click())
            scenario.recreate()
            pressBack()
            onView(withTagValue(equalTo("related-water"))).check(matches(isDisplayed()))
            pressBack()
            onView(withContentDescription("Search all offline guides")).check(matches(withText("nuclear")))
            tab("search").check(matches(isSelected()))
            guide("fallout").check(matches(isDisplayed()))
        }
    }

    @Test fun bookmarkPersistsAndCanBeRemoved() {
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            onView(withContentDescription("Open safe water guide")).perform(click())
            onView(withContentDescription("Reader options")).perform(click())
            onView(withText("Save guide")).perform(click())
            tab("saved").perform(click())
            scenario.recreate(); guide("water").check(matches(isDisplayed()))
            guide("water").perform(click())
            onView(withContentDescription("Reader options")).perform(click())
            onView(withText("Saved · remove bookmark")).perform(click())
            assertFalse(prefs.getStringSet("bookmarks", emptySet())!!.contains("water"))
        }
    }

    @Test fun keepScreenOnIsOptInAndEndsWhenLeavingReader() {
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            onView(withContentDescription("Open safe water guide")).perform(click())
            scenario.onActivity { assertEquals(0, it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            onView(withContentDescription("Reader options")).perform(click())
            onView(withText("Keep screen on · off")).perform(click())
            scenario.onActivity { assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) }
            tab("home").perform(click())
            scenario.onActivity { assertEquals(0, it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    @Test fun guideSectionsCanBeReachedWithoutScrollingThroughWholeArticle() {
        ActivityScenario.launch(EmergencyActivity::class.java).use {
            onView(withContentDescription("Open safe water guide")).perform(click())
            onView(withText("Jump to section")).perform(click())
            onView(withText("Chemicals and fallout")).perform(click())
            onView(withText("Chemicals and fallout")).check(matches(isDisplayed()))
            capture("reader-section")
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyFontConfiguration(scale: Float) {
        instrumentation.runOnMainSync {
            val config = Configuration(context.resources.configuration).apply { fontScale = scale }
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }

    /** API 23 needs an in-process resource fixture; its settings command does not
     * notify the running app. API 36 uses the actual system setting. Both assert
     * the activity's effective configuration; no font assertion is skipped. */
    @Test fun twoHundredPercentFontKeepsNavigationUsable() {
        val previous = shell("settings get system font_scale")
        val previousScale = context.resources.configuration.fontScale
        try {
            if (Build.VERSION.SDK_INT < 24) legacyFontConfiguration(2f)
            else shell("settings put system font_scale 2.0")
            val deadline = System.currentTimeMillis() + 5000
            while (context.resources.configuration.fontScale < 1.9f && System.currentTimeMillis() < deadline) Thread.sleep(100)
            ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
                scenario.onActivity {
                    assertTrue(it.resources.configuration.fontScale >= 1.9f)
                    if (Build.VERSION.SDK_INT < 24) assertEquals(2f, it.resources.displayMetrics.scaledDensity / it.resources.displayMetrics.density, 0.05f)
                }
                onView(withContentDescription("Switch to German")).check { view, _ -> assertEquals(1, (view as TextView).lineCount) }
                capture("large-text-home")
                onView(withContentDescription("Show first-aid guides")).check(matches(isCompletelyDisplayed()))
                tab("search").check(matches(isCompletelyDisplayed()))
                tab("plan").check(matches(isCompletelyDisplayed()))
                onView(withContentDescription("Open safe water guide")).perform(scrollTo(), click())
                onView(withText("Safe drinking water")).check(matches(isDisplayed()))
                tab("home").check(matches(isCompletelyDisplayed()))
                capture("large-text-reader")
                onView(withContentDescription("Switch to German")).perform(click())
                onView(withText("Sicheres Trinkwasser")).check(matches(isDisplayed()))
                onView(withContentDescription("Leseoptionen")).check(matches(isCompletelyDisplayed()))
                onView(withContentDescription("Notruf-Wählfeld 112 öffnen")).check(matches(isCompletelyDisplayed()))
                onView(withContentDescription("Zu Englisch wechseln")).check { view, _ -> assertEquals(1, (view as TextView).lineCount) }
                onView(withContentDescription("Leseoptionen")).check { view, _ -> assertEquals(1, (view as TextView).lineCount) }
                capture("large-text-reader-de")
                tab("home").perform(click())
                onView(withContentDescription("Erste-Hilfe-Anleitungen anzeigen")).check(matches(isCompletelyDisplayed()))
                tab("saved").check(matches(isCompletelyDisplayed()))
                capture("large-text-home-de")
            }
        } finally {
            if (Build.VERSION.SDK_INT < 24) legacyFontConfiguration(previousScale)
            else if (previous == "null" || previous.isBlank()) shell("settings delete system font_scale")
            else shell("settings put system font_scale $previous")
            val deadline = System.currentTimeMillis() + 5000
            while (context.resources.configuration.fontScale >= 1.9f && System.currentTimeMillis() < deadline) Thread.sleep(100)
        }
    }

    @Test fun darkThemeUsesReadablePalette() {
        try {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
                onView(withContentDescription("Open safe water guide")).check(matches(isDisplayed()))
                scenario.onActivity { activity ->
                    fun find(view: View): TextView? {
                        if (view is TextView && view.text.toString() == "Help now") return view
                        if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
                        return null
                    }
                    assertEquals(Color.parseColor("#F1F8F5"), find(activity.window.decorView)!!.currentTextColor)
                }
                capture("dark-home")
            }
        } finally { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
    }
}
