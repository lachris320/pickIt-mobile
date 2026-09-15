package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.engine.PickleballGameEngine
import com.example.fixtures.Fixtures
import com.example.model.CourtStatus
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
class CourtCallPageHoldTest {
    @get:Rule val rule = createComposeRule()

    private fun app() = ApplicationProvider.getApplicationContext<Application>()

    /**
     * Proves the page hold (PAGE_HOLD_MS = 6s) is decoupled from the pulse (PULSE_MS = 2s).
     *
     * manyCourtsSession = 7 courts at w1280dp-h720dp-land (3 cols x 2 rows = capacity 6),
     * so page 1 = courts 1..6, page 2 = court 7. Transitioning court 7 to FINAL pins the board
     * to page 2 and starts the hold. The auto-advance cycle is PAGE_CYCLE_MS = 10s and only
     * resumes once the hold releases:
     *   - with the OLD coupled 2s hold: release @2s -> auto-advance fires @2s+10s = 12s -> "1 / 2"
     *   - with the NEW 6s hold:         release @6s -> auto-advance fires @6s+10s = 16s -> "2 / 2"
     * Asserting the board is still on page 2 at ~14s after the call (past both the 2s pulse and
     * the old 12s auto-advance point, but before the new 16s point) proves the hold outlives the
     * pulse. Fully deterministic under mainClock.autoAdvance = false.
     */
    @Test fun pageHold_outlivesPulse_keepsCalledCourtPage() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        val base = Fixtures.manyCourtsSession()
        vm.loadSessionForTest(base)
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = vm) } }

        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithText("1 / 2").assertIsDisplayed()
        rule.onNodeWithTag("court_call_tile_pulse_7").assertDoesNotExist()

        // Transition court 7 (page 2) to FINAL: completed match while status is IN_PROGRESS.
        val rec7 = base.activeRecommendations.getValue(7)
        val finalMatch = PickleballGameEngine.createMatch(
            courtId = 7,
            teamA = rec7.teamA,
            teamB = rec7.teamB,
        ).copy(scoreA = 11, scoreB = 7, isCompleted = true)
        val called = base.copy(
            courts = base.courts.map { c ->
                if (c.id == 7) c.copy(status = CourtStatus.IN_PROGRESS, currentMatch = finalMatch) else c
            },
        )
        rule.runOnUiThread { vm.loadSessionForTest(called) }

        // ~50ms after the call: board pinned to page 2, pulse visible.
        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithText("2 / 2").assertIsDisplayed()
        rule.onNodeWithTag("court_call_tile_pulse_7").assertIsDisplayed()

        // ~2.5s after the call: past PULSE_MS -> pulse cleared, but page still held.
        rule.mainClock.advanceTimeBy(2_450)
        rule.onNodeWithTag("court_call_tile_pulse_7").assertDoesNotExist()
        rule.onNodeWithText("2 / 2").assertIsDisplayed()

        // ~14s after the call: past the old 12s auto-advance point but before the new 16s one.
        // The extended hold has kept auto-advance suppressed, so the board is still on page 2.
        rule.mainClock.advanceTimeBy(11_500)
        rule.onNodeWithText("2 / 2").assertIsDisplayed()
    }
}
