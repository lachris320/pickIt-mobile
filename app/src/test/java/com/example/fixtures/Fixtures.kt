package com.example.fixtures

import com.example.engine.PickleballGameEngine
import com.example.model.Court
import com.example.model.CourtStatus
import com.example.model.OpenPlaySession
import com.example.model.ParticipantStatus
import com.example.model.Player
import com.example.model.RotationPolicy
import com.example.model.RotationRecommendation
import com.example.model.Team
import com.example.model.TeamId

/** Deterministic fixtures for UI/behavior tests — no run-varying timestamps. */
object Fixtures {
    const val KNOWN_PLAYER_ID = "fix_p0"

    private fun player(i: Int) = Player(
        id = "fix_p$i",
        name = "Player $i",
        status = ParticipantStatus.AVAILABLE,
        queuedTimestamp = i.toLong(),
        matchesPlayed = 0,
    )

    private fun rec(courtId: Int, p: List<Player>) = RotationRecommendation(
        courtId = courtId,
        teamA = Team(TeamId.TEAM_A, p[0], p[1]),
        teamB = Team(TeamId.TEAM_B, p[2], p[3]),
        departingPlayers = emptyList(),
        retainedPlayers = emptyList(),
        incomingPlayers = emptyList(),
        primaryReason = "Next up",
        detailedReason = listOf("Fixture recommendation"),
    )

    fun inProgressSession(): OpenPlaySession {
        val p = (0..7).map { player(it) }
        val m1 = PickleballGameEngine.createMatch(
            courtId = 1,
            teamA = Team(TeamId.TEAM_A, p[0], p[1]),
            teamB = Team(TeamId.TEAM_B, p[2], p[3]),
        ).copy(scoreA = 6, scoreB = 4)
        return OpenPlaySession(
            id = "fix_sess",
            name = "Fixture Session",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            courts = listOf(
                Court(id = 1, name = "Court 1", status = CourtStatus.IN_PROGRESS, currentMatch = m1),
            ),
            roster = p,
        )
    }

    fun twoReadyCourtsSession(): OpenPlaySession {
        val p = (0..7).map { player(it) }
        return OpenPlaySession(
            id = "fix_sess2",
            name = "Fixture Session 2",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            courts = listOf(
                Court(id = 2, name = "Court 2", status = CourtStatus.AVAILABLE),
                Court(id = 3, name = "Court 3", status = CourtStatus.AVAILABLE),
            ),
            roster = p,
            activeRecommendations = mapOf(
                2 to rec(2, p.subList(0, 4)),
                3 to rec(3, p.subList(4, 8)),
            ),
        )
    }

    /**
     * Mixed-state board: court 1 UP NOW (lowest AVAILABLE w/ rec), court 2 READY (AVAILABLE w/ rec),
     * court 3 LIVE (IN_PROGRESS, not completed), court 4 FINAL (IN_PROGRESS but match completed),
     * court 5 OPEN (AVAILABLE, no rec).
     */
    fun boardSession(): OpenPlaySession {
        val p = (0..15).map { player(it) }
        val liveMatch = PickleballGameEngine.createMatch(
            courtId = 3,
            teamA = Team(TeamId.TEAM_A, p[8], p[9]),
            teamB = Team(TeamId.TEAM_B, p[10], p[11]),
        ).copy(scoreA = 6, scoreB = 4)
        val finalMatch = PickleballGameEngine.createMatch(
            courtId = 4,
            teamA = Team(TeamId.TEAM_A, p[12], p[13]),
            teamB = Team(TeamId.TEAM_B, p[14], p[15]),
        ).copy(scoreA = 11, scoreB = 7, isCompleted = true)
        return OpenPlaySession(
            id = "fix_board",
            name = "Board Session",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            courts = listOf(
                Court(id = 1, name = "Court 1", status = CourtStatus.AVAILABLE),
                Court(id = 2, name = "Court 2", status = CourtStatus.AVAILABLE),
                Court(id = 3, name = "Court 3", status = CourtStatus.IN_PROGRESS, currentMatch = liveMatch),
                Court(id = 4, name = "Court 4", status = CourtStatus.IN_PROGRESS, currentMatch = finalMatch),
                Court(id = 5, name = "Court 5", status = CourtStatus.AVAILABLE),
            ),
            roster = p,
            activeRecommendations = mapOf(
                1 to rec(1, p.subList(0, 4)),
                2 to rec(2, p.subList(4, 8)),
            ),
        )
    }

    /** Seven AVAILABLE courts each with a recommendation, for pagination tests. */
    fun manyCourtsSession(): OpenPlaySession {
        val p = (0..27).map { player(it) }
        val courts = (1..7).map { id -> Court(id = id, name = "Court $id", status = CourtStatus.AVAILABLE) }
        val recs = (1..7).associateWith { id -> rec(id, p.subList((id - 1) * 4, id * 4)) }
        return OpenPlaySession(
            id = "fix_many",
            name = "Many Courts",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            courts = courts,
            roster = p,
            activeRecommendations = recs,
        )
    }
}
