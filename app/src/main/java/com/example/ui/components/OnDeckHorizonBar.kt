package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Player
import com.example.ui.theme.*

@Composable
fun OnDeckHorizonBar(
    onDeckPlayers: List<Player>,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPickItTokens.current
    Surface(
        color = tokens.surfaceInset,
        shape = RoundedCornerShape(tokens.radiusMd),
        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.border),
        modifier = modifier
            .fillMaxWidth()
            .testTag("on_deck_horizon_bar")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = tokens.textAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "On deck (next up for court)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textAccent
                    )
                }

                Text(
                    text = "${onDeckPlayers.size} warming up",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (onDeckPlayers.isEmpty()) {
                Text(
                    text = "No players currently waiting in queue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(onDeckPlayers) { index, player ->
                        Surface(
                            color = tokens.surfaceInset,
                            shape = RoundedCornerShape(tokens.radiusSm),
                            border = androidx.compose.foundation.BorderStroke(1.dp, tokens.border)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(tokens.accent, RoundedCornerShape(10.dp))
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.onAccent,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = player.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.textPrimary
                                    )
                                    Text(
                                        text = "${player.matchesPlayed} games today",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
