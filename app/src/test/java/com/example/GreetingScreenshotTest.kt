package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SessionViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun app_screenshot() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = SessionViewModel(app)
    composeTestRule.setContent {
      MyApplicationTheme {
        PickleballAppContent(viewModel = viewModel)
      }
    }

    // Wait for idle to ensure rendering is complete
    composeTestRule.waitForIdle()

    // Capture image to specified path
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/app_home.png")
  }
}
