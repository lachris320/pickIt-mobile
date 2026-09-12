package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startTime: Long,
    val rotationPolicy: String,
    val consecutiveGameCap: Int,
    val targetScore: Int,
    val isPaused: Boolean,
    val isCompleted: Boolean,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "courts")
data class CourtEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val sessionId: String,
    val courtId: Int,
    val name: String,
    val status: String,
    val matchJson: String?
)

@Entity(tableName = "roster")
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val sessionId: String,
    val playerId: String,
    val name: String,
    val status: String,
    val queuedTimestamp: Long,
    val restingTimestamp: Long,
    val matchesPlayed: Int,
    val matchesWon: Int,
    val totalPointsScored: Int,
    val totalPointsConceded: Int,
    val consecutiveGamesOnCourt: Int
)

@Entity(tableName = "matches")
data class CompletedMatchEntity(
    @PrimaryKey val matchId: String,
    val sessionId: String,
    val courtId: Int,
    val teamAPlayer1: String,
    val teamAPlayer2: String,
    val teamBPlayer1: String,
    val teamBPlayer2: String,
    val scoreA: Int,
    val scoreB: Int,
    val winnerTeam: String,
    val startTime: Long,
    val endTime: Long
)
