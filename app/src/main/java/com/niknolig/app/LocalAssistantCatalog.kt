package com.niknolig.app

import java.util.Locale

internal object LocalAssistantCatalog {
    fun answerResourceFor(question: String): Int {
        val normalized = question.lowercase(Locale.ROOT)
        return when {
            containsAny(
                normalized,
                "strom", "elektro", "electric", "sicherung", "fuse", "steckdose", "socket",
                "kabel", "cable", "leitung", "wire", "brand", "fire", "rauch", "smoke",
                "funke", "spark", "schlag", "shock", "bypass", "überbrück"
            ) ->
                R.string.local_assistant_electrical_safety
            containsAny(
                normalized,
                "datenschutz", "privacy", "privat", "private", "speicher", "storage",
                "löschen", "loeschen", "delete", "daten", "data"
            ) ->
                R.string.local_assistant_privacy_answer
            containsAny(
                normalized,
                "bluetooth", "gerät", "geraet", "device", "maschine", "machine", "verbinden",
                "connect", "scan", "suche", "search"
            ) ->
                R.string.local_assistant_devices_answer
            containsAny(
                normalized,
                "transport", "fahrt", "trip", "ziel", "destination", "termin", "appointment"
            ) ->
                R.string.local_assistant_transport_answer
            containsAny(normalized, "arbeit", "aufgabe", "task", "plan", "beruf", "work", "job") ->
                R.string.local_assistant_work_answer
            containsAny(normalized, "alltag", "everyday", "erinner", "remind", "liste", "list", "organis", "organiz") ->
                R.string.local_assistant_everyday_answer
            else -> R.string.local_assistant_fallback_answer
        }
    }

    private fun containsAny(value: String, vararg terms: String): Boolean =
        value.split(Regex("[^\\p{L}]+"))
            .any { word -> terms.any { term -> word.startsWith(term) } }
}
