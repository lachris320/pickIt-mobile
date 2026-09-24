package com.example.viewmodel

import com.example.model.MatchResult
import com.example.model.PlayerDetail
import com.example.model.SessionMeta
import com.example.model.TeamId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvePlayerDetailTest {

    private fun match(id: String, session: String) = MatchResult(
        matchId = id, sessionId = session,
        teamA = listOf("Ann", "Bo"), teamB = listOf("Cy", "Dot"),
        scoreA = 11, scoreB = 5, winner = TeamId.TEAM_A, startTime = 0L, endTime = 1L,
    )

    private fun loaded() = HistoryData.Loaded(
        sessions = listOf(SessionMeta("s1", "Mon", 100)),
        matchesBySession = mapOf("s1" to listOf(match("m1", "s1"))),
        rosterBySession = emptyMap(),
    )

    @Test fun `returns NotAvailable when data is not Loaded`() {
        assertEquals(PlayerDetail.NotAvailable, resolvePlayerDetail(HistoryData.Loading, "ann", null))
        assertEquals(PlayerDetail.NotAvailable, resolvePlayerDetail(HistoryData.Error("x"), "ann", null))
    }

    @Test fun `Found for a known normalized id, NotAvailable for unknown`() {
        val d = resolvePlayerDetail(loaded(), "ann", null)
        assertTrue(d is PlayerDetail.Found)
        assertEquals("Ann", (d as PlayerDetail.Found).data.displayName)
        assertEquals(PlayerDetail.NotAvailable, resolvePlayerDetail(loaded(), "nobody", null))
    }

    @Test fun `active session id is threaded through to the breakdown`() {
        val d = resolvePlayerDetail(loaded(), "ann", activeSessionId = "s1") as PlayerDetail.Found
        assertTrue(d.data.records.single { it.sessionId == "s1" }.isActive)
    }
}
