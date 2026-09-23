# Session History — Design Spec

- **Date:** 2026-09-23
- **Status:** Approved (brainstorming gate passed)
- **Scope:** A read-only, two-tab Session History feature — **History** (browse past sessions → session detail) and **All-Time** (cross-session aggregate leaderboard). Derives entirely from data already persisted. **No schema changes, no new writes, no change to the write path.**
- **Relation to prior work:** The cross-session / all-time counterpart to **Live Ranking** (PR #8), which was scoped to the current session only. Third and final queued feature (serve/callout PR #7 and Live Ranking PR #8 both merged).

## Goal

Let a host review previous open-play sessions and an all-time leaderboard on their phone, without loading a session, reusing the persisted match/roster data and the existing `RankingEngine`.

## What already exists (verified against the code)

- **Sessions** persist to a `sessions` table; `SessionDao.getAllSessions()` returns them all as a `Flow`, newest-first by `lastUpdated`.
- **Completed matches** persist to a `matches` table (`CompletedMatchEntity`) via `SessionRepository.recordCompletedMatch`, storing team player **names** (not ids), `scoreA/scoreB`, `winnerTeam` (`"TEAM_A"`/`"TEAM_B"`/`"UNKNOWN"`), `startTime`, `endTime`.
- **Per-session roster** persists to a `roster` table (`PlayerEntity`) keeping `playerId` **and** the authoritative aggregates (`matchesPlayed`, `matchesWon`, `totalPointsScored`, `totalPointsConceded`).
- **No session "completed" state:** sessions are never marked `isCompleted`; `startNewSession` mints a new session and leaves the previous one in the DB as a de-facto past session. On launch the app loads the *latest* session **only when its roster is non-empty** (`SessionViewModel.kt:107`); a session mid-creation (empty roster) is active in-memory but is not re-installed as active after a restart.
- **Stat integrity (verified):** roster aggregates are mutated in exactly one place — `SessionViewModel.completeMatch` (`SessionViewModel.kt:341-356`), always alongside `recordCompletedMatch` (`:327`); point attribution is symmetric (team A players `+scoreA/−scoreB`, team B the mirror). `abandonMatch` (`:406`) frees the court only — no stat change, no match recorded. There is **no** stat-edit/correction/forfeit path, and undo applies only to the in-progress match (pre-completion). So aggregating a match's team scores reproduces roster totals — **subject to the identity and atomicity caveats below.**

### Identity models (two, deliberately separated)

- **Session scope** (Top player + session-detail Standings): keyed by persisted **roster `playerId`**, run through `RankingEngine`. **Historical standings reproduce the persisted roster rankings** — i.e. what Live Ranking ranked for that session. No same-name merge within a session.
- **All-Time scope**: keyed by **normalized name** (`name.trim().lowercase(Locale.ROOT)`), because match records store names only. **Documented caveat (shown in the UI):** two different people who share a name merge into one all-time row, and a person is one row per normalized name. Not claimed equivalent to any per-session view.

### Consistency caveat (non-atomic write — deferred, not fixed here)

In the existing `completeMatch`, `recordCompletedMatch(...)` and the roster save (`persistSession` → `saveSession`, whose `@Transaction` covers session+courts+roster but **not** the match insert) run in **separate coroutines / separate transactions**. They are **not atomic**: a crash between them can leave a match without its roster increment, or vice versa. Precise consequence for this feature: **historical (session-scope) standings reproduce the persisted roster rankings; match-derived All-Time totals can diverge from roster following an interrupted write.** Session History reads both sources defensively and never assumes they agree. Making the write atomic is a **forward-note**, out of scope for this read-only feature.

## Architecture & data flow

**Single ranking primitive — reuse the shipped `RankingEngine`.** Aggregate matches (or read a persisted roster) into per-player tallies, wrap each as a synthetic `Player`, and call the existing `RankingEngine.rank()` (wins → point-diff → name → id, competition ranks). One primitive drives every ranking:

| Use | Source | Identity |
|---|---|---|
| Session "Top player" (History list) | persisted roster | player id |
| Session "Standings" (session detail) | persisted roster | player id |
| All-Time leaderboard | all completed matches (valid winner only) | normalized name |

**Layers:**
- **Pure (unit-tested, no Android/Room):** `HistoryAggregator` and `sessionSummary` (below).
- **Data:** `SessionRepository` gains read paths; Room entities are mapped to DB-free domain types (`MatchResult`, and `PlayerEntity`→`Player`) so the pure layer never imports Room.
- **UI state:** a dedicated **`HistoryViewModel`**, backed by **reactive** repository reads, usable with no loaded session. Two bulk reads (all matches, all rosters) grouped in memory feed *both* the per-session summaries and the All-Time aggregate — **no per-row queries.**

## Pure-logic contracts

### Domain input (repository maps `CompletedMatchEntity` → this; DB-free)

```kotlin
data class MatchResult(
    val matchId: String,
    val sessionId: String,
    val teamA: List<String>,   // [A1, A2] names, fixed slot order
    val teamB: List<String>,   // [B1, B2] names, fixed slot order
    val scoreA: Int,
    val scoreB: Int,
    val winner: TeamId?,       // null when winnerTeam is not TEAM_A/TEAM_B ("UNKNOWN"/malformed)
    val startTime: Long,
    val endTime: Long,
)
```

### All-Time aggregator (pure)

```kotlin
fun rankAllTime(matches: List<MatchResult>): List<RankedPlayer>
```

- **Only matches with a non-null `winner` contribute to All-Time.** A match with `winner == null` is **skipped entirely** — no games, no points, no loss. (It still appears in that session's match list; see UI.)
- For each contributing match, credit each of the 4 slots in fixed order (A1, A2, B1, B2): `games += 1`; points-for/against from that player's team score; `win += 1` iff `winner == theirTeam`.
- Group by `normalized = name.trim().lowercase(Locale.ROOT)`. Blank/empty normalized names are ignored (defensive; doubles always supplies 4 names).
- **Display spelling** chosen deterministically per group: highest `endTime` → highest `matchId` → **lowest fixed slot-index** (closes the tie where two slots in one match normalize equal).
- Build a synthetic `Player` **inside the aggregator only** (`id = normalized`, **`name = normalized`** too, `matchesWon = wins`, `matchesPlayed = games`, `totalPointsScored`/`Conceded` = totals; fabricated `AVAILABLE` status never escapes), then `RankingEngine.rank(...)`. **After ranking, relabel each output row's `name` to the chosen display spelling** — a post-rank relabel that never affects ordering. Output `RankedPlayer` with `id =` normalized name and `name =` display spelling.
- **Determinism:** ranking sorts on the `Locale.ROOT`-normalized name (both the synthetic `name` — the primary tie key after wins/diff — and the synthetic `id` are that normalized string), and display spelling is applied only *after* ranking, so tie-row order does not depend on device locale. Competition rank numbers are always locale-independent. (RankingEngine re-lowercases the already-normalized name with the default locale — a no-op except for a handful of pathological Unicode cases, which at most reorder exact ties, never ranks.) A determinism test asserts stable ordering for representative names.
- UI reads **Games = `wins + losses`** from the row.

### Session summary (pure)

```kotlin
data class PlayerRef(val id: String, val name: String)
data class SessionSummary(
    val gameCount: Int,          // completed matches for the session, counted once each (from matches)
    val participantCount: Int,   // roster players with matchesPlayed >= 1
    val leaders: List<PlayerRef> // rank-1 entries (id + name); empty when there are no ranked roster players (roster-driven, independent of gameCount)
)

fun sessionSummary(roster: List<Player>, completedMatchCount: Int): SessionSummary
```

- `gameCount = completedMatchCount` (from the matches table — includes malformed "Result unavailable" matches, which are still recorded completed matches).
- `ranked = RankingEngine.rank(roster)` (filters `matchesPlayed >= 1`). `participantCount = ranked.size`. `leaders = ranked.filter { it.rank == 1 }.map { PlayerRef(it.id, it.name) }` — **ids retained so identical names stay distinct**; empty when there are no ranked players.
- Semantic only — the **UI** decides the second-line copy from `gameCount` and `leaders`, and the two can disagree after an interrupted write (see the Consistency caveat). **Mismatch-aware rule:**
  - `gameCount == 0` **and** no leaders → "No completed games".
  - `gameCount > 0` **and** leaders present → the normal leader line (below).
  - **`gameCount > 0` and no leaders** (match recorded, roster increment lost), **or** `gameCount == 0` and leaders present (roster stats without recorded matches) → **"Standings unavailable"** — never claim "No completed games" when games exist, and never show a top player beside zero games.
  - `gameCount` is always displayed as-is on the first line regardless.

### Session detail

- **Standings**: `RankingEngine.rank(roster)` (roster/id-based — reproduces the persisted roster ranking).
- **Match list**: the session's `MatchResult`s ordered **`endTime DESC, matchId DESC`** (newest-first, stable). A match with `winner == null` renders as **"Result unavailable."**

## Data / ViewModel

- **New DAO reads (both reactive, off-main via Room's Flow):** `getAllMatches(): Flow<List<CompletedMatchEntity>>` (`SELECT * FROM matches ORDER BY endTime DESC`) and bulk `getAllRoster(): Flow<List<PlayerEntity>>` (`SELECT * FROM roster`). Both are `Flow` (not blocking `suspend` list reads) to match `getAllSessions()`, avoid any main-thread Room access, and update the board when roster-only rows change. `getAllSessions()` already exists.
- **Active-session identity + readiness (wiring contract).** `SessionViewModel.session` is a `StateFlow<OpenPlaySession?>` that **starts `null` and is populated asynchronously** by `loadLatestSession()` on init (`SessionViewModel.kt:83-120`). A bare `null` therefore cannot distinguish "still loading" from "genuinely no active session," so relying on it directly would let History momentarily mis-classify the active session as past. To fix this: `SessionViewModel` exposes a small additive **`isSessionLoaded: StateFlow<Boolean>`** (flips true once the initial load settles — an in-memory read flag, not a schema or write-path change). `HistoryViewModel` is constructed with (or `combine`s) the shared `SessionViewModel`'s `session` and `isSessionLoaded` flows (the `SessionHistoryScreen` is hosted in `MainActivity`, which already holds the one `SessionViewModel`, and passes them in). **Until `isSessionLoaded` is true, `HistoryUiState` stays `Loading`** (never a wrongly-filtered list). Once loaded, the active id = `session.value?.id`; only a genuinely absent session (loaded && null) includes every session.
- **`HistoryViewModel`** combines the reactive session/match/roster reads **and the active-session id + readiness taken from application state** (**independent of entry origin**) into:

```kotlin
sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Content(
        val pastSessions: List<SessionListItem>,   // newest-first, built from session records
        val allTime: List<RankedPlayer>,
    ) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}
```

- `pastSessions` **excludes the real active session id whenever a session is active** (from any origin); it includes every session only when there is truly no active session. Built from **session records**, so zero-game sessions remain visible with their attached (empty) summary.
- Session detail is derived from the already-loaded bulk data: `SessionDetail = Found(name, date, standings, matches) | NotAvailable`.

## Screens, navigation & state

- **`SessionHistoryScreen`** — an ordinary (non-full-bleed) screen, `TabRow` with **History · All-Time**. It renders **inside `MainActivity`'s existing padded `Surface`** (the inner exhaustive `when`), so it **does not apply its own `safeDrawingPadding()`** — that would double-inset. (Unlike Live Ranking/Court Call, it is *not* added to the outer full-bleed branch.)
  - **History tab:** `LazyColumn` of past sessions (newest-first). Row: `name` + `date · N games · M players`, optional second line `Top player:` / `Joint leaders:` / `No completed games`. Row is tappable → session detail.
    - **Date source:** the session's `startTime`, formatted with a locale-aware medium date (e.g. "23 Sep 2026"). The detail header uses the same.
    - **Leader-line formatting** (from `SessionSummary`, applying the mismatch-aware rule above): "No completed games" / "Standings unavailable" per that rule; otherwise from `leaders` (already in ranked order) — 1 → "Top player: {name}"; 2 → "Joint leaders: {a} & {b}"; ≥3 → "Joint leaders: {first} + {count−1} others".
  - **All-Time tab:** two-line subtitle — **"Ranked by total wins, then point difference."** and **"Grouped by name across sessions. Use consistent, distinct names."** — then rows `Rank | Player | W–L | Games | Diff`. **Non-interactive** (no chevron, no ripple/tap styling).
- **Session detail** — **one vertical scroll** (`LazyColumn`): header (session name + date), a **"Standings"** section (roster-based ranked rows), then the **match list** (`endTime DESC, matchId DESC`); each match shows team A names · `scoreA–scoreB` · team B names (winner emphasized) · time, or "Result unavailable."
- **Navigation & state:**
  - New `AppScreen.SessionHistory` carrying its **origin** (`SessionHub` or `Setup`); Back returns to the origin.
  - Each tab keeps its **own** `LazyListState`; selected tab + `selectedSessionId` survive recreation (`rememberSaveable`).
  - **Both system Back and the screen's Back action** follow the same **detail → list → origin** behavior. Back from detail clears `selectedSessionId` and returns to the list with **tab + scroll preserved**; Back from the list → `navigateTo(origin)`.
  - **Entry points (must cover the no-active-session state):**
    - **Session Hub, active branch:** a Session History button beside Live Ranking/Court Call.
    - **Session Hub, empty branch:** `SessionHubScreen` returns early with only "Start a session" when `session == null` (`SessionHubScreen.kt:41-71`) — the board buttons don't exist there. So add a **secondary "Session History" action in that empty branch too** (below "Start a session"), so History is reachable from the Hub regardless of session state.
    - **Setup:** a **secondary** "Session History" action (primary action stays create/load).
    - Each entry sets `AppScreen.SessionHistory(origin = <that screen>)`; History reads from the DB and requires no active session.

## UI states (distinct)

- **Loading vs Empty vs Error are distinct.** Never flash "No past sessions" while reads are pending (show a loading indicator until `Content`).
- **Empty:** fresh install / no past sessions → a clear empty state per tab.
- **Error:** a read failure surfaces an error state, not an empty list.
- **Vanished selection:** if `selectedSessionId` is no longer present in loaded data, show **"Session no longer available"** with a return action.

## Testing plan (TDD)

**Pure (JUnit):**
- `rankAllTime`: score→per-player for/against attribution; wins credited by `winner` team; **null-winner matches excluded entirely** (no games/points/loss); normalized-name merge (case + whitespace, `Locale.ROOT`); Games = wins+losses; competition ranks (ties share a number, 1,2,2,4); **display-spelling determinism** (endTime → matchId → slot); empty input → empty.
- `sessionSummary`: `gameCount` from match count (incl. a malformed match), `participantCount` from roster `matchesPlayed >= 1`, tie leaders returning **multiple `PlayerRef`s that retain ids for identical names**; **`leaders` is roster-driven** — empty ⟺ no ranked roster players, and a `gameCount == 0` session whose roster still has ranked players (inverse interrupted-write) returns **non-empty** leaders (the UI then renders "Standings unavailable").
- Normalization is locale-independent (`Locale.ROOT`).

**Screen (Robolectric/Compose):**
- Tabs render; History excludes the active session id (and includes all when none active), newest-first, **zero-game session visible** with "No completed games"; summary / "Joint leaders" / "Top player" text.
- **Readiness:** while `isSessionLoaded` is false the state is `Loading` — the active session is **never** transiently shown as a past row; once loaded, it is excluded.
- **Mismatch wording:** a session with `gameCount > 0` but no roster leaders (simulated interrupted write) shows **"Standings unavailable"**, not "No completed games"; the inverse never shows a top player beside zero games.
- All-Time rows content + **two-line subtitle** + **non-interactive** (no navigation on tap).
- **Loading ≠ Empty ≠ Error** (no empty flash during load).
- Tap History row → detail; **one-scroll** standings + matches (newest-first); "Result unavailable" for a null-winner match; Back preserves tab + scroll; **detail → list → origin** for both system Back and the Back button; **vanished session** → "Session no longer available".
- Entry from **Hub (active branch), Hub (empty/no-active-session branch), and Setup**, each returning to the correct origin; active session excluded regardless of origin.

## Non-goals / forward-notes

- **No player-detail screen** and **no "sessions played" stat** (deferred together as the next slice).
- **No win% / performance rating** (future view, with a clearly-stated minimum-games requirement and its own sort).
- **No delete / rename / merge** of sessions or players; **no identity-management** features.
- **No schema changes and no change to the write path.** The `completeMatch` non-atomic-persistence risk is a **forward-note** (wrap the match insert + roster save in one transaction later), not fixed here.
- No TV/big-screen layout (this is a phone-oriented interactive screen, unlike Live Ranking).
