package com.niknolig.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ElektroEngelActivity : Activity() {
    private val scenarioButtons = mutableListOf<Button>()
    private lateinit var previewTitle: TextView
    private lateinit var previewDescription: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_elektro_engel)

        val scrollView = findViewById<android.widget.ScrollView>(R.id.elektro_engel_scroll_view)
        findViewById<Button>(R.id.niknolig_nav_button).setOnClickListener { finish() }
        findViewById<Button>(R.id.elektro_engel_nav_button).setOnClickListener {
            scrollView.smoothScrollTo(0, 0)
        }

        previewTitle = findViewById(R.id.scenario_preview_title)
        previewDescription = findViewById(R.id.scenario_preview_description)
        val scenarioContainer = findViewById<LinearLayout>(R.id.scenario_button_container)
        AssistancePreviewCatalog.scenarios.forEachIndexed { index, scenario ->
            val button = Button(this).apply {
                setText(scenario.buttonLabelResId)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = resources.getDimensionPixelSize(R.dimen.space_small)
                }
                setOnClickListener { showScenario(index) }
            }
            scenarioButtons.add(button)
            scenarioContainer.addView(button)
        }
        showScenario(0)
    }

    private fun showScenario(selectedIndex: Int) {
        AssistancePreviewCatalog.scenarios.forEachIndexed { index, scenario ->
            val button = scenarioButtons[index]
            val label = getString(scenario.buttonLabelResId)
            button.text = if (index == selectedIndex) {
                getString(R.string.preview_scenario_selected, label)
            } else {
                label
            }
            button.isSelected = index == selectedIndex
        }

        AssistancePreviewCatalog.scenarios[selectedIndex].let { scenario ->
            previewTitle.setText(scenario.titleResId)
            previewDescription.setText(scenario.descriptionResId)
        }
    }
}
