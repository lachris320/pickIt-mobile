package com.example.data.repository

import com.example.data.local.CompletedMatchEntity
import com.example.data.local.PlayerEntity
import com.example.model.MatchResult
import com.example.model.ParticipantStatus
import com.example.model.Player
import com.example.model.TeamId

/** CompletedMatchEntity (names + winner string) -> DB-free MatchResult. */
fun CompletedMatchEntity.toMatchResult(): MatchResult = MatchResult(
    matchId = matchId,
    sessionId = sessionId,
    teamA = listOf(teamAPlayer1, teamAPlayer2),
    teamB = listOf(teamBPlayer1, teamBPlayer2),
    scoreA = scoreA,
    scoreB = scoreB,
    winner = when (winnerTeam) {
        "TEAM_A" -> TeamId.TEAM_A
        "TEAM_B" -> TeamId.TEAM_B
        else -> null // "UNKNOWN" or any malformed value
    },
    startTime = startTime,
    endTime = endTime,
)

/** PlayerEntity -> domain Player (id-keyed, with the persisted aggregates). */
fun PlayerEntity.toDomainPlayer(): Player = Player(
    id = playerId,
    name = name,
    status = runCatching { ParticipantStatus.valueOf(status) }.getOrDefault(ParticipantStatus.AVAILABLE),
    queuedTimestamp = queuedTimestamp,
    restingTimestamp = restingTimestamp,
    matchesPlayed = matchesPlayed,
    matchesWon = matchesWon,
    totalPointsScored = totalPointsScored,
    totalPointsConceded = totalPointsConceded,
    consecutiveGamesOnCourt = consecutiveGamesOnCourt,
)
