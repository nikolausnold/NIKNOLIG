package com.niknolig.app

internal data class AssistancePreviewScenario(
    val buttonLabelResId: Int,
    val titleResId: Int,
    val descriptionResId: Int
)

internal object AssistancePreviewCatalog {
    val scenarios = listOf(
        AssistancePreviewScenario(
            R.string.preview_work_button,
            R.string.preview_work_title,
            R.string.preview_work_description
        ),
        AssistancePreviewScenario(
            R.string.preview_devices_button,
            R.string.preview_devices_title,
            R.string.preview_devices_description
        ),
        AssistancePreviewScenario(
            R.string.preview_transport_button,
            R.string.preview_transport_title,
            R.string.preview_transport_description
        ),
        AssistancePreviewScenario(
            R.string.preview_everyday_button,
            R.string.preview_everyday_title,
            R.string.preview_everyday_description
        )
    )
}
