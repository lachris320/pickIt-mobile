package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RotationPolicy
import com.example.ui.theme.*
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    viewModel: SessionViewModel,
    modifier: Modifier = Modifier
) {
    var sessionName by remember { mutableStateOf("") }
    var courtCount by remember { mutableIntStateOf(3) }
    var rotationPolicy by remember { mutableStateOf(RotationPolicy.FOUR_OFF_FOUR_ON) }

    // Roster starts empty so the organizer builds their own; "Load sample players"
    // seeds demo/testing names without contaminating real operational state.
    val selectedPlayers = remember { mutableStateListOf<String>() }

    var customPlayerInput by remember { mutableStateOf("") }
    val tokens = LocalPickItTokens.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "New open play session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.SessionHub) },
                        modifier = Modifier.testTag("setup_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = tokens.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.canvas)
            )
        },
        containerColor = tokens.canvas
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .testTag("setup_screen_content"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Field 1: Session Name
            item {
                Text(
                    text = "SESSION NAME",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textAccent
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = sessionName,
                    onValueChange = { sessionName = it },
                    singleLine = true,
                    placeholder = { Text("Saturday Open Play") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = tokens.textPrimary,
                        unfocusedTextColor = tokens.textPrimary,
                        focusedBorderColor = tokens.accent,
                        unfocusedBorderColor = tokens.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_session_name_input")
                )
            }

            // Field 2: Number of Courts
            item {
                Text(
                    text = "NUMBER OF COURTS AVAILABLE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textAccent
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(1, 2, 3, 4, 6).forEach { count ->
                        val isSelected = (courtCount == count)
                        Button(
                            onClick = { courtCount = count },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) tokens.accent else tokens.surfaceElevated,
                                contentColor = if (isSelected) tokens.onAccent else tokens.textPrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) tokens.accent else tokens.border
                            ),
                            shape = RoundedCornerShape(tokens.radiusSm),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("setup_court_count_$count")
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(text = "$count", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // Field 3: Rotation Policy Preset
            item {
                Text(
                    text = "ROTATION SYSTEM POLICY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textAccent
                )
                Spacer(modifier = Modifier.height(6.dp))

                RotationPolicy.values().forEach { policy ->
                    val isSelected = (rotationPolicy == policy)
                    Surface(
                        onClick = { rotationPolicy = policy },
                        color = if (isSelected) tokens.surfaceInset else tokens.surfaceElevated,
                        shape = RoundedCornerShape(tokens.radiusMd),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (isSelected) tokens.accent else tokens.border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("setup_policy_${policy.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { rotationPolicy = policy },
                                colors = RadioButtonDefaults.colors(selectedColor = tokens.accent)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = policy.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = policy.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Field 4: Initial Player Roster Setup
            item {
                Text(
                    text = "PLAYER ROSTER (${selectedPlayers.size} PLAYERS)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textAccent
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = customPlayerInput,
                        onValueChange = { customPlayerInput = it },
                        placeholder = { Text("Add custom player...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = tokens.textPrimary,
                            unfocusedTextColor = tokens.textPrimary,
                            focusedBorderColor = tokens.accent,
                            unfocusedBorderColor = tokens.border
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("setup_add_player_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (customPlayerInput.isNotBlank()) {
                                selectedPlayers.add(customPlayerInput.trim())
                                customPlayerInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.accent,
                            contentColor = tokens.onAccent
                        ),
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("setup_add_player_btn")
                    ) {
                        Text("Add")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (selectedPlayers.isEmpty()) {
                            selectedPlayers.addAll(viewModel.frequentPlayers.take(12))
                        }
                    },
                    modifier = Modifier.testTag("load_sample_players_button")
                ) {
                    Text("Load sample players")
                }
            }

            // Roster tags/chips
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    selectedPlayers.forEachIndexed { index, player ->
                        InputChip(
                            selected = true,
                            onClick = { selectedPlayers.removeAt(index) },
                            label = { Text(player) },
                            modifier = Modifier.testTag("roster_chip"),
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            // Launch Session Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.startNewSession(
                            name = sessionName,
                            courtCount = courtCount,
                            policy = rotationPolicy,
                            playerNames = selectedPlayers
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("launch_session_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.accent,
                        contentColor = tokens.onAccent
                    ),
                    shape = RoundedCornerShape(tokens.radiusMd)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Launch session",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
