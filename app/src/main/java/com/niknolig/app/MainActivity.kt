package com.niknolig.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.support_contact_button).setOnClickListener {
            openTechnicalSupportEmail()
        }

        findViewById<Button>(R.id.learning_profile_button).setOnClickListener {
            startActivity(Intent(this, LearningProfileActivity::class.java))
        }

        findViewById<Button>(R.id.assistant_button).setOnClickListener {
            startActivity(Intent(this, AssistantActivity::class.java))
        }
    }

    private fun openTechnicalSupportEmail() {
        AlertDialog.Builder(this)
            .setTitle(R.string.support_email_confirmation_title)
            .setMessage(R.string.support_email_confirmation_message)
            .setNegativeButton(R.string.support_email_cancel, null)
            .setPositiveButton(R.string.support_email_continue) { _, _ ->
                prepareTechnicalSupportEmail()
            }
            .show()
    }

    private fun prepareTechnicalSupportEmail() {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.fromParts("mailto", getString(R.string.support_email), null)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_email_subject))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.support_email_body))
        }

        try {
            startActivity(emailIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.support_email_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
