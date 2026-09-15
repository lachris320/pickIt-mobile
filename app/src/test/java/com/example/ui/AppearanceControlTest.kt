package com.example.ui

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [36])
class AppearanceControlTest : RobolectricComposeTest() {

    @Test fun appearanceControl_setsThemeMode() {
        val vm = SessionViewModel(app())
        rule.setContent { MyApplicationTheme { SetupScreen(viewModel = vm) } }
        rule.onNodeWithTag("appearance_button").performClick()
        rule.onNodeWithTag("appearance_option_LIGHT").performClick()
        rule.runOnIdle { assertEquals(ThemeMode.LIGHT, vm.themeMode.value) }
    }
}
