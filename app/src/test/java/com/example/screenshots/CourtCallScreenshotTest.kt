package com.example.screenshots

import androidx.compose.ui.test.onRoot
import com.example.fixtures.Fixtures
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.CourtCallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.SessionViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Single LANDSCAPE baseline of the Court Call board.
 *
 * DELIBERATE DEVIATION from the app's dual-theme baseline convention: the board palette is FIXED
 * (spec §7 — always DarkTokens, independent of ThemeMode), so a light baseline would be dead. We
 * render under ThemeMode.LIGHT on purpose to prove the board ignores the ambient theme and stays
 * dark. Record: `./gradlew :app:recordRoborazziDebug`; verify: `./gradlew :app:verifyRoborazziDebug`.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])
class CourtCallScreenshotTest : RobolectricComposeTest() {

    @Test fun courtCall_dark() {
        val vm = SessionViewModel(app()).apply { loadSessionForTest(Fixtures.boardSession()) }
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                CourtCallScreen(viewModel = vm)
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/court-call-dark.png")
    }
}
