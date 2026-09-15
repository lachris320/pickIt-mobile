package com.example.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onRoot
import com.example.fixtures.Fixtures
import com.example.model.OpenPlaySession
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.LiveScoreboardScreen
import com.example.ui.screens.SessionHubScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.SessionViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dual-theme visual baselines rendered from fixed fixtures (never the live async VM),
 * so captures are deterministic. Record: `./gradlew :app:recordRoborazziDebug`.
 * Verify:  `./gradlew :app:verifyRoborazziDebug`.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ScreenshotBaselinesTest : RobolectricComposeTest() {

    private fun vmWith(session: OpenPlaySession): SessionViewModel =
        SessionViewModel(app()).apply { loadSessionForTest(session) }

    private fun capture(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        rule.setContent { MyApplicationTheme(themeMode = mode) { content() } }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name-${mode.name.lowercase()}.png")
    }

    @Test fun hub_dark() = capture("hub", ThemeMode.DARK) {
        SessionHubScreen(viewModel = vmWith(Fixtures.twoReadyCourtsSession()))
    }

    @Test fun hub_light() = capture("hub", ThemeMode.LIGHT) {
        SessionHubScreen(viewModel = vmWith(Fixtures.twoReadyCourtsSession()))
    }

    @Test fun live_dark() = capture("live", ThemeMode.DARK) {
        LiveScoreboardScreen(courtId = 1, viewModel = vmWith(Fixtures.inProgressSession()))
    }

    @Test fun live_light() = capture("live", ThemeMode.LIGHT) {
        LiveScoreboardScreen(courtId = 1, viewModel = vmWith(Fixtures.inProgressSession()))
    }

    @Test fun setup_dark() = capture("setup", ThemeMode.DARK) {
        SetupScreen(viewModel = SessionViewModel(app()))
    }

    @Test fun setup_light() = capture("setup", ThemeMode.LIGHT) {
        SetupScreen(viewModel = SessionViewModel(app()))
    }
}
