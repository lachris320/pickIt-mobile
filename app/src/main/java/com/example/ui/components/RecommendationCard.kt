package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Player
import com.example.model.RotationRecommendation
import com.example.ui.theme.*

@Composable
private fun RestPlayerButton(
    player: Player,
    tint: androidx.compose.ui.graphics.Color,
    onRestPlayer: (String) -> Unit,
) {
    IconButton(
        onClick = { onRestPlayer(player.id) },
        modifier = Modifier.size(48.dp).testTag("rest_rec_player_${player.id}")
    ) {
        Icon(
            imageVector = Icons.Default.Bedtime,
            contentDescription = "Rest ${player.name}",
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun RecommendationCard(
    recommendation: RotationRecommendation,
    onCallAndStart: () -> Unit,
    onSwapPartners: () -> Unit,
    onRestPlayer: (String) -> Unit,
    isPrimary: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expandedWhy by remember { mutableStateOf(false) }
    val tokens = LocalPickItTokens.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                2.dp,
                if (isPrimary) tokens.accent else tokens.border,
                RoundedCornerShape(tokens.radiusLg)
            )
            .testTag("recommendation_card_${recommendation.courtId}"),
        shape = RoundedCornerShape(tokens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = tokens.surfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header — one amber attention state (dot + label), reserved per von-Restorff
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(tokens.attention)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Court ${recommendation.courtId} ready",
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.attention,
                        fontWeight = FontWeight.Bold
                    )
                }

                AssistChip(
                    onClick = { expandedWhy = !expandedWhy },
                    label = { Text(if (expandedWhy) "Hide why" else "Why?") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (expandedWhy) Icons.Default.ExpandLess else Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Explain recommendation",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Matchup Display (Glanceable)
            Surface(
                color = tokens.surfaceInset,
                shape = RoundedCornerShape(tokens.radiusMd),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TEAM A",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.teamA,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = recommendation.teamA.player1.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.textPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                RestPlayerButton(
                                    player = recommendation.teamA.player1,
                                    tint = tokens.textSecondary,
                                    onRestPlayer = onRestPlayer
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = recommendation.teamA.player2.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.textPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                RestPlayerButton(
                                    player = recommendation.teamA.player2,
                                    tint = tokens.textSecondary,
                                    onRestPlayer = onRestPlayer
                                )
                            }
                        }

                        Text(
                            text = "vs",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.textSecondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "TEAM B",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.teamB,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RestPlayerButton(
                                    player = recommendation.teamB.player1,
                                    tint = tokens.textSecondary,
                                    onRestPlayer = onRestPlayer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = recommendation.teamB.player1.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.textPrimary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RestPlayerButton(
                                    player = recommendation.teamB.player2,
                                    tint = tokens.textSecondary,
                                    onRestPlayer = onRestPlayer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = recommendation.teamB.player2.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.textPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Reason: ${recommendation.primaryReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textAccent
                    )

                    if (recommendation.warningMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = recommendation.warningMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.attention
                        )
                    }
                }
            }

            // Expanded "Why?" section
            AnimatedVisibility(visible = expandedWhy) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(tokens.surfaceInset, RoundedCornerShape(tokens.radiusSm))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Explanation & audit",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textAccent
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    recommendation.detailedReason.forEach { reason ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(text = "• ", color = tokens.textSecondary)
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary action — exactly one lime CTA per Hub (the next ready court).
            // The primary card fills lime and carries the primary_call_button tag; any
            // additional ready courts render a neutral "Call" so a single standout survives.
            if (isPrimary) {
                Box(modifier = Modifier.testTag("primary_call_button")) {
                    Button(
                        onClick = onCallAndStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("call_and_start_button_${recommendation.courtId}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.accent,
                            contentColor = tokens.onAccent
                        ),
                        shape = RoundedCornerShape(tokens.radiusMd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Call & start court ${recommendation.courtId}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onCallAndStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("call_and_start_button_${recommendation.courtId}"),
                    shape = RoundedCornerShape(tokens.radiusMd)
                ) {
                    Text(
                        text = "Call court ${recommendation.courtId}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Quick Actions: Swap Partners
            OutlinedButton(
                onClick = onSwapPartners,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("swap_partners_button_${recommendation.courtId}"),
                shape = RoundedCornerShape(tokens.radiusSm)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Swap partners across net")
            }
        }
    }
}
