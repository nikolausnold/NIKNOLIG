package com.niknolig.app

import android.graphics.Insets
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MainActivityTest {
    @Test
    fun systemBarsAndCutoutUseLargestInsetWithoutAccumulatingPadding() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(R.id.main_root)
                val insets = WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(), Insets.of(10, 20, 30, 40))
                    .setInsets(WindowInsets.Type.displayCutout(), Insets.of(50, 5, 5, 5))
                    .build()

                assertSame(insets, root.dispatchApplyWindowInsets(insets))
                assertPadding(root, 50, 20, 30, 40)
                root.dispatchApplyWindowInsets(insets)
                assertPadding(root, 50, 20, 30, 40)

                root.dispatchApplyWindowInsets(
                    WindowInsets.Builder()
                        .setInsets(
                            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                            Insets.NONE
                        )
                        .build()
                )
                assertPadding(root, 0, 0, 0, 0)
            }
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun legacyInsetsDoNotAccumulatePadding() {
        assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(R.id.main_root)
                val insets = WindowInsets(Rect(10, 20, 30, 40))
                assertSame(insets, root.dispatchApplyWindowInsets(insets))
                assertPadding(root, 10, 20, 30, 40)
                root.dispatchApplyWindowInsets(insets)
                assertPadding(root, 10, 20, 30, 40)
                root.dispatchApplyWindowInsets(WindowInsets(Rect()))
                assertPadding(root, 0, 0, 0, 0)
            }
        }
    }

    @Test
    fun recreationKeepsPreviewNoticeAndDoesNotRequestDevicePermissions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { activity ->
                val notice = activity.findViewById<TextView>(R.id.demo_notice)
                assertEquals(activity.getString(R.string.demo_notice), notice.text.toString())
                assertTrue(notice.isShown)
                @Suppress("DEPRECATION")
                val packageInfo = activity.packageManager.getPackageInfo(
                    activity.packageName,
                    android.content.pm.PackageManager.GET_PERMISSIONS
                )
                assertTrue(packageInfo.requestedPermissions.isNullOrEmpty())
            }
        }
    }

    private fun assertPadding(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        assertEquals(left, view.paddingLeft)
        assertEquals(top, view.paddingTop)
        assertEquals(right, view.paddingRight)
        assertEquals(bottom, view.paddingBottom)
    }
}
