package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PickleballDatabase
import com.example.data.local.ThemePreferences
import com.example.data.repository.SessionRepository
import com.example.ui.theme.ThemeMode
import com.example.engine.PickleballGameEngine
import com.example.engine.RotationEngine
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AppScreen {
    object Setup : AppScreen()
    object SessionHub : AppScreen()
    data class LiveScoreboard(val courtId: Int) : AppScreen()
    data class StandaloneScoreboard(val match: Match) : AppScreen()
}

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SessionRepository by lazy {
        val db = PickleballDatabase.getInstance(application)
        SessionRepository(db.sessionDao())
    }

    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.SessionHub)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _session = MutableStateFlow<OpenPlaySession?>(null)
    val session: StateFlow<OpenPlaySession?> = _session.asStateFlow()

    // Standalone scoreboard match if not in session
    private val _standaloneMatch = MutableStateFlow<Match?>(null)
    val standaloneMatch: StateFlow<Match?> = _standaloneMatch.asStateFlow()

    // App-level appearance preference, read synchronously so there is no theme flash.
    private val themePrefs = ThemePreferences(application)
    private val _themeMode = MutableStateFlow(themePrefs.readMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        themePrefs.writeMode(mode)
    }

    val frequentPlayers = listOf(
        "Alice M.", "Bob T.", "Charlie D.", "Dave K.",
        "Frank L.", "Grace H.", "Henry P.", "Ivy W.",
        "Ken S.", "Elena R.", "Tom H.", "Sarah B.",
        "David L.", "Priya K.", "Carlos M.", "Chloe W."
    )

    init {
        // Attempt to load previously saved active session from Room database,
        // falling back to initial default demo session if no stored state exists.
        viewModelScope.launch {
            val savedSession = repository.loadLatestSession()
            if (savedSession != null && savedSession.roster.isNotEmpty()) {
                // Re-evaluate recommendations
                val recs = mutableMapOf<Int, RotationRecommendation>()
                savedSession.courts.filter { it.status == CourtStatus.AVAILABLE }.forEach { c ->
                    val r = RotationEngine.generateRecommendation(savedSession, c.id, null)
                    if (r != null) recs[c.id] = r
                }
                _session.value = savedSession.copy(activeRecommendations = recs)
            } else {
                createInitialSession()
            }
        }
    }

    private fun createInitialSession() {
        val initialPlayers = frequentPlayers.take(14).mapIndexed { idx, name ->
            Player(
                id = "p_$idx",
                name = name,
                status = ParticipantStatus.AVAILABLE,
                queuedTimestamp = System.currentTimeMillis() - ((14 - idx) * 60_000L),
                matchesPlayed = if (idx < 8) (idx % 2 + 1) else 0
            )
        }

        val court1Match = PickleballGameEngine.createMatch(
            courtId = 1,
            teamA = Team(TeamId.TEAM_A, initialPlayers[0], initialPlayers[1]),
            teamB = Team(TeamId.TEAM_B, initialPlayers[2], initialPlayers[3])
        ).copy(scoreA = 8, scoreB = 5, serverNumber = 1)

        val court2Match = PickleballGameEngine.createMatch(
            courtId = 2,
            teamA = Team(TeamId.TEAM_A, initialPlayers[4], initialPlayers[5]),
            teamB = Team(TeamId.TEAM_B, initialPlayers[6], initialPlayers[7])
        ).copy(scoreA = 4, scoreB = 2, serverNumber = 2)

        val courts = listOf(
            Court(id = 1, name = "Court 1", status = CourtStatus.IN_PROGRESS, currentMatch = court1Match),
            Court(id = 2, name = "Court 2", status = CourtStatus.IN_PROGRESS, currentMatch = court2Match),
            Court(id = 3, name = "Court 3", status = CourtStatus.AVAILABLE, currentMatch = null)
        )

        val updatedRoster = initialPlayers.mapIndexed { idx, player ->
            if (idx < 8) player.copy(status = ParticipantStatus.IN_MATCH) else player
        }

        val initialSession = OpenPlaySession(
            id = "sess_${System.currentTimeMillis()}",
            name = "Friday Morning Open Play",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            consecutiveGameCap = 2,
            courts = courts,
            roster = updatedRoster
        )

        val rec = RotationEngine.generateRecommendation(initialSession, 3, null)
        val recs = if (rec != null) mapOf(3 to rec) else emptyMap()

        val fullSession = initialSession.copy(activeRecommendations = recs)
        _session.value = fullSession
        persistSession(fullSession)
    }

    private fun persistSession(session: OpenPlaySession) {
        viewModelScope.launch {
            repository.saveSession(session)
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun startNewSession(
        name: String,
        courtCount: Int,
        policy: RotationPolicy,
        playerNames: List<String>
    ) {
        val players = playerNames.mapIndexed { idx, pName ->
            Player(
                id = "player_${System.currentTimeMillis()}_$idx",
                name = pName,
                status = ParticipantStatus.AVAILABLE,
                queuedTimestamp = System.currentTimeMillis() + idx
            )
        }

        val courts = (1..courtCount).map { id ->
            Court(id = id, name = "Court $id", status = CourtStatus.AVAILABLE)
        }

        val newSession = OpenPlaySession(
            id = "session_${System.currentTimeMillis()}",
            name = name.ifBlank { "Open Play Session" },
            rotationPolicy = policy,
            courts = courts,
            roster = players
        )

        val recs = mutableMapOf<Int, RotationRecommendation>()
        courts.forEach { c ->
            val rec = RotationEngine.generateRecommendation(newSession, c.id, null)
            if (rec != null) {
                recs[c.id] = rec
            }
        }

        val fullSession = newSession.copy(activeRecommendations = recs)
        _session.value = fullSession
        persistSession(fullSession)
        _currentScreen.value = AppScreen.SessionHub
    }

    fun addPlayerToRoster(name: String) {
        val current = _session.value ?: return
        if (name.isBlank()) return
        val newPlayer = Player(
            id = "p_${UUID.randomUUID()}",
            name = name.trim(),
            status = ParticipantStatus.AVAILABLE,
            queuedTimestamp = System.currentTimeMillis()
        )
        val updatedRoster = current.roster + newPlayer
        val updated = current.copy(roster = updatedRoster)
        _session.value = updated
        persistSession(updated)
        refreshRecommendations()
    }

    fun togglePlayerRest(playerId: String) {
        val current = _session.value ?: return
        val updatedRoster = current.roster.map { player ->
            if (player.id == playerId) {
                if (player.status == ParticipantStatus.AVAILABLE) {
                    player.copy(status = ParticipantStatus.RESTING, restingTimestamp = System.currentTimeMillis())
                } else if (player.status == ParticipantStatus.RESTING) {
                    player.copy(status = ParticipantStatus.AVAILABLE, queuedTimestamp = System.currentTimeMillis())
                } else player
            } else player
        }
        val updated = current.copy(roster = updatedRoster)
        _session.value = updated
        persistSession(updated)
        refreshRecommendations()
    }

    fun checkOutPlayer(playerId: String) {
        val current = _session.value ?: return
        val updatedRoster = current.roster.map { player ->
            if (player.id == playerId) {
                player.copy(status = ParticipantStatus.CHECKED_OUT)
            } else player
        }
        val updated = current.copy(roster = updatedRoster)
        _session.value = updated
        persistSession(updated)
        refreshRecommendations()
    }

    fun moveQueuePlayer(fromIndex: Int, toIndex: Int) {
        val current = _session.value ?: return
        val available = current.roster.filter { it.status == ParticipantStatus.AVAILABLE }.toMutableList()
        if (fromIndex in available.indices && toIndex in available.indices) {
            val item = available.removeAt(fromIndex)
            available.add(toIndex, item)
            val baseTime = System.currentTimeMillis() - 1000_000L
            val reindexed = available.mapIndexed { idx, p ->
                p.copy(queuedTimestamp = baseTime + (idx * 10_000L))
            }
            val others = current.roster.filter { it.status != ParticipantStatus.AVAILABLE }
            val updated = current.copy(roster = reindexed + others)
            _session.value = updated
            persistSession(updated)
            refreshRecommendations()
        }
    }

    fun confirmRecommendation(courtId: Int) {
        val current = _session.value ?: return
        val rec = current.activeRecommendations[courtId] ?: return

        val newMatch = PickleballGameEngine.createMatch(
            courtId = courtId,
            teamA = rec.teamA,
            teamB = rec.teamB,
            targetScore = current.targetScore
        )

        val matchParticipantIds = listOf(
            rec.teamA.player1.id, rec.teamA.player2.id,
            rec.teamB.player1.id, rec.teamB.player2.id
        ).toSet()

        val updatedRoster = current.roster.map { p ->
            if (p.id in matchParticipantIds) {
                p.copy(
                    status = ParticipantStatus.IN_MATCH,
                    consecutiveGamesOnCourt = p.consecutiveGamesOnCourt + 1
                )
            } else p
        }

        val updatedCourts = current.courts.map { c ->
            if (c.id == courtId) {
                c.copy(status = CourtStatus.IN_PROGRESS, currentMatch = newMatch)
            } else c
        }

        val updatedRecs = current.activeRecommendations - courtId

        val updated = current.copy(
            courts = updatedCourts,
            roster = updatedRoster,
            activeRecommendations = updatedRecs
        )
        _session.value = updated
        persistSession(updated)
    }

    fun recordRallyInMatch(courtId: Int, winningTeam: TeamId) {
        val current = _session.value ?: return
        val court = current.courts.find { it.id == courtId } ?: return
        val activeMatch = court.currentMatch ?: return

        val updatedMatch = PickleballGameEngine.recordRally(activeMatch, winningTeam)

        val updatedCourts = current.courts.map { c ->
            if (c.id == courtId) c.copy(currentMatch = updatedMatch) else c
        }

        val updated = current.copy(courts = updatedCourts)
        _session.value = updated
        persistSession(updated)
    }

    fun undoRallyInMatch(courtId: Int) {
        val current = _session.value ?: return
        val court = current.courts.find { it.id == courtId } ?: return
        val activeMatch = court.currentMatch ?: return

        val revertedMatch = PickleballGameEngine.undoLastRally(activeMatch)

        val updatedCourts = current.courts.map { c ->
            if (c.id == courtId) c.copy(currentMatch = revertedMatch) else c
        }

        val updated = current.copy(courts = updatedCourts)
        _session.value = updated
        persistSession(updated)
    }

    fun completeMatch(courtId: Int, finalScoreA: Int, finalScoreB: Int) {
        val current = _session.value ?: return
        val court = current.courts.find { it.id == courtId } ?: return
        val activeMatch = court.currentMatch ?: return

        val completedMatch = PickleballGameEngine.createCompletedMatch(
            courtId = courtId,
            teamA = activeMatch.teamA,
            teamB = activeMatch.teamB,
            scoreA = finalScoreA,
            scoreB = finalScoreB,
            targetScore = current.targetScore
        )

        viewModelScope.launch {
            repository.recordCompletedMatch(current.id, completedMatch)
        }

        val winnerTeam = completedMatch.winnerTeamId
        val teamAPlayerIds = listOf(completedMatch.teamA.player1.id, completedMatch.teamA.player2.id).toSet()
        val teamBPlayerIds = listOf(completedMatch.teamB.player1.id, completedMatch.teamB.player2.id).toSet()

        val updatedRoster = current.roster.map { p ->
            when {
                p.id in teamAPlayerIds -> {
                    val isWinner = (winnerTeam == TeamId.TEAM_A)
                    p.copy(
                        status = ParticipantStatus.AVAILABLE,
                        queuedTimestamp = System.currentTimeMillis(),
                        matchesPlayed = p.matchesPlayed + 1,
                        matchesWon = p.matchesWon + if (isWinner) 1 else 0,
                        totalPointsScored = p.totalPointsScored + finalScoreA,
                        totalPointsConceded = p.totalPointsConceded + finalScoreB,
                        consecutiveGamesOnCourt = if (isWinner) p.consecutiveGamesOnCourt else 0
                    )
                }
                p.id in teamBPlayerIds -> {
                    val isWinner = (winnerTeam == TeamId.TEAM_B)
                    p.copy(
                        status = ParticipantStatus.AVAILABLE,
                        queuedTimestamp = System.currentTimeMillis(),
                        matchesPlayed = p.matchesPlayed + 1,
                        matchesWon = p.matchesWon + if (isWinner) 1 else 0,
                        totalPointsScored = p.totalPointsScored + finalScoreB,
                        totalPointsConceded = p.totalPointsConceded + finalScoreA,
                        consecutiveGamesOnCourt = if (isWinner) p.consecutiveGamesOnCourt else 0
                    )
                }
                else -> p
            }
        }

        val updatedCourts = current.courts.map { c ->
            if (c.id == courtId) c.copy(status = CourtStatus.AVAILABLE, currentMatch = null) else c
        }

        val updatedCompletedMatches = current.completedMatches + completedMatch

        val interimSession = current.copy(
            courts = updatedCourts,
            roster = updatedRoster,
            completedMatches = updatedCompletedMatches
        )

        val newRec = RotationEngine.generateRecommendation(interimSession, courtId, completedMatch)
        val updatedRecs = if (newRec != null) {
            interimSession.activeRecommendations + (courtId to newRec)
        } else {
            interimSession.activeRecommendations - courtId
        }

        val updated = interimSession.copy(activeRecommendations = updatedRecs)
        _session.value = updated
        persistSession(updated)
        _currentScreen.value = AppScreen.SessionHub
    }

    fun adjustRecommendation(courtId: Int, adjustedRec: RotationRecommendation) {
        val current = _session.value ?: return
        val updatedRecs = current.activeRecommendations + (courtId to adjustedRec)
        _session.value = current.copy(activeRecommendations = updatedRecs)
    }

    fun swapRecommendationPartners(courtId: Int) {
        val current = _session.value ?: return
        val rec = current.activeRecommendations[courtId] ?: return

        val newTeamA = Team(TeamId.TEAM_A, rec.teamA.player1, rec.teamB.player2)
        val newTeamB = Team(TeamId.TEAM_B, rec.teamB.player1, rec.teamA.player2)

        val updatedRec = rec.copy(teamA = newTeamA, teamB = newTeamB)
        adjustRecommendation(courtId, updatedRec)
    }

    fun abandonMatch(courtId: Int) {
        val current = _session.value ?: return
        val court = current.courts.find { it.id == courtId } ?: return
        val activeMatch = court.currentMatch ?: return

        val matchIds = listOf(
            activeMatch.teamA.player1.id, activeMatch.teamA.player2.id,
            activeMatch.teamB.player1.id, activeMatch.teamB.player2.id
        ).toSet()

        val updatedRoster = current.roster.map { p ->
            if (p.id in matchIds) {
                p.copy(status = ParticipantStatus.AVAILABLE, queuedTimestamp = System.currentTimeMillis())
            } else p
        }

        val updatedCourts = current.courts.map { c ->
            if (c.id == courtId) c.copy(status = CourtStatus.AVAILABLE, currentMatch = null) else c
        }

        val updated = current.copy(courts = updatedCourts, roster = updatedRoster)
        _session.value = updated
        persistSession(updated)
        _currentScreen.value = AppScreen.SessionHub
        refreshRecommendations()
    }

    fun toggleCourtPause(courtId: Int) {
        val current = _session.value ?: return
        val court = current.courts.find { it.id == courtId } ?: return

        val newStatus = when (court.status) {
            CourtStatus.AVAILABLE -> CourtStatus.PAUSED
            CourtStatus.PAUSED -> CourtStatus.AVAILABLE
            CourtStatus.IN_PROGRESS -> court.status // do not pause active in-progress match directly
        }

        val updatedCourts = current.courts.map { c ->
            if (c.id == courtId) c.copy(status = newStatus) else c
        }

        val updatedRecs = current.activeRecommendations.toMutableMap()
        if (newStatus == CourtStatus.PAUSED) {
            updatedRecs.remove(courtId)
        }

        val updated = current.copy(courts = updatedCourts, activeRecommendations = updatedRecs)
        _session.value = updated
        persistSession(updated)
        if (newStatus == CourtStatus.AVAILABLE) {
            refreshRecommendations()
        }
    }

    private fun refreshRecommendations() {
        val current = _session.value ?: return
        val updatedRecs = current.activeRecommendations.toMutableMap()
        current.courts.filter { it.status == CourtStatus.AVAILABLE }.forEach { c ->
            val rec = RotationEngine.generateRecommendation(current, c.id, null)
            if (rec != null) {
                updatedRecs[c.id] = rec
            }
        }
        val updated = current.copy(activeRecommendations = updatedRecs)
        _session.value = updated
        persistSession(updated)
    }

    fun launchStandaloneScoreboard(firstServingTeam: TeamId = TeamId.TEAM_A) {
        val p1 = Player("s1", "Team A Player 1")
        val p2 = Player("s2", "Team A Player 2")
        val p3 = Player("s3", "Team B Player 1")
        val p4 = Player("s4", "Team B Player 2")

        val match = PickleballGameEngine.createMatch(
            courtId = 0,
            teamA = Team(TeamId.TEAM_A, p1, p2),
            teamB = Team(TeamId.TEAM_B, p3, p4),
            firstServingTeam = firstServingTeam
        )
        _standaloneMatch.value = match
        _currentScreen.value = AppScreen.StandaloneScoreboard(match)
    }

    fun recordStandaloneRally(winningTeam: TeamId) {
        val match = _standaloneMatch.value ?: return
        val updated = PickleballGameEngine.recordRally(match, winningTeam)
        _standaloneMatch.value = updated
        _currentScreen.value = AppScreen.StandaloneScoreboard(updated)
    }

    fun undoStandaloneRally() {
        val match = _standaloneMatch.value ?: return
        val reverted = PickleballGameEngine.undoLastRally(match)
        _standaloneMatch.value = reverted
        _currentScreen.value = AppScreen.StandaloneScoreboard(reverted)
    }
}
