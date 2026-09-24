# Player Detail — Design Spec

- **Date:** 2026-09-24
- **Status:** Approved (brainstorming gate passed)
- **Scope:** A read-only **Player Detail** screen showing a single player's cross-session record — the deferred "next slice" of Session History. Adds the previously-deferred **"sessions played"** stat and a per-session breakdown. Reached by tapping an **All-Time** leaderboard row. **No schema changes, no new writes, no change to the write path.**
- **Relation to prior work:** Builds directly on **Session History** (PR #9). Reuses its persisted data, the `RankingEngine`, and the `HistoryAggregator.rankAllTime` all-time computation. Explicitly listed as a non-goal there ("No player-detail screen and no 'sessions played' stat — deferred together as the next slice").

## Goal

From the All-Time leaderboard, let a host drill into one player and see that player's all-time record and how it breaks down across the sessions they played — including how many sessions that was — without loading a session and without any schema/write change.

## Identity model (single, inherited from All-Time)

- **Player Detail is keyed by normalized name** — `name.trim().lowercase(Locale.ROOT)` — the exact identity the **All-Time** tab already uses (match records store player names, not ids). No id-based (session-scope) reconciliation is performed.
- **Documented caveat, shown on the screen:** records are grouped by name across sessions, so two different people who share a name merge into one Player Detail, and one person is one detail per normalized name. This is the same caveat the All-Time tab already surfaces; Player Detail restates it (see UI).
- Because identity and the source data match All-Time exactly, **the header totals on Player Detail are identical to that player's All-Time leaderboard row** (same rank, W–L, games, point diff, display spelling).

## What already exists (verified against the code)

- `HistoryAggregator.rankAllTime(matches: List<MatchResult>): List<RankedPlayer>` (`engine/HistoryAggregator.kt`) aggregates **valid-winner** matches by normalized name, reuses `RankingEngine.rank`, and post-rank relabels each row to its deterministic **display spelling** (highest `endTime` → highest `matchId` → lowest slot). `RankedPlayer.id` is the normalized name; `RankedPlayer.name` is the display spelling. This is reused wholesale for the header.
- `HistoryData.Loaded` (`viewmodel/HistoryState.kt`) already holds everything needed: `sessions: List<SessionMeta>`, `matchesBySession: Map<String, List<MatchResult>>`, `rosterBySession: Map<String, List<Player>>`. **No new DAO read, query, or schema change is required.**
- `MatchResult` (`model/HistoryModels.kt`): `matchId, sessionId, teamA:List<String>, teamB:List<String>, scoreA, scoreB, winner:TeamId?, startTime, endTime`. `winner == null` ⇒ malformed/"UNKNOWN" — excluded from all-time aggregation.
- `SessionMeta` (`model/HistoryModels.kt`): `id, name, startTime`.
- `SessionHistoryScreen` (`ui/screens/SessionHistoryScreen.kt`) currently renders session detail as **internal screen state** (a single `selectedSessionId: String?`), with the History/All-Time `TabRow` as the base and two hoisted per-tab `LazyListState`s. Back (button **and** system) clears the selection → returns to the tab list; from the list → `navigateTo(origin)`. The **All-Time rows are currently non-interactive** (this spec reverses that for tap-through).
- The active session id + readiness are already available to the screen via the shared `SessionViewModel` (`session: StateFlow<OpenPlaySession?>`, `isSessionLoaded: StateFlow<Boolean>`). All-Time (and therefore Player Detail) **spans every session including the active one**.

## Pure-logic contract (UI-independent, unit-tested, no Android/Room)

The core logic is a **pure function in the engine layer** that takes **domain inputs only** (never the UI-layer `HistoryData`) and returns an explicit `Found | NotAvailable` result.

### Domain output types (in `com.example.model`, DB-free)

```kotlin
/** One session's slice of a player's cross-session record (match-derived, name-keyed). */
data class PlayerSessionRecord(
    val sessionId: String,
    val sessionName: String?,   // null when the session's metadata is missing -> UI: non-tappable "Session unavailable"
    val startTime: Long?,       // null when metadata missing (used for ordering; nulls sort last)
    val games: Int,
    val wins: Int,
    val losses: Int,            // games - wins
    val pointDiff: Int,
    val isActive: Boolean,      // sessionId == activeSessionId -> UI tags "In progress"
)

data class PlayerDetailData(
    val id: String,             // normalized name (identity key)
    val displayName: String,    // deterministic display spelling (from rankAllTime)
    val rank: Int,              // all-time competition rank
    val wins: Int,
    val losses: Int,
    val games: Int,             // wins + losses
    val pointDiff: Int,
    val sessionsPlayed: Int,    // == records.size
    val records: List<PlayerSessionRecord>, // ordered startTime DESC, then sessionId ASC (stable)
)

sealed interface PlayerDetail {
    data class Found(val data: PlayerDetailData) : PlayerDetail
    data object NotAvailable : PlayerDetail   // id has no qualifying (valid-winner) results
}
```

### Pure function (engine)

```kotlin
fun buildPlayerDetail(
    matches: List<MatchResult>,
    sessions: List<SessionMeta>,
    normalizedId: String,
    activeSessionId: String?,
): PlayerDetail
```

Behavior:

1. **Header:** compute `rankAllTime(matches)` and take the `RankedPlayer` row whose `id == normalizedId`. If there is **no** such row (the name has zero valid-winner contributions), return **`NotAvailable`**. Otherwise that row supplies `rank`, `wins`, `losses`, `pointDiff`, and `displayName` (= `RankedPlayer.name`). Note `RankedPlayer` has **no `games` field**; **`games = wins + losses`**, derived exactly as the All-Time tab already does. This guarantees the header equals the All-Time row exactly.
2. **Per-session accumulation:** iterate the same **valid-winner** matches (`winner != null`); for each of the 4 fixed slots (A1, A2, B1, B2) whose `name.trim().lowercase(Locale.ROOT) == normalizedId`, accumulate into that match's `sessionId`: `games += 1`; points-for/against from that player's team score; `wins += 1` iff `winner == theirTeam`. Then per session `losses = games - wins`, `pointDiff = pf - pa`. (This mirrors `rankAllTime`'s attribution, so **`sum(records) == header totals` by construction** — an invariant the tests assert.)
3. **sessionsPlayed** = number of distinct `sessionId`s accumulated = `records.size`.
4. **Metadata join & missing-metadata handling:** for each accumulated `sessionId`, look up `SessionMeta` by id. If present → `sessionName`/`startTime` from it. **If absent → retain the accumulated contribution** (it still counts toward header totals and `sessionsPlayed`) and emit a record with `sessionName = null`, `startTime = null` (UI renders a non-tappable **"Session unavailable"** row). This keeps the breakdown totals consistent with the header even when a session record is missing.
5. **Active tagging:** `isActive = (sessionId == activeSessionId)`. The active session is **included** in the breakdown (its matches are in the same table and already count toward all-time), tagged "In progress" by the UI.
6. **Deterministic ordering:** `records` sorted by `startTime DESC` (a null `startTime` sorts **last**), then `sessionId` **ASC** as a stable fallback.

The function performs **no Android/Room access** and does not reference `HistoryData`.

### Thin UI-layer adapter

A small helper in the viewmodel layer (sibling to `buildSessionDetail`) adapts UI state to the pure call and keeps the screen simple:

```kotlin
fun resolvePlayerDetail(data: HistoryData, normalizedId: String, activeSessionId: String?): PlayerDetail
```

- If `data !is HistoryData.Loaded` → `NotAvailable`.
- Otherwise delegates to `buildPlayerDetail(matches = data.matchesBySession.values.flatten(), sessions = data.sessions, normalizedId, activeSessionId)`.

## Screen, navigation & state

### Entry point

- **All-Time rows become tappable** (gain ripple/tap affordance). Tapping row `r` pushes `PlayerDetail(r.id)` (`r.id` is the normalized name). This **reverses** the "non-interactive All-Time rows" decision from the Session History spec; the two-line All-Time subtitle is unchanged.

### In-screen navigation model (back stack replaces the single selection flag)

- `SessionHistoryScreen`'s single `selectedSessionId: String?` is replaced by an **in-screen back stack** of detail routes:

  ```kotlin
  sealed interface DetailRoute {
      data class SessionDetail(val sessionId: String) : DetailRoute
      data class PlayerDetail(val normalizedId: String) : DetailRoute
  }
  ```

- The History/All-Time `TabRow` + tab lists remain the **base** (with their two hoisted `LazyListState`s, unchanged). Pushing a route shows it over the base; the visible view is always `backStack.lastOrNull()` (base tab list when empty).
- **Drill levels:** All-Time → **Player Detail** → Session Detail (tapping a breakdown row pushes `SessionDetail`); History tab → Session Detail (unchanged). Session Detail can therefore be reached from two places; the back stack makes "Back" return to wherever it was pushed from.
- **Back (button and system) pops exactly one entry.** Popping the last entry returns to the tab list (tab selection + per-tab scroll preserved). Back from the tab list → `navigateTo(origin)`. A single shared `onBack` lambda drives both the `BackHandler` and the Back button (parity, as today).

### Per-route scroll preservation across navigation and Activity recreation

Each back-stack entry owns its own saved scroll position so returning to it restores the exact spot. The mechanism separates the **visible** route (whose scroll must be captured live, even when the capture trigger is Activity recreation rather than a navigation) from the **buried** routes (frozen since the moment they were navigated away from):

- **The visible (top) route** renders in a `LazyColumn` whose `LazyListState` is obtained via `rememberSaveable(routeKey, saver = LazyListState.Saver)` — keyed by the route's stable key (e.g. `"P:<normalizedId>"` / `"S:<sessionId>"`). This delegates live-position capture to Compose's own saveable registry, so the top route's exact scroll is snapshotted **at recreation time** (config change / Activity recreation), not only at navigation time — closing the "stale anchor on recreation" gap.
- **Buried routes:** when the user navigates deeper (push) or back (pop), the outgoing top route's current `(firstVisibleItemIndex, firstVisibleItemScrollOffset)` is captured into its stack entry before it leaves composition. A buried entry cannot scroll (it isn't composed), so this captured anchor stays correct until the entry becomes visible again, at which point its `LazyListState` is seeded from it.
- **The stack itself** — the ordered list of routes plus each buried entry's captured anchor — is held in `rememberSaveable` via a custom `Saver` (each route encodes to a short string; each anchor as its two ints), so the whole drill path is restored on Activity recreation.
- **Scope of "recreation":** this covers **Activity/configuration-change recreation** (rotation, dark-mode toggle, etc.) — exactly what `StateRestorationTester` exercises. **Full process death is out of scope** and is a pre-existing app-wide limitation: `SessionViewModel.currentScreen` initializes to `SessionHub` with no `SavedStateHandle` (`SessionViewModel.kt`), so after process death the app returns to its start screen and `SessionHistoryScreen` is not recomposed at all — the same limitation the merged Session History screen already has. Restoring the app route across process death is a separate, app-wide forward-note, not part of this slice.
- Result: returning from Session Detail to Player Detail restores Player Detail's exact scroll, and Activity recreation restores the visible route's live scroll and the buried drill path. The base tab lists keep their existing hoisted-state preservation.

### Player Detail layout (one scrolling container)

- **A single `LazyColumn`** (one vertical scroll) containing, in order:
  - **Header item(s):** the display name; a small caption **"Records grouped by name across sessions."** (identity caveat); and the core stats — **all-time rank, W–L, total games, sessions played, point differential**. (No win% — still deferred.)
  - **A "Sessions" heading** item.
  - **Breakdown rows** (`records`, already ordered newest-first): each shows session name · date · `games` · `W–L` · `diff`. A row whose session is **active** shows an **"In progress"** chip. A row with missing metadata shows **"Session unavailable"** and is **non-tappable**. All other rows are **tappable** → push `SessionDetail(sessionId)`.
- Styling follows session detail and the `LocalPickItTokens` design system. Opening the active session's detail is **read-only** and **must not change which session is active** (`buildSessionDetail` reads persisted data only; no `loadSession`/write path is touched). Its detail reflects persisted data, which may lag the active session's in-memory in-progress state — acceptable for a read-only historical view.

## UI states (distinct)

- **Found** → the layout above.
- **NotAvailable → "Player no longer available"** with a return action (pops the route). This covers a **restored selection whose name no longer has any qualifying results** (e.g. the underlying data changed while the detail was backgrounded). There is **no** separate empty-breakdown screen — a `Found` result always has ≥1 record (a name only reaches `Found` if it has ≥1 valid-winner match).
- **Loading / Error** are inherited from the host screen's `HistoryUiState` (detail routes render only over `Content`, consistent with the fix in PR #9 that gates detail rendering on load state).

## Testing plan (TDD)

**Pure (`buildPlayerDetail`, JUnit — no Android/Room):**
- Header equals the player's All-Time row (rank, W–L, games, point diff, display spelling) for a representative multi-session fixture.
- **Invariant:** `sum(records)` for games/wins/losses/pointDiff equals the header totals.
- **sessionsPlayed** = distinct sessions with ≥1 valid-winner match for the name; a session where the name appears **only** in null-winner matches does **not** count (and contributes nothing).
- **Missing session metadata:** a qualifying `sessionId` with no `SessionMeta` still appears as a record (`sessionName == null`, `startTime == null`) and its contribution is retained in the header totals; ordering places it last (null `startTime`), tie-broken by `sessionId`.
- **Active tagging:** the record whose `sessionId == activeSessionId` has `isActive == true`; others `false`; `null` activeSessionId ⇒ none active.
- **Ordering:** `startTime DESC`, then `sessionId ASC` stable (including the null-startTime-last rule).
- **NotAvailable:** an unknown normalized id, and a name present only in null-winner matches, both return `NotAvailable`.
- **Same normalized name in multiple slots of one match:** when one match has the id in two slots (e.g. partnering with a same-normalized name), both slots are counted independently — `games += 2` for that one match, and (if the two slots are on opposite teams) one win and one loss — mirroring `rankAllTime`'s per-slot attribution. Assert the `sum(records) == header` invariant still holds. (Documents/protects the shared-name double-count behavior.)
- Normalization is locale-independent (`Locale.ROOT`), consistent with `rankAllTime`.

**Screen (Robolectric/Compose):**
- **All-Time row is now tappable** → Player Detail opens; header stats render and match the tapped row.
- Player Detail is **one scroll** (header + "Sessions" + rows in a single `LazyColumn`); breakdown is newest-first.
- **Active session** appears in the breakdown tagged **"In progress"**; opening its detail does **not** change `SessionViewModel.session`'s active id.
- **Missing session metadata** → a non-tappable **"Session unavailable"** breakdown row; header totals still reflect it.
- Breakdown **row tap → Session Detail**; **Back pops one level** (Session Detail → Player Detail → All-Time tab → origin), preserving tab + scroll at each level.
- **Player Detail scroll is restored** after returning from Session Detail (scroll the breakdown, open a session, Back → same scroll position), and **across Activity/configuration-change recreation** (`StateRestorationTester`) — the visible route's live scroll and the buried drill path both restore. (Process-death route restoration is explicitly out of scope, per the navigation section.)
- **Vanished selection:** a restored `PlayerDetail` whose name no longer has qualifying results shows **"Player no longer available"** with a return action.
- Identity caption **"Records grouped by name across sessions."** is present on Player Detail.

## Non-goals / forward-notes

- **No win% / performance rating** (still deferred; needs a minimum-games rule and its own framing).
- **No rename / merge / delete / identity management.**
- **No schema changes and no change to the write path.** The pre-existing `completeMatch` non-atomic-persistence risk remains a forward-note (Player Detail, like All-Time, reads match-derived data and inherits the same documented caveat).
- **No id-based (session-scope) player view.** Reconciling the normalized-name identity with per-session roster ids is out of scope; the per-session breakdown here is match-derived and name-keyed, and can differ from a session's roster **Standings** after an interrupted write or when two same-named people shared a session (documented caveat).
