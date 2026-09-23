package com.example

import com.example.engine.HistoryAggregator
import com.example.model.MatchResult
import com.example.model.ParticipantStatus
import com.example.model.Player
import com.example.model.TeamId
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryAggregatorTest {

    private fun match(
        id: String, session: String = "s",
        a1: String, a2: String, b1: String, b2: String,
        scoreA: Int, scoreB: Int, winner: TeamId?,
        endTime: Long = 0L,
    ) = MatchResult(
        matchId = id, sessionId = session,
        teamA = listOf(a1, a2), teamB = listOf(b1, b2),
        scoreA = scoreA, scoreB = scoreB, winner = winner,
        startTime = 0L, endTime = endTime,
    )

    private fun player(
        id: String, name: String, played: Int, won: Int,
        pf: Int = 0, pa: Int = 0,
        status: ParticipantStatus = ParticipantStatus.AVAILABLE,
    ) = Player(
        id = id, name = name, status = status,
        matchesPlayed = played, matchesWon = won,
        totalPointsScored = pf, totalPointsConceded = pa,
    )

    // --- rankAllTime ---

    @Test fun `rankAllTime attributes scores and wins per team, ranks by wins then diff`() {
        val matches = listOf(
            match("m1", a1 = "Ana", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 4, winner = TeamId.TEAM_A, endTime = 1),
            match("m2", a1 = "Ana", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 6, winner = TeamId.TEAM_A, endTime = 2),
        )
        val ranked = HistoryAggregator.rankAllTime(matches).associateBy { it.name }
        assertEquals(2, ranked.getValue("Ana").wins)
        assertEquals(0, ranked.getValue("Ana").losses)
        assertEquals(12, ranked.getValue("Ana").pointDiff)   // (11+11) - (4+6)
        assertEquals(1, ranked.getValue("Ana").rank)
        assertEquals(1, ranked.getValue("Bo").rank)          // tie share rank 1
        assertEquals(3, ranked.getValue("Cy").rank)          // competition ranking skips 2
        assertEquals(0, ranked.getValue("Cy").wins)
        assertEquals(2, ranked.getValue("Cy").losses)
        assertEquals(-12, ranked.getValue("Cy").pointDiff)
    }

    @Test fun `rankAllTime merges by normalized name across sessions`() {
        val matches = listOf(
            match("m1", session = "s1", a1 = "Ana", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 2, winner = TeamId.TEAM_A, endTime = 1),
            match("m2", session = "s2", a1 = " ana ", a2 = "Eve", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 5, winner = TeamId.TEAM_A, endTime = 2),
        )
        val ranked = HistoryAggregator.rankAllTime(matches)
        val ana = ranked.single { it.id == "ana" }
        assertEquals(2, ana.wins)
        assertEquals(2, ana.wins + ana.losses) // games = 2
        assertEquals("ana", ana.id)            // id is the normalized key
    }

    @Test fun `rankAllTime skips null-winner matches entirely (no game, no loss)`() {
        val matches = listOf(
            match("m1", a1 = "Ana", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 9, winner = null, endTime = 1),
        )
        assertEquals(emptyList<Any>(), HistoryAggregator.rankAllTime(matches))
    }

    @Test fun `rankAllTime picks display spelling from the latest match by endTime`() {
        val matches = listOf(
            match("m1", a1 = "ANA", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 2, winner = TeamId.TEAM_A, endTime = 1),
            match("m2", a1 = "Ana", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 2, winner = TeamId.TEAM_A, endTime = 5),
        )
        val ana = HistoryAggregator.rankAllTime(matches).single { it.id == "ana" }
        assertEquals("Ana", ana.name) // latest endTime spelling wins
    }

    @Test fun `rankAllTime display spelling falls back to highest matchId when endTime ties`() {
        val matches = listOf(
            match("m1", a1 = "Ana", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 2, winner = TeamId.TEAM_A, endTime = 5),
            match("m9", a1 = "ANA", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 2, winner = TeamId.TEAM_A, endTime = 5),
        )
        val ana = HistoryAggregator.rankAllTime(matches).single { it.id == "ana" }
        assertEquals("ANA", ana.name) // same endTime -> higher matchId ("m9" > "m1") wins
    }

    @Test fun `rankAllTime display spelling falls back to lowest slot when endTime and matchId tie`() {
        val matches = listOf(
            match("m1", a1 = "ana", a2 = "ANA", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 2, winner = TeamId.TEAM_A, endTime = 5),
        )
        val ana = HistoryAggregator.rankAllTime(matches).single { it.id == "ana" }
        assertEquals("ana", ana.name) // same match -> lower slot (teamA[0]=0 vs teamA[1]=1) wins
    }

    @Test fun `rankAllTime empty input yields empty`() {
        assertEquals(emptyList<Any>(), HistoryAggregator.rankAllTime(emptyList()))
    }

    // --- sessionSummary ---

    @Test fun `sessionSummary reports gameCount, participantCount, and rank-1 leaders`() {
        val roster = listOf(
            player("a", "Ana", played = 3, won = 3, pf = 33, pa = 10),
            player("b", "Bo", played = 3, won = 1, pf = 20, pa = 25),
            player("z", "Zed", played = 0, won = 0), // no games -> excluded
        )
        val s = HistoryAggregator.sessionSummary(roster, completedMatchCount = 5)
        assertEquals(5, s.gameCount)
        assertEquals(2, s.participantCount)               // a and b (z filtered)
        assertEquals(listOf("a"), s.leaders.map { it.id }) // Ana rank 1
    }

    @Test fun `sessionSummary keeps ids for tied identical-name leaders`() {
        val roster = listOf(
            player("id1", "Sam", played = 2, won = 2, pf = 20, pa = 10),
            player("id2", "Sam", played = 2, won = 2, pf = 20, pa = 10),
        )
        val s = HistoryAggregator.sessionSummary(roster, completedMatchCount = 2)
        assertEquals(setOf("id1", "id2"), s.leaders.map { it.id }.toSet()) // both leaders, distinct ids
    }

    @Test fun `sessionSummary leaders are roster-driven, independent of gameCount`() {
        val roster = listOf(player("a", "Ana", played = 2, won = 2, pf = 20, pa = 10))
        val s = HistoryAggregator.sessionSummary(roster, completedMatchCount = 0)
        assertEquals(0, s.gameCount)
        assertEquals(1, s.leaders.size) // non-empty despite gameCount == 0
    }
}
