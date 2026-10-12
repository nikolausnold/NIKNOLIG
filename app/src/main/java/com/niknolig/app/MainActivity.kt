package com.niknolig.app

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.main_root)
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val safeInsets = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(
                    left + safeInsets.left,
                    top + safeInsets.top,
                    right + safeInsets.right,
                    bottom + safeInsets.bottom
                )
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    left + insets.systemWindowInsetLeft,
                    top + insets.systemWindowInsetTop,
                    right + insets.systemWindowInsetRight,
                    bottom + insets.systemWindowInsetBottom
                )
            }
            insets
        }
        root.requestApplyInsets()
    }
}
