package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions ORDER BY lastUpdated DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY lastUpdated DESC LIMIT 1")
    suspend fun getLatestSession(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("SELECT * FROM courts WHERE sessionId = :sessionId ORDER BY courtId ASC")
    suspend fun getCourtsForSession(sessionId: String): List<CourtEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourts(courts: List<CourtEntity>)

    @Query("DELETE FROM courts WHERE sessionId = :sessionId")
    suspend fun deleteCourtsForSession(sessionId: String)

    @Query("SELECT * FROM roster WHERE sessionId = :sessionId ORDER BY queuedTimestamp ASC")
    suspend fun getRosterForSession(sessionId: String): List<PlayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoster(roster: List<PlayerEntity>)

    @Query("DELETE FROM roster WHERE sessionId = :sessionId")
    suspend fun deleteRosterForSession(sessionId: String)

    @Query("SELECT * FROM matches WHERE sessionId = :sessionId ORDER BY endTime DESC")
    suspend fun getMatchesForSession(sessionId: String): List<CompletedMatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletedMatch(match: CompletedMatchEntity)

    @Transaction
    suspend fun saveFullSession(
        session: SessionEntity,
        courts: List<CourtEntity>,
        roster: List<PlayerEntity>
    ) {
        insertSession(session)
        deleteCourtsForSession(session.id)
        insertCourts(courts)
        deleteRosterForSession(session.id)
        insertRoster(roster)
    }

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)
}
