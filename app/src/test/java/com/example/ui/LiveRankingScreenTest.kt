package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.model.OpenPlaySession
import com.example.model.ParticipantStatus
import com.example.model.Player
import com.example.model.RotationPolicy
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.LiveRankingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])
class LiveRankingScreenTest : RobolectricComposeTest() {

    private fun player(
        id: String, name: String, played: Int, won: Int,
        pf: Int = 0, pa: Int = 0,
        status: ParticipantStatus = ParticipantStatus.AVAILABLE,
    ) = Player(
        id = id, name = name, status = status,
        matchesPlayed = played, matchesWon = won,
        totalPointsScored = pf, totalPointsConceded = pa,
    )

    private fun sessionOf(players: List<Player>) = OpenPlaySession(
        id = "s1", name = "Test", rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
        courts = emptyList(), roster = players,
    )

    @Test fun emptyState_whenNoGamesPlayed() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(listOf(player("a", "Al", 0, 0), player("b", "Bo", 0, 0))))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm) } }

        rule.onNodeWithTag("live_ranking_empty").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_page_indicator").assertDoesNotExist()
    }

    @Test fun singlePage_showsRows_noPageIndicator() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(listOf(
            player("a", "Al", 3, 3, pf = 33, pa = 10),
            player("b", "Bo", 2, 1, pf = 20, pa = 18),
        )))
        // Override large so 2 players => exactly 1 page (no indicator), deterministically.
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm, rowsPerPageOverride = 10) } }

        rule.onNodeWithTag("live_ranking_row_a").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_page_indicator").assertDoesNotExist()
    }

    @Test fun longName_doesNotHideStats() {
        val vm = SessionViewModel(app())
        val longName = "Bartholomew Featherstonehaugh The Third Of Pickleball"
        vm.loadSessionForTest(sessionOf(listOf(player("a", longName, 4, 3, pf = 44, pa = 26))))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm) } }

        rule.onNodeWithText("3–1").assertIsDisplayed() // W-L still visible (3-1)
        rule.onNodeWithText("+18").assertIsDisplayed()      // diff still visible (44-26)
    }

    @Test fun checkedOutPlayer_showsLeftLabel() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(listOf(
            player("a", "Al", 3, 3, status = ParticipantStatus.CHECKED_OUT),
        )))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm) } }

        rule.onNodeWithTag("live_ranking_left_a").assertIsDisplayed()
    }

    // Five ranked players (wins 5..1) -> deterministic order top, p1, p2, p3, p4.
    private fun fivePlayers() = listOf(
        player("top", "Top", 5, 5, pf = 55, pa = 10),
        player("p1", "P1", 4, 4, pf = 44, pa = 12),
        player("p2", "P2", 3, 3, pf = 33, pa = 14),
        player("p3", "P3", 2, 2, pf = 22, pa = 16),
        player("p4", "P4", 1, 1, pf = 11, pa = 18),
    )
    // With rowsPerPageOverride = 2 and 5 players, pageCount is exactly 3:
    // page 0 = [top, p1], page 1 = [p2, p3], page 2 = [p4].

    @Test fun multiPage_advancesOnEachTick_readsLiveState() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(fivePlayers()))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm, rowsPerPageOverride = 2) } }

        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithText("Page 1 / 3").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_top").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(9_000) // -> page 1
        rule.onNodeWithText("Page 2 / 3").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_top").assertDoesNotExist()
        rule.onNodeWithTag("live_ranking_row_p2").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(9_000) // -> page 2 (proves it did not stall after one tick)
        rule.onNodeWithText("Page 3 / 3").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_p4").assertIsDisplayed()
    }

    @Test fun paging_loopsBackToFirstPage() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(fivePlayers()))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm, rowsPerPageOverride = 2) } }

        rule.mainClock.advanceTimeBy(50)
        rule.mainClock.advanceTimeBy(27_000) // 3 ticks: page 0 -> 1 -> 2 -> 0
        rule.onNodeWithText("Page 1 / 3").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_top").assertIsDisplayed()
    }

    @Test fun shrink_clampsDisplayedPage_thenAdvancesFromIt() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(fivePlayers()))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm, rowsPerPageOverride = 2) } }

        rule.mainClock.advanceTimeBy(50)
        rule.mainClock.advanceTimeBy(18_000) // 2 ticks -> page 2 of 3
        rule.onNodeWithText("Page 3 / 3").assertIsDisplayed()

        // Shrink to 3 players -> pageCount 2. rawPage is 2; the render must CLAMP to page 1
        // ("Page 2 / 2") and still show rows (not blank).
        rule.runOnUiThread { vm.loadSessionForTest(sessionOf(fivePlayers().take(3))) }
        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithText("Page 2 / 2").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_p2").assertIsDisplayed() // page 1 of [top,p1,p2] = [p2]

        // Next tick must advance from the DISPLAYED (clamped) page: nextPage(2, 2) -> 0.
        rule.mainClock.advanceTimeBy(9_000)
        rule.onNodeWithText("Page 1 / 2").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_top").assertIsDisplayed()
    }

    @Test fun measuredPaging_smoke_multiPageAndAdvance() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        // No override: exercise the real rowsPerPage measurement. 200 rows cannot fit one screen.
        val filler = (1..200).map { player("p$it", "Player $it", played = 2, won = 1, pf = 15, pa = 15) }
        vm.loadSessionForTest(sessionOf(listOf(player("top", "Top", 5, 5, pf = 55, pa = 10)) + filler))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm) } }

        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithTag("live_ranking_page_indicator").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_top").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(9_000)
        rule.onNodeWithTag("live_ranking_row_top").assertDoesNotExist()
    }

    @Test fun close_navigatesToHub() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(listOf(player("a", "Al", 1, 1))))
        // AppScreen.LiveRanking does not exist until Task 5. Start on an existing non-Hub screen
        // (CourtCall) so the close button driving currentScreen back to SessionHub is an
        // observable change.
        vm.navigateTo(AppScreen.CourtCall)
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm) } }

        rule.onNodeWithTag("live_ranking_close").performClick()
        rule.runOnIdle { assertEquals(AppScreen.SessionHub, vm.currentScreen.value) }
    }

    @Test fun timerNotRestarted_whenDataChangesButPageCountUnchanged() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(fivePlayers()))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm, rowsPerPageOverride = 2) } }

        rule.mainClock.advanceTimeBy(50)
        rule.mainClock.advanceTimeBy(8_000) // still page 0, before the 9s deadline
        rule.onNodeWithText("Page 1 / 3").assertIsDisplayed()

        // Emit a data change that keeps the player count (pageCount stays 3). If the advance
        // LaunchedEffect were keyed on the roster, this would CANCEL and restart the 9s delay,
        // pushing the next advance out to ~17s. Keyed on pageCount, the running delay is untouched.
        val bumped = fivePlayers().map {
            if (it.id == "p4") it.copy(totalPointsScored = it.totalPointsScored + 1) else it
        }
        rule.runOnUiThread { vm.loadSessionForTest(sessionOf(bumped)) }

        rule.mainClock.advanceTimeBy(1_500) // cross the ORIGINAL 9s deadline (~9.55s total)
        rule.onNodeWithText("Page 2 / 3").assertIsDisplayed()          // advanced on schedule -> not restarted
        rule.onNodeWithTag("live_ranking_row_top").assertDoesNotExist()
    }
}
