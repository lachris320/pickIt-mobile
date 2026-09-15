package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import com.example.engine.PickleballGameEngine
import com.example.model.Court
import com.example.model.CourtStatus
import com.example.model.Player
import com.example.model.RotationRecommendation
import com.example.model.Team
import com.example.model.TeamId
import com.example.testing.RobolectricComposeTest
import com.example.ui.components.CourtCallTile
import com.example.ui.screens.CourtCallState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [36])
class CourtCallTileTest : RobolectricComposeTest() {

    private fun p(i: Int) = Player(id = "p$i", name = "Player $i")
    private fun rec(courtId: Int) = RotationRecommendation(
        courtId = courtId, teamA = Team(TeamId.TEAM_A, p(0), p(1)),
        teamB = Team(TeamId.TEAM_B, p(2), p(3)),
        departingPlayers = emptyList(), retainedPlayers = emptyList(),
        incomingPlayers = emptyList(), primaryReason = "Next up",
        detailedReason = listOf("because A beats B"),
    )

    @Test fun liveTile_showsScoreAndPerCourtTag() {
        val match = PickleballGameEngine.createMatch(
            courtId = 3, teamA = Team(TeamId.TEAM_A, p(0), p(1)),
            teamB = Team(TeamId.TEAM_B, p(2), p(3)),
        ).copy(scoreA = 6, scoreB = 4)
        val court = Court(id = 3, name = "Court 3", status = CourtStatus.IN_PROGRESS, currentMatch = match)
        rule.setContent { MyApplicationTheme { CourtCallTile(court = court, state = CourtCallState.LIVE, recommendation = null) } }

        rule.onAllNodesWithTag("court_call_tile_live").assertCountEquals(1)
        rule.onNodeWithTag("court_call_tile_3").assertIsDisplayed()
        rule.onNodeWithText("6").assertIsDisplayed()
        rule.onNodeWithText("4").assertIsDisplayed()
    }

    @Test fun upNowTile_showsNamesOnly_noReasons() {
        val court = Court(id = 1, name = "Court 1", status = CourtStatus.AVAILABLE)
        rule.setContent { MyApplicationTheme { CourtCallTile(court = court, state = CourtCallState.UP_NOW, recommendation = rec(1)) } }

        rule.onAllNodesWithTag("court_call_tile_up_now").assertCountEquals(1)
        rule.onNodeWithText("Player 0", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Player 3", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Next up").assertDoesNotExist()
        rule.onNodeWithText("because A beats B").assertDoesNotExist()
    }

    @Test fun finalTile_showsFinalScore() {
        val match = PickleballGameEngine.createMatch(
            courtId = 4, teamA = Team(TeamId.TEAM_A, p(0), p(1)),
            teamB = Team(TeamId.TEAM_B, p(2), p(3)),
        ).copy(scoreA = 11, scoreB = 7, isCompleted = true)
        val court = Court(id = 4, name = "Court 4", status = CourtStatus.IN_PROGRESS, currentMatch = match)
        rule.setContent { MyApplicationTheme { CourtCallTile(court = court, state = CourtCallState.FINAL, recommendation = null) } }

        rule.onAllNodesWithTag("court_call_tile_final").assertCountEquals(1)
        rule.onNodeWithText("11").assertIsDisplayed()
        rule.onNodeWithText("7").assertIsDisplayed()
    }

    @Test fun pausedTile_rendersPausedTag() {
        val court = Court(id = 6, name = "Court 6", status = CourtStatus.PAUSED)
        rule.setContent { MyApplicationTheme { CourtCallTile(court = court, state = CourtCallState.PAUSED, recommendation = null) } }
        rule.onAllNodesWithTag("court_call_tile_paused").assertCountEquals(1)
    }
}
