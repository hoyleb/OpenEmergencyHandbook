package org.ligi.survivalmanual

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ligi.survivalmanual.model.HouseholdBackup
import org.ligi.survivalmanual.model.HouseholdTransfer
import org.ligi.survivalmanual.model.TransferState
import org.ligi.survivalmanual.ui.EmergencyActivity
import java.io.File

@RunWith(AndroidJUnit4::class)
class HouseholdTransferTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("emergency-handbook", Context.MODE_PRIVATE)
    private val fixture get() = File(context.cacheDir, "backup-fixture.json")
    @Before fun setup() { prefs.edit().clear().putString("language", "en").putString("notes", "Existing plan").commit(); Intents.init() }
    @After fun teardown() { Intents.release(); fixture.delete() }
    private fun chooseRestore() {
        onView(withTagValue(equalTo("tab-plan"))).perform(click())
        onView(withText("Backup / restore…")).perform(click())
        onView(withText("Restore JSON backup")).perform(click())
    }
    private fun settle(scenario: ActivityScenario<EmergencyActivity>) {
        val deadline = System.currentTimeMillis() + 8000
        var busy = true
        while (busy && System.currentTimeMillis() < deadline) {
            scenario.onActivity { busy = ViewModelProvider(it).get(HouseholdTransfer::class.java).state.value == TransferState.Busy }
            if (busy) Thread.sleep(50)
        }
        instrumentation.waitForIdleSync(); assertFalse("File operation did not finish", busy)
    }
    private fun validFixture() {
        val source = HouseholdBackup.snapshot(prefs).copy(notes = "Restored note: Ärztin\nMeeting point: library", bookmarks = setOf("water"))
        fixture.writeText(HouseholdBackup.encode(source))
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(fixture))))
    }
    @Test fun restoreRequiresConfirmationAndPreviewSurvivesRotation() {
        validFixture()
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            chooseRestore(); settle(scenario)
            onView(withText("Review backup before replacing")).check(matches(isDisplayed()))
            assertEquals("Existing plan", prefs.getString("notes", ""))
            scenario.recreate()
            onView(withText("Review backup before replacing")).check(matches(isDisplayed()))
            onView(withText("Replace plan & saved guides")).perform(click()); settle(scenario)
            assertTrue(prefs.getString("notes", "")!!.contains("Ärztin"))
            assertEquals(setOf("water"), prefs.getStringSet("bookmarks", emptySet()))
            assertEquals("en", prefs.getString("language", ""))
        }
    }
    @Test fun cancellingRestoreKeepsCurrentData() {
        validFixture()
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            chooseRestore(); settle(scenario)
            onView(withText(android.R.string.cancel)).perform(click())
            assertEquals("Existing plan", prefs.getString("notes", "")); assertFalse(prefs.contains("bookmarks"))
        }
    }
    @Test fun invalidFileCannotReplacePlan() {
        fixture.writeText("{\"format\":\"not-this-app\",\"notes\":\"overwrite\"}")
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(fixture))))
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            chooseRestore(); settle(scenario)
            assertEquals("Existing plan", prefs.getString("notes", ""))
            onView(withText("Backup / restore…")).check(matches(isDisplayed()))
        }
    }
    @Test fun backupWritesRestorableSnapshotThroughFilePickerContract() {
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(fixture))))
        ActivityScenario.launch(EmergencyActivity::class.java).use { scenario ->
            onView(withTagValue(equalTo("tab-plan"))).perform(click())
            onView(withText("Backup / restore…")).perform(click())
            onView(withText("Save JSON backup")).perform(click())
            onView(withText("Choose file")).perform(click()); settle(scenario)
            val restored = fixture.inputStream().use { HouseholdBackup.read(it, setOf("water")) }
            assertEquals("Existing plan", restored.notes)
            assertEquals("Existing plan", prefs.getString("notes", ""))
        }
    }
}
