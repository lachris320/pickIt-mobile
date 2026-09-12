package com.example.engine

import com.example.model.*

/**
 * Pure Kotlin Pickleball Rules Engine implementing official USA Pickleball doubles scoring.
 * Evaluates 'Team A Won Rally' or 'Team B Won Rally' into exact scores, server numbers,
 * court positions, side-outs, and win states. Supports 100% accurate event-sourced Undo.
 */
object PickleballGameEngine {

    fun createMatch(
        courtId: Int,
        teamA: Team,
        teamB: Team,
        targetScore: Int = 11,
        winByTwo: Boolean = true
    ): Match {
        // Official start: Serving team begins on Server 2 (0-0-2)
        return Match(
            id = "match_${System.currentTimeMillis()}_${courtId}",
            courtId = courtId,
            teamA = teamA,
            teamB = teamB,
            targetScore = targetScore,
            winByTwo = winByTwo,
            scoreA = 0,
            scoreB = 0,
            servingTeam = TeamId.TEAM_A,
            serverNumber = 2,
            teamAServer1 = teamA.player1,
            teamBServer1 = teamB.player1,
            currentServer = teamA.player1,
            currentReceiver = teamB.player1,
            servingSide = CourtSide.RIGHT,
            rallyHistory = emptyList(),
            isCompleted = false
        )
    }

    fun recordRally(current: Match, winningTeam: TeamId): Match {
        if (current.isCompleted) return current

        val isServingTeamWinner = (winningTeam == current.servingTeam)

        var newScoreA = current.scoreA
        var newScoreB = current.scoreB
        var newServingTeam = current.servingTeam
        var newServerNumber = current.serverNumber
        var newServer = current.currentServer
        var newReceiver = current.currentReceiver
        var newServingSide = current.servingSide
        var isSideOut = false
        var description = ""

        if (isServingTeamWinner) {
            // Serving team won rally -> Points are awarded ONLY to the serving team
            if (current.servingTeam == TeamId.TEAM_A) {
                newScoreA++
                description = "Point Team A (${current.teamA.playerNames()})"
            } else {
                newScoreB++
                description = "Point Team B (${current.teamB.playerNames()})"
            }

            // Serving team switches sides (Right <-> Left)
            newServingSide = if (current.servingSide == CourtSide.RIGHT) CourtSide.LEFT else CourtSide.RIGHT

            // The serving player continues to serve; receiver is determined by opposing side
            val servingTeamObj = if (current.servingTeam == TeamId.TEAM_A) current.teamA else current.teamB
            val receivingTeamObj = if (current.servingTeam == TeamId.TEAM_A) current.teamB else current.teamA

            newServer = current.currentServer
            newReceiver = if (newServingSide == CourtSide.RIGHT) receivingTeamObj.player1 else receivingTeamObj.player2
        } else {
            // Receiving team won rally -> Fault on serving team
            if (current.serverNumber == 1) {
                // Advance to Server 2 for the same team
                newServerNumber = 2
                val servingTeamObj = if (current.servingTeam == TeamId.TEAM_A) current.teamA else current.teamB
                val receivingTeamObj = if (current.servingTeam == TeamId.TEAM_A) current.teamB else current.teamA

                // Server 2 is the partner of Server 1
                newServer = if (current.currentServer.id == servingTeamObj.player1.id) {
                    servingTeamObj.player2
                } else {
                    servingTeamObj.player1
                }
                newReceiver = if (newServingSide == CourtSide.RIGHT) receivingTeamObj.player1 else receivingTeamObj.player2
                description = "Fault. Second Server: ${newServer.name}"
            } else {
                // Server 2 faulted -> SIDE OUT
                isSideOut = true
                newServingTeam = if (current.servingTeam == TeamId.TEAM_A) TeamId.TEAM_B else TeamId.TEAM_A
                newServerNumber = 1

                val newServingTeamObj = if (newServingTeam == TeamId.TEAM_A) current.teamA else current.teamB
                val newReceivingTeamObj = if (newServingTeam == TeamId.TEAM_A) current.teamB else current.teamA
                val currentServingScore = if (newServingTeam == TeamId.TEAM_A) newScoreA else newScoreB

                // Serve side is determined by whether the serving team's score is even (Right) or odd (Left)
                newServingSide = if (currentServingScore % 2 == 0) CourtSide.RIGHT else CourtSide.LEFT

                newServer = newServingTeamObj.player1
                newReceiver = if (newServingSide == CourtSide.RIGHT) newReceivingTeamObj.player1 else newReceivingTeamObj.player2
                description = "Side Out! Serve transfers to ${newServingTeamObj.playerNames()}"
            }
        }

        val event = RallyEvent(
            rallyIndex = current.rallyHistory.size + 1,
            winningTeam = winningTeam,
            scoreA = newScoreA,
            scoreB = newScoreB,
            servingTeam = newServingTeam,
            serverNumber = newServerNumber,
            serverName = newServer.name,
            receiverName = newReceiver.name,
            servingSide = newServingSide,
            description = description,
            isSideOut = isSideOut
        )

        val updatedHistory = current.rallyHistory + event

        // Check winning condition
        val diff = kotlin.math.abs(newScoreA - newScoreB)
        val maxScore = kotlin.math.max(newScoreA, newScoreB)
        val hasWon = maxScore >= current.targetScore && (!current.winByTwo || diff >= 2)
        val winner = if (hasWon) {
            if (newScoreA > newScoreB) TeamId.TEAM_A else TeamId.TEAM_B
        } else null

        return current.copy(
            scoreA = newScoreA,
            scoreB = newScoreB,
            servingTeam = newServingTeam,
            serverNumber = newServerNumber,
            currentServer = newServer,
            currentReceiver = newReceiver,
            servingSide = newServingSide,
            rallyHistory = updatedHistory,
            isCompleted = hasWon,
            winnerTeamId = winner,
            endTime = if (hasWon) System.currentTimeMillis() else null
        )
    }

    /**
     * Pops the last rally event from history and recalculates state from start,
     * guaranteeing 100% mathematical integrity for Undo.
     */
    fun undoLastRally(current: Match): Match {
        if (current.rallyHistory.isEmpty()) return current

        val previousEvents = current.rallyHistory.dropLast(1)
        var replayMatch = createMatch(
            courtId = current.courtId,
            teamA = current.teamA,
            teamB = current.teamB,
            targetScore = current.targetScore,
            winByTwo = current.winByTwo
        )

        for (event in previousEvents) {
            replayMatch = recordRally(replayMatch, event.winningTeam)
        }

        return replayMatch
    }

    /**
     * Directly records a completed match with final scores (Path B).
     */
    fun createCompletedMatch(
        courtId: Int,
        teamA: Team,
        teamB: Team,
        scoreA: Int,
        scoreB: Int,
        targetScore: Int = 11,
        winByTwo: Boolean = true
    ): Match {
        val winner = if (scoreA > scoreB) TeamId.TEAM_A else TeamId.TEAM_B
        return Match(
            id = "match_${System.currentTimeMillis()}_${courtId}",
            courtId = courtId,
            teamA = teamA,
            teamB = teamB,
            targetScore = targetScore,
            winByTwo = winByTwo,
            scoreA = scoreA,
            scoreB = scoreB,
            isCompleted = true,
            winnerTeamId = winner,
            endTime = System.currentTimeMillis()
        )
    }
}
