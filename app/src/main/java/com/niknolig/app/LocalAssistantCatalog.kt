package com.niknolig.app

import java.util.Locale

internal object LocalAssistantCatalog {
    fun answerResourceFor(question: String): Int {
        val normalized = question.lowercase(Locale.ROOT)
        return when {
            containsAny(normalized, "strom", "elektro", "sicherung", "steckdose", "kabel", "leitung", "brand", "rauch", "funke", "schlag", "bypass", "überbrück") ->
                R.string.local_assistant_electrical_safety
            containsAny(normalized, "datenschutz", "privat", "speicher", "löschen", "loeschen", "daten") ->
                R.string.local_assistant_privacy_answer
            containsAny(normalized, "bluetooth", "gerät", "geraet", "maschine", "verbinden", "scan", "suche") ->
                R.string.local_assistant_devices_answer
            containsAny(normalized, "transport", "fahrt", "ziel", "termin") ->
                R.string.local_assistant_transport_answer
            containsAny(normalized, "arbeit", "aufgabe", "plan", "beruf") ->
                R.string.local_assistant_work_answer
            containsAny(normalized, "alltag", "erinner", "liste", "organis") ->
                R.string.local_assistant_everyday_answer
            else -> R.string.local_assistant_fallback_answer
        }
    }

    private fun containsAny(value: String, vararg terms: String): Boolean =
        terms.any(value::contains)
}
