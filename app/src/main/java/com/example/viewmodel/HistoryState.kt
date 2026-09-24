package com.example.viewmodel

import com.example.engine.HistoryAggregator
import com.example.engine.RankingEngine
import com.example.model.MatchResult
import com.example.model.Player
import com.example.model.PlayerDetail
import com.example.model.RankedPlayer
import com.example.model.SessionListItem
import com.example.model.SessionMeta
import com.example.model.SessionSummary

/** Raw reactive data from Room, grouped in memory (produced by HistoryViewModel). */
sealed interface HistoryData {
    data object Loading : HistoryData
    data class Loaded(
        val sessions: List<SessionMeta>,
        val matchesBySession: Map<String, List<MatchResult>>,
        val rosterBySession: Map<String, List<Player>>,
    ) : HistoryData
    data class Error(val message: String) : HistoryData
}

/** What the History list + All-Time tab render from. */
sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Content(
        val pastSessions: List<SessionListItem>,
        val allTime: List<RankedPlayer>,
    ) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

/** What the session-detail view renders from. */
sealed interface SessionDetail {
    data class Found(
        val meta: SessionMeta,
        val standings: List<RankedPlayer>,
        val matches: List<MatchResult>,
    ) : SessionDetail
    data object NotAvailable : SessionDetail
}

/**
 * Assemble the list/all-time state. Stays [HistoryUiState.Loading] until the active-session load
 * has settled (readiness), so the active session is never transiently shown as a past row.
 * The active session is excluded from the past list but still contributes to all-time.
 */
fun assembleHistoryState(
    data: HistoryData,
    activeSessionId: String?,
    isSessionLoaded: Boolean,
): HistoryUiState {
    if (!isSessionLoaded) return HistoryUiState.Loading
    return when (data) {
        is HistoryData.Loading -> HistoryUiState.Loading
        is HistoryData.Error -> HistoryUiState.Error(data.message)
        is HistoryData.Loaded -> {
            val past = data.sessions
                .filter { it.id != activeSessionId }
                .sortedByDescending { it.startTime }
                .map { meta ->
                    SessionListItem(
                        meta = meta,
                        summary = HistoryAggregator.sessionSummary(
                            roster = data.rosterBySession[meta.id].orEmpty(),
                            completedMatchCount = data.matchesBySession[meta.id]?.size ?: 0,
                        ),
                    )
                }
            val allTime = HistoryAggregator.rankAllTime(data.matchesBySession.values.flatten())
            HistoryUiState.Content(pastSessions = past, allTime = allTime)
        }
    }
}

/** Resolve a session-detail view from loaded data; NotAvailable if missing or not yet loaded. */
fun buildSessionDetail(data: HistoryData, sessionId: String): SessionDetail {
    if (data !is HistoryData.Loaded) return SessionDetail.NotAvailable
    val meta = data.sessions.firstOrNull { it.id == sessionId } ?: return SessionDetail.NotAvailable
    val standings = RankingEngine.rank(data.rosterBySession[sessionId].orEmpty())
    val matches = data.matchesBySession[sessionId].orEmpty()
        .sortedWith(compareByDescending<MatchResult> { it.endTime }.thenByDescending { it.matchId })
    return SessionDetail.Found(meta, standings, matches)
}

/** Resolve a player-detail view from loaded data; NotAvailable if not loaded or the id has no results. */
fun resolvePlayerDetail(data: HistoryData, normalizedId: String, activeSessionId: String?): PlayerDetail {
    if (data !is HistoryData.Loaded) return PlayerDetail.NotAvailable
    return HistoryAggregator.buildPlayerDetail(
        matches = data.matchesBySession.values.flatten(),
        sessions = data.sessions,
        normalizedId = normalizedId,
        activeSessionId = activeSessionId,
    )
}

/** Mismatch-aware second-line copy for a session summary. */
fun leaderLine(summary: SessionSummary): String {
    val hasGames = summary.gameCount > 0
    val hasLeaders = summary.leaders.isNotEmpty()
    return when {
        !hasGames && !hasLeaders -> "No completed games"
        hasGames && hasLeaders -> when (summary.leaders.size) {
            1 -> "Top player: ${summary.leaders[0].name}"
            2 -> "Joint leaders: ${summary.leaders[0].name} & ${summary.leaders[1].name}"
            else -> "Joint leaders: ${summary.leaders[0].name} + ${summary.leaders.size - 1} others"
        }
        else -> "Standings unavailable" // (games && !leaders) or (!games && leaders)
    }
}
