package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Court
import com.example.model.RotationRecommendation
import com.example.model.Team
import com.example.ui.screens.CourtCallState
import com.example.ui.theme.LocalPickItTokens

/** Longest single player name that renders before it is capped with an ellipsis. */
private const val NAME_CAP = 14

private fun capName(name: String): String =
    if (name.length <= NAME_CAP) name else name.take(NAME_CAP - 1) + "…"

/**
 * A team's matchup label. Each player's name is capped INDIVIDUALLY so both survive — capping the
 * joined "A & B" string would drop the second player entirely on long names.
 */
private fun cappedMatchup(team: Team): String =
    "${capName(team.player1.name)} & ${capName(team.player2.name)}"

private fun stateTag(state: CourtCallState) = when (state) {
    CourtCallState.UP_NOW -> "court_call_tile_up_now"
    CourtCallState.READY -> "court_call_tile_ready"
    CourtCallState.LIVE -> "court_call_tile_live"
    CourtCallState.FINAL -> "court_call_tile_final"
    CourtCallState.OPEN -> "court_call_tile_open"
    CourtCallState.PAUSED -> "court_call_tile_paused"
}

private fun stateLabel(state: CourtCallState) = when (state) {
    CourtCallState.UP_NOW -> "UP NOW"
    CourtCallState.READY -> "READY"
    CourtCallState.LIVE -> "LIVE"
    CourtCallState.FINAL -> "FINAL"
    CourtCallState.OPEN -> "OPEN"
    CourtCallState.PAUSED -> "PAUSED"
}

@Composable
fun CourtCallTile(
    court: Court,
    state: CourtCallState,
    recommendation: RotationRecommendation?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPickItTokens.current
    val shape = RoundedCornerShape(tokens.radiusLg)

    val fill: Color = when (state) {
        CourtCallState.UP_NOW -> tokens.accent
        CourtCallState.FINAL -> tokens.surfaceInset
        else -> tokens.surface
    }
    val borderColor: Color = when (state) {
        CourtCallState.UP_NOW -> tokens.accent
        CourtCallState.READY -> tokens.accent
        CourtCallState.LIVE -> tokens.statusLive
        CourtCallState.PAUSED -> tokens.statusPaused
        else -> tokens.borderSubtle
    }
    val numberColor: Color = if (state == CourtCallState.UP_NOW) tokens.onAccent else tokens.textPrimary
    val labelColor: Color = when (state) {
        CourtCallState.UP_NOW -> tokens.onAccent
        CourtCallState.LIVE -> tokens.statusLive
        CourtCallState.FINAL, CourtCallState.OPEN -> tokens.textMuted
        CourtCallState.PAUSED -> tokens.statusPaused
        else -> tokens.textAccent
    }
    val nameColor: Color = if (state == CourtCallState.UP_NOW) tokens.onAccent else tokens.textSecondary

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(fill, shape)
            .border(3.dp, borderColor, shape)
            .testTag(stateTag(state))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = court.id.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = numberColor,
            modifier = Modifier.testTag("court_call_tile_${court.id}"),
        )
        Text(
            text = stateLabel(state),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = labelColor,
        )

        when (state) {
            CourtCallState.LIVE, CourtCallState.FINAL -> {
                val match = court.currentMatch
                if (match != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = match.scoreA.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = if (state == CourtCallState.LIVE) tokens.teamA else tokens.textMuted,
                        )
                        Text(
                            text = "  –  ",
                            style = MaterialTheme.typography.headlineSmall,
                            color = tokens.textMuted,
                        )
                        Text(
                            text = match.scoreB.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = if (state == CourtCallState.LIVE) tokens.teamB else tokens.textMuted,
                        )
                    }
                    if (state == CourtCallState.LIVE) {
                        Text(
                            text = cappedMatchup(match.teamA),
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.teamA, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = cappedMatchup(match.teamB),
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.teamB, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            CourtCallState.UP_NOW, CourtCallState.READY -> {
                recommendation?.let { r ->
                    Text(
                        text = cappedMatchup(r.teamA),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = nameColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "vs",
                        style = MaterialTheme.typography.titleSmall, color = nameColor,
                    )
                    Text(
                        text = cappedMatchup(r.teamB),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = nameColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CourtCallState.OPEN, CourtCallState.PAUSED -> Unit
        }
    }
}
