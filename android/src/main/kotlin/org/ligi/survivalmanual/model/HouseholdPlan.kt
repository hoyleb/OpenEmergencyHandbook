package org.ligi.survivalmanual.model

import android.content.SharedPreferences

/** Explicitly limited to household fields: settings are not personal plan data. */
object HouseholdPlan {
    private val keys = listOf("water", "food", "medicine", "light", "radio", "warmth", "hygiene", "documents")
    private val english = listOf("Drinking water and containers", "Food and tin opener", "Medicines and first aid", "Torch and power bank", "Radio and written contacts", "Warmth and safe shelter", "Hygiene and toilet plan", "Documents, cash and evacuation bag")
    private val german = listOf("Trinkwasser und Behälter", "Lebensmittel und Dosenöffner", "Medikamente und Erste Hilfe", "Taschenlampe und Powerbank", "Radio und Kontakte auf Papier", "Wärme und sicherer Schutz", "Hygiene und Toilettenplan", "Dokumente, Bargeld und Notgepäck")

    fun clear(prefs: SharedPreferences) {
        val edit = prefs.edit().remove("people").remove("days").remove("notes")
        keys.forEach { edit.remove("check-$it") }
        edit.apply()
    }

    fun export(prefs: SharedPreferences, language: String, edition: String): String {
        val de = language == "de"
        val p = prefs.getString("people", "1")?.toIntOrNull()
        val d = prefs.getString("days", "3")?.toIntOrNull()
        return buildString {
            appendLine(if (de) "OPEN EMERGENCY — MEIN HAUSHALT" else "OPEN EMERGENCY — MY HOUSEHOLD")
            appendLine(if (de) "Handbuchausgabe: $edition. Persönlicher Plan — vertraulich." else "Handbook edition: $edition. Personal plan — private.")
            appendLine(if (de) "112: Notruf in Deutschland/EU. Keine Live-Warnungen." else "112: emergency number in Germany/EU. No live alerts.")
            appendLine()
            keys.forEachIndexed { i, key ->
                appendLine("[${if (prefs.getBoolean("check-$key", false)) "x" else " "}] ${(if (de) german else english)[i]}")
            }
            appendLine()
            if (p != null && d != null && p in 1..50 && d in 1..90) {
                appendLine(if (de) "$p Personen × $d Tage × 2 L = ${p * d * 2} L für Trinken/Kochen als Ausgangspunkt." else "$p people × $d days × 2 L = ${p * d * 2} L starting supply for drinking/cooking.")
            } else appendLine(if (de) "Wasservorrat: ungültige Eingabe — neu berechnen." else "Water estimate: invalid input — recalculate.")
            appendLine(if (de) "Zusätzlich Wasser für Hygiene, Hitze, Gesundheit und Tiere. Keine Garantie des Gesamtbedarfs." else "Add water for hygiene, heat, health needs and pets. Not a guarantee of total needs.")
            appendLine()
            appendLine(if (de) "KONTAKTE, TREFFPUNKT UND BEDÜRFNISSE" else "CONTACTS, MEETING POINT AND NEEDS")
            appendLine(prefs.getString("notes", ""))
            appendLine()
            appendLine(if (de) "Auch auf Papier aufbewahren. App-Löschung entfernt keine exportierten Kopien." else "Keep a printed copy. Clearing the app does not delete exported copies.")
        }
    }
}
