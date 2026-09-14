package com.example.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppearanceControlTest {
    @get:Rule val rule = createComposeRule()

    @Test fun appearanceControl_setsThemeMode() {
        val vm = SessionViewModel(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent { MyApplicationTheme { SetupScreen(viewModel = vm) } }
        rule.onNodeWithTag("appearance_button").performClick()
        rule.onNodeWithTag("appearance_option_LIGHT").performClick()
        rule.runOnIdle { assertEquals(ThemeMode.LIGHT, vm.themeMode.value) }
    }
}
