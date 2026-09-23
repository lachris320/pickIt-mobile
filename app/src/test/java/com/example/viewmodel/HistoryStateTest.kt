package com.example.viewmodel

import com.example.model.MatchResult
import com.example.model.ParticipantStatus
import com.example.model.Player
import com.example.model.PlayerRef
import com.example.model.SessionMeta
import com.example.model.SessionSummary
import com.example.model.TeamId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStateTest {

    private fun player(id: String, name: String, played: Int, won: Int, pf: Int = 0, pa: Int = 0) =
        Player(
            id = id, name = name, status = ParticipantStatus.AVAILABLE,
            matchesPlayed = played, matchesWon = won,
            totalPointsScored = pf, totalPointsConceded = pa,
        )

    private fun match(id: String, session: String, endTime: Long) = MatchResult(
        matchId = id, sessionId = session,
        teamA = listOf("Ana", "Bo"), teamB = listOf("Cy", "Dot"),
        scoreA = 11, scoreB = 5, winner = TeamId.TEAM_A, startTime = 0L, endTime = endTime,
    )

    private fun loaded() = HistoryData.Loaded(
        sessions = listOf(
            SessionMeta("s1", "Mon", startTime = 100),
            SessionMeta("s2", "Tue", startTime = 200), // newer
            SessionMeta("active", "Now", startTime = 300),
        ),
        matchesBySession = mapOf(
            "s1" to listOf(match("m1", "s1", 1)),
            "s2" to listOf(match("m2", "s2", 2), match("m3", "s2", 3)),
            "active" to listOf(match("m9", "active", 9)),
        ),
        rosterBySession = mapOf(
            "s1" to listOf(player("a", "Ana", 1, 1, 11, 5), player("c", "Cy", 1, 0, 5, 11)),
            "s2" to listOf(player("a", "Ana", 2, 2, 22, 10)),
            "active" to listOf(player("a", "Ana", 1, 1, 11, 5)),
        ),
    )

    @Test fun `assemble returns Loading until the session load settles`() {
        val s = assembleHistoryState(loaded(), activeSessionId = null, isSessionLoaded = false)
        assertEquals(HistoryUiState.Loading, s)
    }

    @Test fun `assemble excludes the active session and sorts past sessions newest-first`() {
        val s = assembleHistoryState(loaded(), activeSessionId = "active", isSessionLoaded = true)
        s as HistoryUiState.Content
        assertEquals(listOf("s2", "s1"), s.pastSessions.map { it.meta.id }) // newest-first, active excluded
        assertEquals(2, s.pastSessions.first().summary.gameCount)            // s2 has 2 matches
    }

    @Test fun `assemble includes all sessions when there is no active session`() {
        val s = assembleHistoryState(loaded(), activeSessionId = null, isSessionLoaded = true)
        s as HistoryUiState.Content
        assertEquals(setOf("s1", "s2", "active"), s.pastSessions.map { it.meta.id }.toSet())
    }

    @Test fun `assemble all-time spans every session including the active one`() {
        val s = assembleHistoryState(loaded(), activeSessionId = "active", isSessionLoaded = true)
        s as HistoryUiState.Content
        val ana = s.allTime.single { it.id == "ana" }
        assertEquals(4, ana.wins) // 1 (s1) + 2 (s2) + 1 (active)
    }

    @Test fun `assemble passes through Loading and Error`() {
        assertEquals(HistoryUiState.Loading, assembleHistoryState(HistoryData.Loading, null, true))
        assertTrue(assembleHistoryState(HistoryData.Error("x"), null, true) is HistoryUiState.Error)
    }

    @Test fun `buildSessionDetail returns Found with roster standings and newest-first matches`() {
        val d = buildSessionDetail(loaded(), "s2")
        d as SessionDetail.Found
        assertEquals("Tue", d.meta.name)
        assertEquals(listOf("m3", "m2"), d.matches.map { it.matchId }) // endTime DESC
        assertEquals("a", d.standings.first().id)                       // Ana tops the roster ranking
    }

    @Test fun `buildSessionDetail returns NotAvailable for a vanished session or while loading`() {
        assertEquals(SessionDetail.NotAvailable, buildSessionDetail(loaded(), "nope"))
        assertEquals(SessionDetail.NotAvailable, buildSessionDetail(HistoryData.Loading, "s1"))
    }

    // --- leaderLine (mismatch-aware) ---

    private fun summary(games: Int, leaders: List<String>) = SessionSummary(
        gameCount = games, participantCount = leaders.size,
        leaders = leaders.map { PlayerRef(it, it) },
    )

    @Test fun `leaderLine formats no-games, single, joint, and mismatch cases`() {
        assertEquals("No completed games", leaderLine(summary(0, emptyList())))
        assertEquals("Top player: Ana", leaderLine(summary(3, listOf("Ana"))))
        assertEquals("Joint leaders: Ana & Bo", leaderLine(summary(3, listOf("Ana", "Bo"))))
        assertEquals("Joint leaders: Ana + 2 others", leaderLine(summary(3, listOf("Ana", "Bo", "Cy"))))
        assertEquals("Standings unavailable", leaderLine(summary(2, emptyList())))
        assertEquals("Standings unavailable", leaderLine(summary(0, listOf("Ana"))))
    }
}
