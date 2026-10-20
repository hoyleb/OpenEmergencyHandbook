package org.ligi.survivalmanual

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
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
import org.ligi.survivalmanual.model.HouseholdBackup
import org.ligi.survivalmanual.ui.EmergencyActivity

@RunWith(AndroidJUnit4::class)
class RecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("emergency-handbook", Context.MODE_PRIVATE)
    private fun read(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }
    @Before fun reset() { prefs.edit().clear().putString("language", "en").commit() }
    @Test fun corruptMainFileRecoversAllGuidesInBothLanguages() {
        for (lang in listOf("en", "de")) {
            val book = EmergencyHandbook({ path -> if (path == "handbook/articles.json") "broken JSON" else read(path) }, lang)
            assertTrue(book.recoveryMode); assertEquals(30, book.articles.size); assertEquals(0, book.missingGuideCount)
            assertNotNull(book.find("water")); assertTrue(book.search("Stromausfall").any { it.id == "power" })
        }
    }
    @Test fun damagedGuideFileIsOmittedAndRelatedLinksStayValid() {
        val book = EmergencyHandbook({ path -> when (path) {
            "handbook/articles.json" -> throw java.io.IOException("fixture")
            "handbook/recovery/water.json" -> read(path).replace("water", "modified")
            else -> read(path)
        } }, "en")
        assertEquals(29, book.articles.size); assertEquals(1, book.missingGuideCount); assertNull(book.find("water"))
        for (article in book.articles) for (id in article.related) assertNotNull(book.find(id))
    }
    @Test fun healthyMainFileWorksWithoutRecoveryManifest() {
        val book = EmergencyHandbook({ path -> if (path.endsWith("index.json")) throw java.io.IOException("fixture") else read(path) }, "en")
        assertFalse(book.recoveryMode); assertEquals(30, book.articles.size)
    }
    @Test fun allContentUnavailableStillAllowsPlanAndBackupAccess() {
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val empty = EmergencyHandbook({ _ -> throw java.io.IOException("fixture") }, "en")
                EmergencyActivity::class.java.getDeclaredField("handbook").apply { isAccessible = true }.set(activity, empty)
                EmergencyActivity::class.java.getDeclaredMethod("render").apply { isAccessible = true }.invoke(activity)
            }
            onView(withText("Guides unavailable")).check(matches(isDisplayed()))
            ScreenshotEvidence.capture("recovery-unavailable")
            onView(withTagValue(equalTo("tab-plan"))).perform(click())
            onView(withText("Backup / restore…")).check(matches(isDisplayed()))
        }
    }
    @Test fun corruptPreferenceTypesAndHistoryDoNotBlockReadingOrPlanning() {
        prefs.edit().putBoolean("language", false).putString("reader-scale", "broken").putInt("bookmarks", 7)
            .putString("recent-guides", "broken JSON").putInt("notes", 99).putString("check-water", "false").commit()
        ActivityScenario.launch(EmergencyActivity::class.java).use {
            onView(withContentDescription("Open safe water guide")).perform(click())
            onView(withText("Safe drinking water")).check(matches(isDisplayed()))
            onView(withTagValue(equalTo("tab-saved"))).perform(click())
            onView(withTagValue(equalTo("tab-plan"))).perform(click())
            onView(withText("Backup / restore…")).check(matches(isDisplayed()))
        }
    }
    @Test fun backupRetainsBookmarksWhenCatalogIsUnavailable() {
        prefs.edit().putStringSet("bookmarks", setOf("water")).commit()
        val encoded = HouseholdBackup.encode(HouseholdBackup.snapshot(prefs))
        assertEquals(setOf("water"), HouseholdBackup.parse(encoded, null).bookmarks)
    }
}
