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
    var newPlayerName by remember { mutableStateOf("") }

    val waitingQueue = roster.filter { it.status == ParticipantStatus.AVAILABLE }
    val restingPlayers = roster.filter { it.status == ParticipantStatus.RESTING }
    val inMatchPlayers = roster.filter { it.status == ParticipantStatus.IN_MATCH }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PickleballDarkCourt
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
                    text = "DIGITAL PADDLE QUEUE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WhiteHighContrast
                )
                Text(
                    text = "${waitingQueue.size} in queue • ${restingPlayers.size} resting",
                    style = MaterialTheme.typography.labelSmall,
                    color = PickleballLime
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
                        focusedTextColor = WhiteHighContrast,
                        unfocusedTextColor = WhiteHighContrast,
                        focusedBorderColor = PickleballLime,
                        unfocusedBorderColor = PickleballCardBorder
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
                        containerColor = PickleballLime,
                        contentColor = Color(0xFF1B3700)
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
                        text = "WAITING QUEUE (FIFO PRIORITY)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                }

                if (waitingQueue.isEmpty()) {
                    item {
                        Text(
                            text = "Queue is empty. Arriving players will be added here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
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
                            text = "RESTING / PAUSED (SKIPPED FOR INTAKE)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CourtAttentionAmber
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
    Surface(
        color = PickleballCardSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PickleballCardBorder),
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
                        .background(PickleballLime, RoundedCornerShape(6.dp))
                ) {
                    Text(
                        text = "#$queueNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3700),
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = WhiteHighContrast
                    )
                    Text(
                        text = "${player.matchesPlayed} games played today",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            // Quick Actions: Move, Rest, Checkout
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onMoveUp != null) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = TextMuted)
                    }
                }
                if (onMoveDown != null) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = TextMuted)
                    }
                }

                FilledTonalButton(
                    onClick = onToggleRest,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rest", fontSize = 12.sp)
                }

                IconButton(onClick = onCheckOut, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Check out", tint = Color(0xFFE57373))
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
    Surface(
        color = Color(0xFF211D15),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CourtAttentionAmber.copy(alpha = 0.4f)),
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
                Icon(imageVector = Icons.Default.PauseCircle, contentDescription = null, tint = CourtAttentionAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = WhiteHighContrast
                    )
                    Text(
                        text = "Resting • Preserving seniority",
                        style = MaterialTheme.typography.labelSmall,
                        color = CourtAttentionAmber
                    )
                }
            }

            Row {
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CourtAvailableGreen,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume", fontSize = 12.sp)
                }
                IconButton(onClick = onCheckOut, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Check out", tint = Color(0xFFE57373))
                }
            }
        }
    }
}

@Composable
private fun ParticipationSummaryCard(roster: List<Player>) {
    Surface(
        color = Color(0xFF141D17),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "PARTICIPATION PARITY AUDIT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = PickleballLime
            )
            Spacer(modifier = Modifier.height(4.dp))
            val avg = if (roster.isNotEmpty()) roster.map { it.matchesPlayed }.average() else 0.0
            val min = roster.minOfOrNull { it.matchesPlayed } ?: 0
            val max = roster.maxOfOrNull { it.matchesPlayed } ?: 0
            Text(
                text = "Average Games Played: %.1f • Range: %d to %d games".format(avg, min, max),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
