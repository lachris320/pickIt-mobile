package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkTokens
import com.example.ui.theme.LocalPickItTokens
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel

@Composable
fun CourtCallScreen(
    viewModel: SessionViewModel,
    modifier: Modifier = Modifier,
) {
    val session by viewModel.session.collectAsState()

    BackHandler { viewModel.navigateTo(AppScreen.SessionHub) }

    CompositionLocalProvider(LocalPickItTokens provides DarkTokens) {
        val tokens = LocalPickItTokens.current
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(tokens.canvas)
                .testTag("court_call_board")
        ) {
            @Suppress("UNUSED_EXPRESSION") session

            IconButton(
                onClick = { viewModel.navigateTo(AppScreen.SessionHub) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .testTag("court_call_close"),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Court Call",
                    tint = tokens.textMuted,
                )
            }
        }
    }
}
