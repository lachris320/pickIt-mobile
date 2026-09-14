package com.example.theme

import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.DarkTokens
import com.example.ui.theme.LightTokens
import com.example.ui.theme.LocalPickItTokens
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeResolutionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun lightMode_providesLightTokens() {
        var captured = DarkTokens
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                captured = LocalPickItTokens.current
            }
        }
        rule.runOnIdle { assertEquals(LightTokens, captured) }
    }

    @Test fun darkMode_providesDarkTokens() {
        var captured = LightTokens
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                captured = LocalPickItTokens.current
            }
        }
        rule.runOnIdle { assertEquals(DarkTokens, captured) }
    }
}
