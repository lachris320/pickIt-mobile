package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.RankingEngine
import com.example.model.RankedPlayer
import com.example.ui.components.RankingRow
import com.example.ui.theme.DarkTokens
import com.example.ui.theme.LocalPickItTokens
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import kotlinx.coroutines.delay

private const val ROW_HEIGHT_DP = 56
private const val FOOTER_DP = 40
private const val PAGE_INTERVAL_MS = 9_000L

@Composable
fun LiveRankingScreen(
    viewModel: SessionViewModel,
    modifier: Modifier = Modifier,
    // Test seam: fixes rows-per-page so paging is deterministic under Robolectric (prod passes null
    // and the value is measured). Inert in production — no caller supplies it.
    rowsPerPageOverride: Int? = null,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    BackHandler { viewModel.navigateTo(AppScreen.SessionHub) } // same action as the close button

    CompositionLocalProvider(LocalPickItTokens provides DarkTokens) {
        val tokens = LocalPickItTokens.current
        BoardDisplayHygiene()
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(tokens.canvas)
                .safeDrawingPadding() // this screen owns inset consumption (full-bleed parent)
                .testTag("live_ranking_board"),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                // Header + subtitle are ALWAYS shown, including the empty state.
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        text = "Live Ranking",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = tokens.textPrimary,
                    )
                    Text(
                        text = "Ranked by wins · Ties broken by point difference.",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textSecondary,
                    )
                }

                val roster = session?.roster
                val ranked = remember(roster) { roster?.let { RankingEngine.rank(it) } ?: emptyList() }

                if (ranked.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No results yet — rankings appear after the first game.",
                            style = MaterialTheme.typography.headlineSmall,
                            color = tokens.textMuted,
                            modifier = Modifier.testTag("live_ranking_empty"),
                        )
                    }
                } else {
                    RankingContent(ranked = ranked, rowsPerPageOverride = rowsPerPageOverride)
                }
            }

            IconButton(
                onClick = { viewModel.navigateTo(AppScreen.SessionHub) },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("live_ranking_close"),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close Live Ranking", tint = tokens.textMuted)
            }
        }
    }
}

/**
 * The paged rows + footer slot. A `ColumnScope` extension so its root `BoxWithConstraints` is a
 * direct child of the caller's Column and can use `Modifier.weight(1f)`.
 */
@Composable
private fun ColumnScope.RankingContent(ranked: List<RankedPlayer>, rowsPerPageOverride: Int? = null) {
    val tokens = LocalPickItTokens.current
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT_DP.dp.roundToPx() }
    val footerPx = with(density) { FOOTER_DP.dp.roundToPx() }
    val rawPage = remember { mutableIntStateOf(0) }

    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
        // Reserve the footer slot inside this region so measurement is independent of pageCount.
        val availableForRows = with(density) { maxHeight.roundToPx() } - footerPx
        val perPage = rowsPerPageOverride ?: rowsPerPage(availableForRows, rowHeightPx)
        val pageCount = pageCountFor(ranked.size, perPage)
        val page = rawPage.intValue.coerceIn(0, pageCount - 1)

        LaunchedEffect(pageCount) {
            if (pageCount <= 1) return@LaunchedEffect
            while (true) {
                delay(PAGE_INTERVAL_MS)
                rawPage.intValue = nextPage(rawPage.intValue, pageCount) // reads live value each tick
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ranked.drop(page * perPage).take(perPage).forEach { rp ->
                    key(rp.id) { RankingRow(player = rp, rowHeight = ROW_HEIGHT_DP.dp) }
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(FOOTER_DP.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (pageCount > 1) {
                    Text(
                        text = "Page ${page + 1} / $pageCount",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textMuted,
                        modifier = Modifier.testTag("live_ranking_page_indicator"),
                    )
                }
            }
        }
    }
}
