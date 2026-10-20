package org.ligi.survivalmanual

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ligi.survivalmanual.model.HouseholdBackup
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
class HouseholdBackupTest {
    private val prefs = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("backup-test", Context.MODE_PRIVATE)
    private val guides = setOf("water", "fallout")
    @Before fun reset() { prefs.edit().clear().putString("notes", "Ärztin: Beispiel\nMeeting point: library 🔦")
        .putBoolean("check-water", true).putString("people", "2").putString("days", "10")
        .putStringSet("bookmarks", setOf("water")).putString("language", "de").commit() }
    private fun encoded() = HouseholdBackup.encode(HouseholdBackup.snapshot(prefs))
    private fun rejected(value: String) {
        val before = prefs.all.toMap()
        try { HouseholdBackup.parse(value, guides); fail("Invalid backup accepted") } catch (_: IllegalArgumentException) { }
        catch (_: org.json.JSONException) { }
        assertEquals(before, prefs.all)
    }

    @Test fun roundTripRestoresUnicodePlanAndBookmarksWithoutSettings() {
        val expected = HouseholdBackup.snapshot(prefs)
        val data = HouseholdBackup.read(ByteArrayInputStream(encoded().toByteArray()), guides)
        prefs.edit().putString("notes", "Newer note").putBoolean("check-water", false).putString("language", "en").commit()
        assertTrue(HouseholdBackup.restore(prefs, data))
        assertEquals(expected, HouseholdBackup.snapshot(prefs)); assertEquals("en", prefs.getString("language", ""))
    }
    @Test fun unsupportedVersionsCoercedTypesAndUnknownFieldsAreRejected() {
        rejected(JSONObject(encoded()).put("version", 2).toString())
        rejected(JSONObject(encoded()).put("version", "1").toString())
        rejected(JSONObject(encoded()).put("format", "other-app").toString())
        rejected(JSONObject(encoded()).put("script", "anything").toString())
        val types = JSONObject(encoded()); types.getJSONObject("plan").getJSONObject("checks").put("water", "true"); rejected(types.toString())
        val number = JSONObject(encoded()); number.getJSONObject("plan").put("people", "999"); rejected(number.toString())
        val note = JSONObject(encoded()); note.getJSONObject("plan").put("notes", "x".repeat(HouseholdBackup.MAX_NOTES + 1)); rejected(note.toString())
        rejected("not a backup")
    }
    @Test fun oversizedAndMalformedUtf8FilesAreRejectedBeforeRestore() {
        for (bytes in listOf(ByteArray(HouseholdBackup.MAX_BYTES + 1) { 65 }, byteArrayOf(0xc3.toByte(), 0x28))) {
            val before = prefs.all.toMap()
            try { HouseholdBackup.read(ByteArrayInputStream(bytes), guides); fail("Invalid bytes accepted") } catch (_: Exception) { }
            assertEquals(before, prefs.all)
        }
    }
    @Test fun RetiredGuideBookmarksDoNotBlockRestoringPersonalNotes() {
        val json = JSONObject(encoded()).put("bookmarks", org.json.JSONArray(listOf("water", "retired-guide")))
        val data = HouseholdBackup.parse(json.toString(), guides)
        assertEquals(setOf("water"), data.bookmarks); assertTrue(data.notes.contains("Ärztin"))
    }
    @Test fun invalidEstimatesStayBlankWithoutDiscardingNotes() {
        prefs.edit().putString("people", "9999999999999").putString("days", "").commit()
        val data = HouseholdBackup.parse(encoded(), guides)
        assertEquals("", data.people); assertEquals("", data.days); assertTrue(data.notes.contains("library"))
    }
    @Test fun failedCommitRollsBackPlanAndPreservesUnrelatedPreferences() {
        val before = HouseholdBackup.snapshot(prefs)
        var writes = 0
        val failing = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor {
                val delegate = prefs.edit()
                return object : SharedPreferences.Editor by delegate {
                    override fun commit(): Boolean { val saved = delegate.commit(); return if (++writes == 1) false else saved }
                }
            }
        }
        assertFalse(HouseholdBackup.restore(failing, before.copy(notes = "Replacement")))
        assertEquals(before, HouseholdBackup.snapshot(prefs)); assertEquals("de", prefs.getString("language", ""))
    }
}
