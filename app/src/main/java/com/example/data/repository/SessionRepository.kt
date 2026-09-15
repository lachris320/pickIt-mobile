package com.example.data.repository

import com.example.data.local.*
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

// `open` so tests can inject a fake repository that fails `loadLatestSession()` deterministically
// (see SessionViewModelLoadFailureTest) without spinning up a real Room DB.
open class SessionRepository(private val sessionDao: SessionDao) {

    val allSessions: Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    open suspend fun loadLatestSession(): OpenPlaySession? = withContext(Dispatchers.IO) {
        val sessionEntity = sessionDao.getLatestSession() ?: return@withContext null
        val courtsEntities = sessionDao.getCourtsForSession(sessionEntity.id)
        val rosterEntities = sessionDao.getRosterForSession(sessionEntity.id)

        val roster = rosterEntities.map { p ->
            Player(
                id = p.playerId,
                name = p.name,
                status = ParticipantStatus.valueOf(p.status),
                queuedTimestamp = p.queuedTimestamp,
                restingTimestamp = p.restingTimestamp,
                matchesPlayed = p.matchesPlayed,
                matchesWon = p.matchesWon,
                totalPointsScored = p.totalPointsScored,
                totalPointsConceded = p.totalPointsConceded,
                consecutiveGamesOnCourt = p.consecutiveGamesOnCourt
            )
        }

        val courts = courtsEntities.map { c ->
            Court(
                id = c.courtId,
                name = c.name,
                status = CourtStatus.valueOf(c.status),
                currentMatch = null // In-progress match can be re-staged or completed
            )
        }

        OpenPlaySession(
            id = sessionEntity.id,
            name = sessionEntity.name,
            startTime = sessionEntity.startTime,
            rotationPolicy = RotationPolicy.valueOf(sessionEntity.rotationPolicy),
            consecutiveGameCap = sessionEntity.consecutiveGameCap,
            targetScore = sessionEntity.targetScore,
            courts = courts,
            roster = roster,
            isPaused = sessionEntity.isPaused,
            isCompleted = sessionEntity.isCompleted
        )
    }

    suspend fun saveSession(session: OpenPlaySession) = withContext(Dispatchers.IO) {
        val sessionEntity = SessionEntity(
            id = session.id,
            name = session.name,
            startTime = session.startTime,
            rotationPolicy = session.rotationPolicy.name,
            consecutiveGameCap = session.consecutiveGameCap,
            targetScore = session.targetScore,
            isPaused = session.isPaused,
            isCompleted = session.isCompleted,
            lastUpdated = System.currentTimeMillis()
        )

        val courtEntities = session.courts.map { c ->
            CourtEntity(
                sessionId = session.id,
                courtId = c.id,
                name = c.name,
                status = c.status.name,
                matchJson = null
            )
        }

        val rosterEntities = session.roster.map { p ->
            PlayerEntity(
                sessionId = session.id,
                playerId = p.id,
                name = p.name,
                status = p.status.name,
                queuedTimestamp = p.queuedTimestamp,
                restingTimestamp = p.restingTimestamp,
                matchesPlayed = p.matchesPlayed,
                matchesWon = p.matchesWon,
                totalPointsScored = p.totalPointsScored,
                totalPointsConceded = p.totalPointsConceded,
                consecutiveGamesOnCourt = p.consecutiveGamesOnCourt
            )
        }

        sessionDao.saveFullSession(sessionEntity, courtEntities, rosterEntities)
    }

    suspend fun recordCompletedMatch(sessionId: String, match: Match) = withContext(Dispatchers.IO) {
        val matchEntity = CompletedMatchEntity(
            matchId = match.id,
            sessionId = sessionId,
            courtId = match.courtId,
            teamAPlayer1 = match.teamA.player1.name,
            teamAPlayer2 = match.teamA.player2.name,
            teamBPlayer1 = match.teamB.player1.name,
            teamBPlayer2 = match.teamB.player2.name,
            scoreA = match.scoreA,
            scoreB = match.scoreB,
            winnerTeam = match.winnerTeamId?.name ?: "UNKNOWN",
            startTime = match.startTime,
            endTime = match.endTime ?: System.currentTimeMillis()
        )
        sessionDao.insertCompletedMatch(matchEntity)
    }

    suspend fun getCompletedMatchesForSession(sessionId: String): List<CompletedMatchEntity> = withContext(Dispatchers.IO) {
        sessionDao.getMatchesForSession(sessionId)
    }
}
