# Player Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the subagent-build skill (`/subagent-build`) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only **Player Detail** view — one normalized-name player's all-time record (rank, W–L, games, **sessions played**, point diff) plus a per-session breakdown — reached by tapping an **All-Time** leaderboard row, with an in-screen back stack so All-Time → Player Detail → Session Detail each preserve their own scroll.

**Architecture:** A pure `HistoryAggregator.buildPlayerDetail` (engine, no Android/Room) reuses the shipped `rankAllTime` for the header and mirrors its per-slot attribution for the breakdown, returning `PlayerDetail.Found | NotAvailable`. A thin `resolvePlayerDetail` adapter (viewmodel layer) maps UI state to that pure call. `SessionHistoryScreen`'s single `selectedSessionId` is replaced by a `DetailRoute` back stack (`SessionDetail` | `PlayerDetail`) with per-route scroll preservation; All-Time rows become tappable.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, StateFlow, JUnit4 (pure logic), Robolectric + Compose UI test (screen). Gradle.

**Spec:** [docs/specs/2026-09-24-player-detail-design.md](../specs/2026-09-24-player-detail-design.md) — passed self-review + `/codex-review` design-spec gate (APPROVE round 2).

**Suggested branch:** `feature/player-detail` (created by `/worktrees` at execution time).

**Commit discipline (project rules):** commit via the `commit` skill; stage only the explicit paths named in each commit step (never `git add -A` — the worktree carries untracked `gradlew`, `local.properties`, `debug.keystore`, `.idea/`, `gradle/wrapper/*`, `gradle.properties`, `gradle/gradle-daemon-jvm.properties` that must NOT be staged). No Claude co-author trailer; no "Generated with Claude Code" line.

**Test commands:**
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.PlayerDetailAggregatorTest"
./gradlew :app:testDebugUnitTest --tests "com.example.viewmodel.ResolvePlayerDetailTest"
./gradlew :app:testDebugUnitTest --tests "com.example.ui.SessionHistoryScreenTest"
```

---

## File Structure

- Create `app/src/main/java/com/example/model/PlayerDetailModels.kt` — DB-free domain types: `PlayerSessionRecord`, `PlayerDetailData`, `PlayerDetail`.
- Modify `app/src/main/java/com/example/engine/HistoryAggregator.kt` — add pure `buildPlayerDetail(...)` (reuses `rankAllTime` + private `normalize`).
- Modify `app/src/main/java/com/example/viewmodel/HistoryState.kt` — add `resolvePlayerDetail(data, normalizedId, activeSessionId)` adapter (sibling of `buildSessionDetail`).
- Modify `app/src/main/java/com/example/ui/screens/SessionHistoryScreen.kt` — replace `selectedSessionId` with a `DetailRoute` back stack + per-route scroll; make All-Time rows tappable; add `PlayerDetailView`.
- Tests: `app/src/test/java/com/example/PlayerDetailAggregatorTest.kt`, `app/src/test/java/com/example/viewmodel/ResolvePlayerDetailTest.kt`, and additions to the existing `app/src/test/java/com/example/ui/SessionHistoryScreenTest.kt`.

**No** schema change, **no** DAO/repository change, **no** `SessionViewModel` change, **no** new `AppScreen` — Player Detail is internal screen state, entered by tapping an All-Time row (`session?.id` and `isSessionLoaded` already exist on `SessionViewModel`).

---

## Task 1: Domain types + pure `buildPlayerDetail`

**Files:**
- Create: `app/src/main/java/com/example/model/PlayerDetailModels.kt`
- Modify: `app/src/main/java/com/example/engine/HistoryAggregator.kt`
- Test: `app/src/test/java/com/example/PlayerDetailAggregatorTest.kt`

- [ ] **Step 1: Create the domain types**

`app/src/main/java/com/example/model/PlayerDetailModels.kt`:
```kotlin
package com.example.model

/** One session's slice of a player's cross-session record (match-derived, name-keyed). */
data class PlayerSessionRecord(
    val sessionId: String,
    val sessionName: String?,   // null when the session's metadata is missing -> UI: non-tappable "Session unavailable"
    val startTime: Long?,       // null when metadata missing (ordering: nulls sort last)
    val games: Int,
    val wins: Int,
    val losses: Int,            // games - wins
    val pointDiff: Int,
    val isActive: Boolean,      // sessionId == activeSessionId -> UI tags "In progress"
)

/** A single player's all-time record plus its per-session breakdown. */
data class PlayerDetailData(
    val id: String,             // normalized name (identity key)
    val displayName: String,    // deterministic display spelling (from rankAllTime)
    val rank: Int,              // all-time competition rank
    val wins: Int,
    val losses: Int,
    val games: Int,             // wins + losses
    val pointDiff: Int,
    val sessionsPlayed: Int,    // == records.size
    val records: List<PlayerSessionRecord>, // ordered startTime DESC (nulls last), then sessionId ASC
)

sealed interface PlayerDetail {
    data class Found(val data: PlayerDetailData) : PlayerDetail
    data object NotAvailable : PlayerDetail   // id has no qualifying (valid-winner) results
}
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/example/PlayerDetailAggregatorTest.kt`:
```kotlin
package com.example

import com.example.engine.HistoryAggregator
import com.example.model.MatchResult
import com.example.model.PlayerDetail
import com.example.model.SessionMeta
import com.example.model.TeamId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDetailAggregatorTest {

    private fun match(
        id: String, session: String,
        a1: String, a2: String, b1: String, b2: String,
        scoreA: Int, scoreB: Int, winner: TeamId?,
        endTime: Long = 0L,
    ) = MatchResult(
        matchId = id, sessionId = session,
        teamA = listOf(a1, a2), teamB = listOf(b1, b2),
        scoreA = scoreA, scoreB = scoreB, winner = winner,
        startTime = 0L, endTime = endTime,
    )

    private fun meta(id: String, name: String, startTime: Long) = SessionMeta(id, name, startTime)

    // Ann&Bo beat Cy&Dot 11-4 in s1, Ann&Bo beat Cy&Dot 11-6 in s2.
    private fun twoSessions() = listOf(
        match("m1", "s1", a1 = "Ann", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 4, winner = TeamId.TEAM_A, endTime = 1),
        match("m2", "s2", a1 = "Ann", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 6, winner = TeamId.TEAM_A, endTime = 2),
    )
    private fun twoMetas() = listOf(meta("s1", "Mon", 100), meta("s2", "Tue", 200))

    private fun found(d: PlayerDetail): PlayerDetail.Found {
        assertTrue("expected Found but was $d", d is PlayerDetail.Found)
        return d as PlayerDetail.Found
    }

    @Test fun `header equals the all-time row and sessionsPlayed counts distinct sessions`() {
        val d = found(HistoryAggregator.buildPlayerDetail(twoSessions(), twoMetas(), "ann", activeSessionId = null)).data
        val allTimeAnn = HistoryAggregator.rankAllTime(twoSessions()).single { it.id == "ann" }
        assertEquals(allTimeAnn.rank, d.rank)
        assertEquals(allTimeAnn.wins, d.wins)
        assertEquals(allTimeAnn.losses, d.losses)
        assertEquals(allTimeAnn.pointDiff, d.pointDiff)
        assertEquals(allTimeAnn.wins + allTimeAnn.losses, d.games)
        assertEquals("Ann", d.displayName)
        assertEquals(2, d.sessionsPlayed)
        assertEquals(2, d.records.size)
    }

    @Test fun `sum of records equals the header totals`() {
        val d = found(HistoryAggregator.buildPlayerDetail(twoSessions(), twoMetas(), "ann", null)).data
        assertEquals(d.games, d.records.sumOf { it.games })
        assertEquals(d.wins, d.records.sumOf { it.wins })
        assertEquals(d.losses, d.records.sumOf { it.losses })
        assertEquals(d.pointDiff, d.records.sumOf { it.pointDiff })
    }

    @Test fun `records are ordered by startTime DESC then sessionId`() {
        val d = found(HistoryAggregator.buildPlayerDetail(twoSessions(), twoMetas(), "ann", null)).data
        assertEquals(listOf("s2", "s1"), d.records.map { it.sessionId }) // Tue(200) before Mon(100)
    }

    @Test fun `null-winner-only session does not count and is excluded`() {
        val matches = twoSessions() + match("m9", "s9", a1 = "Ann", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 9, scoreB = 9, winner = null, endTime = 9)
        val metas = twoMetas() + meta("s9", "Tie Night", 900)
        val d = found(HistoryAggregator.buildPlayerDetail(matches, metas, "ann", null)).data
        assertEquals(2, d.sessionsPlayed)                       // s9 excluded (no valid winner)
        assertTrue(d.records.none { it.sessionId == "s9" })
    }

    @Test fun `missing session metadata is retained with null name and sorts last`() {
        // s1 has a meta; "ghost" has qualifying matches but no SessionMeta.
        val matches = listOf(
            match("m1", "s1", a1 = "Ann", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 4, winner = TeamId.TEAM_A, endTime = 1),
            match("m2", "ghost", a1 = "Ann", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 2, winner = TeamId.TEAM_A, endTime = 2),
        )
        val d = found(HistoryAggregator.buildPlayerDetail(matches, listOf(meta("s1", "Mon", 100)), "ann", null)).data
        assertEquals(2, d.sessionsPlayed)                       // ghost retained
        val ghost = d.records.single { it.sessionId == "ghost" }
        assertEquals(null, ghost.sessionName)
        assertEquals(null, ghost.startTime)
        assertEquals("ghost", d.records.last().sessionId)       // null startTime sorts last
        assertEquals(d.games, d.records.sumOf { it.games })     // header still consistent
    }

    @Test fun `active session is tagged`() {
        val d = found(HistoryAggregator.buildPlayerDetail(twoSessions(), twoMetas(), "ann", activeSessionId = "s2")).data
        assertTrue(d.records.single { it.sessionId == "s2" }.isActive)
        assertTrue(!d.records.single { it.sessionId == "s1" }.isActive)
    }

    @Test fun `unknown id and null-winner-only name both yield NotAvailable`() {
        assertEquals(PlayerDetail.NotAvailable, HistoryAggregator.buildPlayerDetail(twoSessions(), twoMetas(), "nobody", null))
        val onlyNull = listOf(match("m1", "s1", a1 = "Zed", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 9, scoreB = 9, winner = null, endTime = 1))
        assertEquals(PlayerDetail.NotAvailable, HistoryAggregator.buildPlayerDetail(onlyNull, listOf(meta("s1", "s1", 1)), "zed", null))
    }

    @Test fun `same normalized name in two slots of one match double-counts, invariant holds`() {
        // "Ann" partners with "ANN" (same normalized id) and they beat Cy&Dot.
        val matches = listOf(
            match("m1", "s1", a1 = "Ann", a2 = "ANN", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 3, winner = TeamId.TEAM_A, endTime = 1),
        )
        val d = found(HistoryAggregator.buildPlayerDetail(matches, listOf(meta("s1", "Mon", 1)), "ann", null)).data
        val rec = d.records.single { it.sessionId == "s1" }
        assertEquals(2, rec.games)                              // both slots counted
        assertEquals(2, rec.wins)                              // both on the winning team
        assertEquals(d.games, d.records.sumOf { it.games })     // invariant
        assertEquals(d.wins, d.records.sumOf { it.wins })
    }
}
```

- [ ] **Step 3: Run the test to verify it FAILS**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-player-detail" && ./gradlew :app:testDebugUnitTest --tests "com.example.PlayerDetailAggregatorTest"
```
Expected: FAIL — `HistoryAggregator.buildPlayerDetail` does not exist (unresolved reference). Note: the first Gradle run may download dependencies (minutes) — let it run. If the build fails for an environment reason (missing SDK/keystore/wrapper), report BLOCKED; do not modify build config.

- [ ] **Step 4: Add `buildPlayerDetail` to `HistoryAggregator`**

In `app/src/main/java/com/example/engine/HistoryAggregator.kt`, add these imports next to the existing model imports (keep the existing ones):
```kotlin
import com.example.model.PlayerDetail
import com.example.model.PlayerDetailData
import com.example.model.PlayerSessionRecord
import com.example.model.SessionMeta
```
Then add this function inside the `object HistoryAggregator { ... }` (e.g. after `rankAllTime`, before `sessionSummary`). It reuses the existing private `normalize` and `rankAllTime`:
```kotlin
    /**
     * One player's cross-session record, keyed by normalized name (same identity as [rankAllTime]).
     * The header reuses the player's [rankAllTime] row (so it equals the All-Time leaderboard row
     * exactly); the per-session breakdown mirrors rankAllTime's per-slot attribution over the same
     * valid-winner matches, so sum(records) == header by construction. Sessions with missing
     * metadata are retained (name/startTime null). Returns NotAvailable if the id has no
     * valid-winner contribution.
     */
    fun buildPlayerDetail(
        matches: List<MatchResult>,
        sessions: List<SessionMeta>,
        normalizedId: String,
        activeSessionId: String?,
    ): PlayerDetail {
        val row = rankAllTime(matches).firstOrNull { it.id == normalizedId }
            ?: return PlayerDetail.NotAvailable

        data class Acc(var games: Int = 0, var wins: Int = 0, var pf: Int = 0, var pa: Int = 0)
        val bySession = LinkedHashMap<String, Acc>()
        for (m in matches) {
            val winner = m.winner ?: continue
            val slots = listOf(
                m.teamA.getOrNull(0) to TeamId.TEAM_A,
                m.teamA.getOrNull(1) to TeamId.TEAM_A,
                m.teamB.getOrNull(0) to TeamId.TEAM_B,
                m.teamB.getOrNull(1) to TeamId.TEAM_B,
            )
            for ((rawOrNull, team) in slots) {
                val raw = rawOrNull ?: continue
                if (normalize(raw) != normalizedId) continue
                val acc = bySession.getOrPut(m.sessionId) { Acc() }
                acc.games += 1
                if (team == TeamId.TEAM_A) { acc.pf += m.scoreA; acc.pa += m.scoreB }
                else { acc.pf += m.scoreB; acc.pa += m.scoreA }
                if (winner == team) acc.wins += 1
            }
        }

        val metaById = sessions.associateBy { it.id }
        val records = bySession.map { (sid, a) ->
            val meta = metaById[sid]
            PlayerSessionRecord(
                sessionId = sid,
                sessionName = meta?.name,
                startTime = meta?.startTime,
                games = a.games,
                wins = a.wins,
                losses = a.games - a.wins,
                pointDiff = a.pf - a.pa,
                isActive = sid == activeSessionId,
            )
        }.sortedWith(
            compareBy<PlayerSessionRecord> { it.startTime == null }   // non-null first, nulls strictly last
                .thenByDescending { it.startTime ?: 0L }               // newest-first within known dates
                .thenBy { it.sessionId }                               // stable fallback
        )

        return PlayerDetail.Found(
            PlayerDetailData(
                id = row.id,
                displayName = row.name,
                rank = row.rank,
                wins = row.wins,
                losses = row.losses,
                games = row.wins + row.losses,
                pointDiff = row.pointDiff,
                sessionsPlayed = records.size,
                records = records,
            )
        )
    }
```

- [ ] **Step 5: Run the test to verify it PASSES**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-player-detail" && ./gradlew :app:testDebugUnitTest --tests "com.example.PlayerDetailAggregatorTest"
```
Expected: PASS (all 8 tests).

- [ ] **Step 6: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/model/PlayerDetailModels.kt`
- `app/src/main/java/com/example/engine/HistoryAggregator.kt`
- `app/src/test/java/com/example/PlayerDetailAggregatorTest.kt`

Suggested message:
```
feat(player-detail): pure buildPlayerDetail aggregator

Reuse rankAllTime for the header and mirror its per-slot attribution for a
per-session breakdown (keyed by normalized name), so sum(records) == header.
Missing session metadata is retained; the active session is tagged; unknown or
null-winner-only names return NotAvailable.
```

---

## Task 2: `resolvePlayerDetail` adapter (viewmodel layer)

**Files:**
- Modify: `app/src/main/java/com/example/viewmodel/HistoryState.kt`
- Test: `app/src/test/java/com/example/viewmodel/ResolvePlayerDetailTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/viewmodel/ResolvePlayerDetailTest.kt`:
```kotlin
package com.example.viewmodel

import com.example.model.MatchResult
import com.example.model.PlayerDetail
import com.example.model.SessionMeta
import com.example.model.TeamId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvePlayerDetailTest {

    private fun match(id: String, session: String) = MatchResult(
        matchId = id, sessionId = session,
        teamA = listOf("Ann", "Bo"), teamB = listOf("Cy", "Dot"),
        scoreA = 11, scoreB = 5, winner = TeamId.TEAM_A, startTime = 0L, endTime = 1L,
    )

    private fun loaded() = HistoryData.Loaded(
        sessions = listOf(SessionMeta("s1", "Mon", 100)),
        matchesBySession = mapOf("s1" to listOf(match("m1", "s1"))),
        rosterBySession = emptyMap(),
    )

    @Test fun `returns NotAvailable when data is not Loaded`() {
        assertEquals(PlayerDetail.NotAvailable, resolvePlayerDetail(HistoryData.Loading, "ann", null))
        assertEquals(PlayerDetail.NotAvailable, resolvePlayerDetail(HistoryData.Error("x"), "ann", null))
    }

    @Test fun `Found for a known normalized id, NotAvailable for unknown`() {
        val d = resolvePlayerDetail(loaded(), "ann", null)
        assertTrue(d is PlayerDetail.Found)
        assertEquals("Ann", (d as PlayerDetail.Found).data.displayName)
        assertEquals(PlayerDetail.NotAvailable, resolvePlayerDetail(loaded(), "nobody", null))
    }

    @Test fun `active session id is threaded through to the breakdown`() {
        val d = resolvePlayerDetail(loaded(), "ann", activeSessionId = "s1") as PlayerDetail.Found
        assertTrue(d.data.records.single { it.sessionId == "s1" }.isActive)
    }
}
```

- [ ] **Step 2: Run to verify it FAILS**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-player-detail" && ./gradlew :app:testDebugUnitTest --tests "com.example.viewmodel.ResolvePlayerDetailTest"
```
Expected: FAIL — `resolvePlayerDetail` does not exist.

- [ ] **Step 3: Add the adapter**

In `app/src/main/java/com/example/viewmodel/HistoryState.kt`, add the import next to the existing `com.example.engine.HistoryAggregator` import:
```kotlin
import com.example.model.PlayerDetail
```
Then add this function at the end of the file (sibling of `buildSessionDetail`):
```kotlin
/** Resolve a player-detail view from loaded data; NotAvailable if not loaded or the id has no results. */
fun resolvePlayerDetail(data: HistoryData, normalizedId: String, activeSessionId: String?): PlayerDetail {
    if (data !is HistoryData.Loaded) return PlayerDetail.NotAvailable
    return HistoryAggregator.buildPlayerDetail(
        matches = data.matchesBySession.values.flatten(),
        sessions = data.sessions,
        normalizedId = normalizedId,
        activeSessionId = activeSessionId,
    )
}
```

- [ ] **Step 4: Run the test to verify it PASSES**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-player-detail" && ./gradlew :app:testDebugUnitTest --tests "com.example.viewmodel.ResolvePlayerDetailTest"
```
Expected: PASS (all 3 tests).

- [ ] **Step 5: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/viewmodel/HistoryState.kt`
- `app/src/test/java/com/example/viewmodel/ResolvePlayerDetailTest.kt`

Suggested message:
```
feat(player-detail): resolvePlayerDetail UI-state adapter

Thin sibling of buildSessionDetail that flattens HistoryData.Loaded's matches
and sessions and delegates to the pure HistoryAggregator.buildPlayerDetail,
threading the active-session id through.
```

---

## Task 3: SessionHistoryScreen — DetailRoute back stack, tappable All-Time rows, Player Detail view

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/SessionHistoryScreen.kt`
- Test: `app/src/test/java/com/example/ui/SessionHistoryScreenTest.kt`

This task replaces the screen's single `selectedSessionId: String?` with a `DetailRoute` back stack (`SessionDetail` | `PlayerDetail`), each stack entry carrying its own scroll anchor; makes All-Time rows tappable (→ Player Detail); and adds the Player Detail view. **All existing `SessionHistoryScreenTest` behaviors must stay green** except the one assertion that All-Time rows are non-clickable, which is intentionally flipped.

- [ ] **Step 1: Update the existing "non-interactive All-Time row" assertion (this is the RED for tappability) and add the new failing tests**

First, in `app/src/test/java/com/example/ui/SessionHistoryScreenTest.kt`, **add** this import next to the existing `import androidx.compose.ui.test.assertHasNoClickAction` (keep `assertHasNoClickAction` — the new missing-metadata test still uses it on the non-tappable ghost row):
```kotlin
import androidx.compose.ui.test.assertHasClickAction
```
Then in `allTimeTab_showsAggregateRows_andSubtitle` change the last assertion from:
```kotlin
        rule.onNodeWithTag("all_time_row_ann").assertHasNoClickAction()
```
to:
```kotlin
        rule.onNodeWithTag("all_time_row_ann").assertHasClickAction()
```

Then append these new tests to the same class (before its closing brace). They reuse the existing `repo()`, `player()`, `seedSession()`, `fourPlayers()`, `content()`, and `readyVmNoSession()` helpers already in the file:
```kotlin
    // ---- Player Detail ----

    private fun openAnnPlayerDetail() {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("all_time_tab").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("all_time_tab").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("all_time_row_ann").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("all_time_row_ann").performClick()
    }

    @Test fun allTimeRow_opensPlayerDetail_withCaptionAndBreakdown() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 2)
        val vm = readyVmNoSession()
        content(vm)
        openAnnPlayerDetail()
        rule.onNodeWithTag("player_detail").assertIsDisplayed()
        rule.onNodeWithText("Records grouped by name across sessions.").assertIsDisplayed()
        rule.onNodeWithText("Ann").assertIsDisplayed()                              // header display name
        rule.onNodeWithText("Rank #1", substring = true).assertIsDisplayed()        // header stat
        rule.onNodeWithText("sessions played", substring = true).assertIsDisplayed() // sessions-played stat
        rule.onNodeWithTag("player_session_row_s1").assertIsDisplayed()
    }

    @Test fun playerDetail_listsAllSessionsForTheName() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 1, startTime = 100)
        seedSession("s2", "Tue", fourPlayers("s2"), wins = 1, startTime = 200)
        val vm = readyVmNoSession()
        content(vm)
        openAnnPlayerDetail()
        rule.onNodeWithTag("player_session_row_s1").assertIsDisplayed()
        rule.onNodeWithTag("player_session_row_s2").assertIsDisplayed()
    }

    @Test fun playerDetailRow_opensSessionDetail_backPopsOneLevel() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 2)
        val vm = readyVmNoSession()
        content(vm)
        openAnnPlayerDetail()
        rule.onNodeWithTag("player_session_row_s1").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        // First Back: session detail -> player detail (pop one level).
        rule.onNodeWithTag("session_history_back").performClick()
        rule.onNodeWithTag("player_detail").assertIsDisplayed()
        rule.onNodeWithTag("session_detail").assertDoesNotExist()
        // Second Back: player detail -> All-Time tab.
        rule.onNodeWithTag("session_history_back").performClick()
        rule.onNodeWithTag("all_time_tab").assertIsDisplayed()
        // Third Back: tab list -> origin.
        rule.onNodeWithTag("session_history_back").performClick()
        rule.runOnIdle { assertEquals(AppScreen.Setup, vm.currentScreen.value) }
    }

    @Test fun playerDetail_activeSession_taggedInProgress_openDoesNotChangeActive() {
        seedSession("active", "Live One", fourPlayers("act"), wins = 1)
        val vm = SessionViewModel(app()) // real repo -> loads "active" as the active session
        content(vm)
        openAnnPlayerDetail()
        rule.onNodeWithTag("player_session_active_active").assertIsDisplayed()
        rule.onNodeWithTag("player_session_row_active").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        rule.runOnIdle { assertEquals("active", vm.session.value?.id) } // opening detail didn't change active
    }

    @Test fun playerDetail_missingSessionMetadata_showsSessionUnavailable_nonTappable() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 1)
        // A qualifying match for Ann in a session with NO SessionEntity (never saveSession'd).
        runBlocking {
            val players = fourPlayers("g")
            val m = Match(
                id = "ghost-m0", courtId = 1,
                teamA = Team(TeamId.TEAM_A, players[0], players[1]),
                teamB = Team(TeamId.TEAM_B, players[2], players[3]),
                scoreA = 11, scoreB = 3, isCompleted = true, winnerTeamId = TeamId.TEAM_A, endTime = 5L,
            )
            repo().recordCompletedMatch("ghost", m)
        }
        val vm = readyVmNoSession()
        content(vm)
        openAnnPlayerDetail()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("player_session_row_ghost").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Session unavailable").assertIsDisplayed()
        rule.onNodeWithTag("player_session_row_ghost").assertHasNoClickAction()
    }

    @Test fun playerDetail_scrollPreserved_afterReturningFromSessionDetail() {
        // 20 sessions, each with Ann winning; increasing startTime -> newest-first puts p19 at the
        // TOP and p00 at the BOTTOM (enough rows that the top row is genuinely uncomposed when
        // scrolled to the bottom on the w411dp-h891dp viewport — mirrors the history-list scroll test).
        (0..19).forEach { i ->
            seedSession("p%02d".format(i), "S$i", fourPlayers("p$i"), wins = 1, startTime = i.toLong())
        }
        val vm = readyVmNoSession()
        content(vm)
        openAnnPlayerDetail()
        rule.onNodeWithTag("player_detail").performScrollToNode(hasTestTag("player_session_row_p00"))
        rule.onNodeWithTag("player_session_row_p00").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        rule.onNodeWithTag("session_history_back").performClick()
        // Back on player detail: scroll must be retained (p00 visible, top row p19 uncomposed).
        rule.onNodeWithTag("player_session_row_p00").assertIsDisplayed()
        rule.onNodeWithTag("player_session_row_p19").assertDoesNotExist()
    }

    @Test fun playerDetail_vanished_showsPlayerNoLongerAvailable() {
        // A controllable matches flow: emit Ann's match (detail opens), then emit empty -> NotAvailable.
        val matchesFlow = MutableStateFlow(
            listOf(
                CompletedMatchEntity(
                    matchId = "m1", sessionId = "s1", courtId = 1,
                    teamAPlayer1 = "Ann", teamAPlayer2 = "Bo", teamBPlayer1 = "Cy", teamBPlayer2 = "Dot",
                    scoreA = 11, scoreB = 5, winnerTeam = "TEAM_A", startTime = 0, endTime = 1,
                )
            )
        )
        val dao = object : FakeSessionDao() {
            override fun getAllSessions(): Flow<List<SessionEntity>> =
                flowOf(listOf(SessionEntity(id = "s1", name = "Mon", startTime = 100, rotationPolicy = "FOUR_OFF_FOUR_ON", consecutiveGameCap = 0, targetScore = 11, isPaused = false, isCompleted = false)))
            override fun getAllRoster(): Flow<List<PlayerEntity>> = flowOf(emptyList())
            override fun getAllMatches(): Flow<List<CompletedMatchEntity>> = matchesFlow
        }
        val hvm = HistoryViewModel(app(), repositoryOverride = SessionRepository(dao))
        val vm = readyVmNoSession()
        content(vm, hvm)
        openAnnPlayerDetail()
        rule.onNodeWithTag("player_detail").assertIsDisplayed()
        matchesFlow.value = emptyList() // Ann vanishes from all-time
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("player_detail_unavailable").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("player_detail_unavailable").assertIsDisplayed()
    }

    @Test fun playerDetail_scrollAndStack_surviveRecreation() {
        // Exercises the CENTRAL scroll contract across Activity recreation: (a) the visible route's
        // live scroll restores via LazyListState.Saver, and (b) a buried Player Detail anchor in the
        // rememberSaveable stack restores after recreation while a Session Detail is on top.
        (0..19).forEach { i ->
            seedSession("p%02d".format(i), "S$i", fourPlayers("p$i"), wins = 1, startTime = i.toLong())
        }
        val vm = readyVmNoSession()
        val hvm = HistoryViewModel(app())
        val restorer = StateRestorationTester(rule)
        restorer.setContent {
            MyApplicationTheme { SessionHistoryScreen(vm, hvm, AppScreen.Setup) }
        }
        openAnnPlayerDetail()
        rule.onNodeWithTag("player_detail").performScrollToNode(hasTestTag("player_session_row_p00"))
        rule.onNodeWithTag("player_session_row_p00").assertIsDisplayed()
        // (a) Visible-route scroll survives recreation via LazyListState.Saver.
        restorer.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("player_detail").assertIsDisplayed()
        rule.onNodeWithTag("player_session_row_p00").assertIsDisplayed()
        rule.onNodeWithTag("player_session_row_p19").assertDoesNotExist()
        // (b) Push Session Detail (captures the player's buried anchor), recreate, pop back:
        //     the two-entry stack restores and the buried player position is retained.
        rule.onNodeWithTag("player_session_row_p00").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        restorer.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()          // two-entry stack restored
        rule.onNodeWithTag("session_history_back").performClick()
        rule.onNodeWithTag("player_session_row_p00").assertIsDisplayed()  // buried anchor restored
        rule.onNodeWithTag("player_session_row_p19").assertDoesNotExist()
    }
```
Also add these imports to the test file (next to the existing ones — some are already present, don't duplicate `Flow`/`flow`/`flowOf`/`CompletableDeferred`/`Match`/`Team`/`assertDoesNotExist` if already imported; `assertDoesNotExist` is a member call, not an import):
```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
```
(The test already imports `CompletedMatchEntity`, `SessionEntity`, `PlayerEntity`, `Flow`, `flowOf`, `runBlocking`, `Match`, `Team`, `TeamId`, `MutableStateFlow` may be the only new one — verify against the file's current imports and add only what's missing.)

- [ ] **Step 2: Run to verify it FAILS**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-player-detail" && ./gradlew :app:testDebugUnitTest --tests "com.example.ui.SessionHistoryScreenTest"
```
Expected: FAIL — `player_detail`/`player_session_row_*` don't exist yet, and the flipped `assertHasClickAction` fails against the current non-clickable All-Time rows.

- [ ] **Step 3: Rewrite `SessionHistoryScreen.kt`**

Replace the entire contents of `app/src/main/java/com/example/ui/screens/SessionHistoryScreen.kt` with:
```kotlin
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
                Text("In progress", modifier = Modifier.testTag("player_session_active_${rec.sessionId}"),
                    style = MaterialTheme.typography.labelSmall, color = tokens.textAccent)
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
```

Notes for the implementer:
- `formatDiff` already exists in this package (`ui/screens/LiveRankingSupport.kt`) — do not redefine it.
- The detail pane is rendered **only inside the `Content` branch**, so a `Loading`/`Error` state still shows the spinner/error even when the back stack has a route (preserves the PR#9 `errorWhileDetailOpen` behavior).
- The visible route uses `rememberSaveable(saver = LazyListState.Saver)` (Compose snapshots live scroll at Activity recreation); buried routes carry the anchor captured in `openChild` before they leave composition. Do not "simplify" this to a plain `rememberLazyListState()` — that loses per-route restore.
- All-Time rows are now `clickable`; History rows and the detail Standings/Matches rows keep their existing interactivity; **Player Detail breakdown rows are tappable only when `sessionName != null`** (missing-metadata rows are non-tappable).

- [ ] **Step 4: Run the test to verify it PASSES**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-player-detail" && ./gradlew :app:testDebugUnitTest --tests "com.example.ui.SessionHistoryScreenTest"
```
Expected: PASS — all pre-existing `SessionHistoryScreenTest` cases (History list/detail, back chain, restoration, scroll, error/empty/loading, vanished session) plus the new Player Detail tests. The `waitUntil` guards cover Room `Flow` timing.

- [ ] **Step 5: Run the full suite to confirm no regression**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-player-detail" && ./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```
Expected: PASS — all new tests plus the pre-existing suite (Session History, Live Ranking, Court Call, engine). Report the total test count.

- [ ] **Step 6: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/ui/screens/SessionHistoryScreen.kt`
- `app/src/test/java/com/example/ui/SessionHistoryScreenTest.kt`

Suggested message:
```
feat(player-detail): Player Detail view with in-screen back stack

Replace the single selectedSessionId with a DetailRoute back stack
(SessionDetail | PlayerDetail), each entry preserving its own scroll across
navigation and Activity recreation. All-Time rows are now tappable into a
Player Detail view: all-time header stats + sessions played + a per-session
breakdown (active session tagged "In progress"; missing-metadata rows shown
non-tappable as "Session unavailable"). Back pops one level at a time.
```

---

## Notes & non-goals (from the spec)

- **No schema/DAO/repository/write-path change, no new `AppScreen`.** Player Detail is internal screen state reached by tapping an All-Time row; it reuses `session?.id` + `isSessionLoaded` and the existing `HistoryData`/`rankAllTime`.
- **Identity is normalized name** (same as All-Time); the per-session breakdown is match-derived and can differ from a session's roster Standings after an interrupted write or with two same-named people (documented caveat; the "Records grouped by name across sessions." caption surfaces it).
- **Scope of scroll restoration is Activity/configuration-change recreation** (what `StateRestorationTester` exercises). Full process-death route restoration is a pre-existing app-wide limitation (`SessionViewModel.currentScreen` resets to `SessionHub`), out of scope.
- **Deferred (future):** win% / performance rating (needs a minimum-games rule); rename/merge/delete/identity management; id-based (session-scope) player view.
- Before `/create-pr`, run the full suite (`./gradlew :app:testDebugUnitTest`) and `./gradlew assembleDebug`.
```
