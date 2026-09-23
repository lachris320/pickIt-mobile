package com.example.testing

import com.example.data.local.CompletedMatchEntity
import com.example.data.local.CourtEntity
import com.example.data.local.PlayerEntity
import com.example.data.local.SessionDao
import com.example.data.local.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Shared test fake for [SessionDao]. The three reactive reads that [SessionRepository] subscribes
 * to eagerly return empty flows; every one-shot member throws, since fakes here only exercise the
 * `loadLatestSession()` override on the repository above them.
 */
open class FakeSessionDao : SessionDao {
    override fun getAllSessions(): Flow<List<SessionEntity>> = emptyFlow()
    override fun getAllMatches(): Flow<List<CompletedMatchEntity>> = emptyFlow()
    override fun getAllRoster(): Flow<List<PlayerEntity>> = emptyFlow()
    override suspend fun getLatestSession(): SessionEntity? = throw NotImplementedError()
    override suspend fun getSessionById(sessionId: String): SessionEntity? = throw NotImplementedError()
    override suspend fun insertSession(session: SessionEntity) = throw NotImplementedError()
    override suspend fun getCourtsForSession(sessionId: String): List<CourtEntity> = throw NotImplementedError()
    override suspend fun insertCourts(courts: List<CourtEntity>) = throw NotImplementedError()
    override suspend fun deleteCourtsForSession(sessionId: String) = throw NotImplementedError()
    override suspend fun getRosterForSession(sessionId: String): List<PlayerEntity> = throw NotImplementedError()
    override suspend fun insertRoster(roster: List<PlayerEntity>) = throw NotImplementedError()
    override suspend fun deleteRosterForSession(sessionId: String) = throw NotImplementedError()
    override suspend fun getMatchesForSession(sessionId: String): List<CompletedMatchEntity> = throw NotImplementedError()
    override suspend fun insertCompletedMatch(match: CompletedMatchEntity) = throw NotImplementedError()
    override suspend fun deleteSessionById(sessionId: String) = throw NotImplementedError()
}
