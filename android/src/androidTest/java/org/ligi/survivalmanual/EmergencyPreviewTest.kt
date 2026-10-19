package org.ligi.survivalmanual

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ligi.survivalmanual.model.EmergencyHandbook
import org.ligi.survivalmanual.model.HouseholdPlan
import org.ligi.survivalmanual.ui.EmergencyActivity

@RunWith(AndroidJUnit4::class)
class EmergencyPreviewTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun resetPlan() {
        context.getSharedPreferences("emergency-handbook", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun bothLanguagesAndAllDisastersAreBundled() {
        for (language in listOf("en", "de")) {
            val book = EmergencyHandbook(context.assets, language)
            assertTrue(book.articles.size >= 30)
            for (id in listOf("power", "cyber", "earthquake", "fallout", "flood", "pandemic", "first-aid", "water")) {
                assertTrue(book.find(id)!!.body.isNotBlank())
            }
        }
        assertTrue(EmergencyHandbook(context.assets, "en").search("blackout").any { it.id == "power" })
        assertTrue(EmergencyHandbook(context.assets, "de").search("stromausfall").any { it.id == "power" })
        assertTrue(EmergencyHandbook(context.assets, "de").search("uberschwemmung").any { it.id == "flood" })
        assertTrue(EmergencyHandbook(context.assets, "en").search("blackot").any { it.id == "power" })
        assertTrue(EmergencyHandbook(context.assets, "de").search("stromausfal").any { it.id == "power" })
        assertTrue(EmergencyHandbook(context.assets, "en").search("nonsensewordxyz").isEmpty())
        for (article in EmergencyHandbook(context.assets, "en").articles) {
            for (related in article.related) assertNotNull(EmergencyHandbook(context.assets, "en").find(related))
        }
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))
    }

    @Test fun launchSearchOpenGuideAndSwitchLanguage() {
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            onView(withContentDescription("Search all offline guides")).perform(scrollTo(), replaceText("nuclear"), closeSoftKeyboard())
            onView(withText("Nuclear fallout & radiation")).perform(scrollTo(), click())
            onView(withText("OFFLINE GUIDE · UNREVIEWED PREVIEW")).check(matches(isDisplayed()))
            onView(withText("Deutsch")).perform(click())
            onView(withText("Nuklearer Fallout & Strahlung")).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withText("Nuklearer Fallout & Strahlung")).check(matches(isDisplayed()))
        }
    }

    @Test fun householdChecklistPersistsAfterRecreation() {
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            onView(withText("My household · checklist & notes")).perform(scrollTo(), click())
            onView(withText("Stored drinking water & clean containers")).perform(scrollTo(), click())
            scenario.recreate()
            onView(withText("Stored drinking water & clean containers")).check(matches(isChecked()))
        }
    }

    @Test fun firstAidIsReachableInTwoTapsAndRelatedGuidesWork() {
        ActivityScenario.launch(EmergencyActivity::class.java).use {
            onView(withContentDescription("Show first-aid guides")).perform(scrollTo(), click())
            onView(withText("Choking: babies & children")).perform(scrollTo(), click())
            onView(withText("OFFLINE GUIDE · UNREVIEWED PREVIEW")).check(matches(isDisplayed()))
            onView(withText("CPR: babies & children")).perform(scrollTo(), click())
            onView(withText("CPR: babies & children")).check(matches(isDisplayed()))
        }
    }

    @Test fun exportSnapshotContainsPlanAndClearPreservesSettings() {
        val prefs = context.getSharedPreferences("emergency-handbook", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("check-water", true).putString("notes", "Meeting point: library\nContact: Example")
            .putString("people", "2").putString("days", "3").putString("language", "de").putFloat("reader-scale", 1.4f).commit()
        val exported = HouseholdPlan.export(prefs, "en", "2026-10-19")
        assertTrue(exported.contains("[x] Drinking water"))
        assertTrue(exported.contains("12 L"))
        assertTrue(exported.contains("Meeting point: library\nContact: Example"))
        assertFalse(exported.contains("reader-scale"))
        assertTrue(HouseholdPlan.export(prefs, "de", "2026-10-19").contains("12 L"))
        prefs.edit().putString("days", "9999999999999999").commit()
        assertTrue(HouseholdPlan.export(prefs, "en", "2026-10-19").contains("invalid input"))
        HouseholdPlan.clear(prefs)
        assertFalse(prefs.contains("notes"))
        assertFalse(prefs.contains("check-water"))
        assertEquals("de", prefs.getString("language", ""))
        assertEquals(1.4f, prefs.getFloat("reader-scale", 1f), 0.01f)
    }

    @Test fun cancelledExportLeavesPlanIntact() {
        val prefs = context.getSharedPreferences("emergency-handbook", Context.MODE_PRIVATE)
        prefs.edit().putString("notes", "Keep this note").commit()
        ActivityScenario.launch(EmergencyActivity::class.java).use {
            onView(withContentDescription("Open household plan")).perform(click())
            onView(withText("Export household plan…")).perform(scrollTo(), click())
            onView(withText(android.R.string.cancel)).perform(click())
            assertEquals("Keep this note", prefs.getString("notes", ""))
        }
    }

    @Test fun largeReaderTextAndLanguageSurviveRecreation() {
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            onView(withContentDescription("Search all offline guides")).perform(scrollTo(), replaceText("nuclear"), closeSoftKeyboard())
            onView(withText("Nuclear fallout & radiation")).perform(scrollTo(), click())
            onView(withText("A+ Larger")).perform(click())
            val prefs = context.getSharedPreferences("emergency-handbook", Context.MODE_PRIVATE)
            assertTrue(prefs.getFloat("reader-scale", 1f) > 1f)
            onView(withText("Deutsch")).perform(click())
            scenario.recreate()
            onView(withText("Nuklearer Fallout & Strahlung")).check(matches(isDisplayed()))
            onView(withText("A+ Größer")).check(matches(isDisplayed()))
        }
    }
}
