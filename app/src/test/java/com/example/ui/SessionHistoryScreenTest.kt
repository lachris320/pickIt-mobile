package com.example.ui

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.data.local.CompletedMatchEntity
import com.example.data.local.PickleballDatabase
import com.example.data.local.PlayerEntity
import com.example.data.local.SessionEntity
import com.example.data.repository.SessionRepository
import com.example.model.Match
import com.example.model.OpenPlaySession
import com.example.model.Player
import com.example.model.RotationPolicy
import com.example.model.Team
import com.example.model.TeamId
import com.example.testing.FakeSessionDao
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.SessionHistoryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.SessionViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h891dp", sdk = [36])
class SessionHistoryScreenTest : RobolectricComposeTest() {

    @Before fun cleanDb() { resetSharedDb() }

    private fun repo() = SessionRepository(PickleballDatabase.getInstance(app()).sessionDao())

    private fun player(id: String, name: String) = Player(id = id, name = name)

    private fun seedSession(id: String, name: String, players: List<Player>, wins: Int, startTime: Long = 0L) = runBlocking {
        val r = repo()
        val roster = players.mapIndexed { i, p ->
            val isWinner = i < 2
            p.copy(
                matchesPlayed = wins,
                matchesWon = if (isWinner) wins else 0,
                totalPointsScored = (if (isWinner) 11 * wins else 5 * wins) - i,
                totalPointsConceded = if (isWinner) 5 * wins else 11 * wins,
            )
        }
        r.saveSession(
            OpenPlaySession(
                id = id, name = name, startTime = startTime,
                rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
                courts = emptyList(), roster = roster,
            )
        )
        repeat(wins) { k ->
            val teamA = Team(TeamId.TEAM_A, players[0], players[1])
            val teamB = Team(TeamId.TEAM_B, players[2], players[3])
            val m = Match(
                id = "$id-m$k", courtId = 1, teamA = teamA, teamB = teamB,
                scoreA = 11, scoreB = 5, isCompleted = true, winnerTeamId = TeamId.TEAM_A,
                endTime = (k + 1).toLong(),
            )
            r.recordCompletedMatch(id, m)
        }
    }

    private fun fourPlayers(prefix: String) = listOf(
        player("${prefix}1", "Ann"), player("${prefix}2", "Bob"),
        player("${prefix}3", "Cyd"), player("${prefix}4", "Dan"),
    )

    private fun content(
        sessionViewModel: SessionViewModel,
        historyViewModel: HistoryViewModel = HistoryViewModel(app()),
    ) {
        rule.setContent {
            MyApplicationTheme {
                SessionHistoryScreen(
                    sessionViewModel = sessionViewModel,
                    historyViewModel = historyViewModel,
                    origin = AppScreen.Setup,
                )
            }
        }
    }

    private fun readyVmNoSession(): SessionViewModel {
        val dao = PickleballDatabase.getInstance(app()).sessionDao()
        val fake = object : SessionRepository(dao) {
            override suspend fun loadLatestSession(): OpenPlaySession? = null
        }
        return SessionViewModel(app(), repositoryOverride = fake)
    }

    @Test fun emptyState_whenNoSessions() {
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_history_empty").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_history_empty").assertIsDisplayed()
    }

    @Test fun historyTab_listsPastSessions_withSummary() {
        seedSession("s1", "Monday Night", fourPlayers("s1"), wins = 3)
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_s1").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Monday Night").assertIsDisplayed()
        rule.onNodeWithText("Top player: Ann").assertIsDisplayed()
    }

    @Test fun historyRow_opensDetail_withStandingsAndMatches() {
        seedSession("s1", "Monday Night", fourPlayers("s1"), wins = 2)
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_s1").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_s1").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        rule.onNodeWithTag("standings_row_s11").assertIsDisplayed()
        rule.onNodeWithTag("match_row_s1-m1").assertIsDisplayed()
    }

    @Test fun allTimeTab_showsAggregateRows_andSubtitle() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 2)
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("all_time_tab").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("all_time_tab").performClick()
        rule.onNodeWithText("Ranked by total wins, then point difference.").assertIsDisplayed()
        rule.onNodeWithText("Grouped by name across sessions. Use consistent, distinct names.").assertIsDisplayed()
        rule.onNodeWithTag("all_time_row_ann").assertIsDisplayed()
        rule.onNodeWithTag("all_time_row_ann").assertHasNoClickAction()
    }

    @Test fun backFromList_navigatesToOrigin() {
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_history_back").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_history_back").performClick()
        rule.runOnIdle { assertEquals(AppScreen.Setup, vm.currentScreen.value) }
    }

    @Test fun backFromDetail_returnsToList_thenBackAgainToOrigin() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 1)
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_s1").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_s1").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        rule.onNodeWithTag("session_history_back").performClick()
        rule.onNodeWithTag("history_tab").assertIsDisplayed()
        rule.onNodeWithTag("session_detail").assertDoesNotExist()
        rule.onNodeWithTag("session_history_back").performClick()
        rule.runOnIdle { assertEquals(AppScreen.Setup, vm.currentScreen.value) }
    }

    @Test fun zeroGameSession_showsNoCompletedGames() {
        seedSession("z", "Empty Night", fourPlayers("z"), wins = 0)
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_z").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("No completed games").assertIsDisplayed()
    }

    @Test fun nullWinnerMatch_showsResultUnavailable() {
        runBlocking {
            val r = repo()
            val players = fourPlayers("nw")
            r.saveSession(
                OpenPlaySession(
                    id = "nw", name = "Tie Night", rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
                    courts = emptyList(), roster = players.map { it.copy(matchesPlayed = 1) },
                )
            )
            val m = Match(
                id = "nw-m0", courtId = 1,
                teamA = Team(TeamId.TEAM_A, players[0], players[1]),
                teamB = Team(TeamId.TEAM_B, players[2], players[3]),
                scoreA = 9, scoreB = 9, isCompleted = true, winnerTeamId = null, endTime = 1L,
            )
            r.recordCompletedMatch("nw", m)
        }
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_nw").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_nw").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        rule.onNodeWithText("Result unavailable", substring = true).assertIsDisplayed()
    }

    @Test fun activeSession_excludedFromHistoryList() {
        seedSession("active", "Live One", fourPlayers("act"), wins = 1)
        val vm = SessionViewModel(app())
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("history_tab").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_active").assertDoesNotExist()
    }

    @Test fun loadingState_shownWhileSessionNotYetLoaded() {
        val gate = CompletableDeferred<Unit>()
        val dao = PickleballDatabase.getInstance(app()).sessionDao()
        val slowRepo = object : SessionRepository(dao) {
            override suspend fun loadLatestSession(): OpenPlaySession? { gate.await(); return null }
        }
        val vm = SessionViewModel(app(), repositoryOverride = slowRepo)
        content(vm)
        rule.onNodeWithTag("session_history_loading").assertIsDisplayed()
        gate.complete(Unit)
    }

    @Test fun errorState_shownWhenAReadFails() {
        val throwingDao = object : FakeSessionDao() {
            override fun getAllSessions(): Flow<List<SessionEntity>> = flowOf(emptyList())
            override fun getAllRoster(): Flow<List<PlayerEntity>> = flowOf(emptyList())
            override fun getAllMatches(): Flow<List<CompletedMatchEntity>> =
                flow { throw IllegalStateException("boom") }
        }
        val hvm = HistoryViewModel(app(), repositoryOverride = SessionRepository(throwingDao))
        val vm = readyVmNoSession()
        content(vm, hvm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_history_error").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_history_error").assertIsDisplayed()
    }

    @Test fun errorWhileDetailOpen_showsError_notUnavailable() {
        val gate = CompletableDeferred<Unit>()
        val sessionEntity = SessionEntity(
            id = "d1", name = "Detail Night", startTime = 0L,
            rotationPolicy = "FOUR_OFF_FOUR_ON", consecutiveGameCap = 4, targetScore = 11,
            isPaused = false, isCompleted = false,
        )
        fun rosterRow(idx: Int, name: String) = PlayerEntity(
            sessionId = "d1", playerId = "d1p$idx", name = name, status = "AVAILABLE",
            queuedTimestamp = 0L, restingTimestamp = 0L, matchesPlayed = 1,
            matchesWon = if (idx <= 2) 1 else 0,
            totalPointsScored = if (idx <= 2) 11 else 5, totalPointsConceded = if (idx <= 2) 5 else 11,
            consecutiveGamesOnCourt = 0,
        )
        val rosterEntities = listOf(
            rosterRow(1, "Ann"), rosterRow(2, "Bob"), rosterRow(3, "Cyd"), rosterRow(4, "Dan"),
        )
        val matchEntity = CompletedMatchEntity(
            matchId = "d1-m0", sessionId = "d1", courtId = 1,
            teamAPlayer1 = "d1p1", teamAPlayer2 = "d1p2", teamBPlayer1 = "d1p3", teamBPlayer2 = "d1p4",
            scoreA = 11, scoreB = 5, winnerTeam = "TEAM_A", startTime = 0L, endTime = 1L,
        )
        val fakeDao = object : FakeSessionDao() {
            override fun getAllSessions(): Flow<List<SessionEntity>> = flowOf(listOf(sessionEntity))
            override fun getAllRoster(): Flow<List<PlayerEntity>> = flowOf(rosterEntities)
            override fun getAllMatches(): Flow<List<CompletedMatchEntity>> = flow {
                emit(listOf(matchEntity))
                gate.await()
                throw IllegalStateException("boom")
            }
        }
        val hvm = HistoryViewModel(app(), repositoryOverride = SessionRepository(fakeDao))
        val vm = readyVmNoSession()
        content(vm, hvm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_d1").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_d1").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()

        gate.complete(Unit)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_history_error").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_history_error").assertIsDisplayed()
        rule.onNodeWithTag("session_detail_unavailable").assertDoesNotExist()
    }

    @Test fun vanishedSelection_showsSessionNoLongerAvailable() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 1)
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_s1").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_s1").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        runBlocking { PickleballDatabase.getInstance(app()).sessionDao().deleteSessionById("s1") }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_detail_unavailable").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_detail_unavailable").assertIsDisplayed()
    }

    @Test fun selectedTab_survivesRecreation() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 2)
        val vm = readyVmNoSession()
        val hvm = HistoryViewModel(app())
        val restorer = StateRestorationTester(rule)
        restorer.setContent {
            MyApplicationTheme { SessionHistoryScreen(vm, hvm, AppScreen.Setup) }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("all_time_tab").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("all_time_tab").performClick()
        rule.onNodeWithText("Ranked by total wins, then point difference.").assertIsDisplayed()
        restorer.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Ranked by total wins, then point difference.").assertIsDisplayed()
    }

    @Test fun openedDetail_survivesRecreation() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 2)
        val vm = readyVmNoSession()
        val hvm = HistoryViewModel(app())
        val restorer = StateRestorationTester(rule)
        restorer.setContent {
            MyApplicationTheme { SessionHistoryScreen(vm, hvm, AppScreen.Setup) }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_s1").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_s1").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        restorer.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
    }

    @Test fun historyScroll_preservedAcrossDetailNavigation() {
        (0..19).forEach { i ->
            seedSession("h%02d".format(i), "S$i", fourPlayers("h$i"), wins = 0, startTime = i.toLong())
        }
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_history_list").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_history_list").performScrollToNode(hasTestTag("session_row_h00"))
        rule.onNodeWithTag("session_row_h00").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        rule.onNodeWithTag("session_history_back").performClick()
        rule.onNodeWithTag("session_row_h00").assertIsDisplayed()
        rule.onNodeWithTag("session_row_h19").assertDoesNotExist()
    }
}
