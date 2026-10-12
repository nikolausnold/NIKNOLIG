package com.niknolig.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView

class LearningProfileActivity : Activity() {
    private val learningPreferences by lazy {
        getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
    }

    private lateinit var learningEnabledSwitch: Switch
    private lateinit var responseStyleGroup: RadioGroup
    private lateinit var feedbackSummary: TextView
    private lateinit var helpfulButton: Button
    private lateinit var unclearButton: Button
    private lateinit var responsePreview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning_profile)

        learningEnabledSwitch = findViewById(R.id.learning_enabled_switch)
        responseStyleGroup = findViewById(R.id.response_style_group)
        feedbackSummary = findViewById(R.id.learning_feedback_summary)
        helpfulButton = findViewById(R.id.feedback_helpful_button)
        unclearButton = findViewById(R.id.feedback_unclear_button)
        responsePreview = findViewById(R.id.response_style_preview)

        learningEnabledSwitch.isChecked =
            learningPreferences.getBoolean(KEY_LEARNING_ENABLED, false)
        responseStyleGroup.check(
            when (learningPreferences.getString(KEY_RESPONSE_STYLE, STYLE_CONCISE)) {
                STYLE_STEP_BY_STEP -> R.id.response_style_step_by_step
                STYLE_DETAILED -> R.id.response_style_detailed
                else -> R.id.response_style_concise
            }
        )

        learningEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            learningPreferences.edit().putBoolean(KEY_LEARNING_ENABLED, isChecked).apply()
            updateLearningControls()
        }

        responseStyleGroup.setOnCheckedChangeListener { _, checkedId ->
            val style = when (checkedId) {
                R.id.response_style_step_by_step -> STYLE_STEP_BY_STEP
                R.id.response_style_detailed -> STYLE_DETAILED
                else -> STYLE_CONCISE
            }
            learningPreferences.edit().putString(KEY_RESPONSE_STYLE, style).apply()
            updateResponsePreview(checkedId)
        }

        helpfulButton.setOnClickListener { recordFeedback(KEY_HELPFUL_COUNT) }
        unclearButton.setOnClickListener { recordFeedback(KEY_UNCLEAR_COUNT) }
        findViewById<Button>(R.id.clear_learning_data_button).setOnClickListener {
            confirmClearLearningData()
        }

        updateResponsePreview(responseStyleGroup.checkedRadioButtonId)
        updateLearningControls()
    }

    private fun updateLearningControls() {
        val enabled = learningEnabledSwitch.isChecked
        helpfulButton.isEnabled = enabled
        unclearButton.isEnabled = enabled

        val helpfulCount = learningPreferences.getLong(KEY_HELPFUL_COUNT, 0)
        val unclearCount = learningPreferences.getLong(KEY_UNCLEAR_COUNT, 0)
        feedbackSummary.text = getString(
            R.string.learning_feedback_summary,
            helpfulCount,
            unclearCount
        )
    }

    private fun updateResponsePreview(checkedId: Int) {
        val previewResource = when (checkedId) {
            R.id.response_style_step_by_step -> R.string.response_style_preview_step_by_step
            R.id.response_style_detailed -> R.string.response_style_preview_detailed
            else -> R.string.response_style_preview_concise
        }
        responsePreview.setText(previewResource)
    }

    private fun recordFeedback(counterKey: String) {
        if (!learningEnabledSwitch.isChecked) return

        val currentCount = learningPreferences.getLong(counterKey, 0)
        learningPreferences.edit().putLong(counterKey, currentCount + 1).apply()
        updateLearningControls()
    }

    private fun confirmClearLearningData() {
        AlertDialog.Builder(this)
            .setTitle(R.string.learning_clear_confirmation_title)
            .setMessage(R.string.learning_clear_confirmation_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.learning_clear_confirmation_action) { _, _ ->
                learningPreferences.edit().clear().apply()
                learningEnabledSwitch.isChecked = false
                responseStyleGroup.check(R.id.response_style_concise)
                updateLearningControls()
            }
            .show()
    }

    private companion object {
        const val PREFERENCES_FILE = "electro_angel_learning"
        const val KEY_LEARNING_ENABLED = "learning_enabled"
        const val KEY_RESPONSE_STYLE = "response_style"
        const val KEY_HELPFUL_COUNT = "helpful_count"
        const val KEY_UNCLEAR_COUNT = "unclear_count"
        const val STYLE_CONCISE = "concise"
        const val STYLE_STEP_BY_STEP = "step_by_step"
        const val STYLE_DETAILED = "detailed"
    }
}
