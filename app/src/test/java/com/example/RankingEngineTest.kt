package com.example

import com.example.engine.RankingEngine
import com.example.model.ParticipantStatus
import com.example.model.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class RankingEngineTest {

    private fun player(
        id: String, name: String, played: Int, won: Int,
        pf: Int = 0, pa: Int = 0,
        status: ParticipantStatus = ParticipantStatus.AVAILABLE,
    ) = Player(
        id = id, name = name, status = status,
        matchesPlayed = played, matchesWon = won,
        totalPointsScored = pf, totalPointsConceded = pa,
    )

    @Test fun `ranks by wins then point diff then name then id`() {
        val roster = listOf(
            player("c", "Cara", played = 3, won = 2, pf = 30, pa = 20),   // 2 wins, +10
            player("a", "Ana", played = 3, won = 3, pf = 33, pa = 10),    // 3 wins, +23
            player("b", "Bea", played = 3, won = 2, pf = 25, pa = 20),    // 2 wins, +5
        )
        val ranked = RankingEngine.rank(roster)
        assertEquals(listOf("a", "c", "b"), ranked.map { it.id })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
        assertEquals(23, ranked[0].pointDiff)
        assertEquals(0, ranked[0].losses) // 3 played, 3 won
    }

    @Test fun `competition ranking shares a number for equal wins and diff`() {
        val roster = listOf(
            player("w", "Win", played = 4, won = 4, pf = 44, pa = 20),   // rank 1
            player("x", "Xan", played = 3, won = 2, pf = 22, pa = 12),   // 2 wins, +10
            player("y", "Yas", played = 5, won = 2, pf = 30, pa = 20),   // 2 wins, +10 (diff losses)
            player("z", "Zed", played = 2, won = 0, pf = 5, pa = 15),    // rank 4
        )
        val ranked = RankingEngine.rank(roster).associateBy { it.id }
        assertEquals(1, ranked.getValue("w").rank)
        // x and y tie on wins(2)+diff(+10) despite different losses -> both rank 2
        assertEquals(2, ranked.getValue("x").rank)
        assertEquals(2, ranked.getValue("y").rank)
        assertEquals(4, ranked.getValue("z").rank) // competition ranking skips 3
    }

    @Test fun `three-way tie yields 1 1 1 4`() {
        val roster = listOf(
            player("a", "Ana", played = 2, won = 2, pf = 22, pa = 10), // 2 wins, +12
            player("b", "Bea", played = 3, won = 2, pf = 24, pa = 12), // 2 wins, +12
            player("c", "Cara", played = 4, won = 2, pf = 26, pa = 14),// 2 wins, +12
            player("d", "Dan", played = 2, won = 1, pf = 10, pa = 12), // 1 win, -2
        )
        val ranked = RankingEngine.rank(roster)
        assertEquals(listOf(1, 1, 1, 4), ranked.map { it.rank })
    }

    @Test fun `duplicate names order by id but share rank number`() {
        val roster = listOf(
            player("id2", "Sam", played = 2, won = 2, pf = 20, pa = 10),
            player("id1", "Sam", played = 2, won = 2, pf = 20, pa = 10),
        )
        val ranked = RankingEngine.rank(roster)
        assertEquals(listOf("id1", "id2"), ranked.map { it.id }) // id breaks list order
        assertEquals(listOf(1, 1), ranked.map { it.rank })       // but rank number is shared
    }

    @Test fun `filters zero-game players and includes every status with games`() {
        val roster = listOf(
            player("a", "Ana", played = 0, won = 0),                                   // hidden
            player("r", "Rex", played = 2, won = 1, status = ParticipantStatus.RESTING),
            player("m", "Mia", played = 2, won = 1, status = ParticipantStatus.IN_MATCH),
            player("o", "Oli", played = 2, won = 1, status = ParticipantStatus.CHECKED_OUT),
        )
        val ranked = RankingEngine.rank(roster)
        assertEquals(setOf("r", "m", "o"), ranked.map { it.id }.toSet())
        val checkedOut = ranked.filter { it.isCheckedOut }.map { it.id }
        assertEquals(listOf("o"), checkedOut) // only CHECKED_OUT is flagged
    }

    @Test fun `empty roster yields empty list`() {
        assertEquals(emptyList<Any>(), RankingEngine.rank(emptyList()))
    }
}
