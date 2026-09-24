package com.example

import com.example.engine.HistoryAggregator
import com.example.model.MatchResult
import com.example.model.PlayerDetail
import com.example.model.SessionMeta
import com.example.model.TeamId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDetailAggregatorTest {

    private fun match(
        id: String, session: String,
        a1: String, a2: String, b1: String, b2: String,
        scoreA: Int, scoreB: Int, winner: TeamId?,
        endTime: Long = 0L,
    ) = MatchResult(
        matchId = id, sessionId = session,
        teamA = listOf(a1, a2), teamB = listOf(b1, b2),
        scoreA = scoreA, scoreB = scoreB, winner = winner,
        startTime = 0L, endTime = endTime,
    )

    private fun meta(id: String, name: String, startTime: Long) = SessionMeta(id, name, startTime)

    private fun twoSessions() = listOf(
        match("m1", "s1", a1 = "Ann", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 4, winner = TeamId.TEAM_A, endTime = 1),
        match("m2", "s2", a1 = "Ann", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 6, winner = TeamId.TEAM_A, endTime = 2),
    )
    private fun twoMetas() = listOf(meta("s1", "Mon", 100), meta("s2", "Tue", 200))

    private fun found(d: PlayerDetail): PlayerDetail.Found {
        assertTrue("expected Found but was $d", d is PlayerDetail.Found)
        return d as PlayerDetail.Found
    }

    @Test fun `header equals the all-time row and sessionsPlayed counts distinct sessions`() {
        val d = found(HistoryAggregator.buildPlayerDetail(twoSessions(), twoMetas(), "ann", activeSessionId = null)).data
        val allTimeAnn = HistoryAggregator.rankAllTime(twoSessions()).single { it.id == "ann" }
        assertEquals(allTimeAnn.rank, d.rank)
        assertEquals(allTimeAnn.wins, d.wins)
        assertEquals(allTimeAnn.losses, d.losses)
        assertEquals(allTimeAnn.pointDiff, d.pointDiff)
        assertEquals(allTimeAnn.wins + allTimeAnn.losses, d.games)
        assertEquals("Ann", d.displayName)
        assertEquals(2, d.sessionsPlayed)
        assertEquals(2, d.records.size)
    }

    @Test fun `sum of records equals the header totals`() {
        val d = found(HistoryAggregator.buildPlayerDetail(twoSessions(), twoMetas(), "ann", null)).data
        assertEquals(d.games, d.records.sumOf { it.games })
        assertEquals(d.wins, d.records.sumOf { it.wins })
        assertEquals(d.losses, d.records.sumOf { it.losses })
        assertEquals(d.pointDiff, d.records.sumOf { it.pointDiff })
    }

    @Test fun `records are ordered by startTime DESC then sessionId`() {
        val d = found(HistoryAggregator.buildPlayerDetail(twoSessions(), twoMetas(), "ann", null)).data
        assertEquals(listOf("s2", "s1"), d.records.map { it.sessionId }) // Tue(200) before Mon(100)
    }

    @Test fun `null-winner-only session does not count and is excluded`() {
        val matches = twoSessions() + match("m9", "s9", a1 = "Ann", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 9, scoreB = 9, winner = null, endTime = 9)
        val metas = twoMetas() + meta("s9", "Tie Night", 900)
        val d = found(HistoryAggregator.buildPlayerDetail(matches, metas, "ann", null)).data
        assertEquals(2, d.sessionsPlayed)
        assertTrue(d.records.none { it.sessionId == "s9" })
    }

    @Test fun `missing session metadata is retained with null name and sorts last`() {
        val matches = listOf(
            match("m1", "s1", a1 = "Ann", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 4, winner = TeamId.TEAM_A, endTime = 1),
            match("m2", "ghost", a1 = "Ann", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 2, winner = TeamId.TEAM_A, endTime = 2),
        )
        val d = found(HistoryAggregator.buildPlayerDetail(matches, listOf(meta("s1", "Mon", 100)), "ann", null)).data
        assertEquals(2, d.sessionsPlayed)
        val ghost = d.records.single { it.sessionId == "ghost" }
        assertEquals(null, ghost.sessionName)
        assertEquals(null, ghost.startTime)
        assertEquals("ghost", d.records.last().sessionId) // null startTime sorts last
        assertEquals(d.games, d.records.sumOf { it.games })
    }

    @Test fun `active session is tagged`() {
        val d = found(HistoryAggregator.buildPlayerDetail(twoSessions(), twoMetas(), "ann", activeSessionId = "s2")).data
        assertTrue(d.records.single { it.sessionId == "s2" }.isActive)
        assertTrue(!d.records.single { it.sessionId == "s1" }.isActive)
    }

    @Test fun `unknown id and null-winner-only name both yield NotAvailable`() {
        assertEquals(PlayerDetail.NotAvailable, HistoryAggregator.buildPlayerDetail(twoSessions(), twoMetas(), "nobody", null))
        val onlyNull = listOf(match("m1", "s1", a1 = "Zed", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 9, scoreB = 9, winner = null, endTime = 1))
        assertEquals(PlayerDetail.NotAvailable, HistoryAggregator.buildPlayerDetail(onlyNull, listOf(meta("s1", "s1", 1)), "zed", null))
    }

    @Test fun `same normalized name in two slots of one match double-counts, invariant holds`() {
        val matches = listOf(
            match("m1", "s1", a1 = "Ann", a2 = "ANN", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 3, winner = TeamId.TEAM_A, endTime = 1),
        )
        val d = found(HistoryAggregator.buildPlayerDetail(matches, listOf(meta("s1", "Mon", 1)), "ann", null)).data
        val rec = d.records.single { it.sessionId == "s1" }
        assertEquals(2, rec.games)
        assertEquals(2, rec.wins)
        assertEquals(d.games, d.records.sumOf { it.games })
        assertEquals(d.wins, d.records.sumOf { it.wins })
    }
}
