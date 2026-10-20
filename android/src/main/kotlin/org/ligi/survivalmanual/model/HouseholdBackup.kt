package org.ligi.survivalmanual.model

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** A bounded, versioned data file. No HTML, executable content, URIs or settings. */
data class HouseholdSnapshot(val checks: Map<String, Boolean>, val people: String, val days: String,
                             val notes: String, val bookmarks: Set<String>)

object HouseholdBackup {
    const val MAX_BYTES = 512 * 1024
    const val MAX_NOTES = 65536
    val checklistKeys = listOf("water", "food", "medicine", "light", "radio", "warmth", "hygiene", "documents")
    private const val FORMAT = "open-emergency-household"

    fun snapshot(prefs: SharedPreferences): HouseholdSnapshot {
        val values = prefs.all
        fun estimate(key: String, fallback: String, range: IntRange): String {
            val value = values[key] as? String ?: fallback
            val number = value.toIntOrNull()
            return if (number != null && number in range) value else ""
        }
        return HouseholdSnapshot(checklistKeys.associateWith { values["check-$it"] as? Boolean ?: false },
            estimate("people", "1", 1..50), estimate("days", "3", 1..90), values["notes"] as? String ?: "",
            (values["bookmarks"] as? Set<*>)?.filterIsInstance<String>()?.toSet().orEmpty())
    }

    fun encode(data: HouseholdSnapshot): String = JSONObject().put("format", FORMAT).put("version", 1)
        .put("plan", JSONObject().put("checks", JSONObject(data.checks)).put("people", data.people)
            .put("days", data.days).put("notes", data.notes))
        .put("bookmarks", JSONArray(data.bookmarks.sorted())).toString(2)

    fun read(input: InputStream, validGuideIds: Set<String>): HouseholdSnapshot {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val size = input.read(chunk)
            if (size == -1) break
            require(out.size() + size <= MAX_BYTES) { "Backup too large" }
            out.write(chunk, 0, size)
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return parse(decoder.decode(ByteBuffer.wrap(out.toByteArray())).toString(), validGuideIds)
    }

    fun parse(encoded: String, validGuideIds: Set<String>): HouseholdSnapshot {
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup too large" }
        val root = JSONObject(encoded)
        fun fields(obj: JSONObject, expected: Set<String>) {
            require(obj.keys().asSequence().toSet() == expected) { "Unexpected backup fields" }
        }
        fun string(obj: JSONObject, key: String) = (obj.get(key) as? String) ?: throw IllegalArgumentException("Invalid text field")
        fields(root, setOf("format", "version", "plan", "bookmarks"))
        require(root.get("format") == FORMAT && root.get("version") == 1) { "Unsupported backup" }
        val plan = root.getJSONObject("plan")
        fields(plan, setOf("checks", "people", "days", "notes"))
        val checks = plan.getJSONObject("checks")
        fields(checks, checklistKeys.toSet())
        val checked = checklistKeys.associateWith { (checks.get(it) as? Boolean) ?: throw IllegalArgumentException("Invalid checklist") }
        val people = string(plan, "people"); val days = string(plan, "days"); val notes = string(plan, "notes")
        require(people.isEmpty() || (people.matches(Regex("[0-9]{1,2}")) && people.toIntOrNull()?.let { it in 1..50 } == true)) { "Invalid people" }
        require(days.isEmpty() || (days.matches(Regex("[0-9]{1,2}")) && days.toIntOrNull()?.let { it in 1..90 } == true)) { "Invalid days" }
        require(notes.length <= MAX_NOTES) { "Notes too long" }
        val bookmarks = root.getJSONArray("bookmarks")
        require(bookmarks.length() <= 1000) { "Too many bookmarks" }
        val ids = (0 until bookmarks.length()).map { (bookmarks.get(it) as? String) ?: throw IllegalArgumentException("Invalid bookmark") }
        require(ids.toSet().size == ids.size && ids.all { it.matches(Regex("[a-z0-9-]{1,80}")) }) { "Invalid bookmarks" }
        // A valid backup from another edition remains usable; retired guide IDs
        // are ignored, never interpreted as routes, URLs or file paths.
        return HouseholdSnapshot(checked, people, days, notes, ids.filter { it in validGuideIds }.toSet())
    }

    /** Validation precedes this call. Replace only the enumerated fields in one
     * preferences transaction; on a disk failure restore the previous in-memory
     * values too and report failure. Never clear unrelated settings/history. */
    fun restore(prefs: SharedPreferences, data: HouseholdSnapshot): Boolean {
        val fields = checklistKeys.map { "check-$it" } + listOf("people", "days", "notes", "bookmarks")
        val before = prefs.all.filterKeys { it in fields }
        fun write(snapshot: HouseholdSnapshot): Boolean {
            val edit = prefs.edit()
            edit.putString("people", snapshot.people).putString("days", snapshot.days)
                .putString("notes", snapshot.notes).putStringSet("bookmarks", snapshot.bookmarks)
            snapshot.checks.forEach { (key, value) -> edit.putBoolean("check-$key", value) }
            return edit.commit()
        }
        if (write(data)) return true
        val rollback = prefs.edit()
        fields.forEach { rollback.remove(it) }
        before.forEach { (key, value) -> when (value) {
            is String -> rollback.putString(key, value)
            is Boolean -> rollback.putBoolean(key, value)
            is Int -> rollback.putInt(key, value)
            is Long -> rollback.putLong(key, value)
            is Float -> rollback.putFloat(key, value)
            is Set<*> -> rollback.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        rollback.commit()
        return false
    }
}
