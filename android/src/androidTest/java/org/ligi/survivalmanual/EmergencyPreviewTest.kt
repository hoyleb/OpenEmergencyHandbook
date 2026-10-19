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
            assertEquals(15, book.articles.size)
            for (id in listOf("power", "cyber", "earthquake", "fallout", "flood", "pandemic", "first-aid", "water")) {
                assertTrue(book.find(id)!!.body.isNotBlank())
            }
        }
        assertTrue(EmergencyHandbook(context.assets, "en").search("blackout").any { it.id == "power" })
        assertTrue(EmergencyHandbook(context.assets, "de").search("stromausfall").any { it.id == "power" })
        assertTrue(EmergencyHandbook(context.assets, "de").search("uberschwemmung").any { it.id == "flood" })
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
}
