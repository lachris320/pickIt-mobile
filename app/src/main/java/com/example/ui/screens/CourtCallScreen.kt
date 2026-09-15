package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.Court
import com.example.model.OpenPlaySession
import com.example.ui.components.CourtCallTile
import com.example.ui.theme.DarkTokens
import com.example.ui.theme.LocalPickItTokens
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import kotlinx.coroutines.delay

private const val PULSE_MS = 2_000L
private const val PAGE_HOLD_MS = 6_000L
private const val PAGE_CYCLE_MS = 10_000L

@Composable
fun CourtCallScreen(
    viewModel: SessionViewModel,
    modifier: Modifier = Modifier,
) {
    val session by viewModel.session.collectAsState()

    BackHandler { viewModel.navigateTo(AppScreen.SessionHub) }

    CompositionLocalProvider(LocalPickItTokens provides DarkTokens) {
        val tokens = LocalPickItTokens.current
        BoardDisplayHygiene()
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(tokens.canvas)
                .testTag("court_call_board"),
        ) {
            val active = session
            if (active == null) {
                Text(
                    text = "No active session",
                    style = MaterialTheme.typography.displayLarge,
                    color = tokens.textMuted,
                    modifier = Modifier.align(Alignment.Center).testTag("court_call_empty"),
                )
            } else {
                CourtCallGrid(session = active)
                if (active.isPaused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(end = 64.dp)
                            .background(tokens.statusPaused)
                            .padding(vertical = 12.dp)
                            .testTag("court_call_paused_banner"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "SESSION PAUSED",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = tokens.textPrimary,
                        )
                    }
                }
            }

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

@Composable
private fun BoardDisplayHygiene() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        fun enable() {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        fun disable() {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> enable()
                Lifecycle.Event.ON_PAUSE -> disable()
                else -> Unit
            }
        }
        enable()
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            disable()
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun CourtCallGrid(session: OpenPlaySession) {
    val courts = remember(session) { session.courts.sortedBy { it.id } }
    val primaryId = remember(session) { primaryReadyCourtId(session) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth < 600.dp -> 1
            maxWidth < 1000.dp -> 2
            else -> 3
        }
        val rows = if (maxHeight < 480.dp) 1 else 2
        val capacity = (columns * rows).coerceAtLeast(1)
        val pages = courts.chunked(capacity)

        val currentStates: Map<Int, CourtCallState> = courts.associate { court ->
            court.id to courtDisplayState(
                court = court,
                hasRecommendation = session.activeRecommendations[court.id] != null,
                isPrimaryReady = court.id == primaryId,
            )
        }
        var prevStates by remember { mutableStateOf(currentStates) }
        val pulsing = remember { mutableStateMapOf<Int, Boolean>() }
        var justCalledCourtId by remember { mutableStateOf<Int?>(null) }
        var pageIndex by rememberSaveable(pages.size) { mutableStateOf(0) }

        LaunchedEffect(currentStates) {
            val transitioned = currentStates.filter { (id, s) ->
                prevStates[id] != s && (s == CourtCallState.UP_NOW || s == CourtCallState.FINAL)
            }.keys
            prevStates = currentStates
            transitioned.forEach { id ->
                pulsing[id] = true
                justCalledCourtId = id
            }
        }
        LaunchedEffect(pulsing.keys.toList()) {
            if (pulsing.isNotEmpty()) {
                delay(PULSE_MS)
                pulsing.clear()
            }
        }
        LaunchedEffect(justCalledCourtId) {
            if (justCalledCourtId != null) {
                delay(PAGE_HOLD_MS)
                justCalledCourtId = null
            }
        }
        val heldPage = justCalledCourtId?.let { id -> pages.indexOfFirst { p -> p.any { it.id == id } } }
        LaunchedEffect(heldPage) {
            if (heldPage != null && heldPage >= 0) pageIndex = heldPage
        }
        if (pages.size > 1 && justCalledCourtId == null) {
            LaunchedEffect(pages.size) {
                while (true) {
                    delay(PAGE_CYCLE_MS)
                    pageIndex = (pageIndex + 1) % pages.size
                }
            }
        }

        if (pages.isNotEmpty()) {
            val page = pageIndex.coerceIn(0, pages.lastIndex)
            Column(modifier = Modifier.fillMaxSize()) {
                CourtGridLayout(
                    courts = pages[page],
                    session = session,
                    states = currentStates,
                    columns = columns,
                    pulsing = pulsing,
                    modifier = Modifier.weight(1f),
                )
                if (pages.size > 1) {
                    val tokens = LocalPickItTokens.current
                    Text(
                        text = "${page + 1} / ${pages.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 12.dp)
                            .testTag("court_call_page_indicator"),
                    )
                }
            }
        }
    }
}

@Composable
private fun CourtGridLayout(
    courts: List<Court>,
    session: OpenPlaySession,
    states: Map<Int, CourtCallState>,
    columns: Int,
    pulsing: Map<Int, Boolean>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        courts.chunked(columns).forEach { rowCourts ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                rowCourts.forEach { court ->
                    // `states` is built over the same court list these pages are chunked from, so
                    // every rendered id is present; the OPEN fallback is a total-by-construction
                    // safety net (prefer a graceful degrade over a crash on a live board).
                    val state = states[court.id] ?: CourtCallState.OPEN
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        CourtCallTile(
                            court = court,
                            state = state,
                            recommendation = session.activeRecommendations[court.id],
                            pulsing = pulsing[court.id] == true,
                        )
                    }
                }
                repeat(columns - rowCourts.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
