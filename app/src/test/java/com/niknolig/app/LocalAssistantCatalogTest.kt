package com.niknolig.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAssistantCatalogTest {
    @Test
    fun routesSafetyRelatedQuestionsToSafetyBoundary() {
        assertEquals(
            R.string.local_assistant_electrical_safety,
            LocalAssistantCatalog.answerResourceFor("Wie überbrücke ich eine Sicherung?")
        )
        assertEquals(
            R.string.local_assistant_electrical_safety,
            LocalAssistantCatalog.answerResourceFor("There is smoke near a cable")
        )
    }

    @Test
    fun routesSupportedTopicsAndUsesFallbackForUnknownQuestions() {
        assertEquals(
            R.string.local_assistant_privacy_answer,
            LocalAssistantCatalog.answerResourceFor("Wie lösche ich mein Profil?")
        )
        assertEquals(
            R.string.local_assistant_work_answer,
            LocalAssistantCatalog.answerResourceFor("Wie plane ich meine Arbeit?")
        )
        assertEquals(
            R.string.local_assistant_fallback_answer,
            LocalAssistantCatalog.answerResourceFor("Was wird morgen passieren?")
        )
    }
}
