package com.niknolig.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.concurrent.Executors

class AssistantActivity : Activity() {
    private val backendExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assistant)

        findViewById<Button>(R.id.assistant_close_button).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.assistant_learning_profile_button).setOnClickListener {
            startActivity(Intent(this, LearningProfileActivity::class.java))
        }

        val requestInput = findViewById<EditText>(R.id.assistant_request_input)
        val requestStatus = findViewById<TextView>(R.id.assistant_request_status)
        findViewById<Button>(R.id.assistant_request_button).setOnClickListener {
            val request = requestInput.text.toString().trim()
            requestStatus.text = if (request.isEmpty()) {
                getString(R.string.assistant_request_empty)
            } else {
                getString(R.string.assistant_request_local_only, request)
            }
        }

        val backendStatus = findViewById<TextView>(R.id.backend_status)
        val backendCheck = findViewById<Button>(R.id.backend_check_button)
        val baseUrl = BuildConfig.BACKEND_BASE_URL
        backendCheck.isEnabled = baseUrl.isNotEmpty()
        backendStatus.setText(
            if (baseUrl.isEmpty()) R.string.backend_not_configured else R.string.backend_ready
        )
        backendCheck.setOnClickListener {
            backendCheck.isEnabled = false
            backendStatus.setText(R.string.backend_checking)
            backendExecutor.execute {
                val healthy = try {
                    BackendClient(BackendEndpoint(baseUrl)).checkHealth()
                } catch (_: Exception) {
                    false
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        backendCheck.isEnabled = true
                        backendStatus.setText(
                            if (healthy) R.string.backend_healthy else R.string.backend_unavailable
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        backendExecutor.shutdownNow()
        super.onDestroy()
    }
}
