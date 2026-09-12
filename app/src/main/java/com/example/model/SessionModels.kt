package com.example.model

enum class RotationPolicy(val displayName: String, val description: String) {
    FOUR_OFF_FOUR_ON(
        displayName = "4-Off / 4-On",
        description = "All 4 players rotate off; the next 4 in queue take the court."
    ),
    WINNERS_STAY_SPLIT(
        displayName = "Winners Stay & Split",
        description = "Winners stay on court for up to 2 games and split sides; 2 queue players step up."
    )
}

enum class ParticipantStatus {
    AVAILABLE,
    IN_MATCH,
    RESTING,
    CHECKED_OUT
}

enum class CourtStatus {
    AVAILABLE,
    IN_PROGRESS,
    PAUSED
}

enum class TeamId {
    TEAM_A,
    TEAM_B
}

enum class CourtSide {
    RIGHT,
    LEFT
}

data class Player(
    val id: String,
    val name: String,
    val status: ParticipantStatus = ParticipantStatus.AVAILABLE,
    val queuedTimestamp: Long = System.currentTimeMillis(),
    val restingTimestamp: Long = 0L,
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val totalPointsScored: Int = 0,
    val totalPointsConceded: Int = 0,
    val consecutiveGamesOnCourt: Int = 0
)

data class Court(
    val id: Int,
    val name: String,
    val status: CourtStatus = CourtStatus.AVAILABLE,
    val currentMatch: Match? = null
)

data class Team(
    val id: TeamId,
    val player1: Player,
    val player2: Player
) {
    fun playerNames(): String = "${player1.name} & ${player2.name}"
}

data class RallyEvent(
    val rallyIndex: Int,
    val winningTeam: TeamId,
    val scoreA: Int,
    val scoreB: Int,
    val servingTeam: TeamId,
    val serverNumber: Int,
    val serverName: String,
    val receiverName: String,
    val servingSide: CourtSide,
    val description: String,
    val isSideOut: Boolean
)

data class Match(
    val id: String,
    val courtId: Int,
    val teamA: Team,
    val teamB: Team,
    val targetScore: Int = 11,
    val winByTwo: Boolean = true,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val scoreA: Int = 0,
    val scoreB: Int = 0,
    val firstServingTeam: TeamId = TeamId.TEAM_A, // team that served first; needed to replay/undo correctly
    val servingTeam: TeamId = TeamId.TEAM_A,
    val serverNumber: Int = 2, // Official initial serve rule at 0-0-2
    val teamAServer1: Player = teamA.player1,
    val teamBServer1: Player = teamB.player1,
    val currentServer: Player = teamA.player1,
    val currentReceiver: Player = teamB.player1,
    val servingSide: CourtSide = CourtSide.RIGHT,
    val rallyHistory: List<RallyEvent> = emptyList(),
    val isCompleted: Boolean = false,
    val winnerTeamId: TeamId? = null
) {
    fun calloutString(): String {
        return "$scoreA - $scoreB - $serverNumber"
    }

    fun isLegalWin(): Boolean {
        val diff = kotlin.math.abs(scoreA - scoreB)
        val maxScore = kotlin.math.max(scoreA, scoreB)
        return maxScore >= targetScore && (!winByTwo || diff >= 2)
    }
}

data class RotationRecommendation(
    val courtId: Int,
    val teamA: Team,
    val teamB: Team,
    val departingPlayers: List<Player>,
    val retainedPlayers: List<Player>,
    val incomingPlayers: List<Player>,
    val primaryReason: String,
    val detailedReason: List<String>,
    val warningMessage: String? = null
)

data class OpenPlaySession(
    val id: String,
    val name: String,
    val startTime: Long = System.currentTimeMillis(),
    val rotationPolicy: RotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
    val consecutiveGameCap: Int = 2,
    val targetScore: Int = 11,
    val courts: List<Court> = emptyList(),
    val roster: List<Player> = emptyList(),
    val completedMatches: List<Match> = emptyList(),
    val activeRecommendations: Map<Int, RotationRecommendation> = emptyMap(),
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false
)
