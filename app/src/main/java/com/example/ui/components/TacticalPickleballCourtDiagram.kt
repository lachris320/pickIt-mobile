package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CourtSide
import com.example.model.Match
import com.example.model.TeamId
import com.example.ui.theme.*

/**
 * Tactical mini visual diagram of a regulation pickleball court.
 * Visualizes the Non-Volley Zone (Kitchen) in the center and highlights
 * the exact serving box (Right vs Left) and serving team side in high contrast.
 */
@Composable
fun TacticalPickleballCourtDiagram(
    match: Match,
    modifier: Modifier = Modifier
) {
    val kitchenColor = Color(0xFF1E3326)
    val courtSurfaceColor = Color(0xFF132219)
    val courtLineColor = Color(0xFF43604E)
    val servingBoxHighlightColor = PickleballLime.copy(alpha = 0.28f)
    val activeBorderColor = PickleballLime

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(courtSurfaceColor, RoundedCornerShape(8.dp))
            .border(1.5.dp, courtLineColor, RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        // Court canvas drawing lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val kitchenLeft = w * 0.38f
            val kitchenRight = w * 0.62f
            val netX = w * 0.5f

            // 1. Kitchen Zone (Non-Volley Zone) in the middle
            drawRect(
                color = kitchenColor,
                topLeft = Offset(kitchenLeft, 0f),
                size = androidx.compose.ui.geometry.Size(kitchenRight - kitchenLeft, h)
            )

            // 2. Net line (dashed thick line)
            drawLine(
                color = WhiteHighContrast,
                start = Offset(netX, 0f),
                end = Offset(netX, h),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )

            // 3. Kitchen boundary lines (7ft kitchen line)
            drawLine(
                color = courtLineColor,
                start = Offset(kitchenLeft, 0f),
                end = Offset(kitchenLeft, h),
                strokeWidth = 2f
            )
            drawLine(
                color = courtLineColor,
                start = Offset(kitchenRight, 0f),
                end = Offset(kitchenRight, h),
                strokeWidth = 2f
            )

            // 4. Center service lines dividing Right and Left service courts
            // Team A side (Left half of diagram: from 0 to kitchenLeft)
            drawLine(
                color = courtLineColor,
                start = Offset(0f, h * 0.5f),
                end = Offset(kitchenLeft, h * 0.5f),
                strokeWidth = 2f
            )

            // Team B side (Right half of diagram: from kitchenRight to w)
            drawLine(
                color = courtLineColor,
                start = Offset(kitchenRight, h * 0.5f),
                end = Offset(w, h * 0.5f),
                strokeWidth = 2f
            )

            // 5. Highlight the exact active serving quadrant
            val isTeamAServing = (match.servingTeam == TeamId.TEAM_A)
            val isRightCourt = (match.servingSide == CourtSide.RIGHT)

            val highlightTopLeft: Offset
            val highlightSize: androidx.compose.ui.geometry.Size

            if (isTeamAServing) {
                // Team A is on the left side. Right service court is bottom half (from player's perspective facing net)
                val yTop = if (isRightCourt) h * 0.5f else 0f
                highlightTopLeft = Offset(0f, yTop)
                highlightSize = androidx.compose.ui.geometry.Size(kitchenLeft, h * 0.5f)
            } else {
                // Team B is on the right side. Right service court is top half (facing net towards left)
                val yTop = if (isRightCourt) 0f else h * 0.5f
                highlightTopLeft = Offset(kitchenRight, yTop)
                highlightSize = androidx.compose.ui.geometry.Size(w - kitchenRight, h * 0.5f)
            }

            drawRect(
                color = servingBoxHighlightColor,
                topLeft = highlightTopLeft,
                size = highlightSize
            )
        }

        // Overlay labels for instant glanceability
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Team A indicator
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "TEAM A",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TeamAColor
                )
                if (match.servingTeam == TeamId.TEAM_A) {
                    Text(
                        text = "● SERVING",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = PickleballLime
                    )
                }
            }

            // Non-Volley Zone Label
            Text(
                text = "KITCHEN (NVZ)",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted.copy(alpha = 0.6f)
            )

            // Team B indicator
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "TEAM B",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TeamBColor
                )
                if (match.servingTeam == TeamId.TEAM_B) {
                    Text(
                        text = "SERVING ●",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = PickleballLime
                    )
                }
            }
        }
    }
}
