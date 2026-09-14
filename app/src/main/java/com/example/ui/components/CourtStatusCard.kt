package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Court
import com.example.model.CourtStatus
import com.example.ui.theme.*

@Composable
fun CourtStatusCard(
    court: Court,
    onOpenScoreboard: () -> Unit,
    onEnterFinalScore: () -> Unit,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPickItTokens.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, tokens.border, RoundedCornerShape(tokens.radiusLg))
            .testTag("court_card_${court.id}"),
        shape = RoundedCornerShape(tokens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = tokens.surfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Court Name + Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = court.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textPrimary
                )

                CourtStatusIndicator(status = court.status, tokens = tokens)
            }

            Spacer(modifier = Modifier.height(10.dp))

            val match = court.currentMatch
            if (match != null && court.status == CourtStatus.IN_PROGRESS) {
                // Match Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = match.teamA.playerNames(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = tokens.teamA,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "vs ${match.teamB.playerNames()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.teamB,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Score Big Badge
                    Surface(
                        color = tokens.surfaceInset,
                        shape = RoundedCornerShape(tokens.radiusSm),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "${match.scoreA} - ${match.scoreB}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = tokens.textAccent,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions: Score Live vs Final Score
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenScoreboard,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_scoreboard_button_${court.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.statusLive,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(tokens.radiusSm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsTennis,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live score", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onEnterFinalScore,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("enter_final_score_button_${court.id}"),
                        shape = RoundedCornerShape(tokens.radiusSm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Final score")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (court.status == CourtStatus.PAUSED) "Court paused. Excluded from rotation router." else "Court is clear and ready for rotation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (court.status == CourtStatus.PAUSED) tokens.statusPaused else tokens.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onTogglePause,
                        shape = RoundedCornerShape(tokens.radiusSm),
                        modifier = Modifier.testTag("toggle_pause_court_${court.id}")
                    ) {
                        Icon(
                            imageVector = if (court.status == CourtStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (court.status == CourtStatus.PAUSED) "Open Court" else "Pause Court")
                    }
                }
            }
        }
    }
}

/**
 * Status indicator with a distinct shape per state so status never relies on color alone:
 * AVAILABLE = outlined ring, IN_PROGRESS = filled dot, PAUSED = pause glyph.
 */
@Composable
private fun CourtStatusIndicator(status: CourtStatus, tokens: PickItTokens) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            CourtStatus.AVAILABLE -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .border(1.5.dp, tokens.statusOpen, androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.statusOpen
                )
            }
            CourtStatus.IN_PROGRESS -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(tokens.statusLive, androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Live",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.statusLive
                )
            }
            CourtStatus.PAUSED -> {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = null,
                    tint = tokens.statusPaused,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Paused",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.statusPaused
                )
            }
        }
    }
}
