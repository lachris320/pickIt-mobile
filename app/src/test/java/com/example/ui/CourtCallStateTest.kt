package com.example.ui

import com.example.engine.PickleballGameEngine
import com.example.model.Court
import com.example.model.CourtStatus
import com.example.model.OpenPlaySession
import com.example.model.Player
import com.example.model.RotationRecommendation
import com.example.model.Team
import com.example.model.TeamId
import com.example.ui.screens.CourtCallState
import com.example.ui.screens.courtDisplayState
import com.example.ui.screens.primaryReadyCourtId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourtCallStateTest {

    private fun p(i: Int) = Player(id = "p$i", name = "Player $i")
    private fun team(a: Int, b: Int) = Team(TeamId.TEAM_A, p(a), p(b))
    private fun teamB(a: Int, b: Int) = Team(TeamId.TEAM_B, p(a), p(b))

    private fun liveMatch(courtId: Int, completed: Boolean) =
        PickleballGameEngine.createMatch(
            courtId = courtId, teamA = team(0, 1), teamB = teamB(2, 3),
        ).copy(scoreA = 6, scoreB = 4, isCompleted = completed)

    private fun rec(courtId: Int) = RotationRecommendation(
        courtId = courtId, teamA = team(0, 1), teamB = teamB(2, 3),
        departingPlayers = emptyList(), retainedPlayers = emptyList(),
        incomingPlayers = emptyList(), primaryReason = "Next up",
        detailedReason = listOf("because"),
    )

    @Test fun paused_takesTopPrecedence() {
        val court = Court(id = 1, name = "C1", status = CourtStatus.PAUSED, currentMatch = liveMatch(1, true))
        assertEquals(CourtCallState.PAUSED, courtDisplayState(court, hasRecommendation = true, isPrimaryReady = true))
    }

    @Test fun completedMatch_isFinal_evenWhileInProgress() {
        val court = Court(id = 1, name = "C1", status = CourtStatus.IN_PROGRESS, currentMatch = liveMatch(1, true))
        assertEquals(CourtCallState.FINAL, courtDisplayState(court, hasRecommendation = false, isPrimaryReady = false))
    }

    @Test fun inProgressNotCompleted_isLive() {
        val court = Court(id = 1, name = "C1", status = CourtStatus.IN_PROGRESS, currentMatch = liveMatch(1, false))
        assertEquals(CourtCallState.LIVE, courtDisplayState(court, hasRecommendation = false, isPrimaryReady = false))
    }

    @Test fun availableWithRecAndPrimary_isUpNow() {
        val court = Court(id = 1, name = "C1", status = CourtStatus.AVAILABLE)
        assertEquals(CourtCallState.UP_NOW, courtDisplayState(court, hasRecommendation = true, isPrimaryReady = true))
    }

    @Test fun availableWithRecNotPrimary_isReady() {
        val court = Court(id = 2, name = "C2", status = CourtStatus.AVAILABLE)
        assertEquals(CourtCallState.READY, courtDisplayState(court, hasRecommendation = true, isPrimaryReady = false))
    }

    @Test fun availableNoRec_isOpen() {
        val court = Court(id = 3, name = "C3", status = CourtStatus.AVAILABLE)
        assertEquals(CourtCallState.OPEN, courtDisplayState(court, hasRecommendation = false, isPrimaryReady = false))
    }

    @Test fun primaryReadyCourtId_isLowestAvailableCourtWithRec() {
        val session = OpenPlaySession(
            id = "s", name = "s",
            courts = listOf(
                Court(id = 1, name = "C1", status = CourtStatus.IN_PROGRESS, currentMatch = liveMatch(1, false)),
                Court(id = 2, name = "C2", status = CourtStatus.AVAILABLE),
                Court(id = 3, name = "C3", status = CourtStatus.AVAILABLE),
                Court(id = 4, name = "C4", status = CourtStatus.AVAILABLE),
            ),
            activeRecommendations = mapOf(2 to rec(2), 3 to rec(3)),
        )
        assertEquals(2, primaryReadyCourtId(session))
    }

    @Test fun primaryReadyCourtId_isNullWhenNoReadyCourt() {
        val session = OpenPlaySession(
            id = "s", name = "s",
            courts = listOf(Court(id = 1, name = "C1", status = CourtStatus.AVAILABLE)),
        )
        assertNull(primaryReadyCourtId(session))
    }
}
