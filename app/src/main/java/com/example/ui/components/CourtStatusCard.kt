package com.example.ui.components

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
    val borderColor = when (court.status) {
        CourtStatus.AVAILABLE -> CourtAvailableGreen
        CourtStatus.IN_PROGRESS -> CourtActiveBlue
        CourtStatus.PAUSED -> CourtPausedGray
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .testTag("court_card_${court.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PickleballCardSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Court Name + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = court.name.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WhiteHighContrast
                )

                Surface(
                    color = borderColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                ) {
                    Text(
                        text = when (court.status) {
                            CourtStatus.AVAILABLE -> "OPEN / AVAILABLE"
                            CourtStatus.IN_PROGRESS -> "MATCH LIVE (${court.currentMatch?.calloutString() ?: "0-0"})"
                            CourtStatus.PAUSED -> "PAUSED"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = borderColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
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
                            color = TeamAColor
                        )
                        Text(
                            text = "vs ${match.teamB.playerNames()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TeamBColor
                        )
                    }

                    // Score Big Badge
                    Surface(
                        color = Color(0xFF0F1713),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "${match.scoreA} - ${match.scoreB}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = PickleballLime,
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
                            .height(44.dp)
                            .testTag("open_scoreboard_button_${court.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CourtActiveBlue,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsTennis,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live Score", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onEnterFinalScore,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("enter_final_score_button_${court.id}"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Final Score")
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
                        color = if (court.status == CourtStatus.PAUSED) CourtPausedGray else TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onTogglePause,
                        shape = RoundedCornerShape(8.dp),
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
