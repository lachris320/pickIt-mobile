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
import androidx.compose.ui.text.style.TextOverflow
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
    val tokens = LocalPickItTokens.current

    var showFinalScoreSheet by remember { mutableStateOf(false) }
    var showAbandonDialog by remember { mutableStateOf(false) }

    if (match == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.canvas),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No active match on Court $courtId", color = tokens.textPrimary)
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
                                text = "Court ${match.courtId}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.textPrimary
                            )
                            if (isTeamAMatchPoint || isTeamBMatchPoint) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = tokens.textDanger,
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
                            color = tokens.textSecondary
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
                            tint = tokens.textPrimary
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showFinalScoreSheet = true },
                        modifier = Modifier.testTag("direct_final_score_button")
                    ) {
                        Text("Finalize", color = tokens.textAccent, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.canvas)
            )
        },
        containerColor = tokens.canvas
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
                color = tokens.surfaceElevated,
                shape = RoundedCornerShape(tokens.radiusMd),
                border = androidx.compose.foundation.BorderStroke(1.dp, tokens.border),
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
                            color = tokens.textSecondary
                        )
                        Text(
                            text = match.calloutString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = tokens.textAccent
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SERVING SIDE",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary
                        )
                        Text(
                            text = "${match.servingSide.name.lowercase().replaceFirstChar { it.uppercase() }} Court",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "SERVER (SERVER ${match.serverNumber})",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary
                        )
                        Text(
                            text = match.currentServer.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (match.servingTeam == TeamId.TEAM_A) tokens.teamA else tokens.teamB
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
                color = tokens.surfaceInset,
                shape = RoundedCornerShape(tokens.radiusLg),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    if (match.isCompleted) tokens.accent else tokens.border
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
                                    color = tokens.accent,
                                    shape = CircleShape,
                                    modifier = Modifier.size(10.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "Team A",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.teamA
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
                                color = tokens.textPrimary,
                                modifier = Modifier.testTag("team_a_score_display")
                            )
                        }

                        Text(
                            text = match.teamA.playerNames(),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Net Divider
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(80.dp)
                            .background(tokens.border)
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
                                    color = tokens.accent,
                                    shape = CircleShape,
                                    modifier = Modifier.size(10.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "Team B",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.teamB
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
                                color = tokens.textPrimary,
                                modifier = Modifier.testTag("team_b_score_display")
                            )
                        }

                        Text(
                            text = match.teamB.playerNames(),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Last Rally Flash Feed (Instant Feedback)
            val lastRally = match.rallyHistory.lastOrNull()
            if (lastRally != null) {
                Surface(
                    color = tokens.surfaceInset,
                    shape = RoundedCornerShape(tokens.radiusSm),
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
                            color = if (lastRally.isSideOut) tokens.attention else tokens.textAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (lastRally.isSideOut) {
                            Surface(
                                color = tokens.attention.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SIDE OUT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.attention,
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
                        containerColor = tokens.teamA,
                        contentColor = tokens.onTeamA
                    ),
                    shape = RoundedCornerShape(tokens.radiusLg),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${match.teamA.playerNames()} won rally",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (match.servingTeam == TeamId.TEAM_A) "+1 Point (Serving)" else "Side-out / Fault",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.onTeamA
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
                        containerColor = tokens.teamB,
                        contentColor = tokens.onTeamB
                    ),
                    shape = RoundedCornerShape(tokens.radiusLg),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${match.teamB.playerNames()} won rally",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (match.servingTeam == TeamId.TEAM_B) "+1 Point (Serving)" else "Side-out / Fault",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.onTeamB
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
                    shape = RoundedCornerShape(tokens.radiusSm)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Undo Rally (${match.rallyHistory.size})")
                }

                TextButton(
                    onClick = { showAbandonDialog = true },
                    modifier = Modifier.testTag("abandon_match_button")
                ) {
                    Text("Abandon Match", color = tokens.textDanger)
                }
            }
        }
    }

    // Auto-Trigger match completion dialog if game was won legally
    if (match.isCompleted) {
        AlertDialog(
            onDismissRequest = { /* Require action */ },
            title = {
                Text("Match completed!", fontWeight = FontWeight.Bold, color = tokens.textAccent)
            },
            text = {
                val winnerNames = if (match.winnerTeamId == TeamId.TEAM_A) match.teamA.playerNames() else match.teamB.playerNames()
                Text(
                    text = "$winnerNames won with final score ${match.scoreA} - ${match.scoreB}.\nProceed to rotate the court?",
                    color = tokens.textPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.completeMatch(match.courtId, match.scoreA, match.scoreB)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accent, contentColor = tokens.onAccent),
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
            containerColor = tokens.surfaceElevated
        )
    }

    // Abandon-match confirmation (destructive action requires confirmation)
    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { showAbandonDialog = false },
            title = {
                Text("Abandon match?", fontWeight = FontWeight.Bold, color = tokens.textPrimary)
            },
            text = {
                Text(
                    "This ends the match with no result and returns both teams to the queue.",
                    color = tokens.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAbandonDialog = false
                        viewModel.abandonMatch(courtId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.textDanger,
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
            containerColor = tokens.surfaceElevated
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
