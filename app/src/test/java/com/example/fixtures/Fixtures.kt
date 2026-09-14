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
}
