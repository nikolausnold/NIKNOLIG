package com.niknolig.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class AssistantActivity : Activity() {
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
    }
}
