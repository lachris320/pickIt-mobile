# Session History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the subagent-build skill (`/subagent-build`) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only, two-tab **Session History** phone screen — History (browse past sessions → session detail) and All-Time (cross-session aggregate leaderboard) — derived entirely from already-persisted data, reusing the shipped `RankingEngine`.

**Architecture:** Pure aggregation (`HistoryAggregator.rankAllTime` / `sessionSummary`) and a pure state assembler (`assembleHistoryState` / `buildSessionDetail` / `leaderLine`) do all the logic, unit-tested with no Android/Room. A thin reactive `HistoryViewModel` reads three Room `Flow`s (sessions, matches, roster), maps entities → DB-free domain, and groups in memory. `SessionHistoryScreen` renders the assembled state and owns tab/scroll/back state. `SessionViewModel` gains a small `isSessionLoaded` readiness flag and `AppScreen.SessionHistory(origin)`.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Room, StateFlow, JUnit4 (pure logic), Robolectric + Compose UI test (screen). Gradle.

**Spec:** [docs/specs/2026-09-23-session-history-design.md](../specs/2026-09-23-session-history-design.md) — passed self-review + `/codex-review` design-spec gate (APPROVE round 3).

**Suggested branch:** `feature/session-history` (created by `/worktrees` at execution time).

**Commit discipline (project rules):** commit via the `commit` skill; stage only the explicit paths named in each commit step (never `git add -A` — the worktree carries untracked `gradlew`, `local.properties`, `debug.keystore`, `.idea/`, etc. that must NOT be staged). No Claude co-author trailer; no "Generated with Claude Code" line.

**Test commands:**
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.HistoryAggregatorTest"
./gradlew :app:testDebugUnitTest --tests "com.example.viewmodel.HistoryStateTest"
./gradlew :app:testDebugUnitTest --tests "com.example.data.HistoryMappersTest"
./gradlew :app:testDebugUnitTest --tests "com.example.viewmodel.SessionLoadedFlagTest"
./gradlew :app:testDebugUnitTest --tests "com.example.ui.SessionHistoryScreenTest"
./gradlew :app:testDebugUnitTest --tests "com.example.ui.SessionHistoryNavTest"
```

---

## File Structure

- Create `app/src/main/java/com/example/model/HistoryModels.kt` — DB-free domain types: `MatchResult`, `PlayerRef`, `SessionSummary`, `SessionMeta`, `SessionListItem`.
- Create `app/src/main/java/com/example/engine/HistoryAggregator.kt` — pure `rankAllTime` + `sessionSummary` (reuse `RankingEngine`).
- Create `app/src/main/java/com/example/viewmodel/HistoryState.kt` — state types (`HistoryData`, `HistoryUiState`, `SessionDetail`) + pure `assembleHistoryState`, `buildSessionDetail`, `leaderLine`.
- Create `app/src/main/java/com/example/data/repository/HistoryMappers.kt` — `CompletedMatchEntity.toMatchResult()`, `PlayerEntity.toDomainPlayer()`.
- Modify `app/src/main/java/com/example/data/local/SessionDao.kt` — add `getAllMatches()` + `getAllRoster()` `Flow`s.
- Modify `app/src/main/java/com/example/data/repository/SessionRepository.kt` — expose `allMatches` (mapped) + `allRosterEntities` `Flow`s.
- Create `app/src/main/java/com/example/viewmodel/HistoryViewModel.kt` — reactive `data: StateFlow<HistoryData>`.
- Modify `app/src/main/java/com/example/viewmodel/SessionViewModel.kt` — add `isSessionLoaded` + `AppScreen.SessionHistory(origin)`.
- Create `app/src/main/java/com/example/ui/screens/SessionHistoryScreen.kt` — the two-tab screen + session detail.
- Modify `app/src/main/java/com/example/MainActivity.kt` — `HistoryViewModel` + inner-`when` branch (NOT full-bleed).
- Modify `app/src/main/java/com/example/ui/screens/SessionHubScreen.kt` — entry buttons (active branch + empty branch).
- Modify `app/src/main/java/com/example/ui/screens/SetupScreen.kt` — secondary action.
- Tests: `HistoryAggregatorTest.kt`, `viewmodel/HistoryStateTest.kt`, `data/HistoryMappersTest.kt`, `viewmodel/SessionLoadedFlagTest.kt`, `ui/SessionHistoryScreenTest.kt`, `ui/SessionHistoryNavTest.kt`.

---

## Task 1: Pure domain + HistoryAggregator (rankAllTime + sessionSummary)

**Files:**
- Create: `app/src/main/java/com/example/model/HistoryModels.kt`
- Create: `app/src/main/java/com/example/engine/HistoryAggregator.kt`
- Test: `app/src/test/java/com/example/HistoryAggregatorTest.kt`

- [ ] **Step 1: Create the domain types**

`app/src/main/java/com/example/model/HistoryModels.kt`:
```kotlin
package com.example.model

/** One completed match, DB-free (repository maps CompletedMatchEntity -> this). */
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

/** A player reference that keeps id so identical display names stay distinct. */
data class PlayerRef(val id: String, val name: String)

/** Semantic summary of one session (UI formats the copy from this). */
data class SessionSummary(
    val gameCount: Int,          // completed matches for the session (from the matches table)
    val participantCount: Int,   // roster players with matchesPlayed >= 1
    val leaders: List<PlayerRef> // rank-1 entries; empty iff no ranked roster players (roster-driven)
)

/** Lightweight session header for the history list. */
data class SessionMeta(
    val id: String,
    val name: String,
    val startTime: Long,
)

/** One row of the History list: session header + its summary. */
data class SessionListItem(
    val meta: SessionMeta,
    val summary: SessionSummary,
)
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/example/HistoryAggregatorTest.kt`:
```kotlin
package com.example

import com.example.engine.HistoryAggregator
import com.example.model.MatchResult
import com.example.model.ParticipantStatus
import com.example.model.Player
import com.example.model.TeamId
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryAggregatorTest {

    private fun match(
        id: String, session: String = "s",
        a1: String, a2: String, b1: String, b2: String,
        scoreA: Int, scoreB: Int, winner: TeamId?,
        endTime: Long = 0L,
    ) = MatchResult(
        matchId = id, sessionId = session,
        teamA = listOf(a1, a2), teamB = listOf(b1, b2),
        scoreA = scoreA, scoreB = scoreB, winner = winner,
        startTime = 0L, endTime = endTime,
    )

    private fun player(
        id: String, name: String, played: Int, won: Int,
        pf: Int = 0, pa: Int = 0,
        status: ParticipantStatus = ParticipantStatus.AVAILABLE,
    ) = Player(
        id = id, name = name, status = status,
        matchesPlayed = played, matchesWon = won,
        totalPointsScored = pf, totalPointsConceded = pa,
    )

    // --- rankAllTime ---

    @Test fun `rankAllTime attributes scores and wins per team, ranks by wins then diff`() {
        val matches = listOf(
            // Ana&Bo beat Cy&Dot 11-4 ; Ana&Bo beat Cy&Dot 11-6
            match("m1", a1 = "Ana", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 4, winner = TeamId.TEAM_A, endTime = 1),
            match("m2", a1 = "Ana", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 6, winner = TeamId.TEAM_A, endTime = 2),
        )
        val ranked = HistoryAggregator.rankAllTime(matches).associateBy { it.name }
        // Ana: 2 wins, +22-10 = +12 ; Bo same. Cy/Dot: 0 wins, -12.
        assertEquals(2, ranked.getValue("Ana").wins)
        assertEquals(0, ranked.getValue("Ana").losses)
        assertEquals(12, ranked.getValue("Ana").pointDiff)   // (11+11) - (4+6)
        assertEquals(1, ranked.getValue("Ana").rank)
        assertEquals(1, ranked.getValue("Bo").rank)          // tie share rank 1
        assertEquals(3, ranked.getValue("Cy").rank)          // competition ranking skips 2
        assertEquals(0, ranked.getValue("Cy").wins)
        assertEquals(2, ranked.getValue("Cy").losses)
        assertEquals(-12, ranked.getValue("Cy").pointDiff)
    }

    @Test fun `rankAllTime merges by normalized name across sessions`() {
        val matches = listOf(
            match("m1", session = "s1", a1 = "Ana", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 2, winner = TeamId.TEAM_A, endTime = 1),
            match("m2", session = "s2", a1 = " ana ", a2 = "Eve", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 5, winner = TeamId.TEAM_A, endTime = 2),
        )
        val ranked = HistoryAggregator.rankAllTime(matches)
        // "Ana" and " ana " merge -> one row with 2 games, 2 wins.
        val ana = ranked.single { it.id == "ana" }
        assertEquals(2, ana.wins)
        assertEquals(2, ana.wins + ana.losses) // games = 2
        assertEquals("ana", ana.id)            // id is the normalized key
    }

    @Test fun `rankAllTime skips null-winner matches entirely (no game, no loss)`() {
        val matches = listOf(
            match("m1", a1 = "Ana", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 9, winner = null, endTime = 1),
        )
        assertEquals(emptyList<Any>(), HistoryAggregator.rankAllTime(matches))
    }

    @Test fun `rankAllTime picks display spelling from the latest match by endTime`() {
        val matches = listOf(
            match("m1", a1 = "ANA", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 2, winner = TeamId.TEAM_A, endTime = 1),
            match("m2", a1 = "Ana", a2 = "Bo", b1 = "Cy", b2 = "Dot", scoreA = 11, scoreB = 2, winner = TeamId.TEAM_A, endTime = 5),
        )
        val ana = HistoryAggregator.rankAllTime(matches).single { it.id == "ana" }
        assertEquals("Ana", ana.name) // latest endTime spelling wins
    }

    @Test fun `rankAllTime empty input yields empty`() {
        assertEquals(emptyList<Any>(), HistoryAggregator.rankAllTime(emptyList()))
    }

    // --- sessionSummary ---

    @Test fun `sessionSummary reports gameCount, participantCount, and rank-1 leaders`() {
        val roster = listOf(
            player("a", "Ana", played = 3, won = 3, pf = 33, pa = 10),
            player("b", "Bo", played = 3, won = 1, pf = 20, pa = 25),
            player("z", "Zed", played = 0, won = 0), // no games -> excluded
        )
        val s = HistoryAggregator.sessionSummary(roster, completedMatchCount = 5)
        assertEquals(5, s.gameCount)
        assertEquals(2, s.participantCount)               // a and b (z filtered)
        assertEquals(listOf("a"), s.leaders.map { it.id }) // Ana rank 1
    }

    @Test fun `sessionSummary keeps ids for tied identical-name leaders`() {
        val roster = listOf(
            player("id1", "Sam", played = 2, won = 2, pf = 20, pa = 10),
            player("id2", "Sam", played = 2, won = 2, pf = 20, pa = 10),
        )
        val s = HistoryAggregator.sessionSummary(roster, completedMatchCount = 2)
        assertEquals(setOf("id1", "id2"), s.leaders.map { it.id }.toSet()) // both leaders, distinct ids
    }

    @Test fun `sessionSummary leaders are roster-driven, independent of gameCount`() {
        val roster = listOf(player("a", "Ana", played = 2, won = 2, pf = 20, pa = 10))
        // Inverse interrupted-write: roster has a ranked player but zero recorded matches.
        val s = HistoryAggregator.sessionSummary(roster, completedMatchCount = 0)
        assertEquals(0, s.gameCount)
        assertEquals(1, s.leaders.size) // non-empty despite gameCount == 0
    }
}
```

- [ ] **Step 3: Run the test to verify it FAILS**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-session-history" && ./gradlew :app:testDebugUnitTest --tests "com.example.HistoryAggregatorTest"
```
Expected: FAIL — `HistoryAggregator` does not exist (unresolved reference).

- [ ] **Step 4: Implement `HistoryAggregator`**

`app/src/main/java/com/example/engine/HistoryAggregator.kt`:
```kotlin
package com.example.engine

import com.example.model.MatchResult
import com.example.model.Player
import com.example.model.PlayerRef
import com.example.model.RankedPlayer
import com.example.model.SessionSummary
import com.example.model.TeamId
import java.util.Locale

/**
 * Pure aggregation for Session History. Reuses [RankingEngine] for every ranking so the ordering
 * rules (wins -> point-diff -> name -> id, competition ranks) are identical everywhere.
 */
object HistoryAggregator {

    private fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)

    /**
     * All-time leaderboard aggregated by normalized name over matches WITH a valid winner
     * (null-winner matches are skipped entirely). Ranks on the normalized name for locale-stable
     * tie ordering, then relabels each row to its display spelling (post-rank, never affects order).
     */
    fun rankAllTime(matches: List<MatchResult>): List<RankedPlayer> {
        data class Acc(var wins: Int = 0, var games: Int = 0, var pf: Int = 0, var pa: Int = 0)
        // Display-spelling candidate key: highest endTime, then highest matchId, then lowest slot.
        data class SpellKey(val endTime: Long, val matchId: String, val slot: Int)

        val accs = LinkedHashMap<String, Acc>()
        val bestSpell = HashMap<String, Pair<SpellKey, String>>()

        fun considerSpelling(norm: String, raw: String, key: SpellKey) {
            val cur = bestSpell[norm]
            val better = cur == null ||
                key.endTime > cur.first.endTime ||
                (key.endTime == cur.first.endTime && key.matchId > cur.first.matchId) ||
                (key.endTime == cur.first.endTime && key.matchId == cur.first.matchId && key.slot < cur.first.slot)
            if (better) bestSpell[norm] = key to raw
        }

        for (m in matches) {
            val winner = m.winner ?: continue // skip null-winner matches entirely
            val slots = listOf(
                Triple(m.teamA.getOrNull(0), TeamId.TEAM_A, 0),
                Triple(m.teamA.getOrNull(1), TeamId.TEAM_A, 1),
                Triple(m.teamB.getOrNull(0), TeamId.TEAM_B, 2),
                Triple(m.teamB.getOrNull(1), TeamId.TEAM_B, 3),
            )
            for ((rawNameOrNull, team, slot) in slots) {
                val raw = rawNameOrNull ?: continue
                val norm = normalize(raw)
                if (norm.isEmpty()) continue
                val acc = accs.getOrPut(norm) { Acc() }
                acc.games += 1
                if (team == TeamId.TEAM_A) { acc.pf += m.scoreA; acc.pa += m.scoreB }
                else { acc.pf += m.scoreB; acc.pa += m.scoreA }
                if (winner == team) acc.wins += 1
                considerSpelling(norm, raw, SpellKey(m.endTime, m.matchId, slot))
            }
        }

        val synthetic = accs.map { (norm, a) ->
            Player(
                id = norm,
                name = norm, // rank on the normalized name -> locale-stable tie ordering
                matchesPlayed = a.games,
                matchesWon = a.wins,
                totalPointsScored = a.pf,
                totalPointsConceded = a.pa,
            )
        }
        return RankingEngine.rank(synthetic).map { rp ->
            rp.copy(name = bestSpell[rp.id]?.second ?: rp.name) // restore display spelling post-rank
        }
    }

    /** Semantic per-session summary. Leaders are roster-driven (rank-1 of RankingEngine.rank). */
    fun sessionSummary(roster: List<Player>, completedMatchCount: Int): SessionSummary {
        val ranked = RankingEngine.rank(roster)
        val leaders = ranked.filter { it.rank == 1 }.map { PlayerRef(it.id, it.name) }
        return SessionSummary(
            gameCount = completedMatchCount,
            participantCount = ranked.size,
            leaders = leaders,
        )
    }
}
```

- [ ] **Step 5: Run the test to verify it PASSES**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-session-history" && ./gradlew :app:testDebugUnitTest --tests "com.example.HistoryAggregatorTest"
```
Expected: PASS (all 8 tests). Note: the first Gradle run may download dependencies (minutes) — let it run. If the build fails for an environment reason (missing SDK/keystore/wrapper), report BLOCKED; do not modify build config.

- [ ] **Step 6: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/model/HistoryModels.kt`
- `app/src/main/java/com/example/engine/HistoryAggregator.kt`
- `app/src/test/java/com/example/HistoryAggregatorTest.kt`

Suggested message:
```
feat(history): pure aggregator for all-time ranking and session summaries

rankAllTime aggregates completed matches (valid winner only) by normalized
name and reuses RankingEngine; sessionSummary derives roster-driven leaders.
Display spelling is chosen deterministically and applied post-rank.
```

---

## Task 2: Pure state assembler (HistoryUiState / SessionDetail / leaderLine)

**Files:**
- Create: `app/src/main/java/com/example/viewmodel/HistoryState.kt`
- Test: `app/src/test/java/com/example/viewmodel/HistoryStateTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/viewmodel/HistoryStateTest.kt`:
```kotlin
package com.example.viewmodel

import com.example.model.MatchResult
import com.example.model.ParticipantStatus
import com.example.model.Player
import com.example.model.PlayerRef
import com.example.model.SessionMeta
import com.example.model.SessionSummary
import com.example.model.TeamId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStateTest {

    private fun player(id: String, name: String, played: Int, won: Int, pf: Int = 0, pa: Int = 0) =
        Player(
            id = id, name = name, status = ParticipantStatus.AVAILABLE,
            matchesPlayed = played, matchesWon = won,
            totalPointsScored = pf, totalPointsConceded = pa,
        )

    private fun match(id: String, session: String, endTime: Long) = MatchResult(
        matchId = id, sessionId = session,
        teamA = listOf("Ana", "Bo"), teamB = listOf("Cy", "Dot"),
        scoreA = 11, scoreB = 5, winner = TeamId.TEAM_A, startTime = 0L, endTime = endTime,
    )

    private fun loaded() = HistoryData.Loaded(
        sessions = listOf(
            SessionMeta("s1", "Mon", startTime = 100),
            SessionMeta("s2", "Tue", startTime = 200), // newer
            SessionMeta("active", "Now", startTime = 300),
        ),
        matchesBySession = mapOf(
            "s1" to listOf(match("m1", "s1", 1)),
            "s2" to listOf(match("m2", "s2", 2), match("m3", "s2", 3)),
            "active" to listOf(match("m9", "active", 9)),
        ),
        rosterBySession = mapOf(
            "s1" to listOf(player("a", "Ana", 1, 1, 11, 5), player("c", "Cy", 1, 0, 5, 11)),
            "s2" to listOf(player("a", "Ana", 2, 2, 22, 10)),
            "active" to listOf(player("a", "Ana", 1, 1, 11, 5)),
        ),
    )

    @Test fun `assemble returns Loading until the session load settles`() {
        val s = assembleHistoryState(loaded(), activeSessionId = null, isSessionLoaded = false)
        assertEquals(HistoryUiState.Loading, s)
    }

    @Test fun `assemble excludes the active session and sorts past sessions newest-first`() {
        val s = assembleHistoryState(loaded(), activeSessionId = "active", isSessionLoaded = true)
        s as HistoryUiState.Content
        assertEquals(listOf("s2", "s1"), s.pastSessions.map { it.meta.id }) // newest-first, active excluded
        assertEquals(2, s.pastSessions.first().summary.gameCount)            // s2 has 2 matches
    }

    @Test fun `assemble includes all sessions when there is no active session`() {
        val s = assembleHistoryState(loaded(), activeSessionId = null, isSessionLoaded = true)
        s as HistoryUiState.Content
        assertEquals(setOf("s1", "s2", "active"), s.pastSessions.map { it.meta.id }.toSet())
    }

    @Test fun `assemble all-time spans every session including the active one`() {
        val s = assembleHistoryState(loaded(), activeSessionId = "active", isSessionLoaded = true)
        s as HistoryUiState.Content
        val ana = s.allTime.single { it.id == "ana" }
        assertEquals(4, ana.wins) // 1 (s1) + 2 (s2) + 1 (active)
    }

    @Test fun `assemble passes through Loading and Error`() {
        assertEquals(HistoryUiState.Loading, assembleHistoryState(HistoryData.Loading, null, true))
        assertTrue(assembleHistoryState(HistoryData.Error("x"), null, true) is HistoryUiState.Error)
    }

    @Test fun `buildSessionDetail returns Found with roster standings and newest-first matches`() {
        val d = buildSessionDetail(loaded(), "s2")
        d as SessionDetail.Found
        assertEquals("Tue", d.meta.name)
        assertEquals(listOf("m3", "m2"), d.matches.map { it.matchId }) // endTime DESC
        assertEquals("a", d.standings.first().id)                       // Ana tops the roster ranking
    }

    @Test fun `buildSessionDetail returns NotAvailable for a vanished session or while loading`() {
        assertEquals(SessionDetail.NotAvailable, buildSessionDetail(loaded(), "nope"))
        assertEquals(SessionDetail.NotAvailable, buildSessionDetail(HistoryData.Loading, "s1"))
    }

    // --- leaderLine (mismatch-aware) ---

    private fun summary(games: Int, leaders: List<String>) = SessionSummary(
        gameCount = games, participantCount = leaders.size,
        leaders = leaders.map { PlayerRef(it, it) },
    )

    @Test fun `leaderLine formats no-games, single, joint, and mismatch cases`() {
        assertEquals("No completed games", leaderLine(summary(0, emptyList())))
        assertEquals("Top player: Ana", leaderLine(summary(3, listOf("Ana"))))
        assertEquals("Joint leaders: Ana & Bo", leaderLine(summary(3, listOf("Ana", "Bo"))))
        assertEquals("Joint leaders: Ana + 2 others", leaderLine(summary(3, listOf("Ana", "Bo", "Cy"))))
        // gameCount > 0 but roster increment lost -> Standings unavailable
        assertEquals("Standings unavailable", leaderLine(summary(2, emptyList())))
        // roster stats without recorded matches -> Standings unavailable
        assertEquals("Standings unavailable", leaderLine(summary(0, listOf("Ana"))))
    }
}
```

- [ ] **Step 2: Run the test to verify it FAILS**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-session-history" && ./gradlew :app:testDebugUnitTest --tests "com.example.viewmodel.HistoryStateTest"
```
Expected: FAIL — `assembleHistoryState` / `HistoryData` do not exist.

- [ ] **Step 3: Implement the state types + pure functions**

`app/src/main/java/com/example/viewmodel/HistoryState.kt`:
```kotlin
package com.example.viewmodel

import com.example.engine.HistoryAggregator
import com.example.engine.RankingEngine
import com.example.model.MatchResult
import com.example.model.Player
import com.example.model.RankedPlayer
import com.example.model.SessionListItem
import com.example.model.SessionMeta
import com.example.model.SessionSummary

/** Raw reactive data from Room, grouped in memory (produced by HistoryViewModel). */
sealed interface HistoryData {
    data object Loading : HistoryData
    data class Loaded(
        val sessions: List<SessionMeta>,
        val matchesBySession: Map<String, List<MatchResult>>,
        val rosterBySession: Map<String, List<Player>>,
    ) : HistoryData
    data class Error(val message: String) : HistoryData
}

/** What the History list + All-Time tab render from. */
sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Content(
        val pastSessions: List<SessionListItem>,
        val allTime: List<RankedPlayer>,
    ) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

/** What the session-detail view renders from. */
sealed interface SessionDetail {
    data class Found(
        val meta: SessionMeta,
        val standings: List<RankedPlayer>,
        val matches: List<MatchResult>,
    ) : SessionDetail
    data object NotAvailable : SessionDetail
}

/**
 * Assemble the list/all-time state. Stays [HistoryUiState.Loading] until the active-session load
 * has settled (readiness), so the active session is never transiently shown as a past row.
 * The active session is excluded from the past list but still contributes to all-time.
 */
fun assembleHistoryState(
    data: HistoryData,
    activeSessionId: String?,
    isSessionLoaded: Boolean,
): HistoryUiState {
    if (!isSessionLoaded) return HistoryUiState.Loading
    return when (data) {
        is HistoryData.Loading -> HistoryUiState.Loading
        is HistoryData.Error -> HistoryUiState.Error(data.message)
        is HistoryData.Loaded -> {
            val past = data.sessions
                .filter { it.id != activeSessionId }
                .sortedByDescending { it.startTime }
                .map { meta ->
                    SessionListItem(
                        meta = meta,
                        summary = HistoryAggregator.sessionSummary(
                            roster = data.rosterBySession[meta.id].orEmpty(),
                            completedMatchCount = data.matchesBySession[meta.id]?.size ?: 0,
                        ),
                    )
                }
            val allTime = HistoryAggregator.rankAllTime(data.matchesBySession.values.flatten())
            HistoryUiState.Content(pastSessions = past, allTime = allTime)
        }
    }
}

/** Resolve a session-detail view from loaded data; NotAvailable if missing or not yet loaded. */
fun buildSessionDetail(data: HistoryData, sessionId: String): SessionDetail {
    if (data !is HistoryData.Loaded) return SessionDetail.NotAvailable
    val meta = data.sessions.firstOrNull { it.id == sessionId } ?: return SessionDetail.NotAvailable
    val standings = RankingEngine.rank(data.rosterBySession[sessionId].orEmpty())
    val matches = data.matchesBySession[sessionId].orEmpty()
        .sortedWith(compareByDescending<MatchResult> { it.endTime }.thenByDescending { it.matchId })
    return SessionDetail.Found(meta, standings, matches)
}

/** Mismatch-aware second-line copy for a session summary. */
fun leaderLine(summary: SessionSummary): String {
    val hasGames = summary.gameCount > 0
    val hasLeaders = summary.leaders.isNotEmpty()
    return when {
        !hasGames && !hasLeaders -> "No completed games"
        hasGames && hasLeaders -> when (summary.leaders.size) {
            1 -> "Top player: ${summary.leaders[0].name}"
            2 -> "Joint leaders: ${summary.leaders[0].name} & ${summary.leaders[1].name}"
            else -> "Joint leaders: ${summary.leaders[0].name} + ${summary.leaders.size - 1} others"
        }
        else -> "Standings unavailable" // (games && !leaders) or (!games && leaders)
    }
}
```

- [ ] **Step 4: Run the test to verify it PASSES**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-session-history" && ./gradlew :app:testDebugUnitTest --tests "com.example.viewmodel.HistoryStateTest"
```
Expected: PASS (all 8 tests).

- [ ] **Step 5: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/viewmodel/HistoryState.kt`
- `app/src/test/java/com/example/viewmodel/HistoryStateTest.kt`

Suggested message:
```
feat(history): pure state assembler with readiness gating and mismatch copy

assembleHistoryState gates on session-load readiness, excludes the active
session from the list but not from all-time, and sorts newest-first;
buildSessionDetail resolves standings + newest-first matches; leaderLine
encodes the mismatch-aware summary wording.
```

---

## Task 3: Data layer (DAO reads, mappers, repository) + HistoryViewModel + isSessionLoaded

**Files:**
- Create: `app/src/main/java/com/example/data/repository/HistoryMappers.kt`
- Modify: `app/src/main/java/com/example/data/local/SessionDao.kt`
- Modify: `app/src/main/java/com/example/data/repository/SessionRepository.kt`
- Create: `app/src/main/java/com/example/viewmodel/HistoryViewModel.kt`
- Modify: `app/src/main/java/com/example/viewmodel/SessionViewModel.kt` (add `isSessionLoaded`)
- Test: `app/src/test/java/com/example/data/HistoryMappersTest.kt`
- Test: `app/src/test/java/com/example/viewmodel/SessionLoadedFlagTest.kt`

- [ ] **Step 1: Write the failing mapper test**

`app/src/test/java/com/example/data/HistoryMappersTest.kt`:
```kotlin
package com.example.data

import com.example.data.local.CompletedMatchEntity
import com.example.data.local.PlayerEntity
import com.example.data.repository.toDomainPlayer
import com.example.data.repository.toMatchResult
import com.example.model.ParticipantStatus
import com.example.model.TeamId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMappersTest {

    @Test fun `toMatchResult maps names, scores, and winner enum`() {
        val e = CompletedMatchEntity(
            matchId = "m1", sessionId = "s1", courtId = 1,
            teamAPlayer1 = "Ana", teamAPlayer2 = "Bo",
            teamBPlayer1 = "Cy", teamBPlayer2 = "Dot",
            scoreA = 11, scoreB = 7, winnerTeam = "TEAM_B",
            startTime = 10, endTime = 20,
        )
        val r = e.toMatchResult()
        assertEquals(listOf("Ana", "Bo"), r.teamA)
        assertEquals(listOf("Cy", "Dot"), r.teamB)
        assertEquals(TeamId.TEAM_B, r.winner)
        assertEquals(20, r.endTime)
    }

    @Test fun `toMatchResult maps UNKNOWN winner to null`() {
        val e = CompletedMatchEntity(
            matchId = "m1", sessionId = "s1", courtId = 1,
            teamAPlayer1 = "Ana", teamAPlayer2 = "Bo",
            teamBPlayer1 = "Cy", teamBPlayer2 = "Dot",
            scoreA = 9, scoreB = 9, winnerTeam = "UNKNOWN",
            startTime = 10, endTime = 20,
        )
        assertNull(e.toMatchResult().winner)
    }

    @Test fun `toDomainPlayer maps playerId and aggregates`() {
        val e = PlayerEntity(
            sessionId = "s1", playerId = "p1", name = "Ana", status = "CHECKED_OUT",
            queuedTimestamp = 1, restingTimestamp = 0,
            matchesPlayed = 3, matchesWon = 2, totalPointsScored = 30, totalPointsConceded = 20,
            consecutiveGamesOnCourt = 1,
        )
        val p = e.toDomainPlayer()
        assertEquals("p1", p.id)
        assertEquals(ParticipantStatus.CHECKED_OUT, p.status)
        assertEquals(2, p.matchesWon)
        assertEquals(30, p.totalPointsScored)
    }
}
```

- [ ] **Step 2: Run to verify it FAILS**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-session-history" && ./gradlew :app:testDebugUnitTest --tests "com.example.data.HistoryMappersTest"
```
Expected: FAIL — the mapper functions do not exist.

- [ ] **Step 3: Implement the mappers**

`app/src/main/java/com/example/data/repository/HistoryMappers.kt`:
```kotlin
package com.example.data.repository

import com.example.data.local.CompletedMatchEntity
import com.example.data.local.PlayerEntity
import com.example.model.MatchResult
import com.example.model.ParticipantStatus
import com.example.model.Player
import com.example.model.TeamId

/** CompletedMatchEntity (names + winner string) -> DB-free MatchResult. */
fun CompletedMatchEntity.toMatchResult(): MatchResult = MatchResult(
    matchId = matchId,
    sessionId = sessionId,
    teamA = listOf(teamAPlayer1, teamAPlayer2),
    teamB = listOf(teamBPlayer1, teamBPlayer2),
    scoreA = scoreA,
    scoreB = scoreB,
    winner = when (winnerTeam) {
        "TEAM_A" -> TeamId.TEAM_A
        "TEAM_B" -> TeamId.TEAM_B
        else -> null // "UNKNOWN" or any malformed value
    },
    startTime = startTime,
    endTime = endTime,
)

/** PlayerEntity -> domain Player (id-keyed, with the persisted aggregates). */
fun PlayerEntity.toDomainPlayer(): Player = Player(
    id = playerId,
    name = name,
    status = runCatching { ParticipantStatus.valueOf(status) }.getOrDefault(ParticipantStatus.AVAILABLE),
    queuedTimestamp = queuedTimestamp,
    restingTimestamp = restingTimestamp,
    matchesPlayed = matchesPlayed,
    matchesWon = matchesWon,
    totalPointsScored = totalPointsScored,
    totalPointsConceded = totalPointsConceded,
    consecutiveGamesOnCourt = consecutiveGamesOnCourt,
)
```

- [ ] **Step 4: Add the DAO reads**

In `app/src/main/java/com/example/data/local/SessionDao.kt`, add these two queries (next to the existing `getMatchesForSession`):
```kotlin
    @Query("SELECT * FROM matches ORDER BY endTime DESC")
    fun getAllMatches(): Flow<List<CompletedMatchEntity>>

    @Query("SELECT * FROM roster")
    fun getAllRoster(): Flow<List<PlayerEntity>>
```
(`Flow` is already imported in this file.)

**IMPORTANT — this interface change breaks an existing test fake.** `SessionViewModelLoadFailureTest.kt` has a hand-written `SessionDao` implementation (`UnusedDao`) that must now implement the two new methods, or that test file will fail to compile. And because `SessionRepository` reads its flow properties eagerly in property initializers (Step 5), these two must return `emptyFlow()`, **not** throw. To avoid duplicating the 14-method stub, extract a shared fake:

Create `app/src/test/java/com/example/testing/FakeSessionDao.kt`:
```kotlin
package com.example.testing

import com.example.data.local.CompletedMatchEntity
import com.example.data.local.CourtEntity
import com.example.data.local.PlayerEntity
import com.example.data.local.SessionDao
import com.example.data.local.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Shared test fake for [SessionDao]. The three reactive reads that [SessionRepository] subscribes
 * to eagerly return empty flows; every one-shot member throws, since fakes here only exercise the
 * `loadLatestSession()` override on the repository above them.
 */
open class FakeSessionDao : SessionDao {
    override fun getAllSessions(): Flow<List<SessionEntity>> = emptyFlow()
    override fun getAllMatches(): Flow<List<CompletedMatchEntity>> = emptyFlow()
    override fun getAllRoster(): Flow<List<PlayerEntity>> = emptyFlow()
    override suspend fun getLatestSession(): SessionEntity? = throw NotImplementedError()
    override suspend fun getSessionById(sessionId: String): SessionEntity? = throw NotImplementedError()
    override suspend fun insertSession(session: SessionEntity) = throw NotImplementedError()
    override suspend fun getCourtsForSession(sessionId: String): List<CourtEntity> = throw NotImplementedError()
    override suspend fun insertCourts(courts: List<CourtEntity>) = throw NotImplementedError()
    override suspend fun deleteCourtsForSession(sessionId: String) = throw NotImplementedError()
    override suspend fun getRosterForSession(sessionId: String): List<PlayerEntity> = throw NotImplementedError()
    override suspend fun insertRoster(roster: List<PlayerEntity>) = throw NotImplementedError()
    override suspend fun deleteRosterForSession(sessionId: String) = throw NotImplementedError()
    override suspend fun getMatchesForSession(sessionId: String): List<CompletedMatchEntity> = throw NotImplementedError()
    override suspend fun insertCompletedMatch(match: CompletedMatchEntity) = throw NotImplementedError()
    override suspend fun deleteSessionById(sessionId: String) = throw NotImplementedError()
}
```
Then in `app/src/test/java/com/example/viewmodel/SessionViewModelLoadFailureTest.kt`, replace the whole private `object UnusedDao : SessionDao { ... }` block with an import `import com.example.testing.FakeSessionDao` and change `FailingRepository`'s superclass call from `SessionRepository(UnusedDao)` to `SessionRepository(FakeSessionDao())`. (Delete the now-unused `CompletedMatchEntity`/`CourtEntity`/`PlayerEntity`/`SessionEntity`/`Flow`/`emptyFlow` imports it only needed for the stub.)

- [ ] **Step 5: Expose the repository flows**

In `app/src/main/java/com/example/data/repository/SessionRepository.kt`, add these imports at the top:
```kotlin
import com.example.model.MatchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
```
(`Flow` may already be imported — if so, don't duplicate it.) Then add, next to the existing `allSessions` property:
```kotlin
    val allMatches: Flow<List<MatchResult>> =
        sessionDao.getAllMatches().map { list -> list.map { it.toMatchResult() } }

    val allRosterEntities: Flow<List<PlayerEntity>> = sessionDao.getAllRoster()
```

- [ ] **Step 6: Create `HistoryViewModel`**

`app/src/main/java/com/example/viewmodel/HistoryViewModel.kt`:
```kotlin
package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PickleballDatabase
import com.example.data.repository.SessionRepository
import com.example.data.repository.toDomainPlayer
import com.example.model.SessionMeta
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Reactive read-only view of all persisted history. Combines three Room flows (sessions, matches,
 * roster), maps entities to DB-free domain, and groups by session in memory. No per-row queries.
 */
class HistoryViewModel @JvmOverloads constructor(
    application: Application,
    private val repositoryOverride: SessionRepository? = null,
) : AndroidViewModel(application) {

    private val repository: SessionRepository by lazy {
        repositoryOverride ?: SessionRepository(PickleballDatabase.getInstance(application).sessionDao())
    }

    val data: StateFlow<HistoryData> =
        combine(
            repository.allSessions,
            repository.allMatches,
            repository.allRosterEntities,
        ) { sessions, matches, rosterEntities ->
            HistoryData.Loaded(
                sessions = sessions.map { SessionMeta(it.id, it.name, it.startTime) },
                matchesBySession = matches.groupBy { it.sessionId },
                rosterBySession = rosterEntities
                    .groupBy { it.sessionId }
                    .mapValues { (_, rows) -> rows.map { it.toDomainPlayer() } },
            ) as HistoryData
        }
            .catch { emit(HistoryData.Error(it.message ?: "Failed to load history")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryData.Loading)
}
```

- [ ] **Step 7: Add `isSessionLoaded` to `SessionViewModel` + write its failing test**

`app/src/test/java/com/example/viewmodel/SessionLoadedFlagTest.kt` — a plain Robolectric + `runTest` test (NOT a `RobolectricComposeTest`), mirroring `SessionViewModelLoadFailureTest`: it installs the test scheduler as `Dispatchers.Main` so the VM's `viewModelScope` init coroutine actually runs under `advanceUntilIdle()`, and uses a fake repository whose `loadLatestSession()` returns `null` synchronously (no real `Dispatchers.IO` hop, so the drain is deterministic):
```kotlin
package com.example.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SessionRepository
import com.example.model.OpenPlaySession
import com.example.testing.FakeSessionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionLoadedFlagTest {

    private fun app(): Application = ApplicationProvider.getApplicationContext()

    /** Fake whose load returns null synchronously, standing in for a fresh install (no session). */
    private class NoSessionRepository : SessionRepository(FakeSessionDao()) {
        override suspend fun loadLatestSession(): OpenPlaySession? = null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `isSessionLoaded flips true after init settles with no saved session`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = SessionViewModel(app(), NoSessionRepository())
            advanceUntilIdle() // run the init coroutine to completion
            assertNull(vm.session.value)
            assertTrue(vm.isSessionLoaded.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
```
Then in `app/src/main/java/com/example/viewmodel/SessionViewModel.kt`:

Add the backing state next to `_session` (after line 50):
```kotlin
    private val _isSessionLoaded = MutableStateFlow(false)
    val isSessionLoaded: StateFlow<Boolean> = _isSessionLoaded.asStateFlow()
```
In `loadSessionForTest`, mark loaded so injected-session tests are "ready" (after `_session.value = s`):
```kotlin
        _isSessionLoaded.value = true
```
At the END of the `init { viewModelScope.launch { ... } }` block — after the `if (savedSession != null ...) { ... }` and its trailing comment, still inside the `launch` — add:
```kotlin
            _isSessionLoaded.value = true
```

- [ ] **Step 8: Run the tests to verify they PASS**

Run (also re-run the pre-existing load-failure test, since its fake DAO was updated):
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-session-history" && ./gradlew :app:testDebugUnitTest --tests "com.example.data.HistoryMappersTest" --tests "com.example.viewmodel.SessionLoadedFlagTest" --tests "com.example.viewmodel.SessionViewModelLoadFailureTest"
```
Expected: PASS. The `setMain(StandardTestDispatcher(testScheduler))` install makes `advanceUntilIdle()` drain the VM's `viewModelScope` init coroutine deterministically (the fake's `loadLatestSession()` returns synchronously, so there is no real `Dispatchers.IO` hop to wait on). Full DAO/flow wiring is exercised end-to-end by the screen tests in Task 4.

- [ ] **Step 9: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/data/repository/HistoryMappers.kt`
- `app/src/main/java/com/example/data/local/SessionDao.kt`
- `app/src/main/java/com/example/data/repository/SessionRepository.kt`
- `app/src/main/java/com/example/viewmodel/HistoryViewModel.kt`
- `app/src/main/java/com/example/viewmodel/SessionViewModel.kt`
- `app/src/test/java/com/example/testing/FakeSessionDao.kt`
- `app/src/test/java/com/example/viewmodel/SessionViewModelLoadFailureTest.kt`
- `app/src/test/java/com/example/data/HistoryMappersTest.kt`
- `app/src/test/java/com/example/viewmodel/SessionLoadedFlagTest.kt`

Suggested message:
```
feat(history): reactive HistoryViewModel and session-load readiness flag

Add getAllMatches/getAllRoster Flow reads, entity->domain mappers, a
HistoryViewModel that groups the three flows in memory, and a
SessionViewModel.isSessionLoaded readiness flag so History never mis-classifies
the active session while its load is pending.
```

---

## Task 4: SessionHistoryScreen (tabs, list, all-time, session detail) + tests

**Files:**
- Create: `app/src/main/java/com/example/ui/screens/SessionHistoryScreen.kt`
- Test: `app/src/test/java/com/example/ui/SessionHistoryScreenTest.kt`

Note: `AppScreen.SessionHistory` is added in Task 5. To keep this task self-contained, the tests here do **not** reference `AppScreen.SessionHistory`; back-to-origin is asserted against an existing origin (`AppScreen.Setup`) that the screen is given directly.

- [ ] **Step 1: Write the failing screen test**

`app/src/test/java/com/example/ui/SessionHistoryScreenTest.kt`:
```kotlin
package com.example.ui

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.data.local.CompletedMatchEntity
import com.example.data.local.PickleballDatabase
import com.example.data.local.PlayerEntity
import com.example.data.local.SessionEntity
import com.example.data.repository.SessionRepository
import com.example.model.Match
import com.example.model.OpenPlaySession
import com.example.model.Player
import com.example.model.RotationPolicy
import com.example.model.Team
import com.example.model.TeamId
import com.example.testing.FakeSessionDao
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.SessionHistoryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.SessionViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h891dp", sdk = [36])
class SessionHistoryScreenTest : RobolectricComposeTest() {

    @Before fun cleanDb() { resetSharedDb() }

    private fun repo() = SessionRepository(PickleballDatabase.getInstance(app()).sessionDao())

    private fun player(id: String, name: String) = Player(id = id, name = name)

    /**
     * Seed a session with `players` and record `wins` completed matches where p0&p1 beat p2&p3.
     * The persisted roster gives player[0] a STRICTLY higher point differential than player[1]
     * (via `- i` on scored) so player[0] is the unique rank-1 leader — otherwise the two winners
     * would tie and the summary would read "Joint leaders", not "Top player".
     */
    private fun seedSession(id: String, name: String, players: List<Player>, wins: Int, startTime: Long = 0L) = runBlocking {
        val r = repo()
        // Persist roster stats the way completeMatch would (winners: players[0], players[1]).
        val roster = players.mapIndexed { i, p ->
            val isWinner = i < 2
            p.copy(
                matchesPlayed = wins,
                matchesWon = if (isWinner) wins else 0,
                totalPointsScored = (if (isWinner) 11 * wins else 5 * wins) - i, // p0 > p1, p2 > p3
                totalPointsConceded = if (isWinner) 5 * wins else 11 * wins,
            )
        }
        r.saveSession(
            OpenPlaySession(
                id = id, name = name, startTime = startTime,
                rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
                courts = emptyList(), roster = roster,
            )
        )
        repeat(wins) { k ->
            val teamA = Team(TeamId.TEAM_A, players[0], players[1])
            val teamB = Team(TeamId.TEAM_B, players[2], players[3])
            val m = Match(
                id = "$id-m$k", courtId = 1, teamA = teamA, teamB = teamB,
                scoreA = 11, scoreB = 5, isCompleted = true, winnerTeamId = TeamId.TEAM_A,
                endTime = (k + 1).toLong(),
            )
            r.recordCompletedMatch(id, m)
        }
    }

    private fun fourPlayers(prefix: String) = listOf(
        player("${prefix}1", "Ann"), player("${prefix}2", "Bob"),
        player("${prefix}3", "Cyd"), player("${prefix}4", "Dan"),
    )

    private fun content(
        sessionViewModel: SessionViewModel,
        historyViewModel: HistoryViewModel = HistoryViewModel(app()),
    ) {
        rule.setContent {
            MyApplicationTheme {
                SessionHistoryScreen(
                    sessionViewModel = sessionViewModel,
                    historyViewModel = historyViewModel,
                    origin = AppScreen.Setup,
                )
            }
        }
    }

    /**
     * A SessionViewModel with NO active session, deterministically: a fake repository whose
     * loadLatestSession() returns null (so init never adopts a seeded session as active), while
     * HistoryViewModel(app()) still reads the real seeded DB. Avoids the race where the VM's init
     * would load a seeded past session as the active one and exclude it from the history list.
     */
    private fun readyVmNoSession(): SessionViewModel {
        val dao = PickleballDatabase.getInstance(app()).sessionDao()
        val fake = object : SessionRepository(dao) {
            override suspend fun loadLatestSession(): OpenPlaySession? = null
        }
        return SessionViewModel(app(), repositoryOverride = fake)
    }

    @Test fun emptyState_whenNoSessions() {
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_history_empty").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_history_empty").assertIsDisplayed()
    }

    @Test fun historyTab_listsPastSessions_withSummary() {
        seedSession("s1", "Monday Night", fourPlayers("s1"), wins = 3)
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_s1").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Monday Night").assertIsDisplayed()
        rule.onNodeWithText("Top player: Ann").assertIsDisplayed()
    }

    @Test fun historyRow_opensDetail_withStandingsAndMatches() {
        seedSession("s1", "Monday Night", fourPlayers("s1"), wins = 2)
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_s1").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_s1").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        rule.onNodeWithTag("standings_row_s11").assertIsDisplayed()   // Ann in standings
        rule.onNodeWithTag("match_row_s1-m1").assertIsDisplayed()     // newest match present
    }

    @Test fun allTimeTab_showsAggregateRows_andSubtitle() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 2)
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("all_time_tab").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("all_time_tab").performClick()
        rule.onNodeWithText("Ranked by total wins, then point difference.").assertIsDisplayed()
        rule.onNodeWithText("Grouped by name across sessions. Use consistent, distinct names.").assertIsDisplayed()
        rule.onNodeWithTag("all_time_row_ann").assertIsDisplayed()
        // Non-interactive: the all-time row has NO click action (no clickable modifier).
        rule.onNodeWithTag("all_time_row_ann").assertHasNoClickAction()
    }

    @Test fun backFromList_navigatesToOrigin() {
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_history_back").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_history_back").performClick()
        rule.runOnIdle { assertEquals(AppScreen.Setup, vm.currentScreen.value) }
    }

    @Test fun backFromDetail_returnsToList_thenBackAgainToOrigin() {
        // NOTE: this test deliberately does NOT reference AppScreen.SessionHistory (added in Task 5).
        // It verifies the two-level back behavior by OBSERVABLE UI: detail -> list, then list -> origin.
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 1)
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_s1").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_s1").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        rule.onNodeWithTag("session_history_back").performClick()
        // First Back: detail -> list (origin unchanged; still on the History screen).
        rule.onNodeWithTag("history_tab").assertIsDisplayed()
        rule.onNodeWithTag("session_detail").assertDoesNotExist()
        // Second Back: list -> origin.
        rule.onNodeWithTag("session_history_back").performClick()
        rule.runOnIdle { assertEquals(AppScreen.Setup, vm.currentScreen.value) }
    }

    @Test fun zeroGameSession_showsNoCompletedGames() {
        // wins = 0 -> a session record with a roster but no completed matches (stays visible).
        seedSession("z", "Empty Night", fourPlayers("z"), wins = 0)
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_z").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("No completed games").assertIsDisplayed()
    }

    @Test fun nullWinnerMatch_showsResultUnavailable() {
        runBlocking {
            val r = repo()
            val players = fourPlayers("nw")
            r.saveSession(
                OpenPlaySession(
                    id = "nw", name = "Tie Night", rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
                    courts = emptyList(), roster = players.map { it.copy(matchesPlayed = 1) },
                )
            )
            // A completed match with no winner (9-9) -> recordCompletedMatch stores "UNKNOWN".
            val m = Match(
                id = "nw-m0", courtId = 1,
                teamA = Team(TeamId.TEAM_A, players[0], players[1]),
                teamB = Team(TeamId.TEAM_B, players[2], players[3]),
                scoreA = 9, scoreB = 9, isCompleted = true, winnerTeamId = null, endTime = 1L,
            )
            r.recordCompletedMatch("nw", m)
        }
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_nw").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_nw").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        rule.onNodeWithText("Result unavailable", substring = true).assertIsDisplayed()
    }

    @Test fun activeSession_excludedFromHistoryList() {
        // A saved session that IS the active one (loaded on VM init) must not appear as a past row.
        seedSession("active", "Live One", fourPlayers("act"), wins = 1)
        val vm = SessionViewModel(app()) // real repo -> loads "active" as the active session on init
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("history_tab").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_active").assertDoesNotExist()
    }

    @Test fun loadingState_shownWhileSessionNotYetLoaded() {
        // A repo whose loadLatestSession() suspends on a gate keeps isSessionLoaded == false,
        // so the readiness gate holds the screen in Loading deterministically.
        val gate = CompletableDeferred<Unit>()
        val dao = PickleballDatabase.getInstance(app()).sessionDao()
        val slowRepo = object : SessionRepository(dao) {
            override suspend fun loadLatestSession(): OpenPlaySession? { gate.await(); return null }
        }
        val vm = SessionViewModel(app(), repositoryOverride = slowRepo)
        content(vm)
        rule.onNodeWithTag("session_history_loading").assertIsDisplayed()
        gate.complete(Unit) // let init finish so the VM's load coroutine doesn't stay suspended
    }

    @Test fun errorState_shownWhenAReadFails() {
        // A DAO whose matches flow throws drives HistoryViewModel.data to Error via its catch{}.
        // The other two flows emit an empty list (not emptyFlow) so combine has values from them
        // and the throw is surfaced deterministically rather than racing an empty completion.
        val throwingDao = object : FakeSessionDao() {
            override fun getAllSessions(): Flow<List<SessionEntity>> = flowOf(emptyList())
            override fun getAllRoster(): Flow<List<PlayerEntity>> = flowOf(emptyList())
            override fun getAllMatches(): Flow<List<CompletedMatchEntity>> =
                flow { throw IllegalStateException("boom") }
        }
        val hvm = HistoryViewModel(app(), repositoryOverride = SessionRepository(throwingDao))
        val vm = readyVmNoSession()
        content(vm, hvm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_history_error").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_history_error").assertIsDisplayed()
    }

    @Test fun vanishedSelection_showsSessionNoLongerAvailable() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 1)
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_s1").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_s1").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        // Delete the open session out from under the detail; the reactive read drops it -> NotAvailable.
        runBlocking { PickleballDatabase.getInstance(app()).sessionDao().deleteSessionById("s1") }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_detail_unavailable").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_detail_unavailable").assertIsDisplayed()
    }

    @Test fun selectedTab_survivesRecreation() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 2)
        val vm = readyVmNoSession()
        val hvm = HistoryViewModel(app())
        val restorer = StateRestorationTester(rule)
        restorer.setContent {
            MyApplicationTheme { SessionHistoryScreen(vm, hvm, AppScreen.Setup) }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("all_time_tab").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("all_time_tab").performClick()
        rule.onNodeWithText("Ranked by total wins, then point difference.").assertIsDisplayed()
        restorer.emulateSavedInstanceStateRestore()
        // rememberSaveable restores the selected tab -> still on All-Time after recreation.
        rule.onNodeWithText("Ranked by total wins, then point difference.").assertIsDisplayed()
    }

    @Test fun openedDetail_survivesRecreation() {
        seedSession("s1", "Mon", fourPlayers("s1"), wins = 2)
        val vm = readyVmNoSession()
        val hvm = HistoryViewModel(app())
        val restorer = StateRestorationTester(rule)
        restorer.setContent {
            MyApplicationTheme { SessionHistoryScreen(vm, hvm, AppScreen.Setup) }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_row_s1").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("session_row_s1").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        restorer.emulateSavedInstanceStateRestore()
        // rememberSaveable restores selectedSessionId -> still on the detail after recreation.
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
    }

    @Test fun historyScroll_preservedAcrossDetailNavigation() {
        // 20 sessions with INCREASING startTime: newest-first sorting puts h19 at the TOP and h00
        // at the BOTTOM. The LazyListState is hoisted above the detail/list branch, so scrolling to
        // the bottom then navigating detail -> back must retain the scrolled position.
        (0..19).forEach { i ->
            seedSession("h%02d".format(i), "S$i", fourPlayers("h$i"), wins = 0, startTime = i.toLong())
        }
        val vm = readyVmNoSession()
        content(vm)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("session_history_list").fetchSemanticsNodes().isNotEmpty()
        }
        // Scroll to the BOTTOM row (h00, oldest) and open it.
        rule.onNodeWithTag("session_history_list").performScrollToNode(hasTestTag("session_row_h00"))
        rule.onNodeWithTag("session_row_h00").performClick()
        rule.onNodeWithTag("session_detail").assertIsDisplayed()
        // Back to the list: if scroll were reset to the top, h00 would be off-screen. It must remain
        // displayed while the top row h19 stays uncomposed.
        rule.onNodeWithTag("session_history_back").performClick()
        rule.onNodeWithTag("session_row_h00").assertIsDisplayed()
        rule.onNodeWithTag("session_row_h19").assertDoesNotExist()
    }
}
```
Note on system Back: the screen defines a single `onBack` lambda wired to BOTH the `BackHandler` and the Back button, so the button tests above exercise the exact same code path. Dispatching a real hardware/gesture back event needs an activity-backed instrumented test (not `createComposeRule`), so that one parity is out of unit-test scope — do not add a flaky simulation. Everything else the spec's testing plan calls out (readiness/Loading, Error, empty, mismatch/leader wording, null-winner "Result unavailable", vanished selection, tab restoration, opened-detail restoration, scroll preservation, non-interactive all-time) is covered by the tests above plus the pure `HistoryStateTest`/`leaderLine` in Task 2.
Notes for the implementer:
- These tests do **not** reference `AppScreen.SessionHistory` (added in Task 5). Back-to-origin is asserted via `AppScreen.Setup` (the origin the screen is constructed with) and via observable UI, so the file compiles at Task 4.
- Seed order matters: the no-active-session tests inject a fake repo (`readyVmNoSession`) whose `loadLatestSession()` returns null, so seeding before constructing the VM is safe; only `activeSession_excludedFromHistoryList` uses the real `SessionViewModel(app())` (which adopts the seeded session as active).

- [ ] **Step 2: Run to verify it FAILS**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-session-history" && ./gradlew :app:testDebugUnitTest --tests "com.example.ui.SessionHistoryScreenTest"
```
Expected: FAIL — `SessionHistoryScreen` does not exist.

- [ ] **Step 3: Implement `SessionHistoryScreen`**

`app/src/main/java/com/example/ui/screens/SessionHistoryScreen.kt`:
```kotlin
package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.MatchResult
import com.example.model.RankedPlayer
import com.example.model.SessionListItem
import com.example.ui.theme.LocalPickItTokens
import com.example.viewmodel.AppScreen
import com.example.viewmodel.HistoryUiState
import com.example.viewmodel.HistoryViewModel
import com.example.viewmodel.SessionDetail
import com.example.viewmodel.SessionViewModel
import com.example.model.TeamId
import com.example.viewmodel.assembleHistoryState
import com.example.viewmodel.buildSessionDetail
import com.example.viewmodel.leaderLine
import java.text.DateFormat
import java.util.Date

// Locale-aware medium date (e.g. "Sep 23, 2026" / "23 Sept 2026" per the device locale).
private fun formatSessionDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

private fun formatMatchTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

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

    val uiState = remember(data, session, isLoaded) {
        assembleHistoryState(data, session?.id, isLoaded)
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    val historyListState = rememberLazyListState()
    val allTimeListState = rememberLazyListState()

    // ONE back action, shared by the system BackHandler and the Back button so parity can't drift:
    // in detail -> return to the list (tab + scroll preserved); on the list -> return to origin.
    val onBack: () -> Unit = {
        if (selectedSessionId != null) selectedSessionId = null
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

        val detailId = selectedSessionId
        if (detailId != null) {
            SessionDetailView(detail = buildSessionDetail(data, detailId))
            return@Column
        }

        when (val s = uiState) {
            is HistoryUiState.Loading -> CenterBox {
                CircularProgressIndicator(modifier = Modifier.testTag("session_history_loading"))
            }
            is HistoryUiState.Error -> CenterBox {
                Text("Couldn't load history.", color = tokens.textMuted, modifier = Modifier.testTag("session_history_error"))
            }
            is HistoryUiState.Content -> {
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
                        onOpen = { selectedSessionId = it },
                    )
                } else {
                    AllTimeList(rows = s.allTime, listState = allTimeListState)
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
    listState: androidx.compose.foundation.lazy.LazyListState,
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
private fun AllTimeList(rows: List<RankedPlayer>, listState: androidx.compose.foundation.lazy.LazyListState) {
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
                        .testTag("all_time_row_${r.id}")   // NON-interactive: no clickable
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${r.rank}", modifier = Modifier.padding(end = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (r.rank == 1) FontWeight.Black else FontWeight.Bold,
                        color = if (r.rank == 1) tokens.textAccent else tokens.textPrimary)
                    Text(r.name, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge, color = tokens.textPrimary)
                    Text("${r.wins}\u2013${r.losses}", style = MaterialTheme.typography.bodyMedium, color = tokens.textSecondary)
                    Text("  ${r.wins + r.losses}g", style = MaterialTheme.typography.bodyMedium, color = tokens.textMuted)
                    Text("  ${if (r.pointDiff > 0) "+${r.pointDiff}" else r.pointDiff}",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun SessionDetailView(detail: SessionDetail) {
    val tokens = LocalPickItTokens.current
    when (detail) {
        is SessionDetail.NotAvailable -> CenterBox {
            Text("Session no longer available.", color = tokens.textMuted,
                modifier = Modifier.testTag("session_detail_unavailable"))
        }
        is SessionDetail.Found -> {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("session_detail")) {
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
                        Text("${p.wins}\u2013${p.losses}  ${if (p.pointDiff > 0) "+${p.pointDiff}" else p.pointDiff}",
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
private fun MatchRow(m: MatchResult) {
    val tokens = LocalPickItTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().testTag("match_row_${m.matchId}").padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (m.winner == null) {
            Text(
                "${m.teamA.joinToString(" & ")} vs ${m.teamB.joinToString(" & ")} \u2014 Result unavailable",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, color = tokens.textMuted,
            )
        } else {
            val aWon = m.winner == TeamId.TEAM_A
            Text(
                m.teamA.joinToString(" & "),
                fontWeight = if (aWon) FontWeight.Bold else FontWeight.Normal, // winner emphasized
                color = tokens.textPrimary, style = MaterialTheme.typography.bodyMedium,
            )
            Text("  ${m.scoreA}\u2013${m.scoreB}  ", color = tokens.textSecondary,
                style = MaterialTheme.typography.bodyMedium)
            Text(
                m.teamB.joinToString(" & "),
                modifier = Modifier.weight(1f),
                fontWeight = if (!aWon) FontWeight.Bold else FontWeight.Normal, // winner emphasized
                color = tokens.textPrimary, style = MaterialTheme.typography.bodyMedium,
            )
            Text(formatMatchTime(m.endTime), style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
        }
    }
}

```
Implementer note: the History-list rows use `Modifier.clickable { ... }` to open the detail. The **All-Time rows must remain non-clickable** (no `clickable` modifier) — that is what makes them non-interactive per the spec. The session-detail "Standings" and "Matches" rows are likewise non-clickable.

- [ ] **Step 4: Run the test to verify it PASSES**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-session-history" && ./gradlew :app:testDebugUnitTest --tests "com.example.ui.SessionHistoryScreenTest"
```
Expected: PASS. If a Room `Flow` emission hasn't arrived when an assertion runs, the `rule.waitUntil(...)` guards in the tests cover it; keep those. Robolectric main-thread Room access is avoided because reads are `Flow`s collected off the main dispatcher.

- [ ] **Step 5: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/ui/screens/SessionHistoryScreen.kt`
- `app/src/test/java/com/example/ui/SessionHistoryScreenTest.kt`

Suggested message:
```
feat(history): Session History screen with History and All-Time tabs

Two-tab inset screen: past-session list (name, date, games, players, leader
line) tapping into a one-scroll session detail (standings + newest-first
matches), and a non-interactive all-time leaderboard. Own tab/scroll/back state.
```

---

## Task 5: Navigation wiring (AppScreen + MainActivity + Hub + Setup)

**Files:**
- Modify: `app/src/main/java/com/example/viewmodel/SessionViewModel.kt` (`AppScreen.SessionHistory`)
- Modify: `app/src/main/java/com/example/MainActivity.kt`
- Modify: `app/src/main/java/com/example/ui/screens/SessionHubScreen.kt` (active + empty branch)
- Modify: `app/src/main/java/com/example/ui/screens/SetupScreen.kt` (secondary action)
- Test: `app/src/test/java/com/example/ui/SessionHistoryNavTest.kt`

- [ ] **Step 1: Write the failing wiring test**

`app/src/test/java/com/example/ui/SessionHistoryNavTest.kt`:
```kotlin
package com.example.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.model.OpenPlaySession
import com.example.model.Player
import com.example.model.RotationPolicy
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.SessionHubScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h891dp", sdk = [36])
class SessionHistoryNavTest : RobolectricComposeTest() {

    @Before fun cleanDb() { resetSharedDb() }

    @Test fun hubEmptyBranch_opensHistory_withHubOrigin() {
        val vm = SessionViewModel(app()) // no saved session -> Hub empty branch
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }
        rule.onNodeWithTag("session_history_button").performClick()
        assertEquals(AppScreen.SessionHistory(AppScreen.SessionHub), vm.currentScreen.value)
    }

    @Test fun hubActiveBranch_opensHistory_withHubOrigin() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(
            OpenPlaySession(
                id = "s1", name = "Live", rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
                courts = emptyList(),
                roster = listOf(Player(id = "a", name = "Al", matchesPlayed = 1, matchesWon = 1)),
            )
        )
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }
        rule.onNodeWithTag("session_history_button").performClick()
        assertEquals(AppScreen.SessionHistory(AppScreen.SessionHub), vm.currentScreen.value)
    }

    @Test fun setup_opensHistory_withSetupOrigin() {
        val vm = SessionViewModel(app())
        rule.setContent { MyApplicationTheme { SetupScreen(viewModel = vm) } }
        // The action lives late in the Setup LazyColumn (tag "setup_screen_content") — scroll first.
        rule.onNodeWithTag("setup_screen_content").performScrollToNode(hasTestTag("setup_history_button"))
        rule.onNodeWithTag("setup_history_button").performClick()
        assertEquals(AppScreen.SessionHistory(AppScreen.Setup), vm.currentScreen.value)
    }
}
```

- [ ] **Step 2: Run to verify it FAILS**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-session-history" && ./gradlew :app:testDebugUnitTest --tests "com.example.ui.SessionHistoryNavTest"
```
Expected: FAIL — `AppScreen.SessionHistory` and the buttons do not exist.

- [ ] **Step 3: Add `AppScreen.SessionHistory`**

In `app/src/main/java/com/example/viewmodel/SessionViewModel.kt`, add to the `AppScreen` sealed class (after `object LiveRanking`):
```kotlin
    data class SessionHistory(val origin: AppScreen) : AppScreen()
```

- [ ] **Step 4: Wire `MainActivity`**

In `app/src/main/java/com/example/MainActivity.kt`:

Add imports:
```kotlin
import com.example.ui.screens.SessionHistoryScreen
import com.example.viewmodel.HistoryViewModel
```
Add the second ViewModel to the activity (next to `sessionViewModel`):
```kotlin
    private val historyViewModel: HistoryViewModel by viewModels()
```
Pass it into the content composable — change the `setContent` call:
```kotlin
                PickleballAppContent(viewModel = sessionViewModel, historyViewModel = historyViewModel)
```
Change the composable signature:
```kotlin
fun PickleballAppContent(viewModel: SessionViewModel, historyViewModel: HistoryViewModel) {
```
Add the `SessionHistory` branch to the **inner** exhaustive `when(screen)` (it is NOT full-bleed — it renders inside the padded `Surface`):
```kotlin
                        is AppScreen.SessionHistory -> SessionHistoryScreen(
                            sessionViewModel = viewModel,
                            historyViewModel = historyViewModel,
                            origin = screen.origin,
                        )
```
(Place it among the other inner-`when` arms. The outer `when {}` full-bleed branch is unchanged — `SessionHistory` is not added there.)

- [ ] **Step 5: Add the Hub entry buttons (active branch + empty branch)**

In `app/src/main/java/com/example/ui/screens/SessionHubScreen.kt`:

Add the icon import near the other icon imports:
```kotlin
import androidx.compose.material.icons.filled.History
```
**Active branch** — add this `IconButton` immediately before the `live_ranking_button` `IconButton` (`SessionHubScreen.kt:120`):
```kotlin
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.SessionHistory(AppScreen.SessionHub)) },
                        modifier = Modifier.testTag("session_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Session History",
                            tint = tokens.textPrimary
                        )
                    }
```
**Empty branch** — inside the `if (activeSession == null) { ... }` block, add a secondary action below the "Start a session" `Button` (after `SessionHubScreen.kt:67`, before the closing `}` of the `Column`):
```kotlin
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.navigateTo(AppScreen.SessionHistory(AppScreen.SessionHub)) },
                    modifier = Modifier.testTag("session_history_button"),
                ) {
                    Text("Session History", color = LocalPickItTokens.current.textSecondary)
                }
```
(If `Spacer`/`height` are not already imported in this file, they are — the empty branch already uses `Spacer(Modifier.height(...))`.)

- [ ] **Step 6: Add the Setup secondary action**

In `app/src/main/java/com/example/ui/screens/SetupScreen.kt`, add an `item {}` immediately after the "Launch Session Button" `item { ... }` block (which ends around `SetupScreen.kt:346-350`). Match the file's existing `item { }` + `tokens` + `viewModel` usage:
```kotlin
            item {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.navigateTo(AppScreen.SessionHistory(AppScreen.Setup)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_history_button"),
                ) {
                    Text("Session History", color = tokens.textSecondary)
                }
            }
```
(Verify `AppScreen` is imported in `SetupScreen.kt`; it already references `AppScreen` via `viewModel.navigateTo(AppScreen.SessionHub)` elsewhere in the file — if not, add `import com.example.viewmodel.AppScreen`.)

- [ ] **Step 7: No screen-test change needed**

The Task 4 `SessionHistoryScreenTest` is deliberately self-contained (it never references `AppScreen.SessionHistory`), so nothing in it needs updating now that the route exists. Origin wiring is covered by `SessionHistoryNavTest` (this task), and `backFromDetail_returnsToList_thenBackAgainToOrigin` (Task 4) already verifies the detail → list → origin sequence via observable UI. Leave the Task 4 tests unchanged.

- [ ] **Step 8: Run the wiring test to verify it PASSES**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-session-history" && ./gradlew :app:testDebugUnitTest --tests "com.example.ui.SessionHistoryNavTest"
```
Expected: PASS.

- [ ] **Step 9: Run the full suite to confirm nothing regressed**

Run:
```bash
cd "C:/Users/USER/OneDrive - usep.edu.ph/Desktop/pickit-mobile-session-history" && ./gradlew :app:testDebugUnitTest
```
Expected: PASS — all new tests plus the pre-existing suite (including Live Ranking and Court Call) are green. Report the total test count.

- [ ] **Step 10: Commit (via the `commit` skill)**

Stage ONLY:
- `app/src/main/java/com/example/viewmodel/SessionViewModel.kt`
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/ui/screens/SessionHubScreen.kt`
- `app/src/main/java/com/example/ui/screens/SetupScreen.kt`
- `app/src/test/java/com/example/ui/SessionHistoryNavTest.kt`

Suggested message:
```
feat(history): wire Session History into navigation, Hub, and Setup

Add AppScreen.SessionHistory(origin), render it inside MainActivity's padded
Surface (not full-bleed), and add entry actions on the Session Hub (active and
empty branches) and Setup, each returning to its origin.
```

---

## Notes & non-goals (from the spec)

- **No schema changes, no new writes, no write-path change.** Only additive reactive DAO reads and a small in-memory `isSessionLoaded` flag. The `completeMatch` non-atomic-persistence risk is a documented forward-note, not fixed here.
- **Two identity models:** session-scope standings/top-player run `RankingEngine` on the persisted roster (by player id, reproducing Live Ranking); All-Time aggregates by normalized name (documented merge caveat, surfaced in the All-Time subtitle).
- **All-Time spans every session including the active one** (matches are in the same table); only the History list excludes the active session id, and only once `isSessionLoaded` is true.
- **Deferred (next slice):** player-detail screen + "sessions played" stat; win%/performance view; delete/rename/merge; no TV layout.
- Before `/create-pr`, run the full suite (`./gradlew :app:testDebugUnitTest`) and `./gradlew assembleDebug`. CI runs `:app:testDebugUnitTest --tests PickleballEngineTest` + `assembleDebug`.
