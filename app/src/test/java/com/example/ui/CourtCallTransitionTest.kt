package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.example.fixtures.Fixtures
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.CourtCallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SessionViewModel
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])
class CourtCallTransitionTest : RobolectricComposeTest() {

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
