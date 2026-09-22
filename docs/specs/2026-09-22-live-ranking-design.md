# Live Ranking — Design Spec

- **Date:** 2026-09-22
- **Status:** Approved pending spec review (brainstorming gate)
- **Feature:** 2nd of three (serve/callout fix shipped in PR #7; Session History is 3rd).
- **Scope:** A dedicated full-screen **TV board** that ranks the current session's players by wins (point differential as tie-break), auto-paging when the roster is long, showing the latest standings whenever opened and live-recomposing for any session change that occurs while it is displayed. UI + one pure ranking function. **No persistence/schema changes.** (Note: completing a match returns to the Session Hub today — see Refresh behavior — so the board is not continuously on-screen across a completion.)

## Goal

Give an open-play session a glanceable, self-updating leaderboard suitable for a big screen, so players can see where they stand without anyone maintaining it by hand.

## Data source (no new persistence)

Everything derives from the existing reactive `SessionViewModel.session` (`StateFlow<OpenPlaySession?>`). `completeMatch` already updates each player's `matchesPlayed`, `matchesWon`, `totalPointsScored`, `totalPointsConceded` in `session.roster` (`app/src/main/java/com/example/viewmodel/SessionViewModel.kt:311-357`). The board reads `session.roster` and recomputes on each emission. No Room/DAO/entity changes.

`Player` already carries the needed fields (`SessionModels.kt:37-48`): `id`, `name`, `status: ParticipantStatus` (`AVAILABLE`, `IN_MATCH`, `RESTING`, `CHECKED_OUT`), `matchesPlayed`, `matchesWon`, `totalPointsScored`, `totalPointsConceded`.

## Ranking logic (the pure, testable core)

New pure object `engine/RankingEngine.kt` with `fun rank(roster: List<Player>): List<RankedPlayer>`.

**Filter:** include only players with `matchesPlayed >= 1`. Resting **and** checked-out players remain (their completed results are part of the session). Zero-game players are hidden until their first result.

**Point differential:** `pointDiff = totalPointsScored - totalPointsConceded`.

**Ordering (list order):** sort by
1. `matchesWon` descending,
2. `pointDiff` descending,
3. `name` ascending (case-insensitive),
4. `id` ascending — final deterministic tiebreak so duplicate names never leave order dependent on roster position.

**Rank numbers (competition ranking):** two players share the same rank number iff they have **identical `matchesWon` AND identical `pointDiff`** (e.g. 1, 2, 2, 4). `name`/`id` break *list order* within such a tie but **must not** affect the shared rank number. Rank number of an entry = 1 + (count of players strictly ahead of it by the wins-then-diff comparison).

**`RankedPlayer` model** (new, alongside `RankingEngine` or in `model/`):
```kotlin
data class RankedPlayer(
    val id: String,          // stable player id — Compose row key + final order fallback
    val rank: Int,           // competition rank (ties share a number)
    val name: String,
    val wins: Int,           // matchesWon
    val losses: Int,         // matchesPlayed - matchesWon
    val pointDiff: Int,      // signed
    val isCheckedOut: Boolean // status == CHECKED_OUT -> subtle "Left" label
)
```

## Display surface (TV)

Mirrors the Court Call precedent. New `AppScreen.LiveRanking` (object) in `SessionViewModel`; a **Session Hub button** (next to the existing Court Call button, `SessionHubScreen.kt:120-129`) calls `viewModel.navigateTo(AppScreen.LiveRanking)`.

**Wiring (must match how Court Call is actually rendered):** `CourtCall` is handled in `MainActivity`'s **outer full-bleed `if`** (`MainActivity.kt:48-51`) that deliberately omits `safeDrawingPadding()` and the `MaterialTheme` `Surface`; the inner `when` (lines 59-65) applies both and carries a `Unit` no-op branch for `CourtCall` to stay exhaustive. `LiveRanking` must be added the **same way**: render `LiveRankingScreen(viewModel)` in that outer full-bleed `if` (dark canvas, no padding, no Surface), and add a `is AppScreen.LiveRanking -> Unit` no-op branch to the inner `when`. Adding it to the inner `when` instead would render it padded inside a light Surface — wrong for a TV board.

**Exit (no back stack in this app):** like Court Call (`CourtCallScreen.kt:67,109-121`), the board provides an explicit close affordance **and** a `BackHandler` — both call `viewModel.navigateTo(AppScreen.SessionHub)`. Without this, system Back has nowhere to go (the app drives navigation through `currentScreen`, not a NavHost back stack).

**Keep-awake (DRY):** Court Call's keep-awake + cutout hygiene lives in `private` helpers `BoardDisplayHygiene`/`findActivity` inside `CourtCallScreen.kt` (~lines 126-169). Extract these into a shared util (e.g. `ui/screens/BoardDisplayHygiene.kt`) so both screens use one copy; `CourtCallScreen` switches to the shared helper with no behavior change. `LiveRankingScreen` uses `DarkTokens`/`LocalPickItTokens` for colors.

**Header (fixed, non-scrolling):**
- Title: e.g. "Live Ranking".
- **Subtitle (rule made visible):** "Ranked by wins · Ties broken by point difference." This explains why two players with different W–L can share a rank, and supersedes any win-percentage display.

**Row:** `Rank │ Name │ W–L │ ±Diff` — e.g. `1   Alice   4–1   +18`. Diff sign rule: positive → `+18`, negative → `-3` (intrinsic minus), **zero → `0`** (never `+0`). Only `CHECKED_OUT` players show a subtle **"Left"** label (not "OUT" — avoids implying elimination), at readable contrast against the dark canvas; `AVAILABLE`/`IN_MATCH`/`RESTING` players carry no label. **Rank 1** gets a subtle accent; no top-3 podium styling (kept minimal). Long names truncate (ellipsis) so the `W–L`/`±Diff` columns never get pushed off-screen.

**Empty state:** when no player has `matchesPlayed >= 1`, show "No results yet — rankings appear after the first game." (no paging, no timer).

## Paging (never overflows)

A pure helper `fun rowsPerPage(availableHeightPx: Int, rowHeightPx: Int): Int = max(1, availableHeightPx / rowHeightPx)` (integer floor).

- **Inset ownership:** because the full-bleed `MainActivity` branch does **not** apply `safeDrawingPadding()`, `LiveRankingScreen` **owns inset consumption itself** — wrap the content in `Modifier.safeDrawingPadding()` (or explicit `windowInsetsPadding(WindowInsets.safeDrawing)`) so measurement excludes the status bar / navigation bar / display cutout. Skipping this makes `rowsPerPage` measure full physical height and the top/bottom rows render under the system bars/cutout.
- **Available height** is then measured *after* insets, the header (title + subtitle), the footer slot, and container padding — via `BoxWithConstraints` over the remaining content region. `rowHeightPx` is a **fixed readable constant**; it is never shrunk to cram more rows.
- **Constant-height footer slot:** the page-indicator footer occupies a **fixed-height slot that is always reserved** (rendered empty when single-page). This makes the content-region measurement independent of `pageCount`, avoiding a footer↔`rowsPerPage`↔`pageCount` circular re-layout at the single/multi-page boundary.
- **No floor forces more than one over-capacity row; zero-capacity is defined.** There is no `minRows` that inflates the count. `rowsPerPage` is a plain floor with a defensive `max(1, …)`. Real target displays (TV/large phone) always leave room for ≥1 readable row after insets/header/footer, so capacity is ≥1 in practice; the "never exceeds `available / rowHeight`" guarantee applies **when capacity ≥ 1**. The `max(1, …)` is a documented defensive fallback for the degenerate `available < rowHeight` case (not expected on real displays): it renders a single (possibly clipped) row rather than an empty/crashing page. This is stated so the behavior is explicit rather than accidental.
- **Pages:** `pageCount = ceil(rankedCount / rowsPerPage)`. Split the ranked list into fixed contiguous pages of `rowsPerPage`.
- **Advance:** when `pageCount > 1`, auto-advance every **9 s**, looping, with the page indicator visible ("Page 2 / 3"). When `pageCount == 1`, the timer and page animation are **disabled entirely** and the footer slot renders empty (same decoupling discipline as the Court Call cleanup).

## Refresh behavior

The board must update results without disrupting the reading rhythm:
- **Refresh model (reconciled with existing navigation):** `completeMatch` currently ends with `_currentScreen.value = AppScreen.SessionHub` (`SessionViewModel.kt:385`) — completing a match navigates back to the Hub, so the board is **not** continuously on-screen *across* a completion (same as Court Call today; a match is completed from a scoreboard, not from the board). The board therefore always shows the **latest** standings whenever it is opened, and additionally live-recomposes for any `session` emission that occurs while it is displayed. Changing that navigation behavior is out of scope for this feature (it would also affect Court Call).
- **Immediate data refresh (while visible):** recompute `rank(roster)` on every `session` emission collected with `collectAsStateWithLifecycle` (wrapped in `remember(roster)` for recomposition efficiency), so any state change that happens while the board is up is reflected at once.
- **Preserve current page** when it is still valid after a refresh.
- **State + advance (avoids stale capture):** hold the raw index in `val rawPage = remember { mutableIntStateOf(0) }`. The advance coroutine must read the **live** state each tick, not a value captured at launch:
  ```kotlin
  LaunchedEffect(pageCount) {
      if (pageCount <= 1) return@LaunchedEffect
      while (true) {
          delay(9_000)
          // Advance from the CLAMPED live value, so a shrink advances from the
          // currently displayed page rather than stalling a tick.
          rawPage.intValue = (rawPage.intValue.coerceIn(0, pageCount - 1) + 1) % pageCount
      }
  }
  ```
  Reading `rawPage.intValue` inside the loop returns the current value at read-time (snapshot state), so advancing never uses a stale `page` — a naive `(page + 1) % pageCount` over a page value captured at effect launch would stop after one advance and is explicitly rejected. Coercing before incrementing also handles a **page-count shrink**: if `rawPage` was 4 and `pageCount` drops to 2, the render shows clamped page 1 and the next tick advances `(1 + 1) % 2 = 0` (not `(4 + 1) % 2 = 1`, which would stall on page 1). `pageCount` is the **sole restart key**.
- **Clamp at *read* time**, not by writing state during composition: `val page = rawPage.intValue.coerceIn(0, pageCount - 1)` where the page is rendered (mirrors `CourtCallScreen.kt:234`). Writing a coerced value back into state during composition is a write-state-during-composition anti-pattern and is avoided; the clamped-advance above keeps `rawPage` in range from the next tick.
- **A score/roster update must not restart the 9 s timer or jump to page 1.** Because the `LaunchedEffect` is keyed only on `pageCount`, a data change with unchanged `pageCount` neither cancels nor restarts the coroutine. The earlier "jump to the leaders after a match" idea is **dropped** (it would repeatedly starve lower pages).

## Data flow

`SessionViewModel.session` (StateFlow) → `LiveRankingScreen` collects with lifecycle awareness → `RankingEngine.rank(roster)` (recomputed per emission; may be wrapped in `remember(roster)` for recomposition efficiency) → `rowsPerPage`/paging → current page rendered. **Paging index and timer are local UI state**, not in the ViewModel. No new ViewModel methods required beyond `navigateTo(AppScreen.LiveRanking)` reuse.

## Components (each independently testable)

- `engine/RankingEngine.kt` + `RankedPlayer` — pure ranking (filter, sort, competition rank). Primary TDD target.
- `ui/screens/LiveRankingScreen.kt` — TV surface: header/subtitle, self-owned inset consumption, paging + timer, constant-height footer slot, empty state, close affordance + `BackHandler` → SessionHub.
- `ui/components/RankingRow.kt` — one row (`Rank │ Name │ W–L │ ±Diff`, "Left" label, rank-1 accent, name ellipsis). `RankedPlayer.id` is the Compose `key`.
- `ui/screens/BoardDisplayHygiene.kt` — keep-awake/cutout helper extracted from `CourtCallScreen` and shared by both boards (DRY; no behavior change to Court Call).
- Helper `rowsPerPage(...)` — pure; lives with the screen or in a small paging util.
- Wiring: `AppScreen.LiveRanking` (SessionViewModel), Hub button (SessionHubScreen), `MainActivity` **outer full-bleed `if`** (like the `CourtCall` branch) + a `Unit` no-op in the inner `when`.

## Testing (TDD)

**`RankingEngine` unit tests (JUnit, pure):**
- Sorts by wins desc; `pointDiff` desc breaks equal wins; `name` asc then `id` asc break full ties (incl. duplicate-name case → deterministic, id decides order but rank number stays shared).
- Competition ranking: equal (wins, diff) share a rank number; next distinct entry skips (1,2,2,4). A **3-way** tie yields `1,1,1,4`. Differing losses with equal wins+diff still share a rank.
- Filters out `matchesPlayed == 0`; includes players of **every** status with `matchesPlayed>=1` (`AVAILABLE`, `IN_MATCH`, `RESTING`, `CHECKED_OUT`); `isCheckedOut` is true only for `CHECKED_OUT`.
- `losses = matchesPlayed - matchesWon`; signed `pointDiff`. Empty roster → empty list.

**`rowsPerPage` unit tests (pure):** exact fit and remainder both return `floor(available / rowHeight)` and never exceed capacity (capacity ≥ 1 cases); the degenerate `available < rowHeight` case returns `1` by the defensive floor (documented as the not-expected-on-real-displays fallback, exempt from the "never exceeds" guarantee).

**Row-format tests:** zero diff renders `0` (not `+0`); positive `+N`, negative `-N`; only `CHECKED_OUT` shows the "Left" label.

**Compose/Robolectric test (Court Call precedent):**
- Single page (`pageCount == 1`) → no paging timer, empty (reserved) footer slot; content static.
- Multiple pages → advances and loops; a data change that leaves `pageCount` unchanged does **not** reset the timer or the current page (assert page preserved).
- **Shrink advances from the displayed page:** with `rawPage` beyond the new range (e.g. was 4, `pageCount` drops to 2), the render clamps to the last page and the **first tick after the shrink advances from that displayed page** (→ page 0), not from the stale raw value.
- Empty roster → empty-state text, no timer.
- A very long player name does not push the `W–L`/`±Diff` columns off-screen (truncates).
- **Exit:** the close affordance and system Back (`BackHandler`) both navigate to `AppScreen.SessionHub`.
- Optional Roborazzi snapshot of a populated board.

## Non-goals

- No cross-session / all-time ranking or history — that's the **Session History** feature (3rd cycle).
- No persistence, DAO, entity, or schema changes.
- No editing of stats from this screen.
- No phone-optimized layout (TV-first; it still renders in-app on a phone, just not specially tuned).
- No podium/top-3 styling (rank-1 accent only).

## Confirmed decisions

- Rank by **wins**, tie-break **point differential**, then name, then id (id = order only, never rank number).
- **Competition ranking** (ties share a rank number).
- Board lists `matchesPlayed >= 1` incl. resting/checked-out; checked-out shows a subtle **"Left"** label.
- Auto-page **9 s**, subtle indicator, single page disables timer/animation.
- Rank-1 accent only.
- Refresh preserves/clamps page, never restarts the timer on score updates; no "jump to leaders."
