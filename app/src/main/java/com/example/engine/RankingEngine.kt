package com.example.engine

import com.example.model.ParticipantStatus
import com.example.model.Player
import com.example.model.RankedPlayer

/**
 * Pure ranking for the Live Ranking board. Includes only players with >= 1 completed game
 * (any status). Orders by wins desc, then point differential desc, then name asc, then id asc;
 * assigns competition ranks so players tied on (wins, diff) share a rank number (1, 2, 2, 4).
 */
object RankingEngine {
    fun rank(roster: List<Player>): List<RankedPlayer> {
        val eligible = roster.filter { it.matchesPlayed >= 1 }
        val sorted = eligible.sortedWith(
            compareByDescending<Player> { it.matchesWon }
                .thenByDescending { it.totalPointsScored - it.totalPointsConceded }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
        return sorted.map { p ->
            val diff = p.totalPointsScored - p.totalPointsConceded
            val strictlyAhead = sorted.count { other ->
                val od = other.totalPointsScored - other.totalPointsConceded
                other.matchesWon > p.matchesWon ||
                    (other.matchesWon == p.matchesWon && od > diff)
            }
            RankedPlayer(
                id = p.id,
                rank = 1 + strictlyAhead,
                name = p.name,
                wins = p.matchesWon,
                losses = p.matchesPlayed - p.matchesWon,
                pointDiff = diff,
                isCheckedOut = p.status == ParticipantStatus.CHECKED_OUT,
            )
        }
    }
}
