package com.example

import com.example.engine.PickleballGameEngine
import com.example.engine.RotationEngine
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

class UsabilityWalkthroughEdgeCasesTest {

    @Test
    fun `odd roster size of 5 players rotates resting player and produces odd-count warning`() {
        val players = (1..5).map {
            Player(id = "p$it", name = "Player $it", status = ParticipantStatus.AVAILABLE)
        }
        val court = Court(id = 1, name = "Court 1", status = CourtStatus.AVAILABLE)
        val session = OpenPlaySession(
            id = "s_odd5",
            name = "Odd 5 Session",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            courts = listOf(court),
            roster = players
        )

        // 1. Initial recommendation fills court with first 4 players, 1 left waiting
        val rec = RotationEngine.generateRecommendation(session, 1, null)
        assertNotNull(rec)
        assertEquals(4, rec?.incomingPlayers?.size)
        // Warning triggered because exactly 1 player is left waiting (< 4 for another court)
        assertNotNull(rec?.warningMessage)
        assertTrue(rec?.warningMessage?.contains("1 player(s) waiting") == true)

        // 2. Simulate match ending
        val match = PickleballGameEngine.createMatch(1, rec!!.teamA, rec.teamB)
        val completedMatch = match.copy(isCompleted = true, scoreA = 11, scoreB = 8, winnerTeamId = TeamId.TEAM_A)

        // With 4-off, the 4 players exit with a newer timestamp.
        // Player 5 (who had not played yet) has seniority in the FIFO queue!
        val updatedRoster = players.map { p ->
            if (p.id == "p5") {
                p.copy(status = ParticipantStatus.AVAILABLE, queuedTimestamp = 1000L, matchesPlayed = 0)
            } else {
                p.copy(status = ParticipantStatus.AVAILABLE, queuedTimestamp = 5000L, matchesPlayed = 1)
            }
        }
        val updatedSession = session.copy(roster = updatedRoster)

        val nextRec = RotationEngine.generateRecommendation(updatedSession, 1, completedMatch)
        assertNotNull(nextRec)
        // Player 5 MUST be in the incoming players because they waited while the other 4 played
        val incomingIds = nextRec!!.incomingPlayers.map { it.id }
        assertTrue("Player 5 must be in next match", incomingIds.contains("p5"))
    }

    @Test
    fun `odd roster size with fewer than 4 available players prevents invalid match creation`() {
        val players = (1..3).map {
            Player(id = "p$it", name = "Player $it", status = ParticipantStatus.AVAILABLE)
        }
        val court = Court(id = 1, name = "Court 1", status = CourtStatus.AVAILABLE)
        val session = OpenPlaySession(
            id = "s_few3",
            name = "Few Players Session",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            courts = listOf(court),
            roster = players
        )

        val rec = RotationEngine.generateRecommendation(session, 1, null)
        assertNull("Cannot generate a doubles match recommendation with only 3 available players", rec)
    }

    @Test
    fun `pausing a court removes active recommendation and prevents scheduling`() {
        val court1 = Court(id = 1, name = "Court 1", status = CourtStatus.PAUSED)
        val players = (1..8).map {
            Player(id = "p$it", name = "Player $it", status = ParticipantStatus.AVAILABLE)
        }
        val session = OpenPlaySession(
            id = "s_pause",
            name = "Paused Court Session",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            courts = listOf(court1),
            roster = players
        )

        val rec = RotationEngine.generateRecommendation(session, 1, null)
        // A paused court cannot receive recommendations
        assertNull(rec)
    }

    @Test
    fun `consecutive game cap forces 2-game streak player out in winners-stay rotation`() {
        val p1 = Player("p1", "StreakKing", status = ParticipantStatus.AVAILABLE, consecutiveGamesOnCourt = 2)
        val p2 = Player("p2", "Partner", status = ParticipantStatus.AVAILABLE, consecutiveGamesOnCourt = 2)
        val p3 = Player("p3", "Challenger1", status = ParticipantStatus.AVAILABLE, consecutiveGamesOnCourt = 1)
        val p4 = Player("p4", "Challenger2", status = ParticipantStatus.AVAILABLE, consecutiveGamesOnCourt = 1)
        val waiting = (5..8).map { Player("p$it", "Queue$it", status = ParticipantStatus.AVAILABLE) }

        val court = Court(id = 1, name = "Court 1", status = CourtStatus.AVAILABLE)
        val session = OpenPlaySession(
            id = "s_cap",
            name = "Cap Session",
            rotationPolicy = RotationPolicy.WINNERS_STAY_SPLIT,
            consecutiveGameCap = 2,
            courts = listOf(court),
            roster = listOf(p1, p2, p3, p4) + waiting
        )

        val lastMatch = Match(
            id = "m1",
            courtId = 1,
            teamA = Team(TeamId.TEAM_A, p1, p2),
            teamB = Team(TeamId.TEAM_B, p3, p4),
            scoreA = 11,
            scoreB = 7,
            winnerTeamId = TeamId.TEAM_A,
            isCompleted = true
        )

        val rec = RotationEngine.generateRecommendation(session, 1, lastMatch)
        assertNotNull(rec)
        // Even though Team A won, p1 and p2 hit the consecutive game cap (2), so they must rotate off
        assertTrue(rec!!.departingPlayers.any { it.id == "p1" })
        assertTrue(rec.departingPlayers.any { it.id == "p2" })
        assertTrue(rec.detailedReason.any { it.contains("consecutive game cap") })
    }

    @Test
    fun `deuce win by 2 scoring edge case 10-10 to 12-10`() {
        val p1 = Player("p1", "A1")
        val p2 = Player("p2", "A2")
        val p3 = Player("p3", "B1")
        val p4 = Player("p4", "B2")

        var match = PickleballGameEngine.createMatch(
            courtId = 1,
            teamA = Team(TeamId.TEAM_A, p1, p2),
            teamB = Team(TeamId.TEAM_B, p3, p4),
            targetScore = 11
        )

        // Set score to 10-10
        match = match.copy(scoreA = 10, scoreB = 10, servingTeam = TeamId.TEAM_A, serverNumber = 1)

        // Team A scores point -> 11-10 (not over, must win by 2)
        match = PickleballGameEngine.recordRally(match, TeamId.TEAM_A)
        assertEquals(11, match.scoreA)
        assertEquals(10, match.scoreB)
        assertFalse(match.isCompleted)

        // Team A scores second point -> 12-10 (won by 2, match complete!)
        match = PickleballGameEngine.recordRally(match, TeamId.TEAM_A)
        assertEquals(12, match.scoreA)
        assertEquals(10, match.scoreB)
        assertTrue(match.isCompleted)
        assertEquals(TeamId.TEAM_A, match.winnerTeamId)
    }
}
