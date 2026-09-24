package com.example.engine

import com.example.model.MatchResult
import com.example.model.Player
import com.example.model.PlayerDetail
import com.example.model.PlayerDetailData
import com.example.model.PlayerRef
import com.example.model.PlayerSessionRecord
import com.example.model.RankedPlayer
import com.example.model.SessionMeta
import com.example.model.SessionSummary
import com.example.model.TeamId
import java.util.Locale

/**
 * Pure aggregation for Session History. Reuses [RankingEngine] for every ranking so the ordering
 * rules (wins -> point-diff -> name -> id, competition ranks) are identical everywhere.
 */
object HistoryAggregator {

    private fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)

    /**
     * All-time leaderboard aggregated by normalized name over matches WITH a valid winner
     * (null-winner matches are skipped entirely). Ranks on the normalized name for locale-stable
     * tie ordering, then relabels each row to its display spelling (post-rank, never affects order).
     */
    fun rankAllTime(matches: List<MatchResult>): List<RankedPlayer> {
        data class Acc(var wins: Int = 0, var games: Int = 0, var pf: Int = 0, var pa: Int = 0)
        // Display-spelling candidate key: highest endTime, then highest matchId, then lowest slot.
        data class SpellKey(val endTime: Long, val matchId: String, val slot: Int)

        val accs = LinkedHashMap<String, Acc>()
        val bestSpell = HashMap<String, Pair<SpellKey, String>>()

        fun considerSpelling(norm: String, raw: String, key: SpellKey) {
            val cur = bestSpell[norm]
            val better = cur == null ||
                key.endTime > cur.first.endTime ||
                (key.endTime == cur.first.endTime && key.matchId > cur.first.matchId) ||
                (key.endTime == cur.first.endTime && key.matchId == cur.first.matchId && key.slot < cur.first.slot)
            if (better) bestSpell[norm] = key to raw
        }

        for (m in matches) {
            val winner = m.winner ?: continue // skip null-winner matches entirely
            val slots = listOf(
                Triple(m.teamA.getOrNull(0), TeamId.TEAM_A, 0),
                Triple(m.teamA.getOrNull(1), TeamId.TEAM_A, 1),
                Triple(m.teamB.getOrNull(0), TeamId.TEAM_B, 2),
                Triple(m.teamB.getOrNull(1), TeamId.TEAM_B, 3),
            )
            for ((rawNameOrNull, team, slot) in slots) {
                val raw = rawNameOrNull ?: continue
                val norm = normalize(raw)
                if (norm.isEmpty()) continue
                val acc = accs.getOrPut(norm) { Acc() }
                acc.games += 1
                if (team == TeamId.TEAM_A) { acc.pf += m.scoreA; acc.pa += m.scoreB }
                else { acc.pf += m.scoreB; acc.pa += m.scoreA }
                if (winner == team) acc.wins += 1
                considerSpelling(norm, raw, SpellKey(m.endTime, m.matchId, slot))
            }
        }

        val synthetic = accs.map { (norm, a) ->
            Player(
                id = norm,
                name = norm, // rank on the normalized name -> locale-stable tie ordering
                matchesPlayed = a.games,
                matchesWon = a.wins,
                totalPointsScored = a.pf,
                totalPointsConceded = a.pa,
            )
        }
        return RankingEngine.rank(synthetic).map { rp ->
            rp.copy(name = bestSpell[rp.id]?.second ?: rp.name) // restore display spelling post-rank
        }
    }

    /**
     * One player's cross-session record, keyed by normalized name (same identity as [rankAllTime]).
     * The header reuses the player's [rankAllTime] row; the per-session breakdown mirrors rankAllTime's
     * per-slot attribution over the same valid-winner matches, so sum(records) == header by
     * construction. Sessions with missing metadata are retained (name/startTime null). Returns
     * NotAvailable if the id has no valid-winner contribution.
     */
    fun buildPlayerDetail(
        matches: List<MatchResult>,
        sessions: List<SessionMeta>,
        normalizedId: String,
        activeSessionId: String?,
    ): PlayerDetail {
        val row = rankAllTime(matches).firstOrNull { it.id == normalizedId }
            ?: return PlayerDetail.NotAvailable

        data class Acc(var games: Int = 0, var wins: Int = 0, var pf: Int = 0, var pa: Int = 0)
        val bySession = LinkedHashMap<String, Acc>()
        for (m in matches) {
            val winner = m.winner ?: continue
            val slots = listOf(
                m.teamA.getOrNull(0) to TeamId.TEAM_A,
                m.teamA.getOrNull(1) to TeamId.TEAM_A,
                m.teamB.getOrNull(0) to TeamId.TEAM_B,
                m.teamB.getOrNull(1) to TeamId.TEAM_B,
            )
            for ((rawOrNull, team) in slots) {
                val raw = rawOrNull ?: continue
                if (normalize(raw) != normalizedId) continue
                val acc = bySession.getOrPut(m.sessionId) { Acc() }
                acc.games += 1
                if (team == TeamId.TEAM_A) { acc.pf += m.scoreA; acc.pa += m.scoreB }
                else { acc.pf += m.scoreB; acc.pa += m.scoreA }
                if (winner == team) acc.wins += 1
            }
        }

        val metaById = sessions.associateBy { it.id }
        val records = bySession.map { (sid, a) ->
            val meta = metaById[sid]
            PlayerSessionRecord(
                sessionId = sid,
                sessionName = meta?.name,
                startTime = meta?.startTime,
                games = a.games,
                wins = a.wins,
                losses = a.games - a.wins,
                pointDiff = a.pf - a.pa,
                isActive = sid == activeSessionId,
            )
        }.sortedWith(
            compareBy<PlayerSessionRecord> { it.startTime == null }   // non-null first, nulls strictly last
                .thenByDescending { it.startTime ?: 0L }               // newest-first within known dates
                .thenBy { it.sessionId }                               // stable fallback
        )

        return PlayerDetail.Found(
            PlayerDetailData(
                id = row.id,
                displayName = row.name,
                rank = row.rank,
                wins = row.wins,
                losses = row.losses,
                games = row.wins + row.losses,
                pointDiff = row.pointDiff,
                sessionsPlayed = records.size,
                records = records,
            )
        )
    }

    /** Semantic per-session summary. Leaders are roster-driven (rank-1 of RankingEngine.rank). */
    fun sessionSummary(roster: List<Player>, completedMatchCount: Int): SessionSummary {
        val ranked = RankingEngine.rank(roster)
        val leaders = ranked.filter { it.rank == 1 }.map { PlayerRef(it.id, it.name) }
        return SessionSummary(
            gameCount = completedMatchCount,
            participantCount = ranked.size,
            leaders = leaders,
        )
    }
}
