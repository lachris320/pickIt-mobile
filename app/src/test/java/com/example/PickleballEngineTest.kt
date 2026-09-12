package com.example

import com.example.engine.PickleballGameEngine
import com.example.engine.RotationEngine
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

class PickleballEngineTest {

    private val playerA1 = Player("a1", "Alice")
    private val playerA2 = Player("a2", "Bob")
    private val playerB1 = Player("b1", "Charlie")
    private val playerB2 = Player("b2", "Dave")

    private val teamA = Team(TeamId.TEAM_A, playerA1, playerA2)
    private val teamB = Team(TeamId.TEAM_B, playerB1, playerB2)

    @Test
    fun `initial match starts at 0-0-2 for serving team`() {
        val match = PickleballGameEngine.createMatch(courtId = 1, teamA = teamA, teamB = teamB)
        assertEquals(0, match.scoreA)
        assertEquals(0, match.scoreB)
        assertEquals(TeamId.TEAM_A, match.servingTeam)
        assertEquals(2, match.serverNumber)
        assertEquals("0 - 0 - 2", match.calloutString())
        assertEquals(CourtSide.RIGHT, match.servingSide)
    }

    @Test
    fun `serving team rally win awards point and switches serve side`() {
        var match = PickleballGameEngine.createMatch(courtId = 1, teamA = teamA, teamB = teamB)
        match = PickleballGameEngine.recordRally(match, winningTeam = TeamId.TEAM_A)

        assertEquals(1, match.scoreA)
        assertEquals(0, match.scoreB)
        assertEquals(TeamId.TEAM_A, match.servingTeam)
        assertEquals(CourtSide.LEFT, match.servingSide)
        assertEquals(2, match.serverNumber)
    }

    @Test
    fun `initial server fault causes side out to opposing team`() {
        var match = PickleballGameEngine.createMatch(courtId = 1, teamA = teamA, teamB = teamB)
        // At 0-0-2, receiving team wins rally -> side out immediately
        match = PickleballGameEngine.recordRally(match, winningTeam = TeamId.TEAM_B)

        assertEquals(0, match.scoreA)
        assertEquals(0, match.scoreB)
        assertEquals(TeamId.TEAM_B, match.servingTeam)
        assertEquals(1, match.serverNumber) // Now Server 1 for Team B
    }

    @Test
    fun `undo rally accurately restores exact previous state via event replay`() {
        var match = PickleballGameEngine.createMatch(courtId = 1, teamA = teamA, teamB = teamB)
        match = PickleballGameEngine.recordRally(match, TeamId.TEAM_A) // 1-0-2
        match = PickleballGameEngine.recordRally(match, TeamId.TEAM_A) // 2-0-2
        assertEquals(2, match.scoreA)

        match = PickleballGameEngine.undoLastRally(match)
        assertEquals(1, match.scoreA)
        assertEquals(CourtSide.LEFT, match.servingSide)

        match = PickleballGameEngine.undoLastRally(match)
        assertEquals(0, match.scoreA)
        assertEquals(CourtSide.RIGHT, match.servingSide)
    }

    @Test
    fun `rotation engine generates fair 4-off recommendation`() {
        val players = (1..8).map {
            Player(id = "p$it", name = "Player $it", status = ParticipantStatus.AVAILABLE)
        }
        val court = Court(id = 1, name = "Court 1", status = CourtStatus.AVAILABLE)
        val session = OpenPlaySession(
            id = "s1",
            name = "Test Session",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            courts = listOf(court),
            roster = players
        )

        val rec = RotationEngine.generateRecommendation(session, 1, null)
        assertNotNull(rec)
        assertEquals(1, rec?.courtId)
        assertEquals(4, rec?.incomingPlayers?.size)
        assertEquals("Player 1", rec?.incomingPlayers?.get(0)?.name)
    }
}
