package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CourtSide
import com.example.model.Match
import com.example.model.TeamId
import com.example.ui.components.TacticalPickleballCourtDiagram
import com.example.ui.theme.*
import com.example.ui.util.SensoryHaptics
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandaloneScoreboardScreen(
    match: Match,
    viewModel: SessionViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tokens = LocalPickItTokens.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Standalone scoreboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.SessionHub) },
                        modifier = Modifier.testTag("standalone_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = tokens.textPrimary
                        )
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
            // First-server selector — only before the first rally is recorded.
            // Re-creates the match so the chosen team serves first (0-0-2).
            if (match.rallyHistory.isEmpty()) {
                Surface(
                    color = tokens.surfaceElevated,
                    shape = RoundedCornerShape(tokens.radiusMd),
                    border = androidx.compose.foundation.BorderStroke(1.dp, tokens.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(
                            text = "FIRST SERVER",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val teamAServing = match.servingTeam == TeamId.TEAM_A
                            Button(
                                onClick = { viewModel.launchStandaloneScoreboard(TeamId.TEAM_A) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("first_server_team_a_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (teamAServing) tokens.teamA else tokens.surfaceInset,
                                    contentColor = if (teamAServing) tokens.onTeamA else tokens.teamA
                                ),
                                shape = RoundedCornerShape(tokens.radiusSm)
                            ) { Text("TEAM A", fontWeight = FontWeight.Black) }

                            Button(
                                onClick = { viewModel.launchStandaloneScoreboard(TeamId.TEAM_B) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("first_server_team_b_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!teamAServing) tokens.teamB else tokens.surfaceInset,
                                    contentColor = if (!teamAServing) tokens.onTeamB else tokens.teamB
                                ),
                                shape = RoundedCornerShape(tokens.radiusSm)
                            ) { Text("TEAM B", fontWeight = FontWeight.Black) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Secondary Information (3-number callout + server info)
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
                            text = "CALLOUT",
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
                            text = "${match.servingSide.name} COURT",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "SERVER",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary
                        )
                        Text(
                            text = "SERVER ${match.serverNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (match.servingTeam == TeamId.TEAM_A) tokens.teamA else tokens.teamB
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Tactical Court Diagram
            TacticalPickleballCourtDiagram(match = match)

            Spacer(modifier = Modifier.height(8.dp))

            // High-Contrast Primary Scoreboard Display
            Surface(
                color = tokens.surfaceInset,
                shape = RoundedCornerShape(tokens.radiusLg),
                border = androidx.compose.foundation.BorderStroke(2.dp, tokens.border),
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
                                text = "TEAM A",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.teamA
                            )
                        }

                        AnimatedContent(
                            targetState = match.scoreA,
                            transitionSpec = {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> -height } + fadeOut()
                                )
                            },
                            label = "StandaloneTeamAScore"
                        ) { targetScore ->
                            Text(
                                text = "$targetScore",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black,
                                fontSize = 68.sp,
                                color = tokens.textPrimary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(80.dp)
                            .background(tokens.border)
                    )

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
                                text = "TEAM B",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.teamB
                            )
                        }

                        AnimatedContent(
                            targetState = match.scoreB,
                            transitionSpec = {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> -height } + fadeOut()
                                )
                            },
                            label = "StandaloneTeamBScore"
                        ) { targetScore ->
                            Text(
                                text = "$targetScore",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black,
                                fontSize = 68.sp,
                                color = tokens.textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Declarative Rally Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.35f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val isServing = (match.servingTeam == TeamId.TEAM_A)
                        if (isServing) {
                            SensoryHaptics.performPointHaptic(context)
                        } else {
                            SensoryHaptics.performSideOutHaptic(context)
                        }
                        viewModel.recordStandaloneRally(TeamId.TEAM_A)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("standalone_team_a_won_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.teamA,
                        contentColor = tokens.onTeamA
                    ),
                    shape = RoundedCornerShape(tokens.radiusLg),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TEAM A WON RALLY",
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

                Button(
                    onClick = {
                        val isServing = (match.servingTeam == TeamId.TEAM_B)
                        if (isServing) {
                            SensoryHaptics.performPointHaptic(context)
                        } else {
                            SensoryHaptics.performSideOutHaptic(context)
                        }
                        viewModel.recordStandaloneRally(TeamId.TEAM_B)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("standalone_team_b_won_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.teamB,
                        contentColor = tokens.onTeamB
                    ),
                    shape = RoundedCornerShape(tokens.radiusLg),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TEAM B WON RALLY",
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

            // Bottom Controls (Undo)
            Button(
                onClick = {
                    SensoryHaptics.performUndoHaptic(context)
                    viewModel.undoStandaloneRally()
                },
                enabled = match.rallyHistory.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("standalone_undo_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.surfaceInset,
                    contentColor = tokens.textAccent
                ),
                shape = RoundedCornerShape(tokens.radiusSm)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Undo Rally (${match.rallyHistory.size})", fontWeight = FontWeight.Bold)
            }
        }
    }
}
