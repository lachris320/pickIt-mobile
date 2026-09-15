package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.CourtCallScreen
import com.example.ui.screens.LiveScoreboardScreen
import com.example.ui.screens.SessionHubScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.StandaloneScoreboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel

class MainActivity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by sessionViewModel.themeMode.collectAsState()
            MyApplicationTheme(themeMode = themeMode) {
                PickleballAppContent(viewModel = sessionViewModel)
            }
        }
    }
}

@Composable
fun PickleballAppContent(viewModel: SessionViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
        if (screen is AppScreen.CourtCall) {
            // Full-bleed: no safeDrawingPadding, no MaterialTheme.colorScheme Surface.
            // CourtCallScreen paints DarkTokens.canvas edge-to-edge itself.
            CourtCallScreen(viewModel = viewModel)
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                color = MaterialTheme.colorScheme.background,
            ) {
                when (screen) {
                    is AppScreen.SessionHub -> SessionHubScreen(viewModel = viewModel)
                    is AppScreen.LiveScoreboard -> LiveScoreboardScreen(courtId = screen.courtId, viewModel = viewModel)
                    is AppScreen.StandaloneScoreboard -> StandaloneScoreboardScreen(match = screen.match, viewModel = viewModel)
                    is AppScreen.Setup -> SetupScreen(viewModel = viewModel)
                    is AppScreen.CourtCall -> Unit // handled above; keeps `when` exhaustive
                }
            }
        }
    }
}
