package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.FastFinalScoreSheet
import com.example.ui.components.TacticalPickleballCourtDiagram
import com.example.ui.theme.*
import com.example.ui.util.SensoryHaptics
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScoreboardScreen(
    courtId: Int,
    viewModel: SessionViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()
    val court = session?.courts?.find { it.id == courtId }
    val match = court?.currentMatch

    var showFinalScoreSheet by remember { mutableStateOf(false) }
    var showAbandonDialog by remember { mutableStateOf(false) }

    if (match == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CanvasDark),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No active match on Court $courtId", color = WhiteHighContrast)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { viewModel.navigateTo(AppScreen.SessionHub) }) {
                    Text("Back to Hub")
                }
            }
        }
        return
    }

    // Sensory notification on match completion
    LaunchedEffect(match.isCompleted) {
        if (match.isCompleted) {
            SensoryHaptics.performMatchWonHaptic(context)
        }
    }

    // Check if either team is at Match Point (e.g. score >= 10 and ahead by 1+)
    val isTeamAMatchPoint = match.scoreA >= (match.targetScore - 1) && (match.scoreA - match.scoreB) >= 1 && match.servingTeam == TeamId.TEAM_A
    val isTeamBMatchPoint = match.scoreB >= (match.targetScore - 1) && (match.scoreB - match.scoreA) >= 1 && match.servingTeam == TeamId.TEAM_B

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "COURT ${match.courtId}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = WhiteHighContrast
                            )
                            if (isTeamAMatchPoint || isTeamBMatchPoint) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFFD32F2F),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "MATCH POINT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Target: ${match.targetScore} (Win by 2)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.SessionHub) },
                        modifier = Modifier.testTag("back_to_hub_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = WhiteHighContrast
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showFinalScoreSheet = true },
                        modifier = Modifier.testTag("direct_final_score_button")
                    ) {
                        Text("Finalize", color = PickleballLime, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasDark)
            )
        },
        containerColor = CanvasDark
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Section 1: Secondary Pickleball Context Bar
            Surface(
                color = PickleballCardSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PickleballCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "OFFICIAL CALLOUT",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                        Text(
                            text = match.calloutString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = PickleballLime
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SERVING SIDE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                        Text(
                            text = "${match.servingSide.name} COURT",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = WhiteHighContrast
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "SERVER (SERVER ${match.serverNumber})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                        Text(
                            text = match.currentServer.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (match.servingTeam == TeamId.TEAM_A) TeamAColor else TeamBColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Section 2: Tactical Mini Court Diagram
            TacticalPickleballCourtDiagram(match = match)

            Spacer(modifier = Modifier.height(8.dp))

            // Section 3: High-Visibility Primary Scoreboard Display
            Surface(
                color = Color(0xFF141D17),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    if (match.isCompleted) PickleballLime else PickleballCardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Team A Column
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (match.servingTeam == TeamId.TEAM_A) {
                                Surface(
                                    color = PickleballLime,
                                    shape = CircleShape,
                                    modifier = Modifier.size(10.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "TEAM A",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TeamAColor
                            )
                        }

                        // Animated Score Number with pop transition
                        AnimatedContent(
                            targetState = match.scoreA,
                            transitionSpec = {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> -height } + fadeOut()
                                )
                            },
                            label = "TeamAScore"
                        ) { targetScore ->
                            Text(
                                text = "$targetScore",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black,
                                fontSize = 68.sp,
                                color = WhiteHighContrast,
                                modifier = Modifier.testTag("team_a_score_display")
                            )
                        }

                        Text(
                            text = match.teamA.playerNames(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }

                    // Net Divider
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(80.dp)
                            .background(PickleballCardBorder)
                    )

                    // Team B Column
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (match.servingTeam == TeamId.TEAM_B) {
                                Surface(
                                    color = PickleballLime,
                                    shape = CircleShape,
                                    modifier = Modifier.size(10.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "TEAM B",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TeamBColor
                            )
                        }

                        // Animated Score Number with pop transition
                        AnimatedContent(
                            targetState = match.scoreB,
                            transitionSpec = {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> -height } + fadeOut()
                                )
                            },
                            label = "TeamBScore"
                        ) { targetScore ->
                            Text(
                                text = "$targetScore",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black,
                                fontSize = 68.sp,
                                color = WhiteHighContrast,
                                modifier = Modifier.testTag("team_b_score_display")
                            )
                        }

                        Text(
                            text = match.teamB.playerNames(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Last Rally Flash Feed (Instant Feedback)
            val lastRally = match.rallyHistory.lastOrNull()
            if (lastRally != null) {
                Surface(
                    color = Color(0xFF16211A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Rally #${lastRally.rallyIndex}: ${lastRally.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (lastRally.isSideOut) CourtAttentionAmber else PickleballLime,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (lastRally.isSideOut) {
                            Surface(
                                color = CourtAttentionAmber.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SIDE OUT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CourtAttentionAmber,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 4: Glare-proof, High-Contrast Declarative Rally Buttons (40%+ Screen Height)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.35f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: Team A Won Rally
                Button(
                    onClick = {
                        val isServing = (match.servingTeam == TeamId.TEAM_A)
                        if (isServing) {
                            SensoryHaptics.performPointHaptic(context)
                        } else {
                            SensoryHaptics.performSideOutHaptic(context)
                        }
                        viewModel.recordRallyInMatch(courtId, TeamId.TEAM_A)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("team_a_won_rally_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TeamAColor,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${match.teamA.playerNames()} WON RALLY",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (match.servingTeam == TeamId.TEAM_A) "+1 Point (Serving)" else "Side-out / Fault",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00363A)
                        )
                    }
                }

                // Button 2: Team B Won Rally
                Button(
                    onClick = {
                        val isServing = (match.servingTeam == TeamId.TEAM_B)
                        if (isServing) {
                            SensoryHaptics.performPointHaptic(context)
                        } else {
                            SensoryHaptics.performSideOutHaptic(context)
                        }
                        viewModel.recordRallyInMatch(courtId, TeamId.TEAM_B)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("team_b_won_rally_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TeamBColor,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${match.teamB.playerNames()} WON RALLY",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (match.servingTeam == TeamId.TEAM_B) "+1 Point (Serving)" else "Side-out / Fault",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4E1400)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 5: Bottom Controls (Undo + Abandon Match)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        SensoryHaptics.performUndoHaptic(context)
                        viewModel.undoRallyInMatch(courtId)
                    },
                    enabled = match.rallyHistory.isNotEmpty(),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("undo_rally_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Undo Rally (${match.rallyHistory.size})")
                }

                TextButton(
                    onClick = { showAbandonDialog = true },
                    modifier = Modifier.testTag("abandon_match_button")
                ) {
                    Text("Abandon Match", color = Color(0xFFEF5350))
                }
            }
        }
    }

    // Auto-Trigger match completion dialog if game was won legally
    if (match.isCompleted) {
        AlertDialog(
            onDismissRequest = { /* Require action */ },
            title = {
                Text("MATCH COMPLETED!", fontWeight = FontWeight.Bold, color = PickleballLime)
            },
            text = {
                val winnerNames = if (match.winnerTeamId == TeamId.TEAM_A) match.teamA.playerNames() else match.teamB.playerNames()
                Text(
                    text = "$winnerNames won with final score ${match.scoreA} - ${match.scoreB}.\nProceed to rotate the court?",
                    color = WhiteHighContrast
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.completeMatch(match.courtId, match.scoreA, match.scoreB)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PickleballLime, contentColor = Color(0xFF1B3700)),
                    modifier = Modifier.testTag("confirm_match_completion_button")
                ) {
                    Text("Generate Rotation & Return to Hub", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    SensoryHaptics.performUndoHaptic(context)
                    viewModel.undoRallyInMatch(courtId)
                }) {
                    Text("Undo Last Rally")
                }
            },
            containerColor = PickleballCardSurface
        )
    }

    // Abandon-match confirmation (destructive action requires confirmation)
    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { showAbandonDialog = false },
            title = {
                Text("Abandon match?", fontWeight = FontWeight.Bold, color = LocalPickItTokens.current.textPrimary)
            },
            text = {
                Text(
                    "This ends the match with no result and returns both teams to the queue.",
                    color = LocalPickItTokens.current.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAbandonDialog = false
                        viewModel.abandonMatch(courtId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalPickItTokens.current.textDanger,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_abandon_button")
                ) {
                    Text("Abandon", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAbandonDialog = false },
                    modifier = Modifier.testTag("dismiss_abandon_button")
                ) {
                    Text("Keep playing")
                }
            },
            containerColor = LocalPickItTokens.current.surfaceElevated
        )
    }

    // Fast Final Score override sheet
    if (showFinalScoreSheet) {
        FastFinalScoreSheet(
            match = match,
            onConfirm = { finalA, finalB ->
                viewModel.completeMatch(match.courtId, finalA, finalB)
                showFinalScoreSheet = false
            },
            onDismiss = { showFinalScoreSheet = false }
        )
    }
}
