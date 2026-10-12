package com.niknolig.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
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
        setupLocalAssistant()
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

    private fun setupLocalAssistant() {
        val question = findViewById<EditText>(R.id.assistant_question)
        val answer = findViewById<TextView>(R.id.assistant_answer)
        val consent = findViewById<CheckBox>(R.id.interest_consent)
        val status = findViewById<TextView>(R.id.interest_profile_status)
        val interests = interestCheckboxes()
        val preferences = getSharedPreferences(INTEREST_PROFILE_PREFERENCES, MODE_PRIVATE)
        val savedProfile = preferences.getBoolean(INTEREST_PROFILE_ENABLED, false)
        consent.isChecked = savedProfile
        if (savedProfile) {
            val selected = preferences.getStringSet(INTEREST_PROFILE_CATEGORIES, mutableSetOf()).orEmpty()
            interests.forEach { (key, checkbox) -> checkbox.isChecked = key in selected }
            status.text = profileStatus(selected, interests)
        }

        consent.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                preferences.edit().clear().apply()
                interests.values.forEach { it.isChecked = false }
                status.setText(R.string.interest_profile_disabled_status)
            }
        }

        findViewById<Button>(R.id.assistant_ask_button).setOnClickListener {
            val userQuestion = question.text.toString().trim()
            if (userQuestion.isEmpty()) {
                answer.setText(R.string.local_assistant_empty_question)
                return@setOnClickListener
            }

            val answerResource = LocalAssistantCatalog.answerResourceFor(userQuestion)
            val personalizedTopics = if (
                answerResource == R.string.local_assistant_fallback_answer &&
                preferences.getBoolean(INTEREST_PROFILE_ENABLED, false)
            ) {
                preferences.getStringSet(INTEREST_PROFILE_CATEGORIES, mutableSetOf())
                    .orEmpty()
                    .mapNotNull { key -> interests[key]?.text?.toString() }
            } else {
                emptyList()
            }
            answer.text = if (personalizedTopics.isNotEmpty()) {
                getString(
                    R.string.local_assistant_personalized_fallback,
                    getString(answerResource),
                    personalizedTopics.joinToString(", ")
                )
            } else {
                getString(answerResource)
            }
            question.text.clear()
        }

        findViewById<Button>(R.id.interest_profile_save_button).setOnClickListener {
            if (!consent.isChecked) {
                preferences.edit().clear().apply()
                interests.values.forEach { it.isChecked = false }
                status.setText(R.string.interest_profile_disabled_status)
                return@setOnClickListener
            }

            val selected = interests.filterValues { it.isChecked }.keys.toSet()
            preferences.edit()
                .putBoolean(INTEREST_PROFILE_ENABLED, true)
                .putStringSet(INTEREST_PROFILE_CATEGORIES, selected)
                .apply()
            status.text = profileStatus(selected, interests)
        }

        findViewById<Button>(R.id.interest_profile_clear_button).setOnClickListener {
            preferences.edit().clear().apply()
            consent.isChecked = false
            interests.values.forEach { it.isChecked = false }
            status.setText(R.string.interest_profile_cleared_status)
        }
    }

    private fun interestCheckboxes(): Map<String, CheckBox> = mapOf(
        INTEREST_WORK to findViewById<CheckBox>(R.id.interest_work),
        INTEREST_DEVICES to findViewById<CheckBox>(R.id.interest_devices),
        INTEREST_TRANSPORT to findViewById<CheckBox>(R.id.interest_transport),
        INTEREST_EVERYDAY to findViewById<CheckBox>(R.id.interest_everyday)
    )

    private fun profileStatus(
        selected: Set<String>,
        interests: Map<String, CheckBox>
    ): String {
        val labels = selected.mapNotNull { key -> interests[key]?.text?.toString() }
        return if (labels.isEmpty()) {
            getString(R.string.interest_profile_enabled_empty_status)
        } else {
            getString(R.string.interest_profile_enabled_status, labels.joinToString(", "))
        }
    }

    private companion object {
        const val INTEREST_PROFILE_PREFERENCES = "local_interest_profile"
        const val INTEREST_PROFILE_ENABLED = "enabled"
        const val INTEREST_PROFILE_CATEGORIES = "categories"
        const val INTEREST_WORK = "work"
        const val INTEREST_DEVICES = "devices"
        const val INTEREST_TRANSPORT = "transport"
        const val INTEREST_EVERYDAY = "everyday"
    }
}
