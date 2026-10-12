package com.niknolig.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistancePreviewCatalogTest {
    @Test
    fun providesOnePreparedScenarioForEachRequestedArea() {
        val scenarios = AssistancePreviewCatalog.scenarios

        assertEquals(
            listOf(
                R.string.preview_work_button,
                R.string.preview_devices_button,
                R.string.preview_transport_button,
                R.string.preview_everyday_button
            ),
            scenarios.map { it.buttonLabelResId }
        )
    }

    @Test
    fun allScenariosHaveDistinctLabelsAndDescriptions() {
        val scenarios = AssistancePreviewCatalog.scenarios

        assertEquals(scenarios.size, scenarios.map { it.buttonLabelResId }.toSet().size)
        assertEquals(scenarios.size, scenarios.map { it.descriptionResId }.toSet().size)
        assertTrue(scenarios.all { it.titleResId != 0 && it.descriptionResId != 0 })
    }
}
