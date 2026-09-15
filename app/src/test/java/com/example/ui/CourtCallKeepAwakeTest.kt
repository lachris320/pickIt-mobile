package com.example.ui

import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.MainActivity
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CourtCallKeepAwakeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val keepOn get() =
        rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

    @Test fun keepScreenOn_setOnCourtCall_clearedOnLeave() {
        val vm = ViewModelProvider(rule.activity)[SessionViewModel::class.java]

        rule.runOnUiThread { vm.navigateTo(AppScreen.CourtCall) }
        rule.waitForIdle()
        assertNotEquals(0, keepOn)

        rule.runOnUiThread { vm.navigateTo(AppScreen.SessionHub) }
        rule.waitForIdle()
        assertEquals(0, keepOn)
    }
}
