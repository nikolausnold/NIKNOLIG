package com.niknolig.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
    private lateinit var correctionButton: Button
    private lateinit var responsePreview: TextView
    private lateinit var sessionSummaryInput: EditText
    private lateinit var sessionSummaryStatus: TextView
    private var isResettingLearningProfile = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning_profile)

        findViewById<Button>(R.id.learning_profile_back_button).setOnClickListener {
            finish()
        }

        learningEnabledSwitch = findViewById(R.id.learning_enabled_switch)
        responseStyleGroup = findViewById(R.id.response_style_group)
        feedbackSummary = findViewById(R.id.learning_feedback_summary)
        helpfulButton = findViewById(R.id.feedback_helpful_button)
        unclearButton = findViewById(R.id.feedback_unclear_button)
        correctionButton = findViewById(R.id.feedback_correction_button)
        responsePreview = findViewById(R.id.response_style_preview)
        sessionSummaryInput = findViewById(R.id.session_summary_input)
        sessionSummaryStatus = findViewById(R.id.session_summary_status)

        learningEnabledSwitch.isChecked =
            learningPreferences.getBoolean(KEY_LEARNING_ENABLED, false)
        sessionSummaryInput.setText(learningPreferences.getString(KEY_SESSION_SUMMARY, ""))
        responseStyleGroup.check(
            when (learningPreferences.getString(KEY_RESPONSE_STYLE, STYLE_CONCISE)) {
                STYLE_STEP_BY_STEP -> R.id.response_style_step_by_step
                STYLE_DETAILED -> R.id.response_style_detailed
                else -> R.id.response_style_concise
            }
        )

        learningEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isResettingLearningProfile) return@setOnCheckedChangeListener
            learningPreferences.edit().putBoolean(KEY_LEARNING_ENABLED, isChecked).apply()
            updateLearningControls()
        }

        responseStyleGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isResettingLearningProfile) return@setOnCheckedChangeListener
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
        correctionButton.setOnClickListener { recordFeedback(KEY_CORRECTION_COUNT) }
        findViewById<Button>(R.id.save_session_summary_button).setOnClickListener {
            saveSessionSummary()
        }
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
        correctionButton.isEnabled = enabled

        val helpfulCount = learningPreferences.getLong(KEY_HELPFUL_COUNT, 0)
        val unclearCount = learningPreferences.getLong(KEY_UNCLEAR_COUNT, 0)
        val correctionCount = learningPreferences.getLong(KEY_CORRECTION_COUNT, 0)
        feedbackSummary.text = getString(
            R.string.learning_feedback_summary,
            helpfulCount,
            unclearCount,
            correctionCount
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

    private fun saveSessionSummary() {
        val summary = sessionSummaryInput.text.toString().trim()
        if (summary.isEmpty()) {
            learningPreferences.edit().remove(KEY_SESSION_SUMMARY).apply()
            sessionSummaryStatus.setText(R.string.session_summary_empty_status)
            return
        }

        learningPreferences.edit().putString(KEY_SESSION_SUMMARY, summary).apply()
        sessionSummaryStatus.setText(R.string.session_summary_saved_status)
    }

    private fun confirmClearLearningData() {
        AlertDialog.Builder(this)
            .setTitle(R.string.learning_clear_confirmation_title)
            .setMessage(R.string.learning_clear_confirmation_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.learning_clear_confirmation_action) { _, _ ->
                learningPreferences.edit().clear().apply()
                isResettingLearningProfile = true
                try {
                    learningEnabledSwitch.isChecked = false
                    responseStyleGroup.check(R.id.response_style_concise)
                    sessionSummaryInput.text.clear()
                } finally {
                    isResettingLearningProfile = false
                }
                updateResponsePreview(R.id.response_style_concise)
                sessionSummaryStatus.setText(R.string.session_summary_cleared_status)
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
        const val KEY_CORRECTION_COUNT = "correction_count"
        const val KEY_SESSION_SUMMARY = "session_summary"
        const val STYLE_CONCISE = "concise"
        const val STYLE_STEP_BY_STEP = "step_by_step"
        const val STYLE_DETAILED = "detailed"
    }
}
