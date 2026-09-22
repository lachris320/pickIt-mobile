package com.example.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RankedPlayer
import com.example.ui.screens.formatDiff
import com.example.ui.theme.LocalPickItTokens

/** One board row: `Rank | Name [Left?] | W-L | ±Diff`. Rank 1 gets a subtle accent. */
@Composable
fun RankingRow(player: RankedPlayer, rowHeight: Dp, modifier: Modifier = Modifier) {
    val tokens = LocalPickItTokens.current
    val accent = player.rank == 1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .testTag("live_ranking_row_${player.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${player.rank}",
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = if (accent) FontWeight.Black else FontWeight.Bold,
            color = if (accent) tokens.textAccent else tokens.textPrimary,
        )
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = player.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (player.isCheckedOut) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Left",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textMuted,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .testTag("live_ranking_left_${player.id}"),
                )
            }
        }
        Text(
            text = "${player.wins}–${player.losses}", // en-dash W-L
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textSecondary,
        )
        Text(
            text = formatDiff(player.pointDiff),
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = tokens.textPrimary,
        )
    }
}
