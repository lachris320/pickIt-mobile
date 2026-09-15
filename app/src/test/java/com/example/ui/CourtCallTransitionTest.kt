package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.fixtures.Fixtures
import com.example.ui.screens.CourtCallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SessionViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])
class CourtCallTransitionTest {
    @get:Rule val rule = createComposeRule()

    private fun app() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun transitionToUpNow_pulsesBriefly_thenClears() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        val start = Fixtures.boardSession().let { s ->
            s.copy(activeRecommendations = s.activeRecommendations - 1)
        }
        vm.loadSessionForTest(start)
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = vm) } }
        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithTag("court_call_tile_pulse_1").assertDoesNotExist()

        rule.runOnUiThread { vm.loadSessionForTest(Fixtures.boardSession()) }
        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithTag("court_call_tile_pulse_1").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(2_500)
        rule.onNodeWithTag("court_call_tile_pulse_1").assertDoesNotExist()
    }
}
