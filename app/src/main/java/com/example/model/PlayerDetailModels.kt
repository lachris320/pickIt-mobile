package com.example.model

/** One session's slice of a player's cross-session record (match-derived, name-keyed). */
data class PlayerSessionRecord(
    val sessionId: String,
    val sessionName: String?,   // null when the session's metadata is missing -> UI: non-tappable "Session unavailable"
    val startTime: Long?,       // null when metadata missing (ordering: nulls sort last)
    val games: Int,
    val wins: Int,
    val losses: Int,            // games - wins
    val pointDiff: Int,
    val isActive: Boolean,      // sessionId == activeSessionId -> UI tags "In progress"
)

/** A single player's all-time record plus its per-session breakdown. */
data class PlayerDetailData(
    val id: String,             // normalized name (identity key)
    val displayName: String,    // deterministic display spelling (from rankAllTime)
    val rank: Int,              // all-time competition rank
    val wins: Int,
    val losses: Int,
    val games: Int,             // wins + losses
    val pointDiff: Int,
    val sessionsPlayed: Int,    // == records.size
    val records: List<PlayerSessionRecord>, // ordered startTime DESC (nulls last), then sessionId ASC
)

sealed interface PlayerDetail {
    data class Found(val data: PlayerDetailData) : PlayerDetail
    data object NotAvailable : PlayerDetail   // id has no qualifying (valid-winner) results
}
