package com.example.engine

import com.example.model.*

/**
 * Pure Kotlin Rotation & Allocation Engine implementing the fairness hierarchy:
 * 1. Hard Gate: Availability & Resting state
 * 2. Hard Gate: Anti-Hogging Consecutive-Game Cap
 * 3. Policy: 4-Off vs. Winners-Stay-Split
 * 4. Queue Seniority: FIFO wait time
 * 5. Parity Arbiter: Fewest games played
 * Produces structured, explainable RotationRecommendations.
 */
object RotationEngine {

    fun generateRecommendation(
        session: OpenPlaySession,
        courtId: Int,
        justFinishedMatch: Match?
    ): RotationRecommendation? {
        val court = session.courts.find { it.id == courtId } ?: return null
        if (court.status == CourtStatus.PAUSED) {
            return null // Paused courts are excluded from automatic rotation recommendations
        }

        val availableQueue = session.roster
            .filter { it.status == ParticipantStatus.AVAILABLE }
            .sortedWith(
                compareBy<Player> { it.queuedTimestamp }
                    .thenBy { it.matchesPlayed }
            )

        var departingPlayers = listOf<Player>()
        var retainedPlayers = listOf<Player>()
        var vacanciesNeeded = 4
        var primaryReason = ""
        val detailedReasons = mutableListOf<String>()
        var warning: String? = null

        if (justFinishedMatch != null && justFinishedMatch.isCompleted) {
            val winners = if (justFinishedMatch.winnerTeamId == TeamId.TEAM_A) {
                listOf(justFinishedMatch.teamA.player1, justFinishedMatch.teamA.player2)
            } else {
                listOf(justFinishedMatch.teamB.player1, justFinishedMatch.teamB.player2)
            }

            val losers = if (justFinishedMatch.winnerTeamId == TeamId.TEAM_A) {
                listOf(justFinishedMatch.teamB.player1, justFinishedMatch.teamB.player2)
            } else {
                listOf(justFinishedMatch.teamA.player1, justFinishedMatch.teamA.player2)
            }

            when (session.rotationPolicy) {
                RotationPolicy.FOUR_OFF_FOUR_ON -> {
                    departingPlayers = winners + losers
                    retainedPlayers = emptyList()
                    vacanciesNeeded = 4
                    primaryReason = "Next in queue (4-Off / 4-On)"
                    detailedReasons.add("All 4 players from Court $courtId rotate off.")
                    detailedReasons.add("4 vacancies opened for the waiting queue.")
                }

                RotationPolicy.WINNERS_STAY_SPLIT -> {
                    // Check consecutive game cap
                    val capReached = winners.any { it.consecutiveGamesOnCourt >= session.consecutiveGameCap }

                    if (capReached) {
                        departingPlayers = winners + losers
                        retainedPlayers = emptyList()
                        vacanciesNeeded = 4
                        primaryReason = "Game cap reached (All 4 rotate)"
                        detailedReasons.add("Winners reached consecutive game cap (${session.consecutiveGameCap} games).")
                        detailedReasons.add("All 4 players rotated off to ensure fair court sharing.")
                    } else {
                        departingPlayers = losers
                        retainedPlayers = winners
                        vacanciesNeeded = 2
                        primaryReason = "Winners stay & split • 2 next from queue"
                        detailedReasons.add("Winners stay for game ${(winners.firstOrNull()?.consecutiveGamesOnCourt ?: 1) + 1} of ${session.consecutiveGameCap}.")
                        detailedReasons.add("Winners split to opposite sides of the net for competitive balance.")
                    }
                }
            }
        } else {
            // Empty court assignment (e.g. at session start or after court was cleared)
            vacanciesNeeded = 4
            primaryReason = "Next in queue (#1 - #4)"
            detailedReasons.add("Filling open Court $courtId from the waiting queue.")
        }

        // Draw incoming players from available queue
        val incomingPlayers = mutableListOf<Player>()

        if (availableQueue.size >= vacanciesNeeded) {
            incomingPlayers.addAll(availableQueue.take(vacanciesNeeded))
            detailedReasons.add("${incomingPlayers.joinToString { it.name }} pulled from top of queue.")

            val remainingWaiting = availableQueue.size - vacanciesNeeded
            if (remainingWaiting in 1..3) {
                warning = "Notice: $remainingWaiting player(s) waiting in queue; rotation cadence adjusted."
            }
        } else {
            // Odd player / partial queue situation
            incomingPlayers.addAll(availableQueue)
            val stillNeeded = vacanciesNeeded - availableQueue.size

            if (departingPlayers.isNotEmpty()) {
                // Select from departing players who have played the fewest games today
                val sortedDeparting = departingPlayers.sortedBy { it.matchesPlayed }
                val backfilled = sortedDeparting.take(stillNeeded)
                incomingPlayers.addAll(backfilled)
                warning = "Odd queue depth: ${backfilled.joinToString { it.name }} retained based on lowest game count."
                detailedReasons.add("Only ${availableQueue.size} waiting in queue. ${backfilled.joinToString { it.name }} re-added for parity.")
            } else {
                warning = "Not enough players in queue (Need $vacanciesNeeded, have ${availableQueue.size})."
            }
        }

        if (incomingPlayers.size + retainedPlayers.size < 4) {
            return null // Cannot form a full doubles match yet
        }

        // Pair up teams
        val (teamA, teamB) = if (retainedPlayers.size == 2) {
            // Winners Stay & Split: Pair Winner 1 with Queue 1, Winner 2 with Queue 2
            val winner1 = retainedPlayers[0]
            val winner2 = retainedPlayers[1]
            val queue1 = incomingPlayers.getOrNull(0) ?: winner1
            val queue2 = incomingPlayers.getOrNull(1) ?: winner2

            Team(TeamId.TEAM_A, winner1, queue1) to Team(TeamId.TEAM_B, winner2, queue2)
        } else {
            // All 4 incoming from queue: Pair #1 & #2 vs #3 & #4
            val p1 = incomingPlayers[0]
            val p2 = incomingPlayers[1]
            val p3 = incomingPlayers[2]
            val p4 = incomingPlayers[3]

            Team(TeamId.TEAM_A, p1, p2) to Team(TeamId.TEAM_B, p3, p4)
        }

        return RotationRecommendation(
            courtId = courtId,
            teamA = teamA,
            teamB = teamB,
            departingPlayers = departingPlayers,
            retainedPlayers = retainedPlayers,
            incomingPlayers = incomingPlayers,
            primaryReason = primaryReason,
            detailedReason = detailedReasons,
            warningMessage = warning
        )
    }
}
