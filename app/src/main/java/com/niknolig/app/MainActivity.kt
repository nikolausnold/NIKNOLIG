package com.niknolig.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.ImageButton

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<ImageButton>(R.id.assistant_button).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.assistant_name)
                .setMessage(R.string.assistant_unavailable)
                .setPositiveButton(R.string.close, null)
                .show()
        }
    }
}
