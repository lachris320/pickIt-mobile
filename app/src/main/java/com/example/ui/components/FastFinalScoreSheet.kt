package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Match
import com.example.model.TeamId
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastFinalScoreSheet(
    match: Match,
    onConfirm: (finalScoreA: Int, finalScoreB: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var winningTeam by remember { mutableStateOf(TeamId.TEAM_A) }
    var losingScore by remember { mutableStateOf(8) }
    var customWinnerScore by remember { mutableStateOf(11) }

    // Auto-compute winning score (if losing score is 10+, winner must be at least losingScore + 2)
    val actualWinnerScore = if (losingScore >= 10) losingScore + 2 else 11

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PickleballCardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("fast_final_score_sheet")
        ) {
            Text(
                text = "RECORD FINAL SCORE: COURT ${match.courtId}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WhiteHighContrast
            )
            Text(
                text = "Tap winning team and losing team's score to rotate in 2 taps.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 1: Select Winner
            Text(
                text = "STEP 1: SELECT WINNING TEAM",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = PickleballLime
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { winningTeam = TeamId.TEAM_A },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (winningTeam == TeamId.TEAM_A) TeamAColor.copy(alpha = 0.2f) else Color.Transparent
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (winningTeam == TeamId.TEAM_A) TeamAColor else Color(0xFF37474F)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("select_team_a_winner")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TEAM A",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TeamAColor
                        )
                        Text(
                            text = match.teamA.playerNames(),
                            style = MaterialTheme.typography.bodySmall,
                            color = WhiteHighContrast,
                            maxLines = 1
                        )
                    }
                }

                OutlinedButton(
                    onClick = { winningTeam = TeamId.TEAM_B },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (winningTeam == TeamId.TEAM_B) TeamBColor.copy(alpha = 0.2f) else Color.Transparent
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (winningTeam == TeamId.TEAM_B) TeamBColor else Color(0xFF37474F)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("select_team_b_winner")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TEAM B",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TeamBColor
                        )
                        Text(
                            text = match.teamB.playerNames(),
                            style = MaterialTheme.typography.bodySmall,
                            color = WhiteHighContrast,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: Select Losing Score
            Text(
                text = "STEP 2: SELECT LOSING TEAM'S SCORE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = PickleballLime
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Grid of quick numbers: 0 to 10 and 12 (Deuce)
            val scoreChoices = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                scoreChoices.take(6).forEach { score ->
                    ScorePillButton(
                        score = score,
                        isSelected = (losingScore == score),
                        onClick = { losingScore = score },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                scoreChoices.drop(6).forEach { score ->
                    ScorePillButton(
                        score = score,
                        isSelected = (losingScore == score),
                        onClick = { losingScore = score },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Result Preview
            val (finalA, finalB) = if (winningTeam == TeamId.TEAM_A) {
                actualWinnerScore to losingScore
            } else {
                losingScore to actualWinnerScore
            }

            Surface(
                color = Color(0xFF0F1713),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "PREVIEW RESULT:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = if (winningTeam == TeamId.TEAM_A) {
                                "${match.teamA.playerNames()} won $finalA - $finalB"
                            } else {
                                "${match.teamB.playerNames()} won $finalB - $finalA"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = WhiteHighContrast
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm & Rotate button
            Button(
                onClick = { onConfirm(finalA, finalB) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("confirm_final_score_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PickleballLime,
                    contentColor = Color(0xFF1B3700)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CONFIRM $finalA - $finalB & ROTATE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ScorePillButton(
    score: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) PickleballLime else Color(0xFF233027),
            contentColor = if (isSelected) Color(0xFF1B3700) else WhiteHighContrast
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(4.dp),
        modifier = modifier
            .height(44.dp)
            .testTag("score_pill_$score")
    ) {
        Text(
            text = if (score == 12) "12+" else "$score",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
