package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ParticipantStatus
import com.example.model.Player
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueRosterSheet(
    roster: List<Player>,
    onAddPlayer: (String) -> Unit,
    onToggleRest: (String) -> Unit,
    onCheckOut: (String) -> Unit,
    onMovePlayer: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalPickItTokens.current
    var newPlayerName by remember { mutableStateOf("") }

    val waitingQueue = roster.filter { it.status == ParticipantStatus.AVAILABLE }
    val restingPlayers = roster.filter { it.status == ParticipantStatus.RESTING }
    val inMatchPlayers = roster.filter { it.status == ParticipantStatus.IN_MATCH }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceInset
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("queue_roster_sheet")
        ) {
            // Title & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Digital paddle queue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textPrimary
                )
                Text(
                    text = "${waitingQueue.size} in queue • ${restingPlayers.size} resting",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textAccent
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Add Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newPlayerName,
                    onValueChange = { newPlayerName = it },
                    placeholder = { Text("Enter player name...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = tokens.textPrimary,
                        unfocusedTextColor = tokens.textPrimary,
                        focusedBorderColor = tokens.accent,
                        unfocusedBorderColor = tokens.border
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_player_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newPlayerName.isNotBlank()) {
                            onAddPlayer(newPlayerName)
                            newPlayerName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.accent,
                        contentColor = tokens.onAccent
                    ),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("add_player_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add player")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Section 1: Active Waiting Queue
                item {
                    Text(
                        text = "Waiting queue (FIFO priority)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textSecondary
                    )
                }

                if (waitingQueue.isEmpty()) {
                    item {
                        Text(
                            text = "Queue is empty. Arriving players will be added here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    itemsIndexed(waitingQueue) { index, player ->
                        QueueItemCard(
                            queueNumber = index + 1,
                            player = player,
                            onToggleRest = { onToggleRest(player.id) },
                            onCheckOut = { onCheckOut(player.id) },
                            onMoveUp = if (index > 0) { { onMovePlayer(index, index - 1) } } else null,
                            onMoveDown = if (index < waitingQueue.size - 1) { { onMovePlayer(index, index + 1) } } else null
                        )
                    }
                }

                // Section 2: Resting Players
                if (restingPlayers.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Resting / paused (skipped for intake)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.attention
                        )
                    }

                    itemsIndexed(restingPlayers) { _, player ->
                        RestingItemCard(
                            player = player,
                            onResume = { onToggleRest(player.id) },
                            onCheckOut = { onCheckOut(player.id) }
                        )
                    }
                }

                // Section 3: Fairness Summary
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    ParticipationSummaryCard(roster = roster)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun QueueItemCard(
    queueNumber: Int,
    player: Player,
    onToggleRest: () -> Unit,
    onCheckOut: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    val tokens = LocalPickItTokens.current
    Surface(
        color = tokens.surfaceElevated,
        shape = RoundedCornerShape(tokens.radiusSm),
        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .background(tokens.accent, RoundedCornerShape(6.dp))
                ) {
                    Text(
                        text = "#$queueNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.onAccent,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = "${player.matchesPlayed} games played today",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
                    )
                }
            }

            // Quick Actions: Move, Rest, Checkout
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onMoveUp != null) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = tokens.textSecondary)
                    }
                }
                if (onMoveDown != null) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = tokens.textSecondary)
                    }
                }

                FilledTonalButton(
                    onClick = onToggleRest,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(tokens.radiusSm),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rest", fontSize = 12.sp)
                }

                IconButton(onClick = onCheckOut, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Check out", tint = tokens.textDanger)
                }
            }
        }
    }
}

@Composable
private fun RestingItemCard(
    player: Player,
    onResume: () -> Unit,
    onCheckOut: () -> Unit
) {
    val tokens = LocalPickItTokens.current
    Surface(
        color = tokens.surfaceInset,
        shape = RoundedCornerShape(tokens.radiusSm),
        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.attention.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.PauseCircle, contentDescription = null, tint = tokens.attention)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = "Resting • Preserving seniority",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.attention
                    )
                }
            }

            Row {
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.statusOpen,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(tokens.radiusSm),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume", fontSize = 12.sp)
                }
                IconButton(onClick = onCheckOut, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Check out", tint = tokens.textDanger)
                }
            }
        }
    }
}

@Composable
private fun ParticipationSummaryCard(roster: List<Player>) {
    val tokens = LocalPickItTokens.current
    Surface(
        color = tokens.surfaceInset,
        shape = RoundedCornerShape(tokens.radiusSm),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Participation parity audit",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.textAccent
            )
            Spacer(modifier = Modifier.height(4.dp))
            val avg = if (roster.isNotEmpty()) roster.map { it.matchesPlayed }.average() else 0.0
            val min = roster.minOfOrNull { it.matchesPlayed } ?: 0
            val max = roster.maxOfOrNull { it.matchesPlayed } ?: 0
            Text(
                text = "Average Games Played: %.1f • Range: %d to %d games".format(avg, min, max),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }
    }
}
