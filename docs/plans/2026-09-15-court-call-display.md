# Court Call Big-Screen Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the subagent-build skill (`/subagent-build`) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `CourtCall`, a read-only big-screen projection of the live open-play session for a venue TV (mirrored from the phone). It renders each court's derived display state (UP NOW / READY / LIVE / FINAL / OPEN / PAUSED) with exactly one von-Restorff standout (the lowest-`courtId` ready court), a distance-legibility contract (court number largest), a fixed high-contrast dark palette independent of `ThemeMode`, deterministic viewport-driven pagination with ~10s auto-cycle on overflow, a brief transition highlight, and screen keep-awake + immersive full-screen while it is showing — all with no domain/Room/`SessionViewModel` state changes.

**Architecture:** A read-only Compose projection over the existing `viewModel.session: StateFlow<OpenPlaySession?>`. A new `object CourtCall : AppScreen()` route reached from a Session Hub top-bar action. `CourtCallScreen` forces `DarkTokens` into its subtree via `CompositionLocalProvider(LocalPickItTokens provides DarkTokens)`, paints `DarkTokens.canvas` full-bleed **outside** `MainActivity`'s themed, `safeDrawingPadding` Surface, derives one of six states per court from already-live data (pure functions in `CourtCallState.kt`), and lays courts in fixed `courtId` order into a responsive `BoxWithConstraints` grid of `CourtCallTile`s. Ephemeral view state only (page index, auto-cycle timer, previous-state snapshot) lives in `remember`/`rememberSaveable` and never feeds back to the VM.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Robolectric + Compose UI Test, Roborazzi; Gradle.

---

## Conventions & commands (read once)

- **Unit/behavior tests:** `./gradlew :app:testDebugUnitTest --tests "com.example.<TestClass>"`. No wrapper is committed — if `./gradlew` is missing locally, generate it (`gradle wrapper`) or rely on CI; every step still shows the exact command + expected result.
- **Roborazzi:** record baselines `./gradlew :app:recordRoborazziDebug`; verify `./gradlew :app:verifyRoborazziDebug`.
- **Commit** via the `commit` skill (Conventional Commits, **no Claude co-author trailer**). Each task ends with a commit step showing the exact `git add` + `git commit -m` to run.
- All new Compose files: package under `com.example.ui.*`; named exports; keep logic out of the VM (the board only reads it).
- **Verified codebase facts** (do not re-derive): `DarkTokens` and `LightTokens` are top-level `val`s of type `PickItTokens` in `ui/theme/Tokens.kt` (NOT objects); `LocalPickItTokens` is a `staticCompositionLocalOf { DarkTokens }`. `Court(id:Int, name:String, status:CourtStatus, currentMatch:Match?)`; `enum CourtStatus { AVAILABLE, IN_PROGRESS, PAUSED }` (no `FINAL`); `Match(... scoreA, scoreB, isCompleted, teamA, teamB, targetScore ...)`; `Team.playerNames()`; `OpenPlaySession(... courts, roster, activeRecommendations:Map<Int,RotationRecommendation>, isPaused ...)`. `viewModel.loadSessionForTest(session)` injects synchronously and survives `init`. `Fixtures` (`app/src/test/java/com/example/fixtures/Fixtures.kt`) already has `inProgressSession()`, `twoReadyCourtsSession()`, `KNOWN_PLAYER_ID`, and private `player(i)` / `rec(courtId, p)` helpers.

---

## File Structure

**Create (main):**
- `app/src/main/java/com/example/ui/screens/CourtCallState.kt` — `enum class CourtCallState`, `courtDisplayState(...)`, `primaryReadyCourtId(...)`.
- `app/src/main/java/com/example/ui/components/CourtCallTile.kt` — one court tile.
- `app/src/main/java/com/example/ui/screens/CourtCallScreen.kt` — the board container (grid, pagination, banners, keep-awake).

**Create (test):**
- `app/src/test/java/com/example/ui/CourtCallRouteTest.kt`
- `app/src/test/java/com/example/ui/CourtCallStateTest.kt`
- `app/src/test/java/com/example/ui/CourtCallTileTest.kt`
- `app/src/test/java/com/example/ui/CourtCallScreenTest.kt`
- `app/src/test/java/com/example/ui/CourtCallPaginationTest.kt`
- `app/src/test/java/com/example/ui/CourtCallKeepAwakeTest.kt`
- `app/src/test/java/com/example/ui/CourtCallTransitionTest.kt`
- `app/src/test/java/com/example/screenshots/CourtCallScreenshotTest.kt`

**Modify:**
- `app/src/main/java/com/example/viewmodel/SessionViewModel.kt` — add `object CourtCall : AppScreen()`.
- `app/src/main/java/com/example/ui/screens/SessionHubScreen.kt` — add the `court_call_button` top-bar action.
- `app/src/main/java/com/example/MainActivity.kt` — add the `CourtCall` branch; restructure so it renders full-bleed outside the themed Surface (Task 6).
- `app/src/test/java/com/example/fixtures/Fixtures.kt` — add `boardSession()` and `manyCourtsSession()`.

---

## Task 1: Route + Hub entry + minimal `CourtCallScreen` stub

Adds `object CourtCall` to `AppScreen`, the Hub top-bar action, a minimal board stub, and the `MainActivity` `when` branch (inside the existing Surface for now — Task 6 moves it full-bleed).

**Files:**
- Modify: `viewmodel/SessionViewModel.kt`, `ui/screens/SessionHubScreen.kt`, `MainActivity.kt`
- Create: `ui/screens/CourtCallScreen.kt`
- Test: `ui/CourtCallRouteTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/ui/CourtCallRouteTest.kt`:

```kotlin
package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.fixtures.Fixtures
import com.example.ui.screens.CourtCallScreen
import com.example.ui.screens.SessionHubScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CourtCallRouteTest {
    @get:Rule val rule = createComposeRule()

    private fun app() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun hubButton_navigatesToCourtCall() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(Fixtures.twoReadyCourtsSession())
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }

        rule.onNodeWithTag("court_call_button").assertIsDisplayed()
        rule.onNodeWithTag("court_call_button").performClick()
        rule.runOnIdle { assertEquals(AppScreen.CourtCall, vm.currentScreen.value) }
    }

    @Test fun nullSessionHub_hasNoCourtCallButton() {
        val vm = SessionViewModel(app())   // fresh install -> session == null -> top-bar-less Scaffold
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }
        rule.onNodeWithTag("court_call_button").assertDoesNotExist()
    }

    @Test fun closeButton_returnsToHub() {
        val vm = SessionViewModel(app())
        vm.navigateTo(AppScreen.CourtCall)
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = vm) } }

        rule.onNodeWithTag("court_call_close").assertIsDisplayed()
        rule.onNodeWithTag("court_call_close").performClick()
        rule.runOnIdle { assertEquals(AppScreen.SessionHub, vm.currentScreen.value) }
    }
}
```

> The close affordance renders in every board state (including the null-session empty state), so this test needs no injected session. `BackHandler` (system-back exit) is wired in the same screen; it is exercised by the real-Activity flow in Task 6 rather than re-asserted here. Note: `BackHandler` reads `LocalOnBackPressedDispatcherOwner.current` — `createComposeRule()` is backed by a `ComponentActivity`, which provides that owner via `ViewTreeOnBackPressedDispatcherOwner`, so rendering `CourtCallScreen` under the plain compose rule does not crash.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallRouteTest"`
Expected: FAIL — `AppScreen.CourtCall` unresolved, no `court_call_button` node, and no `court_call_close` node.

- [ ] **Step 3: Add the `CourtCall` route**

In `viewmodel/SessionViewModel.kt`, add one line to the sealed class (after `StandaloneScoreboard`):

```kotlin
sealed class AppScreen {
    object Setup : AppScreen()
    object SessionHub : AppScreen()
    data class LiveScoreboard(val courtId: Int) : AppScreen()
    data class StandaloneScoreboard(val match: Match) : AppScreen()
    object CourtCall : AppScreen()
}
```

- [ ] **Step 4: Add the Hub top-bar action**

In `ui/screens/SessionHubScreen.kt`, inside the non-null `TopAppBar`'s `actions = { ... }` block, insert this `IconButton` **before** the existing `session_settings_button` `IconButton` (the `Icons.Default.*` wildcard import already covers `Cast`):

```kotlin
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.CourtCall) },
                        modifier = Modifier.testTag("court_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = "Court Call display",
                            tint = tokens.textPrimary
                        )
                    }
```

No new imports are needed (`AppScreen`, `Icons.Default.*`, `testTag`, `tokens` are all already in scope).

- [ ] **Step 5: Create the minimal `CourtCallScreen` stub**

Create `app/src/main/java/com/example/ui/screens/CourtCallScreen.kt`:

```kotlin
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

    // System-back exit (no BackHandler exists anywhere else in the app, and the board is a
    // single-Activity screen — without this, back would finish the Activity and leave the app).
    BackHandler { viewModel.navigateTo(AppScreen.SessionHub) }

    CompositionLocalProvider(LocalPickItTokens provides DarkTokens) {
        val tokens = LocalPickItTokens.current
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(tokens.canvas)
                .testTag("court_call_board")
        ) {
            // Fleshed out in Tasks 4–7. `session` is read here so the stub compiles
            // against the same flow the real board uses.
            @Suppress("UNUSED_EXPRESSION") session

            // The only touch target on the board: a small, deliberately low-contrast corner close.
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
```

- [ ] **Step 6: Wire the `MainActivity` branch**

In `MainActivity.kt`, add the import and the `when` branch inside `PickleballAppContent`'s `Crossfade` (Task 6 restructures this to render full-bleed; for now it renders inside the existing Surface):

Add import:
```kotlin
import com.example.ui.screens.CourtCallScreen
```
Add branch (after the `AppScreen.Setup` branch):
```kotlin
            is AppScreen.CourtCall -> {
                CourtCallScreen(viewModel = viewModel)
            }
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallRouteTest"`
Expected: PASS (all three tests: `hubButton_navigatesToCourtCall`, `nullSessionHub_hasNoCourtCallButton`, `closeButton_returnsToHub`).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/viewmodel/SessionViewModel.kt \
        app/src/main/java/com/example/ui/screens/SessionHubScreen.kt \
        app/src/main/java/com/example/MainActivity.kt \
        app/src/main/java/com/example/ui/screens/CourtCallScreen.kt \
        app/src/test/java/com/example/ui/CourtCallRouteTest.kt
git commit -m "feat(court-call): add CourtCall route + Hub entry action + screen stub"
```

---

## Task 2: Derived display-state (pure, unit-tested)

A pure function that maps a `Court` (+ whether it has a rec, + whether it is the primary ready court) to one of six `CourtCallState`s, plus a selector for the single standout. No Android/Compose deps → plain JUnit.

**Files:**
- Create: `ui/screens/CourtCallState.kt`
- Test: `ui/CourtCallStateTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/ui/CourtCallStateTest.kt`:

```kotlin
package com.example.ui

import com.example.engine.PickleballGameEngine
import com.example.model.Court
import com.example.model.CourtStatus
import com.example.model.OpenPlaySession
import com.example.model.Player
import com.example.model.RotationRecommendation
import com.example.model.Team
import com.example.model.TeamId
import com.example.ui.screens.CourtCallState
import com.example.ui.screens.courtDisplayState
import com.example.ui.screens.primaryReadyCourtId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourtCallStateTest {

    private fun p(i: Int) = Player(id = "p$i", name = "Player $i")
    private fun team(a: Int, b: Int) = Team(TeamId.TEAM_A, p(a), p(b))
    private fun teamB(a: Int, b: Int) = Team(TeamId.TEAM_B, p(a), p(b))

    private fun liveMatch(courtId: Int, completed: Boolean) =
        PickleballGameEngine.createMatch(
            courtId = courtId, teamA = team(0, 1), teamB = teamB(2, 3),
        ).copy(scoreA = 6, scoreB = 4, isCompleted = completed)

    private fun rec(courtId: Int) = RotationRecommendation(
        courtId = courtId, teamA = team(0, 1), teamB = teamB(2, 3),
        departingPlayers = emptyList(), retainedPlayers = emptyList(),
        incomingPlayers = emptyList(), primaryReason = "Next up",
        detailedReason = listOf("because"),
    )

    @Test fun paused_takesTopPrecedence() {
        val court = Court(id = 1, name = "C1", status = CourtStatus.PAUSED, currentMatch = liveMatch(1, true))
        assertEquals(CourtCallState.PAUSED, courtDisplayState(court, hasRecommendation = true, isPrimaryReady = true))
    }

    @Test fun completedMatch_isFinal_evenWhileInProgress() {
        val court = Court(id = 1, name = "C1", status = CourtStatus.IN_PROGRESS, currentMatch = liveMatch(1, true))
        assertEquals(CourtCallState.FINAL, courtDisplayState(court, hasRecommendation = false, isPrimaryReady = false))
    }

    @Test fun inProgressNotCompleted_isLive() {
        val court = Court(id = 1, name = "C1", status = CourtStatus.IN_PROGRESS, currentMatch = liveMatch(1, false))
        assertEquals(CourtCallState.LIVE, courtDisplayState(court, hasRecommendation = false, isPrimaryReady = false))
    }

    @Test fun availableWithRecAndPrimary_isUpNow() {
        val court = Court(id = 1, name = "C1", status = CourtStatus.AVAILABLE)
        assertEquals(CourtCallState.UP_NOW, courtDisplayState(court, hasRecommendation = true, isPrimaryReady = true))
    }

    @Test fun availableWithRecNotPrimary_isReady() {
        val court = Court(id = 2, name = "C2", status = CourtStatus.AVAILABLE)
        assertEquals(CourtCallState.READY, courtDisplayState(court, hasRecommendation = true, isPrimaryReady = false))
    }

    @Test fun availableNoRec_isOpen() {
        val court = Court(id = 3, name = "C3", status = CourtStatus.AVAILABLE)
        assertEquals(CourtCallState.OPEN, courtDisplayState(court, hasRecommendation = false, isPrimaryReady = false))
    }

    @Test fun primaryReadyCourtId_isLowestAvailableCourtWithRec() {
        val session = OpenPlaySession(
            id = "s", name = "s",
            courts = listOf(
                Court(id = 1, name = "C1", status = CourtStatus.IN_PROGRESS, currentMatch = liveMatch(1, false)),
                Court(id = 2, name = "C2", status = CourtStatus.AVAILABLE),  // has rec -> candidate
                Court(id = 3, name = "C3", status = CourtStatus.AVAILABLE),  // has rec -> candidate
                Court(id = 4, name = "C4", status = CourtStatus.AVAILABLE),  // no rec
            ),
            activeRecommendations = mapOf(2 to rec(2), 3 to rec(3)),
        )
        assertEquals(2, primaryReadyCourtId(session))
    }

    @Test fun primaryReadyCourtId_isNullWhenNoReadyCourt() {
        val session = OpenPlaySession(
            id = "s", name = "s",
            courts = listOf(Court(id = 1, name = "C1", status = CourtStatus.AVAILABLE)),
        )
        assertNull(primaryReadyCourtId(session))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallStateTest"`
Expected: FAIL — `CourtCallState`, `courtDisplayState`, `primaryReadyCourtId` unresolved.

- [ ] **Step 3: Write the minimal implementation**

Create `app/src/main/java/com/example/ui/screens/CourtCallState.kt`:

```kotlin
package com.example.ui.screens

import com.example.model.Court
import com.example.model.CourtStatus
import com.example.model.OpenPlaySession

/** The six mutually-exclusive display states a court tile can render on the Court Call board. */
enum class CourtCallState { UP_NOW, READY, LIVE, FINAL, OPEN, PAUSED }

/**
 * Derive the display state for one court from already-live data. Precedence (top-down):
 * PAUSED, then FINAL (completed match, even while status is still IN_PROGRESS), then LIVE
 * (in-progress, not completed), then for an AVAILABLE court: UP_NOW if it is the single
 * primary ready court, READY if it has a recommendation, else OPEN.
 */
fun courtDisplayState(
    court: Court,
    hasRecommendation: Boolean,
    isPrimaryReady: Boolean,
): CourtCallState = when {
    court.status == CourtStatus.PAUSED -> CourtCallState.PAUSED
    court.currentMatch?.isCompleted == true -> CourtCallState.FINAL
    court.status == CourtStatus.IN_PROGRESS -> CourtCallState.LIVE
    court.status == CourtStatus.AVAILABLE && isPrimaryReady -> CourtCallState.UP_NOW
    court.status == CourtStatus.AVAILABLE && hasRecommendation -> CourtCallState.READY
    else -> CourtCallState.OPEN
}

/**
 * The single von-Restorff standout: the lowest-`courtId` court that is AVAILABLE and has a
 * recommendation. `null` when no court is ready. (`activeRecommendations` is an unordered Map,
 * so selection sorts by `courtId`.)
 */
fun primaryReadyCourtId(session: OpenPlaySession): Int? =
    session.courts
        .filter { it.status == CourtStatus.AVAILABLE && session.activeRecommendations[it.id] != null }
        .minByOrNull { it.id }
        ?.id
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallStateTest"`
Expected: PASS (all eight).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/ui/screens/CourtCallState.kt \
        app/src/test/java/com/example/ui/CourtCallStateTest.kt
git commit -m "feat(court-call): derive six per-court display states + one-standout selector"
```

---

## Task 3: `CourtCallTile`

One court tile: court number is the largest element; a state label with consistent color + shape (never color alone); names-only for idle/next tiles (no reasons); live/final score; capped single-line names. Per-state `testTag` on the outer tile and a per-court `testTag` on the number (two tags must sit on **different** nodes — a second `Modifier.testTag` on the same node replaces the first).

**Files:**
- Create: `ui/components/CourtCallTile.kt`
- Test: `ui/CourtCallTileTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/ui/CourtCallTileTest.kt`:

```kotlin
package com.example.ui

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import com.example.engine.PickleballGameEngine
import com.example.model.Court
import com.example.model.CourtStatus
import com.example.model.Player
import com.example.model.RotationRecommendation
import com.example.model.Team
import com.example.model.TeamId
import com.example.ui.components.CourtCallTile
import com.example.ui.screens.CourtCallState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CourtCallTileTest {
    @get:Rule val rule = createComposeRule()

    private fun p(i: Int) = Player(id = "p$i", name = "Player $i")
    private fun rec(courtId: Int) = RotationRecommendation(
        courtId = courtId, teamA = Team(TeamId.TEAM_A, p(0), p(1)),
        teamB = Team(TeamId.TEAM_B, p(2), p(3)),
        departingPlayers = emptyList(), retainedPlayers = emptyList(),
        incomingPlayers = emptyList(), primaryReason = "Next up",
        detailedReason = listOf("because A beats B"),
    )

    @Test fun liveTile_showsScoreAndPerCourtTag() {
        val match = PickleballGameEngine.createMatch(
            courtId = 3, teamA = Team(TeamId.TEAM_A, p(0), p(1)),
            teamB = Team(TeamId.TEAM_B, p(2), p(3)),
        ).copy(scoreA = 6, scoreB = 4)
        val court = Court(id = 3, name = "Court 3", status = CourtStatus.IN_PROGRESS, currentMatch = match)
        rule.setContent { MyApplicationTheme { CourtCallTile(court = court, state = CourtCallState.LIVE, recommendation = null) } }

        rule.onAllNodesWithTag("court_call_tile_live").assertCountEquals(1)
        rule.onNodeWithTag("court_call_tile_3").assertIsDisplayed()   // per-court tag on the number
        rule.onNodeWithText("6").assertIsDisplayed()
        rule.onNodeWithText("4").assertIsDisplayed()
    }

    @Test fun upNowTile_showsNamesOnly_noReasons() {
        val court = Court(id = 1, name = "Court 1", status = CourtStatus.AVAILABLE)
        rule.setContent { MyApplicationTheme { CourtCallTile(court = court, state = CourtCallState.UP_NOW, recommendation = rec(1)) } }

        rule.onAllNodesWithTag("court_call_tile_up_now").assertCountEquals(1)
        rule.onNodeWithText("Player 0", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Player 3", substring = true).assertIsDisplayed()
        // Reasons must never appear on the board:
        rule.onNodeWithText("Next up").assertDoesNotExist()
        rule.onNodeWithText("because A beats B").assertDoesNotExist()
    }

    @Test fun finalTile_showsFinalScore() {
        val match = PickleballGameEngine.createMatch(
            courtId = 4, teamA = Team(TeamId.TEAM_A, p(0), p(1)),
            teamB = Team(TeamId.TEAM_B, p(2), p(3)),
        ).copy(scoreA = 11, scoreB = 7, isCompleted = true)
        val court = Court(id = 4, name = "Court 4", status = CourtStatus.IN_PROGRESS, currentMatch = match)
        rule.setContent { MyApplicationTheme { CourtCallTile(court = court, state = CourtCallState.FINAL, recommendation = null) } }

        rule.onAllNodesWithTag("court_call_tile_final").assertCountEquals(1)
        rule.onNodeWithText("11").assertIsDisplayed()
        rule.onNodeWithText("7").assertIsDisplayed()
    }

    @Test fun pausedTile_rendersPausedTag() {
        val court = Court(id = 6, name = "Court 6", status = CourtStatus.PAUSED)
        rule.setContent { MyApplicationTheme { CourtCallTile(court = court, state = CourtCallState.PAUSED, recommendation = null) } }
        rule.onAllNodesWithTag("court_call_tile_paused").assertCountEquals(1)
    }
}
```

> The two idle-tile assertions rely on the **per-player** cap: each of "Player 0" and "Player 3" is capped on its own (see `cappedMatchup` in Step 3), so both survive and Step 4's PASS is truthful. Capping the joined string would have dropped "Player 3" and failed the test.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallTileTest"`
Expected: FAIL — `CourtCallTile` unresolved.

- [ ] **Step 3: Write the minimal implementation**

Create `app/src/main/java/com/example/ui/components/CourtCallTile.kt`:

```kotlin
package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Court
import com.example.model.RotationRecommendation
import com.example.model.Team
import com.example.ui.screens.CourtCallState
import com.example.ui.theme.LocalPickItTokens

/** Longest single player name that renders before it is capped with an ellipsis. */
private const val NAME_CAP = 14

private fun capName(name: String): String =
    if (name.length <= NAME_CAP) name else name.take(NAME_CAP - 1) + "…"

/**
 * A team's matchup label. Each player's name is capped INDIVIDUALLY so both survive — capping the
 * joined "A & B" string would drop the second player entirely on long names.
 */
private fun cappedMatchup(team: Team): String =
    "${capName(team.player1.name)} & ${capName(team.player2.name)}"

private fun stateTag(state: CourtCallState) = when (state) {
    CourtCallState.UP_NOW -> "court_call_tile_up_now"
    CourtCallState.READY -> "court_call_tile_ready"
    CourtCallState.LIVE -> "court_call_tile_live"
    CourtCallState.FINAL -> "court_call_tile_final"
    CourtCallState.OPEN -> "court_call_tile_open"
    CourtCallState.PAUSED -> "court_call_tile_paused"
}

private fun stateLabel(state: CourtCallState) = when (state) {
    CourtCallState.UP_NOW -> "UP NOW"
    CourtCallState.READY -> "READY"
    CourtCallState.LIVE -> "LIVE"
    CourtCallState.FINAL -> "FINAL"
    CourtCallState.OPEN -> "OPEN"
    CourtCallState.PAUSED -> "PAUSED"
}

@Composable
fun CourtCallTile(
    court: Court,
    state: CourtCallState,
    recommendation: RotationRecommendation?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPickItTokens.current
    val shape = RoundedCornerShape(tokens.radiusLg)

    // Per-state fill / border / text (color + shape + label, never color alone).
    val fill: Color = when (state) {
        CourtCallState.UP_NOW -> tokens.accent
        CourtCallState.FINAL -> tokens.surfaceInset
        else -> tokens.surface
    }
    val borderColor: Color = when (state) {
        CourtCallState.UP_NOW -> tokens.accent
        CourtCallState.READY -> tokens.accent
        CourtCallState.LIVE -> tokens.statusLive
        CourtCallState.PAUSED -> tokens.statusPaused
        else -> tokens.borderSubtle
    }
    val numberColor: Color = if (state == CourtCallState.UP_NOW) tokens.onAccent else tokens.textPrimary
    val labelColor: Color = when (state) {
        CourtCallState.UP_NOW -> tokens.onAccent
        CourtCallState.LIVE -> tokens.statusLive
        CourtCallState.FINAL, CourtCallState.OPEN -> tokens.textMuted
        CourtCallState.PAUSED -> tokens.statusPaused
        else -> tokens.textAccent
    }
    val nameColor: Color = if (state == CourtCallState.UP_NOW) tokens.onAccent else tokens.textSecondary

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(fill, shape)
            .border(3.dp, borderColor, shape)
            .testTag(stateTag(state))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Court NUMBER is the largest element on every tile (distance anchor).
        Text(
            text = court.id.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = numberColor,
            modifier = Modifier.testTag("court_call_tile_${court.id}"),
        )
        Text(
            text = stateLabel(state),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = labelColor,
        )

        when (state) {
            CourtCallState.LIVE, CourtCallState.FINAL -> {
                val match = court.currentMatch
                if (match != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = match.scoreA.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = if (state == CourtCallState.LIVE) tokens.teamA else tokens.textMuted,
                        )
                        Text(
                            text = "  –  ",
                            style = MaterialTheme.typography.headlineSmall,
                            color = tokens.textMuted,
                        )
                        Text(
                            text = match.scoreB.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = if (state == CourtCallState.LIVE) tokens.teamB else tokens.textMuted,
                        )
                    }
                    if (state == CourtCallState.LIVE) {
                        Text(
                            text = cappedMatchup(match.teamA),
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.teamA, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = cappedMatchup(match.teamB),
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.teamB, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            CourtCallState.UP_NOW, CourtCallState.READY -> {
                // Names-only. Never render primaryReason / detailedReason.
                recommendation?.let { r ->
                    Text(
                        text = cappedMatchup(r.teamA),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = nameColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "vs",
                        style = MaterialTheme.typography.titleSmall, color = nameColor,
                    )
                    Text(
                        text = cappedMatchup(r.teamB),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = nameColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CourtCallState.OPEN, CourtCallState.PAUSED -> Unit // label only
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallTileTest"`
Expected: PASS (all four).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/ui/components/CourtCallTile.kt \
        app/src/test/java/com/example/ui/CourtCallTileTest.kt
git commit -m "feat(court-call): CourtCallTile with color+shape+label states, names-only, big number"
```

---

## Task 4: `CourtCallScreen` board (grid + banners) + `boardSession()` fixture

Adds the `boardSession()` / `manyCourtsSession()` fixtures (needed here and in Tasks 5, 7, 8), then fills the board: force `DarkTokens`, compute the standout, render one `CourtCallTile` per court in `courtId` order, plus the empty and paused states. (Static single-page grid for now; Task 5 adds pagination.)

**Files:**
- Modify: `app/src/test/java/com/example/fixtures/Fixtures.kt`, `ui/screens/CourtCallScreen.kt`
- Test: `ui/CourtCallScreenTest.kt`

- [ ] **Step 1: Add the fixtures**

In `app/src/test/java/com/example/fixtures/Fixtures.kt`, add these two functions inside `object Fixtures` (they reuse the existing private `player(i)` and `rec(courtId, p)` helpers):

```kotlin
    /**
     * Mixed-state board: court 1 UP NOW (lowest AVAILABLE w/ rec), court 2 READY (AVAILABLE w/ rec),
     * court 3 LIVE (IN_PROGRESS, not completed), court 4 FINAL (IN_PROGRESS but match completed),
     * court 5 OPEN (AVAILABLE, no rec).
     */
    fun boardSession(): OpenPlaySession {
        val p = (0..15).map { player(it) }
        val liveMatch = PickleballGameEngine.createMatch(
            courtId = 3,
            teamA = Team(TeamId.TEAM_A, p[8], p[9]),
            teamB = Team(TeamId.TEAM_B, p[10], p[11]),
        ).copy(scoreA = 6, scoreB = 4)
        val finalMatch = PickleballGameEngine.createMatch(
            courtId = 4,
            teamA = Team(TeamId.TEAM_A, p[12], p[13]),
            teamB = Team(TeamId.TEAM_B, p[14], p[15]),
        ).copy(scoreA = 11, scoreB = 7, isCompleted = true)
        return OpenPlaySession(
            id = "fix_board",
            name = "Board Session",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            courts = listOf(
                Court(id = 1, name = "Court 1", status = CourtStatus.AVAILABLE),
                Court(id = 2, name = "Court 2", status = CourtStatus.AVAILABLE),
                Court(id = 3, name = "Court 3", status = CourtStatus.IN_PROGRESS, currentMatch = liveMatch),
                Court(id = 4, name = "Court 4", status = CourtStatus.IN_PROGRESS, currentMatch = finalMatch),
                Court(id = 5, name = "Court 5", status = CourtStatus.AVAILABLE),
            ),
            roster = p,
            activeRecommendations = mapOf(
                1 to rec(1, p.subList(0, 4)),
                2 to rec(2, p.subList(4, 8)),
            ),
        )
    }

    /** Seven AVAILABLE courts each with a recommendation, for pagination tests. */
    fun manyCourtsSession(): OpenPlaySession {
        val p = (0..27).map { player(it) }
        val courts = (1..7).map { id -> Court(id = id, name = "Court $id", status = CourtStatus.AVAILABLE) }
        val recs = (1..7).associateWith { id -> rec(id, p.subList((id - 1) * 4, id * 4)) }
        return OpenPlaySession(
            id = "fix_many",
            name = "Many Courts",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            courts = courts,
            roster = p,
            activeRecommendations = recs,
        )
    }
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/example/ui/CourtCallScreenTest.kt`:

```kotlin
package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.fixtures.Fixtures
import com.example.ui.screens.CourtCallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SessionViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])
class CourtCallScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun app() = ApplicationProvider.getApplicationContext<Application>()
    private fun vmWith(s: com.example.model.OpenPlaySession) =
        SessionViewModel(app()).apply { loadSessionForTest(s) }

    @Test fun board_hasExactlyOneUpNow_andOneReady() {
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = vmWith(Fixtures.boardSession())) } }
        rule.onAllNodesWithTag("court_call_tile_up_now").assertCountEquals(1)
        rule.onAllNodesWithTag("court_call_tile_ready").assertCountEquals(1)
        rule.onAllNodesWithTag("court_call_tile_live").assertCountEquals(1)
        rule.onAllNodesWithTag("court_call_tile_final").assertCountEquals(1)
        rule.onAllNodesWithTag("court_call_tile_open").assertCountEquals(1)
    }

    @Test fun nullSession_showsEmptyState() {
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = SessionViewModel(app())) } }
        rule.onNodeWithTag("court_call_empty").assertIsDisplayed()
    }

    @Test fun pausedSession_showsPausedBanner() {
        rule.setContent {
            MyApplicationTheme {
                CourtCallScreen(viewModel = vmWith(Fixtures.boardSession().copy(isPaused = true)))
            }
        }
        rule.onNodeWithTag("court_call_paused_banner").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallScreenTest"`
Expected: FAIL — the stub renders no tiles / banners.

- [ ] **Step 4: Replace `CourtCallScreen.kt` with the board**

Replace the whole file `app/src/main/java/com/example/ui/screens/CourtCallScreen.kt`:

```kotlin
package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Court
import com.example.model.OpenPlaySession
import com.example.ui.components.CourtCallTile
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

    // System-back exit (no other BackHandler exists in the app; the board is a single-Activity
    // screen, so without this, back would finish the Activity and leave the app).
    BackHandler { viewModel.navigateTo(AppScreen.SessionHub) }

    CompositionLocalProvider(LocalPickItTokens provides DarkTokens) {
        val tokens = LocalPickItTokens.current
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

            // The only touch target on the board: a small, deliberately low-contrast corner close.
            // Rendered last (top of the Box) and in EVERY state so exit is always reachable.
            // NOTE: in the PAUSED state the TopCenter paused banner and this TopEnd close can
            // sit on the same row; close is drawn last so it stays tappable. Give the paused
            // banner trailing padding/width < full so it does not run under the close icon —
            // eyeball this in the Task 8 Roborazzi capture and nudge if they visually collide.
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

/**
 * Static single-page grid. Courts render in fixed `courtId` order. Pagination and auto-cycle
 * are layered on in Task 5.
 */
@Composable
private fun CourtCallGrid(session: OpenPlaySession) {
    val courts = session.courts.sortedBy { it.id }
    val primaryId = primaryReadyCourtId(session)
    CourtGridLayout(courts = courts, session = session, primaryId = primaryId, columns = columnsFor(courts.size))
}

/** Simple column count so a small board is not stretched into one giant row before Task 5. */
private fun columnsFor(count: Int): Int = when {
    count <= 1 -> 1
    count <= 4 -> 2
    else -> 3
}

@Composable
private fun CourtGridLayout(
    courts: List<Court>,
    session: OpenPlaySession,
    primaryId: Int?,
    columns: Int,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        courts.chunked(columns).forEach { rowCourts ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                rowCourts.forEach { court ->
                    val hasRec = session.activeRecommendations[court.id] != null
                    val state = courtDisplayState(
                        court = court,
                        hasRecommendation = hasRec,
                        isPrimaryReady = court.id == primaryId,
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        CourtCallTile(
                            court = court,
                            state = state,
                            recommendation = session.activeRecommendations[court.id],
                        )
                    }
                }
                // Pad short final rows so tiles keep fixed widths/positions.
                repeat(columns - rowCourts.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallScreenTest"`
Expected: PASS (all three).

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/example/fixtures/Fixtures.kt \
        app/src/main/java/com/example/ui/screens/CourtCallScreen.kt \
        app/src/test/java/com/example/ui/CourtCallScreenTest.kt
git commit -m "feat(court-call): board grid with one standout, empty + paused states; add fixtures"
```

---

## Task 5: Deterministic pagination + auto-cycle

Replace the ad-hoc grid with `BoxWithConstraints` breakpoints: `columns` = 1/2/3 at `maxWidth` < 600 / < 1000 / else; `rows` = 1/2 at `maxHeight` < 480 / else; `capacity = columns * rows`. `courtCount <= capacity` → one static page, no indicator. Otherwise deterministic `courtId`-ordered slices, fixed positions, a `court_call_page_indicator`, and a ~10s `LaunchedEffect` auto-advance.

**Files:**
- Modify: `ui/screens/CourtCallScreen.kt`
- Test: `ui/CourtCallPaginationTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/ui/CourtCallPaginationTest.kt`:

```kotlin
package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.fixtures.Fixtures
import com.example.model.OpenPlaySession
import com.example.ui.screens.CourtCallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SessionViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])  // columns=3, rows=2 -> capacity=6
class CourtCallPaginationTest {
    @get:Rule val rule = createComposeRule()

    private fun app() = ApplicationProvider.getApplicationContext<Application>()
    private fun vmWith(s: OpenPlaySession) = SessionViewModel(app()).apply { loadSessionForTest(s) }

    @Test fun underCapacity_showsNoPageIndicator() {
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = vmWith(Fixtures.boardSession())) } }
        rule.onNodeWithTag("court_call_page_indicator").assertDoesNotExist()
    }

    @Test fun overCapacity_showsIndicator_andAutoAdvances() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = vmWith(Fixtures.manyCourtsSession())) } }
        rule.mainClock.advanceTimeBy(50)  // let first frame settle

        // 7 courts, capacity 6 -> 2 pages.
        rule.onNodeWithTag("court_call_page_indicator").assertIsDisplayed()
        rule.onNodeWithText("1 / 2").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(10_000)  // fire the ~10s auto-advance
        rule.onNodeWithText("2 / 2").assertIsDisplayed()
    }
}
```

> **VALIDATE the test-clock ↔ `delay` coupling before relying on it.** `overCapacity_showsIndicator_andAutoAdvances` assumes `rule.mainClock.advanceTimeBy(10_000)` drives the `kotlinx.coroutines.delay` inside the `LaunchedEffect` under Robolectric. That coupling holds when Compose's test clock also drives the `LaunchedEffect` coroutine dispatcher, but it is **not guaranteed** on every Robolectric/Compose combination. When you first run this step (Step 4), if the indicator does **not** advance to `2 / 2`:
> - **Fallback A (looper):** after `advanceTimeBy(10_000)`, pump the main looper —
>   `shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(10))` (import `org.robolectric.Shadows.shadowOf`) — then `rule.waitForIdle()` before the assertion.
> - **Fallback B (frame-driven cycle):** re-key the auto-advance so it is driven by the frame clock the test controls — replace the `while (true) { delay(10_000); ... }` body with an accumulator over `withInfiniteAnimationFrameNanos { frameNanos -> ... }` (advance the page when accumulated frame nanos cross 10s). `advanceTimeBy` deterministically drives the frame clock, so the page turns without relying on `delay`.
> Pick whichever makes the assertion pass with `mainClock.autoAdvance = false`; keep the ~10s interval either way. **Task 7's pulse-clear step (`delay(2_000)`) has the same dependency — apply the same chosen fallback there.**

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallPaginationTest"`
Expected: FAIL — no `court_call_page_indicator`; no cycling.

- [ ] **Step 3: Replace the grid section of `CourtCallScreen.kt` with the paginated layout**

Replace `CourtCallGrid`, `columnsFor`, and `CourtGridLayout` (from Task 4) with the following, and add the imports listed under the code:

```kotlin
@Composable
private fun CourtCallGrid(session: OpenPlaySession) {
    val courts = session.courts.sortedBy { it.id }
    val primaryId = primaryReadyCourtId(session)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth < 600.dp -> 1
            maxWidth < 1000.dp -> 2
            else -> 3
        }
        val rows = if (maxHeight < 480.dp) 1 else 2
        val capacity = (columns * rows).coerceAtLeast(1)

        val pages = courts.chunked(capacity)
        var pageIndex by rememberSaveable(pages.size) { mutableStateOf(0) }

        // ~10s auto-advance, only when there is more than one page.
        if (pages.size > 1) {
            LaunchedEffect(pages.size) {
                while (true) {
                    delay(10_000)
                    pageIndex = (pageIndex + 1) % pages.size
                }
            }
        }

        if (pages.isNotEmpty()) {
            // DERIVE the clamped page for display — never write pageIndex during composition
            // (a snapshot-write-in-composition is a Compose error). pageIndex is only mutated
            // from the auto-advance effect above.
            val page = pageIndex.coerceIn(0, pages.lastIndex)
            Column(modifier = Modifier.fillMaxSize()) {
                CourtGridLayout(
                    courts = pages[page],
                    session = session,
                    primaryId = primaryId,
                    columns = columns,
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
    primaryId: Int?,
    columns: Int,
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
                    val hasRec = session.activeRecommendations[court.id] != null
                    val state = courtDisplayState(
                        court = court,
                        hasRecommendation = hasRec,
                        isPrimaryReady = court.id == primaryId,
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        CourtCallTile(
                            court = court,
                            state = state,
                            recommendation = session.activeRecommendations[court.id],
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
```

Add these imports to `CourtCallScreen.kt`:
```kotlin
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallPaginationTest"`
Expected: PASS (both). Also re-run `CourtCallScreenTest` to confirm no regression:
`./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallScreenTest"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/ui/screens/CourtCallScreen.kt \
        app/src/test/java/com/example/ui/CourtCallPaginationTest.kt
git commit -m "feat(court-call): viewport-breakpoint pagination with ~10s auto-cycle on overflow"
```

---

## Task 6: Full-bleed escape + keep-awake / immersive lifecycle

Restructure `MainActivity` so `CourtCall` renders **outside** the themed, `safeDrawingPadding` Surface (all other screens keep it). Add one lifecycle owner inside `CourtCallScreen` (a `LifecycleEventObserver`) that sets `FLAG_KEEP_SCREEN_ON` + immersive on `ON_RESUME`/entry and clears them on `ON_PAUSE`/exit.

**Files:**
- Modify: `MainActivity.kt`, `ui/screens/CourtCallScreen.kt`
- Test: `ui/CourtCallKeepAwakeTest.kt`

- [ ] **Step 1: Write the failing test** (needs the real Activity to reach the window flags)

Create `app/src/test/java/com/example/ui/CourtCallKeepAwakeTest.kt`:

```kotlin
package com.example.ui

import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.MainActivity
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CourtCallKeepAwakeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val keepOn get() =
        rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

    @Test fun keepScreenOn_setOnCourtCall_clearedOnLeave() {
        // `by viewModels()` shares the Activity's ViewModelStore, so this is the same instance.
        val vm = ViewModelProvider(rule.activity)[SessionViewModel::class.java]

        rule.runOnUiThread { vm.navigateTo(AppScreen.CourtCall) }
        rule.waitForIdle()
        assertNotEquals(0, keepOn)   // flag set while showing Court Call

        rule.runOnUiThread { vm.navigateTo(AppScreen.SessionHub) }
        rule.waitForIdle()
        assertEquals(0, keepOn)      // flag cleared after leaving
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallKeepAwakeTest"`
Expected: FAIL — nothing sets `FLAG_KEEP_SCREEN_ON`.

- [ ] **Step 3: Restructure `MainActivity` for the full-bleed escape**

In `MainActivity.kt`, remove the single wrapping `Surface` from `setContent` and move theming into `PickleballAppContent`, special-casing `CourtCall`. Replace `setContent { ... }` body and `PickleballAppContent` with:

```kotlin
        setContent {
            val themeMode by sessionViewModel.themeMode.collectAsState()
            MyApplicationTheme(themeMode = themeMode) {
                PickleballAppContent(viewModel = sessionViewModel)
            }
        }
```

```kotlin
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
```

(All imports used above already exist in `MainActivity.kt` from Task 1.)

- [ ] **Step 4: Add the keep-awake / immersive lifecycle owner to `CourtCallScreen`**

At the top of `CourtCallScreen`'s body (inside the `CompositionLocalProvider`, before the `Box`), add:

```kotlin
        BoardDisplayHygiene()
```

Then add this private composable and helper to `CourtCallScreen.kt`:

```kotlin
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
        enable() // apply now: CourtCallScreen only enters composition while the board is showing
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            disable() // clear when navigating away from the board
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
```

Add these imports to `CourtCallScreen.kt`:
```kotlin
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

> Note: immersive bar-hiding is best-effort under Robolectric and is asserted only indirectly; the test asserts the observable `FLAG_KEEP_SCREEN_ON`. If `androidx.lifecycle.compose.LocalLifecycleOwner` is unresolved on this toolchain, use `androidx.compose.ui.platform.LocalLifecycleOwner` instead (older Compose location) — do not change any other line.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallKeepAwakeTest"`
Expected: PASS. Re-run the route + screen tests to confirm the restructure did not regress them:
`./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallRouteTest" --tests "com.example.ui.CourtCallScreenTest"` → PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/MainActivity.kt \
        app/src/main/java/com/example/ui/screens/CourtCallScreen.kt \
        app/src/test/java/com/example/ui/CourtCallKeepAwakeTest.kt
git commit -m "feat(court-call): render board full-bleed outside app theme; keep-awake + immersive lifecycle"
```

---

## Task 7: State-change transition highlight (+ hold on a just-called court)

Remember the previous derived-state snapshot; when a court transitions **to** UP NOW or FINAL, briefly show a highlight (`court_call_tile_pulse_${id}`) for a short window, and — when paginating — jump to the page holding the just-called court rather than cycling away. Minimal / YAGNI: a short one-shot emphasis, not a loop.

**Files:**
- Modify: `ui/screens/CourtCallScreen.kt`, `ui/components/CourtCallTile.kt`
- Test: `ui/CourtCallTransitionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/ui/CourtCallTransitionTest.kt`:

```kotlin
package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.fixtures.Fixtures
import com.example.ui.screens.CourtCallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SessionViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])
class CourtCallTransitionTest {
    @get:Rule val rule = createComposeRule()

    private fun app() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun transitionToUpNow_pulsesBriefly_thenClears() {
        rule.mainClock.autoAdvance = false
        val vm = SessionViewModel(app())
        // Start with court 1 OPEN (no rec) so it is NOT UP NOW yet.
        val start = Fixtures.boardSession().let { s ->
            s.copy(activeRecommendations = s.activeRecommendations - 1)
        }
        vm.loadSessionForTest(start)
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = vm) } }
        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithTag("court_call_tile_pulse_1").assertDoesNotExist()

        // Now court 1 gains a recommendation -> becomes UP NOW: a transition.
        rule.runOnUiThread { vm.loadSessionForTest(Fixtures.boardSession()) }
        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithTag("court_call_tile_pulse_1").assertIsDisplayed()

        // Window elapses -> highlight clears.
        rule.mainClock.advanceTimeBy(2_500)
        rule.onNodeWithTag("court_call_tile_pulse_1").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallTransitionTest"`
Expected: FAIL — no `court_call_tile_pulse_1` node and no `pulsing` param on the tile.

- [ ] **Step 3: Add a `pulsing` overlay to `CourtCallTile`**

Add a parameter (default `false`, so Task 3 tests still compile) and a tagged overlay. Change the `CourtCallTile` signature:

```kotlin
@Composable
fun CourtCallTile(
    court: Court,
    state: CourtCallState,
    recommendation: RotationRecommendation?,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
```

Then wrap the tile's `Column` in a `Box` and add the overlay when `pulsing`. Replace the outer `Column(...) { ... }` with:

```kotlin
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(fill, shape)
                .border(3.dp, borderColor, shape)
                .testTag(stateTag(state))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // ... existing tile content unchanged ...
        }
        if (pulsing) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(6.dp, tokens.attention, shape)
                    .testTag("court_call_tile_pulse_${court.id}"),
            )
        }
    }
```

Add imports to `CourtCallTile.kt`:
```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
```

- [ ] **Step 4: Track transitions and drive the hold — all in one place**

**Single location for all view-state.** Every new `remember`/`LaunchedEffect` (snapshot, pulsing, held page, pagination) lives **inside `BoxWithConstraints`** — the one composable that has `session`, `courts`, `primaryId`, `pages`, and `pageIndex` all in scope. Do **not** put any of it up in `CourtCallGrid`'s outer body and thread it down. Replace the **entire body of `BoxWithConstraints`** established in Task 5 with the following:

```kotlin
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth < 600.dp -> 1
            maxWidth < 1000.dp -> 2
            else -> 3
        }
        val rows = if (maxHeight < 480.dp) 1 else 2
        val capacity = (columns * rows).coerceAtLeast(1)
        val pages = courts.chunked(capacity)

        // Derived state per court, for transition detection.
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

        // Detect transitions TO UP NOW / FINAL -> pulse + remember the just-called court.
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
        // Clear each pulse after a short, non-looping window.
        // NOTE: this `delay` has the same test-clock dependency as Task 5's auto-advance —
        // apply the SAME fallback you chose there (Task 5, Step 1 note) if the pulse-clear
        // assertion does not fire under `mainClock.advanceTimeBy`.
        LaunchedEffect(pulsing.keys.toList()) {
            if (pulsing.isNotEmpty()) {
                delay(2_000)
                pulsing.clear()
                justCalledCourtId = null
            }
        }
        // Hold on a just-called court's page instead of cycling away from it.
        val heldPage = justCalledCourtId?.let { id -> pages.indexOfFirst { p -> p.any { it.id == id } } }
        LaunchedEffect(heldPage) {
            if (heldPage != null && heldPage >= 0) pageIndex = heldPage
        }
        // ~10s auto-advance, only when there is more than one page AND nothing is being held.
        if (pages.size > 1 && justCalledCourtId == null) {
            LaunchedEffect(pages.size, justCalledCourtId) {
                while (true) {
                    delay(10_000)
                    pageIndex = (pageIndex + 1) % pages.size
                }
            }
        }

        if (pages.isNotEmpty()) {
            val page = pageIndex.coerceIn(0, pages.lastIndex)  // derived, never written in composition
            Column(modifier = Modifier.fillMaxSize()) {
                CourtGridLayout(
                    courts = pages[page],
                    session = session,
                    primaryId = primaryId,
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
```

Then give `CourtGridLayout` a `pulsing` parameter and pass it to each tile — change its signature and the `CourtCallTile` call:

```kotlin
@Composable
private fun CourtGridLayout(
    courts: List<Court>,
    session: OpenPlaySession,
    primaryId: Int?,
    columns: Int,
    pulsing: Map<Int, Boolean>,
    modifier: Modifier = Modifier,
) {
    // ... unchanged layout; in the inner rowCourts.forEach, pass pulsing to the tile:
    //     CourtCallTile(
    //         court = court,
    //         state = state,
    //         recommendation = session.activeRecommendations[court.id],
    //         pulsing = pulsing[court.id] == true,
    //     )
}
```

Add these imports to `CourtCallScreen.kt` (on top of Task 5's pagination imports):
```kotlin
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallTransitionTest"`
Expected: PASS. Re-run tile + pagination tests for regressions:
`./gradlew :app:testDebugUnitTest --tests "com.example.ui.CourtCallTileTest" --tests "com.example.ui.CourtCallPaginationTest"` → PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/ui/screens/CourtCallScreen.kt \
        app/src/main/java/com/example/ui/components/CourtCallTile.kt \
        app/src/test/java/com/example/ui/CourtCallTransitionTest.kt
git commit -m "feat(court-call): brief highlight + page-hold on transition to UP NOW / FINAL"
```

---

## Task 8: Roborazzi landscape baseline

One landscape baseline `court-call-dark.png` from `boardSession()`, rendered while the app theme is LIGHT to prove the board stays dark. Dual-theme is deliberately N/A (fixed palette per spec §7) — documented in the test.

**Files:**
- Create: `app/src/test/java/com/example/screenshots/CourtCallScreenshotTest.kt`

- [ ] **Step 1: Write the baseline test**

Create `app/src/test/java/com/example/screenshots/CourtCallScreenshotTest.kt`:

```kotlin
package com.example.screenshots

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.fixtures.Fixtures
import com.example.ui.screens.CourtCallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import com.example.viewmodel.SessionViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Single LANDSCAPE baseline of the Court Call board.
 *
 * DELIBERATE DEVIATION from the app's dual-theme baseline convention: the board palette is FIXED
 * (spec §7 — always DarkTokens, independent of ThemeMode), so a light baseline would be dead. We
 * render under ThemeMode.LIGHT on purpose to prove the board ignores the ambient theme and stays
 * dark. Record: `./gradlew :app:recordRoborazziDebug`; verify: `./gradlew :app:verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])
class CourtCallScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun app() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun courtCall_dark() {
        val vm = SessionViewModel(app()).apply { loadSessionForTest(Fixtures.boardSession()) }
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {  // board must stay dark regardless
                CourtCallScreen(viewModel = vm)
            }
        }
        rule.onRoot().captureRoboImage(filePath = "src/test/screenshots/court-call-dark.png")
    }
}
```

- [ ] **Step 2: Record the baseline**

Run: `./gradlew :app:recordRoborazziDebug`
Expected: writes `app/src/test/screenshots/court-call-dark.png`. Eyeball it: dark ground, one lime UP-NOW standout (court 1), court 3 LIVE with 6 – 4, court 4 FINAL 11 – 7, court 5 OPEN, court numbers the largest elements.

- [ ] **Step 3: Verify**

Run: `./gradlew :app:verifyRoborazziDebug`
Expected: PASS (no diff against the just-recorded baseline).

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/example/screenshots/CourtCallScreenshotTest.kt \
        app/src/test/screenshots/court-call-dark.png
git commit -m "test(court-call): landscape dark board Roborazzi baseline"
```

---

## Task 9: Final verification + acceptance sweep

- [ ] **Step 1: Run the whole unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all new Court Call tests + the existing suite from cycle A). If an existing test regresses, fix forward — do not weaken an assertion to make it pass.

- [ ] **Step 2: Verify screenshots**

Run: `./gradlew :app:verifyRoborazziDebug`
Expected: PASS (the new `court-call-dark.png` plus cycle A's baselines).

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Acceptance sweep against spec §11** — verify each by inspection/grep:
  - **#1** One standout + transition highlight → `CourtCallScreenTest.board_hasExactlyOneUpNow_andOneReady` (exactly one `court_call_tile_up_now`) + `CourtCallTransitionTest`.
  - **#2** Consistent color + shape + label per state, grayscale/color-blind safe → every `CourtCallTile` branch renders a shape + a text label, never color alone.
  - **#3** Distance-legibility: court number largest (`displayLarge`), TV type floors (`headlineSmall`/`titleMedium`), capped names (`NAME_CAP`), high contrast (dark palette).
  - **#4** Fixed board palette independent of `ThemeMode` → `CourtCallScreenshotTest` renders under LIGHT yet the board is dark; board reads only `LocalPickItTokens` provided as `DarkTokens`. Grep to confirm no `MaterialTheme.colorScheme` in the board: `grep -rn "colorScheme" app/src/main/java/com/example/ui/screens/CourtCallScreen.kt app/src/main/java/com/example/ui/components/CourtCallTile.kt` → no matches.
  - **#5** Every `CourtStatus` + derived FINAL has a rendering → `CourtCallStateTest` covers all six.
  - **#6** Stable court positions across pagination → courts always `sortedBy { it.id }`, deterministic `chunked(capacity)` slices, fixed-weight cells + spacer padding.
  - **#7** Idle tiles names-only → `CourtCallTileTest.upNowTile_showsNamesOnly_noReasons` asserts reason text absent.

- [ ] **Step 5: Commit** (only if the sweep required any fix-ups)

```bash
git add -A
git commit -m "chore(court-call): finalize acceptance sweep for big-screen display"
```

---

## Spec coverage

| Spec section | Implemented by |
|---|---|
| §4 Navigation & app-theme-frame escape (route, Hub entry, exit, full-bleed, keep-awake/immersive, single lifecycle owner) | Task 1 (route + `court_call_button` + branch; **exit**: `court_call_close` corner affordance + `BackHandler`, both in `CourtCallScreen`, tested by `CourtCallRouteTest.closeButton_returnsToHub`), Task 6 (full-bleed restructure + `BoardDisplayHygiene` lifecycle owner) |
| §5 Derived display state (six states, precedence, one-standout invariant) | Task 2 (`courtDisplayState` + `primaryReadyCourtId`), Task 3 (per-state tile treatments) |
| §6 Distance-legibility (court number largest, TV type floors, name truncation, names-only no reasons) | Task 3 (`CourtCallTile`, `NAME_CAP`, `displayLarge`/`headlineSmall`/`titleMedium`) |
| §7 Fixed dark board palette (forced `DarkTokens`, no `colorScheme`, full-bleed canvas) | Task 4 (`CompositionLocalProvider(LocalPickItTokens provides DarkTokens)` + canvas paint), Task 6 (escape frame), Task 8 (baseline proving theme-independence) |
| §8 Layout, pagination & feedback (viewport breakpoints, static-vs-paginated, ~10s cycle, transition highlight, hold on called court, empty/paused) | Task 4 (grid + empty/paused banners), Task 5 (`BoxWithConstraints` breakpoints + auto-cycle + indicator), Task 7 (pulse + page hold) |
| §9 Accessibility (shape+label+color, court number anchor, high contrast, stable layout, testTags) | Task 2, Task 3 (tags + shape/label per state), Task 4 (fixed order) |
| §10 Testing (behavior TDD, pinned viewport, test clock, keep-awake via real Activity, single Roborazzi baseline) | Tasks 1–7 (Robolectric + Compose UI Test), Task 8 (Roborazzi) |
| §11 Acceptance criteria #1–#7 | Task 9 (sweep, mapped criterion-by-criterion) |
