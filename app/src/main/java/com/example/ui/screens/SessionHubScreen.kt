package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CourtStatus
import com.example.model.Match
import com.example.model.ParticipantStatus
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHubScreen(
    viewModel: SessionViewModel,
    modifier: Modifier = Modifier
) {
    val session by viewModel.session.collectAsState()

    var showQueueSheet by remember { mutableStateOf(false) }
    var courtToRecordScore by remember { mutableStateOf<Match?>(null) }

    val activeSession = session
    if (activeSession == null) {
        Scaffold(containerColor = LocalPickItTokens.current.canvas) { pad ->
            Column(
                modifier = Modifier.fillMaxSize().padding(pad).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "No active session",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalPickItTokens.current.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Start a session to line up courts and keep score.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalPickItTokens.current.textSecondary,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.navigateTo(AppScreen.Setup) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalPickItTokens.current.accent,
                        contentColor = LocalPickItTokens.current.onAccent,
                    ),
                    modifier = Modifier.height(56.dp).testTag("start_session_button"),
                ) { Text("Start a session", fontWeight = FontWeight.Bold) }
            }
        }
        return
    }

    val availableQueue = activeSession.roster.filter { it.status == ParticipantStatus.AVAILABLE }
    val activeMatchesCount = activeSession.courts.count { it.status == CourtStatus.IN_PROGRESS }
    val recommendations = activeSession.activeRecommendations
    val tokens = LocalPickItTokens.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeSession.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "Policy: ${activeSession.rotationPolicy.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textAccent
                        )
                    }
                },
                actions = {
                    // Digital Paddle Queue trigger button with count badge
                    FilledTonalButton(
                        onClick = { showQueueSheet = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = tokens.surfaceInset,
                            contentColor = tokens.textAccent
                        ),
                        shape = RoundedCornerShape(tokens.radiusSm),
                        modifier = Modifier.testTag("open_queue_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "Open Queue",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Queue (${availableQueue.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.Setup) },
                        modifier = Modifier.testTag("session_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Settings",
                            tint = tokens.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tokens.canvas
                )
            )
        },
        containerColor = tokens.canvas
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("session_hub_scrollable"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: Attention Required / Active Rotation Recommendations
            if (recommendations.isNotEmpty()) {
                item {
                    Text(
                        text = "ROTATION RECOMMENDATIONS READY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.attention,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                val sortedRecs = recommendations.values.sortedBy { it.courtId }
                itemsIndexed(sortedRecs) { index, rec ->
                    RecommendationCard(
                        recommendation = rec,
                        isPrimary = (index == 0),
                        onCallAndStart = { viewModel.confirmRecommendation(rec.courtId) },
                        onSwapPartners = { viewModel.swapRecommendationPartners(rec.courtId) },
                        onRestPlayer = { playerId -> viewModel.togglePlayerRest(playerId) }
                    )
                }
            }

            // Section 2: On-Deck Horizon (Next 4 Players)
            item {
                val onDeck = availableQueue.take(4)
                OnDeckHorizonBar(onDeckPlayers = onDeck)
            }

            // Section 3: Court Grid / Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COURTS OVERVIEW (${activeMatchesCount}/${activeSession.courts.size} IN PLAY)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textSecondary
                    )

                    Text(
                        text = "Tap Live or Final to record",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
                    )
                }
            }

            items(activeSession.courts) { court ->
                CourtStatusCard(
                    court = court,
                    onOpenScoreboard = {
                        if (court.currentMatch != null) {
                            viewModel.navigateTo(AppScreen.LiveScoreboard(court.id))
                        }
                    },
                    onEnterFinalScore = {
                        courtToRecordScore = court.currentMatch
                    },
                    onTogglePause = {
                        viewModel.toggleCourtPause(court.id)
                    }
                )
            }

            // Section 4: Standalone / Quick Scorekeeper Affordance
            item {
                Surface(
                    color = tokens.surfaceInset,
                    shape = RoundedCornerShape(tokens.radiusMd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Standalone Scorekeeper",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = tokens.textPrimary
                            )
                            Text(
                                text = "Keep score for an ad-hoc pick-up game",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.launchStandaloneScoreboard() },
                            shape = RoundedCornerShape(tokens.radiusSm),
                            modifier = Modifier.testTag("launch_standalone_scoreboard_button")
                        ) {
                            Text("Launch")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Modal Sheet 1: Digital Paddle Queue & Roster
    if (showQueueSheet) {
        QueueRosterSheet(
            roster = activeSession.roster,
            onAddPlayer = { viewModel.addPlayerToRoster(it) },
            onToggleRest = { viewModel.togglePlayerRest(it) },
            onCheckOut = { viewModel.checkOutPlayer(it) },
            onMovePlayer = { from, to -> viewModel.moveQueuePlayer(from, to) },
            onDismiss = { showQueueSheet = false }
        )
    }

    // Modal Sheet 2: Fast 2-Tap Final Score Entry
    courtToRecordScore?.let { match ->
        FastFinalScoreSheet(
            match = match,
            onConfirm = { finalA, finalB ->
                viewModel.completeMatch(match.courtId, finalA, finalB)
                courtToRecordScore = null
            },
            onDismiss = { courtToRecordScore = null }
        )
    }
}
