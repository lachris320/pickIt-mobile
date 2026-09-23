package com.example.data

import com.example.data.local.CompletedMatchEntity
import com.example.data.local.PlayerEntity
import com.example.data.repository.toDomainPlayer
import com.example.data.repository.toMatchResult
import com.example.model.ParticipantStatus
import com.example.model.TeamId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMappersTest {

    @Test fun `toMatchResult maps names, scores, and winner enum`() {
        val e = CompletedMatchEntity(
            matchId = "m1", sessionId = "s1", courtId = 1,
            teamAPlayer1 = "Ana", teamAPlayer2 = "Bo",
            teamBPlayer1 = "Cy", teamBPlayer2 = "Dot",
            scoreA = 11, scoreB = 7, winnerTeam = "TEAM_B",
            startTime = 10, endTime = 20,
        )
        val r = e.toMatchResult()
        assertEquals(listOf("Ana", "Bo"), r.teamA)
        assertEquals(listOf("Cy", "Dot"), r.teamB)
        assertEquals(TeamId.TEAM_B, r.winner)
        assertEquals(20, r.endTime)
    }

    @Test fun `toMatchResult maps UNKNOWN winner to null`() {
        val e = CompletedMatchEntity(
            matchId = "m1", sessionId = "s1", courtId = 1,
            teamAPlayer1 = "Ana", teamAPlayer2 = "Bo",
            teamBPlayer1 = "Cy", teamBPlayer2 = "Dot",
            scoreA = 9, scoreB = 9, winnerTeam = "UNKNOWN",
            startTime = 10, endTime = 20,
        )
        assertNull(e.toMatchResult().winner)
    }

    @Test fun `toDomainPlayer maps playerId and aggregates`() {
        val e = PlayerEntity(
            sessionId = "s1", playerId = "p1", name = "Ana", status = "CHECKED_OUT",
            queuedTimestamp = 1, restingTimestamp = 0,
            matchesPlayed = 3, matchesWon = 2, totalPointsScored = 30, totalPointsConceded = 20,
            consecutiveGamesOnCourt = 1,
        )
        val p = e.toDomainPlayer()
        assertEquals("p1", p.id)
        assertEquals(ParticipantStatus.CHECKED_OUT, p.status)
        assertEquals(2, p.matchesWon)
        assertEquals(30, p.totalPointsScored)
    }
}
