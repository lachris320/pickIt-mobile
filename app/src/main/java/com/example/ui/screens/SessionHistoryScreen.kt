package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.MatchResult
import com.example.model.PlayerDetail
import com.example.model.PlayerSessionRecord
import com.example.model.RankedPlayer
import com.example.model.SessionListItem
import com.example.model.TeamId
import com.example.ui.theme.LocalPickItTokens
import com.example.viewmodel.AppScreen
import com.example.viewmodel.HistoryUiState
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.SessionDetail
import com.example.viewmodel.SessionViewModel
import com.example.viewmodel.assembleHistoryState
import com.example.viewmodel.buildSessionDetail
import com.example.viewmodel.leaderLine
import com.example.viewmodel.resolvePlayerDetail
import java.text.DateFormat
import java.util.Date

private fun formatSessionDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

private fun formatMatchTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

/** An in-screen detail route above the History/All-Time tab base. */
private sealed interface DetailRoute {
    val key: String
    data class SessionDetail(val sessionId: String) : DetailRoute {
        override val key get() = "S:$sessionId"
    }
    data class PlayerDetail(val normalizedId: String) : DetailRoute {
        override val key get() = "P:$normalizedId"
    }
}

/** A back-stack entry: the route plus the scroll anchor captured when it was last navigated away from. */
private data class RouteEntry(val route: DetailRoute, val index: Int, val offset: Int)

private fun decodeRoute(key: String): DetailRoute = when {
    key.startsWith("S:") -> DetailRoute.SessionDetail(key.removePrefix("S:"))
    key.startsWith("P:") -> DetailRoute.PlayerDetail(key.removePrefix("P:"))
    else -> error("unknown route key: $key")
}

// Persist the whole stack (routes + buried anchors) across Activity recreation.
private val routeStackSaver = listSaver<List<RouteEntry>, List<Any>>(
    save = { stack -> stack.map { listOf(it.route.key, it.index, it.offset) } },
    restore = { saved -> saved.map { e -> RouteEntry(decodeRoute(e[0] as String), e[1] as Int, e[2] as Int) } },
)

@Composable
fun SessionHistoryScreen(
    sessionViewModel: SessionViewModel,
    historyViewModel: HistoryViewModel,
    origin: AppScreen,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPickItTokens.current
    val data by historyViewModel.data.collectAsStateWithLifecycle()
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val isLoaded by sessionViewModel.isSessionLoaded.collectAsStateWithLifecycle()

    val uiState = remember(data, session?.id, isLoaded) {
        assembleHistoryState(data, session?.id, isLoaded)
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var backStack by rememberSaveable(stateSaver = routeStackSaver) { mutableStateOf(emptyList<RouteEntry>()) }
    val historyListState = rememberLazyListState()
    val allTimeListState = rememberLazyListState()

    // Push from the tab base (no detail scroll to capture; the tab lists keep their hoisted state).
    fun openFromTabs(route: DetailRoute) { backStack = backStack + RouteEntry(route, 0, 0) }
    // Push a child from within a detail pane, capturing the current pane's live scroll first.
    fun openChild(current: LazyListState, child: DetailRoute) {
        val top = backStack.lastOrNull() ?: return openFromTabs(child)
        backStack = backStack.dropLast(1) +
            top.copy(index = current.firstVisibleItemIndex, offset = current.firstVisibleItemScrollOffset) +
            RouteEntry(child, 0, 0)
    }

    val onBack: () -> Unit = {
        if (backStack.isNotEmpty()) backStack = backStack.dropLast(1)
        else sessionViewModel.navigateTo(origin)
    }
    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxSize().background(tokens.canvas).testTag("session_history_screen")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("session_history_back"),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tokens.textPrimary)
            }
            Text(
                text = "Session History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary,
            )
        }

        when (val s = uiState) {
            is HistoryUiState.Loading -> CenterBox {
                CircularProgressIndicator(modifier = Modifier.testTag("session_history_loading"))
            }
            is HistoryUiState.Error -> CenterBox {
                Text("Couldn't load history.", color = tokens.textMuted, modifier = Modifier.testTag("session_history_error"))
            }
            is HistoryUiState.Content -> {
                val top = backStack.lastOrNull()
                if (top == null) {
                    TabRow(selectedTabIndex = selectedTab, containerColor = tokens.canvas) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            modifier = Modifier.testTag("history_tab")) { Text("History", modifier = Modifier.padding(12.dp)) }
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                            modifier = Modifier.testTag("all_time_tab")) { Text("All-Time", modifier = Modifier.padding(12.dp)) }
                    }
                    if (selectedTab == 0) {
                        HistoryList(
                            items = s.pastSessions,
                            listState = historyListState,
                            onOpen = { openFromTabs(DetailRoute.SessionDetail(it)) },
                        )
                    } else {
                        AllTimeList(
                            rows = s.allTime,
                            listState = allTimeListState,
                            onOpenPlayer = { openFromTabs(DetailRoute.PlayerDetail(it)) },
                        )
                    }
                } else {
                    key(top.route.key) {
                        val paneState = rememberSaveable(saver = LazyListState.Saver) {
                            LazyListState(top.index, top.offset)
                        }
                        when (val route = top.route) {
                            is DetailRoute.SessionDetail ->
                                SessionDetailView(buildSessionDetail(data, route.sessionId), paneState)
                            is DetailRoute.PlayerDetail ->
                                PlayerDetailView(
                                    detail = resolvePlayerDetail(data, route.normalizedId, session?.id),
                                    listState = paneState,
                                    onOpenSession = { sid -> openChild(paneState, DetailRoute.SessionDetail(sid)) },
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun HistoryList(
    items: List<SessionListItem>,
    listState: LazyListState,
    onOpen: (String) -> Unit,
) {
    val tokens = LocalPickItTokens.current
    if (items.isEmpty()) {
        CenterBox {
            Text(
                "No past sessions yet.",
                color = tokens.textMuted,
                modifier = Modifier.testTag("session_history_empty"),
            )
        }
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("session_history_list")) {
        items(items, key = { it.meta.id }) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_row_${item.meta.id}")
                    .clickable { onOpen(item.meta.id) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(item.meta.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                Text(
                    "${formatSessionDate(item.meta.startTime)} · ${item.summary.gameCount} games · ${item.summary.participantCount} players",
                    style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary,
                )
                Text(leaderLine(item.summary), style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
            }
        }
    }
}

@Composable
private fun AllTimeList(
    rows: List<RankedPlayer>,
    listState: LazyListState,
    onOpenPlayer: (String) -> Unit,
) {
    val tokens = LocalPickItTokens.current
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Ranked by total wins, then point difference.",
                    style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                Text("Grouped by name across sessions. Use consistent, distinct names.",
                    style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
            }
        }
        if (rows.isEmpty()) {
            item {
                CenterBox {
                    Text("No games recorded yet.", color = tokens.textMuted,
                        modifier = Modifier.testTag("all_time_empty"))
                }
            }
        } else {
            items(rows, key = { it.id }) { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("all_time_row_${r.id}")
                        .clickable { onOpenPlayer(r.id) } // tap -> Player Detail
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${r.rank}", modifier = Modifier.padding(end = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (r.rank == 1) FontWeight.Black else FontWeight.Bold,
                        color = if (r.rank == 1) tokens.textAccent else tokens.textPrimary)
                    Text(r.name, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge, color = tokens.textPrimary)
                    Text("${r.wins}–${r.losses}", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    Text("  ${r.wins + r.losses}g", style = MaterialTheme.typography.bodyMedium, color = tokens.textMuted)
                    Text("  ${formatDiff(r.pointDiff)}",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun SessionDetailView(detail: SessionDetail, listState: LazyListState) {
    val tokens = LocalPickItTokens.current
    when (detail) {
        is SessionDetail.NotAvailable -> CenterBox {
            Text("Session no longer available.", color = tokens.textMuted,
                modifier = Modifier.testTag("session_detail_unavailable"))
        }
        is SessionDetail.Found -> {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("session_detail")) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(detail.meta.name, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                        Text(formatSessionDate(detail.meta.startTime),
                            style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    }
                }
                item {
                    Text("Standings", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                }
                items(detail.standings, key = { "st_${it.id}" }) { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("standings_row_${p.id}")
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${p.rank}. ${p.name}", color = tokens.textPrimary)
                        Text("${p.wins}–${p.losses}  ${formatDiff(p.pointDiff)}",
                            color = tokens.textSecondary)
                    }
                }
                item {
                    Text("Matches", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                }
                items(detail.matches, key = { "mt_${it.matchId}" }) { m ->
                    MatchRow(m)
                }
            }
        }
    }
}

@Composable
private fun PlayerDetailView(
    detail: PlayerDetail,
    listState: LazyListState,
    onOpenSession: (String) -> Unit,
) {
    val tokens = LocalPickItTokens.current
    when (detail) {
        is PlayerDetail.NotAvailable -> CenterBox {
            Text("Player no longer available.", color = tokens.textMuted,
                modifier = Modifier.testTag("player_detail_unavailable"))
        }
        is PlayerDetail.Found -> {
            val d = detail.data
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("player_detail")) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(d.displayName, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                        Text("Records grouped by name across sessions.",
                            style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                        Spacer(Modifier.height(8.dp))
                        Text("Rank #${d.rank}  ·  ${d.wins}–${d.losses}",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = tokens.textPrimary)
                        Text("${d.games} games · ${d.sessionsPlayed} sessions played · ${formatDiff(d.pointDiff)}",
                            style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    }
                }
                item {
                    Text("Sessions", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                }
                items(d.records, key = { "ps_${it.sessionId}" }) { rec ->
                    PlayerSessionRow(rec, onOpenSession)
                }
            }
        }
    }
}

@Composable
private fun PlayerSessionRow(rec: PlayerSessionRecord, onOpenSession: (String) -> Unit) {
    val tokens = LocalPickItTokens.current
    val base = Modifier.fillMaxWidth().testTag("player_session_row_${rec.sessionId}")
    val rowMod = if (rec.sessionName != null) base.clickable { onOpenSession(rec.sessionId) } else base
    Column(modifier = rowMod.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                rec.sessionName ?: "Session unavailable",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = if (rec.sessionName != null) tokens.textPrimary else tokens.textMuted,
            )
            if (rec.isActive) {
                Text(
                    "In progress",
                    modifier = Modifier
                        .testTag("player_session_active_${rec.sessionId}"),
                    style = MaterialTheme.typography.labelSmall, color = tokens.textAccent,
                )
            }
        }
        val dateLine = rec.startTime?.let { formatSessionDate(it) }
        Text(
            listOfNotNull(dateLine, "${rec.games} games", "${rec.wins}–${rec.losses}", formatDiff(rec.pointDiff))
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary,
        )
    }
}

@Composable
private fun MatchRow(m: MatchResult) {
    val tokens = LocalPickItTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().testTag("match_row_${m.matchId}").padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (m.winner == null) {
            Text(
                "${m.teamA.joinToString(" & ")} vs ${m.teamB.joinToString(" & ")} — Result unavailable",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, color = tokens.textMuted,
            )
        } else {
            val aWon = m.winner == TeamId.TEAM_A
            Text(
                m.teamA.joinToString(" & "),
                fontWeight = if (aWon) FontWeight.Bold else FontWeight.Normal,
                color = tokens.textPrimary, style = MaterialTheme.typography.bodyMedium,
            )
            Text("  ${m.scoreA}–${m.scoreB}  ", color = tokens.textSecondary,
                style = MaterialTheme.typography.bodyMedium)
            Text(
                m.teamB.joinToString(" & "),
                modifier = Modifier.weight(1f),
                fontWeight = if (!aWon) FontWeight.Bold else FontWeight.Normal,
                color = tokens.textPrimary, style = MaterialTheme.typography.bodyMedium,
            )
            Text(formatMatchTime(m.endTime), style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
        }
    }
}
