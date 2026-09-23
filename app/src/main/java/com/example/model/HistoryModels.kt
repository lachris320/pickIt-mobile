package com.example.model

/** One completed match, DB-free (repository maps CompletedMatchEntity -> this). */
data class MatchResult(
    val matchId: String,
    val sessionId: String,
    val teamA: List<String>,   // [A1, A2] names, fixed slot order
    val teamB: List<String>,   // [B1, B2] names, fixed slot order
    val scoreA: Int,
    val scoreB: Int,
    val winner: TeamId?,       // null when winnerTeam is not TEAM_A/TEAM_B ("UNKNOWN"/malformed)
    val startTime: Long,
    val endTime: Long,
)

/** A player reference that keeps id so identical display names stay distinct. */
data class PlayerRef(val id: String, val name: String)

/** Semantic summary of one session (UI formats the copy from this). */
data class SessionSummary(
    val gameCount: Int,          // completed matches for the session (from the matches table)
    val participantCount: Int,   // roster players with matchesPlayed >= 1
    val leaders: List<PlayerRef> // rank-1 entries; empty iff no ranked roster players (roster-driven)
)

/** Lightweight session header for the history list. */
data class SessionMeta(
    val id: String,
    val name: String,
    val startTime: Long,
)

/** One row of the History list: session header + its summary. */
data class SessionListItem(
    val meta: SessionMeta,
    val summary: SessionSummary,
)
