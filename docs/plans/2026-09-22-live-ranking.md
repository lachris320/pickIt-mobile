# Live Ranking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the subagent-build skill (`/subagent-build`) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a full-screen TV "Live Ranking" leaderboard that ranks the session's players by wins (point-difference tie-break), auto-pages long lists, and refreshes off the existing reactive session — with a pure ranking engine and no persistence changes.

**Architecture:** A pure `RankingEngine.rank(roster)` produces `RankedPlayer` rows (competition ranking). Pure paging/format helpers (`rowsPerPage`, `pageCountFor`, `nextPage`, `formatDiff`) drive a Compose TV screen (`LiveRankingScreen`) that mirrors Court Call: rendered in `MainActivity`'s outer full-bleed branch with keep-awake, self-owned insets, a constant-height footer slot, and a `pageCount`-keyed 9s advance timer. Court Call's keep-awake helper is extracted to a shared file first so both boards share it.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, JUnit4 (pure logic), Robolectric + Compose UI test (screen). Gradle.

**Spec:** [docs/specs/2026-09-22-live-ranking-design.md](../specs/2026-09-22-live-ranking-design.md) — passed both `/claude-review` and `/codex-review` design-spec gates.

**Suggested branch:** `feature/live-ranking` (created by `/worktrees` at execution time).

**Commit discipline (project rules):** commit via the `commit` skill; stage only the explicit paths named in each commit step (never `git add -A` — the worktree carries untracked `gradlew`, `local.properties`, `debug.keystore`, `.idea/`, etc. that must NOT be staged). No Claude co-author trailer; no "Generated with Claude Code" line.

**Test commands:**
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.RankingEngineTest"
./gradlew :app:testDebugUnitTest --tests "com.example.ui.LiveRankingSupportTest"
./gradlew :app:testDebugUnitTest --tests "com.example.ui.LiveRankingScreenTest"
./gradlew :app:testDebugUnitTest --tests "com.example.ui.SessionHubLiveRankingTest"
```

---

## File Structure

- Create `app/src/main/java/com/example/model/RankedPlayer.kt` — the ranked-row model.
- Create `app/src/main/java/com/example/engine/RankingEngine.kt` — pure ranking (filter, sort, competition rank).
- Create `app/src/main/java/com/example/ui/screens/LiveRankingSupport.kt` — pure UI math: `rowsPerPage`, `pageCountFor`, `nextPage`, `formatDiff`.
- Create `app/src/main/java/com/example/ui/screens/BoardDisplayHygiene.kt` — keep-awake/cutout helper extracted from `CourtCallScreen` (shared by both boards).
- Modify `app/src/main/java/com/example/ui/screens/CourtCallScreen.kt` — remove the moved helpers (no behavior change).
- Create `app/src/main/java/com/example/ui/components/RankingRow.kt` — one board row.
- Create `app/src/main/java/com/example/ui/screens/LiveRankingScreen.kt` — the TV surface.
- Modify `app/src/main/java/com/example/viewmodel/SessionViewModel.kt` — add `AppScreen.LiveRanking`.
- Modify `app/src/main/java/com/example/MainActivity.kt` — full-bleed branch + `when` no-op.
- Modify `app/src/main/java/com/example/ui/screens/SessionHubScreen.kt` — Hub entry button.
- Tests: `RankingEngineTest.kt`, `ui/LiveRankingSupportTest.kt`, `ui/LiveRankingScreenTest.kt`, `ui/SessionHubLiveRankingTest.kt`.

---

## Task 1: RankingEngine + RankedPlayer (pure logic)

**Files:**
- Create: `app/src/main/java/com/example/model/RankedPlayer.kt`
- Create: `app/src/main/java/com/example/engine/RankingEngine.kt`
- Test: `app/src/test/java/com/example/RankingEngineTest.kt`

- [ ] **Step 1: Create the `RankedPlayer` model**

`app/src/main/java/com/example/model/RankedPlayer.kt`:
```kotlin
package com.example.model

/** One row of the Live Ranking board. `rank` is a competition rank (ties share a number). */
data class RankedPlayer(
    val id: String,
    val rank: Int,
    val name: String,
    val wins: Int,
    val losses: Int,
    val pointDiff: Int,
    val isCheckedOut: Boolean,
)
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/example/RankingEngineTest.kt`:
```kotlin
package com.example

import com.example.engine.RankingEngine
import com.example.model.ParticipantStatus
import com.example.model.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class RankingEngineTest {

    private fun player(
        id: String, name: String, played: Int, won: Int,
        pf: Int = 0, pa: Int = 0,
        status: ParticipantStatus = ParticipantStatus.AVAILABLE,
    ) = Player(
        id = id, name = name, status = status,
        matchesPlayed = played, matchesWon = won,
        totalPointsScored = pf, totalPointsConceded = pa,
    )

    @Test fun `ranks by wins then point diff then name then id`() {
        val roster = listOf(
            player("c", "Cara", played = 3, won = 2, pf = 30, pa = 20),   // 2 wins, +10
            player("a", "Ana", played = 3, won = 3, pf = 33, pa = 10),    // 3 wins, +23
            player("b", "Bea", played = 3, won = 2, pf = 25, pa = 20),    // 2 wins, +5
        )
        val ranked = RankingEngine.rank(roster)
        assertEquals(listOf("a", "c", "b"), ranked.map { it.id })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
        assertEquals(23, ranked[0].pointDiff)
        assertEquals(0, ranked[0].losses) // 3 played, 3 won
    }

    @Test fun `competition ranking shares a number for equal wins and diff`() {
        val roster = listOf(
            player("w", "Win", played = 4, won = 4, pf = 44, pa = 20),   // rank 1
            player("x", "Xan", played = 3, won = 2, pf = 22, pa = 12),   // 2 wins, +10
            player("y", "Yas", played = 5, won = 2, pf = 30, pa = 20),   // 2 wins, +10 (diff losses)
            player("z", "Zed", played = 2, won = 0, pf = 5, pa = 15),    // rank 4
        )
        val ranked = RankingEngine.rank(roster).associateBy { it.id }
        assertEquals(1, ranked.getValue("w").rank)
        // x and y tie on wins(2)+diff(+10) despite different losses -> both rank 2
        assertEquals(2, ranked.getValue("x").rank)
        assertEquals(2, ranked.getValue("y").rank)
        assertEquals(4, ranked.getValue("z").rank) // competition ranking skips 3
    }

    @Test fun `three-way tie yields 1 1 1 4`() {
        val roster = listOf(
            player("a", "Ana", played = 2, won = 2, pf = 22, pa = 10), // 2 wins, +12
            player("b", "Bea", played = 3, won = 2, pf = 24, pa = 12), // 2 wins, +12
            player("c", "Cara", played = 4, won = 2, pf = 26, pa = 14),// 2 wins, +12
            player("d", "Dan", played = 2, won = 1, pf = 10, pa = 12), // 1 win, -2
        )
        val ranked = RankingEngine.rank(roster)
        assertEquals(listOf(1, 1, 1, 4), ranked.map { it.rank })
    }

    @Test fun `duplicate names order by id but share rank number`() {
        val roster = listOf(
            player("id2", "Sam", played = 2, won = 2, pf = 20, pa = 10),
            player("id1", "Sam", played = 2, won = 2, pf = 20, pa = 10),
        )
        val ranked = RankingEngine.rank(roster)
        assertEquals(listOf("id1", "id2"), ranked.map { it.id }) // id breaks list order
        assertEquals(listOf(1, 1), ranked.map { it.rank })       // but rank number is shared
    }

    @Test fun `filters zero-game players and includes every status with games`() {
        val roster = listOf(
            player("a", "Ana", played = 0, won = 0),                                   // hidden
            player("r", "Rex", played = 2, won = 1, status = ParticipantStatus.RESTING),
            player("m", "Mia", played = 2, won = 1, status = ParticipantStatus.IN_MATCH),
            player("o", "Oli", played = 2, won = 1, status = ParticipantStatus.CHECKED_OUT),
        )
        val ranked = RankingEngine.rank(roster)
        assertEquals(setOf("r", "m", "o"), ranked.map { it.id }.toSet())
        val checkedOut = ranked.filter { it.isCheckedOut }.map { it.id }
        assertEquals(listOf("o"), checkedOut) // only CHECKED_OUT is flagged
    }

    @Test fun `empty roster yields empty list`() {
        assertEquals(emptyList<Any>(), RankingEngine.rank(emptyList()))
    }
}
```

- [ ] **Step 3: Run the test to verify it FAILS**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.RankingEngineTest"
```
Expected: FAIL — `RankingEngine` does not exist yet (unresolved reference).

- [ ] **Step 4: Implement `RankingEngine`**

`app/src/main/java/com/example/engine/RankingEngine.kt`:
```kotlin
package com.example.engine

import com.example.model.ParticipantStatus
import com.example.model.Player
import com.example.model.RankedPlayer

/**
 * Pure ranking for the Live Ranking board. Includes only players with >= 1 completed game
 * (any status). Orders by wins desc, then point differential desc, then name asc, then id asc;
 * assigns competition ranks so players tied on (wins, diff) share a rank number (1, 2, 2, 4).
 */
object RankingEngine {
    fun rank(roster: List<Player>): List<RankedPlayer> {
        val eligible = roster.filter { it.matchesPlayed >= 1 }
        val sorted = eligible.sortedWith(
            compareByDescending<Player> { it.matchesWon }
                .thenByDescending { it.totalPointsScored - it.totalPointsConceded }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
        return sorted.map { p ->
            val diff = p.totalPointsScored - p.totalPointsConceded
            val strictlyAhead = sorted.count { other ->
                val od = other.totalPointsScored - other.totalPointsConceded
                other.matchesWon > p.matchesWon ||
                    (other.matchesWon == p.matchesWon && od > diff)
            }
            RankedPlayer(
                id = p.id,
                rank = 1 + strictlyAhead,
                name = p.name,
                wins = p.matchesWon,
                losses = p.matchesPlayed - p.matchesWon,
                pointDiff = diff,
                isCheckedOut = p.status == ParticipantStatus.CHECKED_OUT,
            )
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it PASSES**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.RankingEngineTest"
```
Expected: PASS (all 6 tests).

- [ ] **Step 6: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/model/RankedPlayer.kt`
- `app/src/main/java/com/example/engine/RankingEngine.kt`
- `app/src/test/java/com/example/RankingEngineTest.kt`

Suggested message:
```
feat(ranking): pure RankingEngine with competition ranking

Ranks session players by wins, then point differential, then name, then id
(id decides order only, never the shared rank number). Filters out zero-game
players, includes every status, and flags checked-out players.
```

---

## Task 2: Pure paging & format helpers

**Files:**
- Create: `app/src/main/java/com/example/ui/screens/LiveRankingSupport.kt`
- Test: `app/src/test/java/com/example/ui/LiveRankingSupportTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/ui/LiveRankingSupportTest.kt`:
```kotlin
package com.example.ui

import com.example.ui.screens.formatDiff
import com.example.ui.screens.nextPage
import com.example.ui.screens.pageCountFor
import com.example.ui.screens.rowsPerPage
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRankingSupportTest {

    @Test fun `rowsPerPage floors to capacity and never overflows`() {
        assertEquals(5, rowsPerPage(availableHeightPx = 500, rowHeightPx = 100)) // exact fit
        assertEquals(5, rowsPerPage(availableHeightPx = 560, rowHeightPx = 100)) // remainder, no overflow
    }

    @Test fun `rowsPerPage returns 1 for degenerate heights (defensive floor)`() {
        assertEquals(1, rowsPerPage(availableHeightPx = 40, rowHeightPx = 100)) // < one row
        assertEquals(1, rowsPerPage(availableHeightPx = 100, rowHeightPx = 0))  // guard div-by-zero
    }

    @Test fun `pageCountFor divides with ceiling and is at least 1`() {
        assertEquals(1, pageCountFor(itemCount = 0, rowsPerPage = 10))
        assertEquals(1, pageCountFor(itemCount = 10, rowsPerPage = 10))
        assertEquals(2, pageCountFor(itemCount = 11, rowsPerPage = 10))
    }

    @Test fun `nextPage advances and loops`() {
        assertEquals(1, nextPage(current = 0, pageCount = 3))
        assertEquals(0, nextPage(current = 2, pageCount = 3)) // wraps
        assertEquals(0, nextPage(current = 0, pageCount = 1)) // single page never moves
    }

    @Test fun `nextPage advances from the clamped page after a shrink`() {
        // rawPage was 4 on a 5-page board; pageCount just dropped to 2.
        // The displayed page is clamped to 1, and the next tick must advance from there to 0,
        // not compute (4+1)%2 == 1 and stall.
        assertEquals(0, nextPage(current = 4, pageCount = 2))
    }

    @Test fun `formatDiff signs positive, plain zero, intrinsic negative`() {
        assertEquals("+18", formatDiff(18))
        assertEquals("0", formatDiff(0))
        assertEquals("-3", formatDiff(-3))
    }
}
```

- [ ] **Step 2: Run the test to verify it FAILS**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.ui.LiveRankingSupportTest"
```
Expected: FAIL — the helper functions do not exist yet.

- [ ] **Step 3: Implement the helpers**

`app/src/main/java/com/example/ui/screens/LiveRankingSupport.kt`:
```kotlin
package com.example.ui.screens

import kotlin.math.max

/**
 * Max fully-readable rows that fit in [availableHeightPx] at a fixed [rowHeightPx].
 * Plain floor with a defensive `max(1, ...)`: real displays always fit >= 1 row after
 * insets/header/footer, so capacity is >= 1 in practice; the degenerate case returns a single
 * (possibly clipped) row rather than an empty/crashing page.
 */
fun rowsPerPage(availableHeightPx: Int, rowHeightPx: Int): Int =
    if (rowHeightPx <= 0) 1 else max(1, availableHeightPx / rowHeightPx)

/** Fixed page count for [itemCount] rows at [rowsPerPage] per page (always >= 1). */
fun pageCountFor(itemCount: Int, rowsPerPage: Int): Int =
    if (itemCount <= 0 || rowsPerPage <= 0) 1 else (itemCount + rowsPerPage - 1) / rowsPerPage

/**
 * Next page index. Advances from the CLAMPED current page so a page-count shrink self-corrects:
 * if [current] is out of range for the new [pageCount], it advances from the last valid page.
 */
fun nextPage(current: Int, pageCount: Int): Int =
    if (pageCount <= 1) 0 else (current.coerceIn(0, pageCount - 1) + 1) % pageCount

/** Callout-style diff: positive prefixed with '+', zero as '0', negative intrinsic ('-3'). */
fun formatDiff(diff: Int): String = if (diff > 0) "+$diff" else diff.toString()
```

- [ ] **Step 4: Run the test to verify it PASSES**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.ui.LiveRankingSupportTest"
```
Expected: PASS (all 6 tests).

- [ ] **Step 5: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/ui/screens/LiveRankingSupport.kt`
- `app/src/test/java/com/example/ui/LiveRankingSupportTest.kt`

Suggested message:
```
feat(ranking): pure paging and diff-format helpers for the board

rowsPerPage/pageCountFor/nextPage/formatDiff as pure functions. nextPage
advances from the clamped page so a page-count shrink self-corrects instead
of stalling a tick.
```

---

## Task 3: Extract shared BoardDisplayHygiene (refactor)

Moves Court Call's keep-awake/cutout helper into a shared file so `LiveRankingScreen` can reuse it. No behavior change — existing Court Call tests must stay green.

**Files:**
- Create: `app/src/main/java/com/example/ui/screens/BoardDisplayHygiene.kt`
- Modify: `app/src/main/java/com/example/ui/screens/CourtCallScreen.kt` (remove the moved private helpers + their now-unused imports)

- [ ] **Step 1: Create the shared helper file**

`app/src/main/java/com/example/ui/screens/BoardDisplayHygiene.kt` (moved verbatim from `CourtCallScreen.kt:126-169`; same package, so callers need no import change):
```kotlin
package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Keeps the screen awake and hides the system bars while a big-screen board is displayed,
 * restoring both on pause/dispose. Shared by CourtCallScreen and LiveRankingScreen.
 */
@Composable
internal fun BoardDisplayHygiene() {
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

internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
```

- [ ] **Step 2: Remove the moved helpers from `CourtCallScreen.kt`**

Delete the `@Composable private fun BoardDisplayHygiene()` block (lines ~126-160) and the `private fun Context.findActivity()` block (lines ~162-169) from `app/src/main/java/com/example/ui/screens/CourtCallScreen.kt`. The remaining `BoardDisplayHygiene()` call at line ~71 now resolves to the shared `internal` function (same package). Then delete these now-unused imports from `CourtCallScreen.kt` (they were only used by the moved code):
```
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
```

- [ ] **Step 3: Run Court Call tests to verify NO regression**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCall*"
```
Expected: PASS — all existing Court Call tests (screen, page-hold, keep-awake) stay green; the refactor is behavior-preserving. If the compiler flags any of the removed imports as still used elsewhere in the file, re-add only that import.

- [ ] **Step 4: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/ui/screens/BoardDisplayHygiene.kt`
- `app/src/main/java/com/example/ui/screens/CourtCallScreen.kt`

Suggested message:
```
refactor(ui): extract BoardDisplayHygiene for reuse by both boards

Move Court Call's keep-awake/cutout helper (and findActivity) into a shared
file so Live Ranking can reuse it instead of duplicating. No behavior change.
```

---

## Task 4: RankingRow + LiveRankingScreen + Robolectric tests

**Files:**
- Create: `app/src/main/java/com/example/ui/components/RankingRow.kt`
- Create: `app/src/main/java/com/example/ui/screens/LiveRankingScreen.kt`
- Test: `app/src/test/java/com/example/ui/LiveRankingScreenTest.kt`

- [ ] **Step 1: Create the `RankingRow` component**

`app/src/main/java/com/example/ui/components/RankingRow.kt`:
```kotlin
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
            text = "${player.wins}–${player.losses}", // en-dash W–L
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
```

- [ ] **Step 2: Write the failing screen test**

`app/src/test/java/com/example/ui/LiveRankingScreenTest.kt`:
```kotlin
package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.model.OpenPlaySession
import com.example.model.ParticipantStatus
import com.example.model.Player
import com.example.model.RotationPolicy
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.LiveRankingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])
class LiveRankingScreenTest : RobolectricComposeTest() {

    private fun player(
        id: String, name: String, played: Int, won: Int,
        pf: Int = 0, pa: Int = 0,
        status: ParticipantStatus = ParticipantStatus.AVAILABLE,
    ) = Player(
        id = id, name = name, status = status,
        matchesPlayed = played, matchesWon = won,
        totalPointsScored = pf, totalPointsConceded = pa,
    )

    private fun sessionOf(players: List<Player>) = OpenPlaySession(
        id = "s1", name = "Test", rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
        courts = emptyList(), roster = players,
    )

    @Test fun emptyState_whenNoGamesPlayed() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(listOf(player("a", "Al", 0, 0), player("b", "Bo", 0, 0))))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm) } }

        rule.onNodeWithTag("live_ranking_empty").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_page_indicator").assertDoesNotExist()
    }

    @Test fun singlePage_showsRows_noPageIndicator() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(listOf(
            player("a", "Al", 3, 3, pf = 33, pa = 10),
            player("b", "Bo", 2, 1, pf = 20, pa = 18),
        )))
        // Override large so 2 players => exactly 1 page (no indicator), deterministically.
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm, rowsPerPageOverride = 10) } }

        rule.onNodeWithTag("live_ranking_row_a").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_page_indicator").assertDoesNotExist()
    }

    @Test fun longName_doesNotHideStats() {
        val vm = SessionViewModel(app())
        val longName = "Bartholomew Featherstonehaugh The Third Of Pickleball"
        vm.loadSessionForTest(sessionOf(listOf(player("a", longName, 4, 3, pf = 44, pa = 26))))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm) } }

        rule.onNodeWithText("3–1").assertIsDisplayed() // W–L still visible (3-1)
        rule.onNodeWithText("+18").assertIsDisplayed()      // diff still visible (44-26)
    }

    @Test fun checkedOutPlayer_showsLeftLabel() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(listOf(
            player("a", "Al", 3, 3, status = ParticipantStatus.CHECKED_OUT),
        )))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm) } }

        rule.onNodeWithTag("live_ranking_left_a").assertIsDisplayed()
    }

    // Five ranked players (wins 5..1) -> deterministic order top, p1, p2, p3, p4.
    private fun fivePlayers() = listOf(
        player("top", "Top", 5, 5, pf = 55, pa = 10),
        player("p1", "P1", 4, 4, pf = 44, pa = 12),
        player("p2", "P2", 3, 3, pf = 33, pa = 14),
        player("p3", "P3", 2, 2, pf = 22, pa = 16),
        player("p4", "P4", 1, 1, pf = 11, pa = 18),
    )
    // With rowsPerPageOverride = 2 and 5 players, pageCount is exactly 3:
    // page 0 = [top, p1], page 1 = [p2, p3], page 2 = [p4].

    @Test fun multiPage_advancesOnEachTick_readsLiveState() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(fivePlayers()))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm, rowsPerPageOverride = 2) } }

        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithText("Page 1 / 3").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_top").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(9_000) // -> page 1
        rule.onNodeWithText("Page 2 / 3").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_top").assertDoesNotExist()
        rule.onNodeWithTag("live_ranking_row_p2").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(9_000) // -> page 2 (proves it did not stall after one tick)
        rule.onNodeWithText("Page 3 / 3").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_p4").assertIsDisplayed()
    }

    @Test fun paging_loopsBackToFirstPage() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(fivePlayers()))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm, rowsPerPageOverride = 2) } }

        rule.mainClock.advanceTimeBy(50)
        rule.mainClock.advanceTimeBy(27_000) // 3 ticks: page 0 -> 1 -> 2 -> 0
        rule.onNodeWithText("Page 1 / 3").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_top").assertIsDisplayed()
    }

    @Test fun shrink_clampsDisplayedPage_thenAdvancesFromIt() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(fivePlayers()))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm, rowsPerPageOverride = 2) } }

        rule.mainClock.advanceTimeBy(50)
        rule.mainClock.advanceTimeBy(18_000) // 2 ticks -> page 2 of 3
        rule.onNodeWithText("Page 3 / 3").assertIsDisplayed()

        // Shrink to 3 players -> pageCount 2. rawPage is 2; the render must CLAMP to page 1
        // ("Page 2 / 2") and still show rows (not blank).
        rule.runOnUiThread { vm.loadSessionForTest(sessionOf(fivePlayers().take(3))) }
        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithText("Page 2 / 2").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_p2").assertIsDisplayed() // page 1 of [top,p1,p2] = [p2]

        // Next tick must advance from the DISPLAYED (clamped) page: nextPage(2, 2) -> 0.
        rule.mainClock.advanceTimeBy(9_000)
        rule.onNodeWithText("Page 1 / 2").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_top").assertIsDisplayed()
    }

    @Test fun measuredPaging_smoke_multiPageAndAdvance() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        // No override: exercise the real rowsPerPage measurement. 200 rows cannot fit one screen.
        val filler = (1..200).map { player("p$it", "Player $it", played = 2, won = 1, pf = 15, pa = 15) }
        vm.loadSessionForTest(sessionOf(listOf(player("top", "Top", 5, 5, pf = 55, pa = 10)) + filler))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm) } }

        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithTag("live_ranking_page_indicator").assertIsDisplayed()
        rule.onNodeWithTag("live_ranking_row_top").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(9_000)
        rule.onNodeWithTag("live_ranking_row_top").assertDoesNotExist()
    }

    @Test fun close_navigatesToHub() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(listOf(player("a", "Al", 1, 1))))
        vm.navigateTo(AppScreen.LiveRanking) // start on the board so returning to Hub is an observable change
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm) } }

        rule.onNodeWithTag("live_ranking_close").performClick()
        rule.runOnIdle { assertEquals(AppScreen.SessionHub, vm.currentScreen.value) }
    }

    @Test fun timerNotRestarted_whenDataChangesButPageCountUnchanged() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(sessionOf(fivePlayers()))
        rule.setContent { MyApplicationTheme { LiveRankingScreen(viewModel = vm, rowsPerPageOverride = 2) } }

        rule.mainClock.advanceTimeBy(50)
        rule.mainClock.advanceTimeBy(8_000) // still page 0, before the 9s deadline
        rule.onNodeWithText("Page 1 / 3").assertIsDisplayed()

        // Emit a data change that keeps the player count (pageCount stays 3). If the advance
        // LaunchedEffect were keyed on the roster, this would CANCEL and restart the 9s delay,
        // pushing the next advance out to ~17s. Keyed on pageCount, the running delay is untouched.
        val bumped = fivePlayers().map {
            if (it.id == "p4") it.copy(totalPointsScored = it.totalPointsScored + 1) else it
        }
        rule.runOnUiThread { vm.loadSessionForTest(sessionOf(bumped)) }

        rule.mainClock.advanceTimeBy(1_500) // cross the ORIGINAL 9s deadline (~9.55s total)
        rule.onNodeWithText("Page 2 / 3").assertIsDisplayed()          // advanced on schedule -> not restarted
        rule.onNodeWithTag("live_ranking_row_top").assertDoesNotExist()
    }
}
```

- [ ] **Step 3: Run the test to verify it FAILS**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.ui.LiveRankingScreenTest"
```
Expected: FAIL — `LiveRankingScreen` does not exist yet.

- [ ] **Step 4: Implement `LiveRankingScreen`**

`app/src/main/java/com/example/ui/screens/LiveRankingScreen.kt`:
```kotlin
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
```

- [ ] **Step 5: Run the test to verify it PASSES**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.ui.LiveRankingScreenTest"
```
Expected: PASS (all 10 tests).

- [ ] **Step 6: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/ui/components/RankingRow.kt`
- `app/src/main/java/com/example/ui/screens/LiveRankingScreen.kt`
- `app/src/test/java/com/example/ui/LiveRankingScreenTest.kt`

Suggested message:
```
feat(ui): Live Ranking TV board screen

Full-bleed board with keep-awake, self-owned insets, a constant-height footer
slot, and a pageCount-keyed 9s auto-advance that reads live snapshot state.
Empty state, rank-1 accent, "Left" label for checked-out players, name ellipsis.
```

---

## Task 5: Navigation wiring (AppScreen + MainActivity + Hub button)

**Files:**
- Modify: `app/src/main/java/com/example/viewmodel/SessionViewModel.kt` (AppScreen sealed class, ~line 22-27)
- Modify: `app/src/main/java/com/example/MainActivity.kt` (~line 47-68)
- Modify: `app/src/main/java/com/example/ui/screens/SessionHubScreen.kt` (~line 120)
- Test: `app/src/test/java/com/example/ui/SessionHubLiveRankingTest.kt`

- [ ] **Step 1: Write the failing wiring test**

`app/src/test/java/com/example/ui/SessionHubLiveRankingTest.kt`:
```kotlin
package com.example.ui

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.model.OpenPlaySession
import com.example.model.Player
import com.example.model.RotationPolicy
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.SessionHubScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])
class SessionHubLiveRankingTest : RobolectricComposeTest() {

    @Test fun hubButton_opensLiveRanking() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(
            OpenPlaySession(
                id = "s1", name = "Test", rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
                courts = emptyList(),
                roster = listOf(Player(id = "a", name = "Al", matchesPlayed = 1, matchesWon = 1)),
            )
        )
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }

        rule.onNodeWithTag("live_ranking_button").performClick()
        assertEquals(AppScreen.LiveRanking, vm.currentScreen.value)
    }
}
```

- [ ] **Step 2: Run the test to verify it FAILS**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.ui.SessionHubLiveRankingTest"
```
Expected: FAIL — `AppScreen.LiveRanking` does not exist / no `live_ranking_button` node.

- [ ] **Step 3: Add `AppScreen.LiveRanking`**

In `app/src/main/java/com/example/viewmodel/SessionViewModel.kt`, add to the `AppScreen` sealed class (next to `object CourtCall`):
```kotlin
    object LiveRanking : AppScreen()
```

- [ ] **Step 4: Wire `MainActivity` (full-bleed branch + exhaustive `when`)**

In `app/src/main/java/com/example/MainActivity.kt`, add the import:
```kotlin
import com.example.ui.screens.LiveRankingScreen
```
Replace the `Crossfade` body (lines ~47-68) so Live Ranking renders full-bleed like Court Call, and the inner `when` stays exhaustive:
```kotlin
    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
        when {
            screen is AppScreen.CourtCall -> CourtCallScreen(viewModel = viewModel)
            screen is AppScreen.LiveRanking -> LiveRankingScreen(viewModel = viewModel)
            else -> {
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
                        is AppScreen.LiveRanking -> Unit // handled above; keeps `when` exhaustive
                    }
                }
            }
        }
    }
```

- [ ] **Step 5: Add the Hub entry button**

In `app/src/main/java/com/example/ui/screens/SessionHubScreen.kt`, add the icon import near the other icon imports:
```kotlin
import androidx.compose.material.icons.filled.Leaderboard
```
Then add this `IconButton` immediately before the existing Court Call button (the one with `testTag("court_call_button")`, ~line 120):
```kotlin
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.LiveRanking) },
                        modifier = Modifier.testTag("live_ranking_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Leaderboard,
                            contentDescription = "Live Ranking display",
                            tint = tokens.textPrimary
                        )
                    }
```
(If `Icons.Default.Leaderboard` does not resolve in this icon set, use `Icons.Default.EmojiEvents` with the same import path `androidx.compose.material.icons.filled.EmojiEvents`.)

- [ ] **Step 6: Run the wiring test to verify it PASSES**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.ui.SessionHubLiveRankingTest"
```
Expected: PASS.

- [ ] **Step 7: Run the full suite to confirm nothing regressed**

Run:
```bash
./gradlew :app:testDebugUnitTest
```
Expected: PASS — all new tests plus the pre-existing suite (including Court Call) are green.

- [ ] **Step 8: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/viewmodel/SessionViewModel.kt`
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/ui/screens/SessionHubScreen.kt`
- `app/src/test/java/com/example/ui/SessionHubLiveRankingTest.kt`

Suggested message:
```
feat(ui): wire Live Ranking into navigation and the Session Hub

Add AppScreen.LiveRanking, render it full-bleed in MainActivity like Court
Call, and add a Session Hub button to open the board.
```

---

## Notes & non-goals (from the spec)

- **No persistence/DAO/schema changes.** The board derives entirely from the reactive `session.roster`.
- **Refresh vs navigation:** `completeMatch` returns to the Session Hub (`SessionViewModel.kt:385`), so the board is not continuously on-screen across a completion (same as Court Call). It shows the latest standings whenever opened and live-recomposes for emissions while visible. Not changed here.
- **Scope-A boundaries:** no cross-session/all-time ranking (that's the Session History feature), no editing stats from this screen, no phone-tuned layout, rank-1 accent only (no podium).
- Before `/create-pr`, run the full suite (`./gradlew :app:testDebugUnitTest`) and `./gradlew assembleDebug`. CI runs `:app:testDebugUnitTest --tests PickleballEngineTest` + `assembleDebug`.
