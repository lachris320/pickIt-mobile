# Serve-Side & Callout Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the subagent-build skill (`/subagent-build`) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix two pure-logic defects in the pickleball engine — (1) after a side-out the incoming team's first serve must always come from the RIGHT/even court, and (2) `calloutString()` must lead with the serving team's score.

**Architecture:** Both fixes live in the pure engine/model layer. Fix 1 is a 3-line change in `PickleballGameEngine.recordRally`'s side-out branch (drop score-parity, always RIGHT); Fix 2 rewrites `Match.calloutString()` to pick the serving team's score first. The engine is event-sourced, so `undoLastRally` (which replays through `recordRally`) inherits Fix 1 automatically. No UI/persistence/schema changes — `LiveScoreboardScreen`, `StandaloneScoreboardScreen`, and `TacticalPickleballCourtDiagram` already consume `calloutString()`/`servingSide`.

**Tech Stack:** Kotlin, plain JUnit4 (pure engine tests — no Robolectric needed for `PickleballEngineTest`). Gradle.

**Spec:** [docs/specs/2026-09-16-serve-callout-correctness-design.md](../specs/2026-09-16-serve-callout-correctness-design.md) (design-spec review: APPROVED).

**Suggested branch:** `fix/serve-side-and-callout` (created by `/worktrees` at execution time).

**Commit discipline (project rules):** commit via the `commit` skill; stage only the explicit paths named in each commit step (never `git add -A` — the tree has untracked `gradlew`, `gradle/wrapper/*`, `.idea/`, etc. that must NOT be staged). No Claude co-author trailer; no "Generated with Claude Code" line.

**Test command (used throughout):**
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.PickleballEngineTest"
```

---

## File Structure

- `app/src/main/java/com/example/engine/PickleballGameEngine.kt` — modify `recordRally` side-out branch (~lines 106-115).
- `app/src/main/java/com/example/model/SessionModels.kt` — modify `Match.calloutString()` (~lines 103-105).
- `app/src/test/java/com/example/PickleballEngineTest.kt` — add a `play(...)` helper and the regression tests for both fixes.

No new files. No UI files touched.

---

## Reference: rally-sequence traces (verified against the engine)

These winner sequences are used by the tests below. Each was hand-traced through `recordRally`. `A` = `TeamId.TEAM_A` won the rally, `B` = `TeamId.TEAM_B` won.

**Sequence ODD_SIDEOUT_B** — from `createMatch(teamA, teamB)` (Team A serves first). Reaches the reported bug: an odd-score side-out to Team B.
`B, B, B, B, B, B, A, A, A, B, B`
- Final state (with Fix 1): `scoreA=1, scoreB=5, servingTeam=TEAM_B, serverNumber=1, servingSide=RIGHT, currentServer="b1", currentReceiver="a1"`.
- Rally 10 (one before the last) is the `1–5–2` state: `scoreA=1, scoreB=5, servingTeam=TEAM_A, serverNumber=2, servingSide=RIGHT, currentServer="a2"`.
- Under the OLD buggy code the final `servingSide` is `LEFT` (score 5 is odd) — this is what the test turns RED on.

**Sequence ODD_SIDEOUT_A (mirror)** — from `createMatch(teamA, teamB, firstServingTeam = TEAM_B)` (Team B serves first). Odd-score side-out to Team A.
`A, A, A, A, A, A, B, B, B, A, A`
- Final state (with Fix 1): `scoreA=5, scoreB=1, servingTeam=TEAM_A, serverNumber=1, servingSide=RIGHT`.

**Sequence EVEN_SIDEOUT** — from `createMatch(teamA, teamB)`. Side-out to Team A at a non-zero EVEN incoming score (2). Guards against a naive "always flip" regression (passes under both old and new code).
`A, A, B, A, A`
- Final state: `scoreA=2, scoreB=0, servingTeam=TEAM_A, serverNumber=1, servingSide=RIGHT`.

---

## Task 1: Fix serve side after side-out (Fix 1)

**Files:**
- Modify: `app/src/main/java/com/example/engine/PickleballGameEngine.kt:106-115`
- Test: `app/src/test/java/com/example/PickleballEngineTest.kt`

- [ ] **Step 1: Add the `play(...)` helper to the test class**

Add this private helper inside `class PickleballEngineTest` (e.g. just under the `teamB` field on line 17), so multi-rally tests stay DRY:

```kotlin
    /** Plays a sequence of rally winners through the engine and returns the final match. */
    private fun play(start: Match, vararg winners: TeamId): Match {
        var m = start
        for (w in winners) m = PickleballGameEngine.recordRally(m, w)
        return m
    }
```

- [ ] **Step 2: Write the failing test (odd-score side-out → RIGHT)**

Add to `PickleballEngineTest`:

```kotlin
    @Test
    fun `side out at odd receiving score serves from RIGHT not LEFT`() {
        val start = PickleballGameEngine.createMatch(courtId = 1, teamA = teamA, teamB = teamB)
        // ODD_SIDEOUT_B: reaches 1-5-2 (Team A serving, server 2), then Team B side-outs in.
        val match = play(start, TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_B,
            TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_A, TeamId.TEAM_A, TeamId.TEAM_A,
            TeamId.TEAM_B, TeamId.TEAM_B)

        assertEquals(1, match.scoreA)
        assertEquals(5, match.scoreB)
        assertEquals(TeamId.TEAM_B, match.servingTeam)
        assertEquals(1, match.serverNumber)
        // The bug: score 5 is odd, so the old code put the first serve on the LEFT.
        assertEquals(CourtSide.RIGHT, match.servingSide)
        assertEquals("b1", match.currentServer.id)
        assertEquals("a1", match.currentReceiver.id) // right-court receiver = receiving team player1
    }
```

- [ ] **Step 3: Run the test to verify it FAILS**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.PickleballEngineTest"
```
Expected: FAIL — `side out at odd receiving score serves from RIGHT not LEFT` asserts `RIGHT` but the current code yields `CourtSide.LEFT` (and `currentReceiver` `"a2"`).

- [ ] **Step 4: Apply Fix 1 in `recordRally`**

In `app/src/main/java/com/example/engine/PickleballGameEngine.kt`, the side-out branch currently reads:

```kotlin
                val newServingTeamObj = if (newServingTeam == TeamId.TEAM_A) current.teamA else current.teamB
                val newReceivingTeamObj = if (newServingTeam == TeamId.TEAM_A) current.teamB else current.teamA
                val currentServingScore = if (newServingTeam == TeamId.TEAM_A) newScoreA else newScoreB

                // Serve side is determined by whether the serving team's score is even (Right) or odd (Left)
                newServingSide = if (currentServingScore % 2 == 0) CourtSide.RIGHT else CourtSide.LEFT

                newServer = newServingTeamObj.player1
                newReceiver = if (newServingSide == CourtSide.RIGHT) newReceivingTeamObj.player1 else newReceivingTeamObj.player2
                description = "Side Out! Serve transfers to ${newServingTeamObj.playerNames()}"
```

Replace it with (removes the now-dead `currentServingScore`, always serves RIGHT):

```kotlin
                val newServingTeamObj = if (newServingTeam == TeamId.TEAM_A) current.teamA else current.teamB
                val newReceivingTeamObj = if (newServingTeam == TeamId.TEAM_A) current.teamB else current.teamA

                // The incoming team's FIRST serve after a side-out is ALWAYS from the
                // right/even court. Score parity picks which PLAYER stands in the right
                // court, not which side the first serve comes from.
                newServingSide = CourtSide.RIGHT
                newServer = newServingTeamObj.player1
                newReceiver = newReceivingTeamObj.player1
                description = "Side Out! Serve transfers to ${newServingTeamObj.playerNames()}"
```

Leave the continuing-serve branch (~line 71) and the server-1→server-2 branch (~line 90) unchanged.

- [ ] **Step 5: Run the test to verify it PASSES**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.PickleballEngineTest"
```
Expected: PASS — the new test is green and all pre-existing tests still pass (every existing side-out assertion is at score 0, where old and new code both give RIGHT).

- [ ] **Step 6: Add the remaining Fix 1 regression tests**

Add to `PickleballEngineTest`:

```kotlin
    @Test
    fun `serve alternates correctly after the corrected side out`() {
        val start = PickleballGameEngine.createMatch(courtId = 1, teamA = teamA, teamB = teamB)
        // Reach 5-1-1 RIGHT (Team B serving), then Team B wins two rallies.
        var match = play(start, TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_B,
            TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_A, TeamId.TEAM_A, TeamId.TEAM_A,
            TeamId.TEAM_B, TeamId.TEAM_B)
        assertEquals(CourtSide.RIGHT, match.servingSide) // 5-1-1

        match = PickleballGameEngine.recordRally(match, TeamId.TEAM_B) // 6-1-1
        assertEquals(6, match.scoreB)
        assertEquals(1, match.serverNumber)
        assertEquals(CourtSide.LEFT, match.servingSide)

        match = PickleballGameEngine.recordRally(match, TeamId.TEAM_B) // 7-1-1
        assertEquals(7, match.scoreB)
        assertEquals(1, match.serverNumber)
        assertEquals(CourtSide.RIGHT, match.servingSide)
    }

    @Test
    fun `side out at odd score serves from RIGHT for Team A too (mirror)`() {
        // Team B serves first; ODD_SIDEOUT_A reaches an odd-score side-out to Team A.
        val start = PickleballGameEngine.createMatch(
            courtId = 1, teamA = teamA, teamB = teamB, firstServingTeam = TeamId.TEAM_B
        )
        val match = play(start, TeamId.TEAM_A, TeamId.TEAM_A, TeamId.TEAM_A, TeamId.TEAM_A,
            TeamId.TEAM_A, TeamId.TEAM_A, TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_B,
            TeamId.TEAM_A, TeamId.TEAM_A)

        assertEquals(5, match.scoreA)
        assertEquals(1, match.scoreB)
        assertEquals(TeamId.TEAM_A, match.servingTeam)
        assertEquals(1, match.serverNumber)
        assertEquals(CourtSide.RIGHT, match.servingSide)
    }

    @Test
    fun `side out at non-zero even score still serves from RIGHT`() {
        val start = PickleballGameEngine.createMatch(courtId = 1, teamA = teamA, teamB = teamB)
        // EVEN_SIDEOUT: Team A comes in via side-out at its own even score (2).
        val match = play(start, TeamId.TEAM_A, TeamId.TEAM_A, TeamId.TEAM_B,
            TeamId.TEAM_A, TeamId.TEAM_A)

        assertEquals(2, match.scoreA)
        assertEquals(0, match.scoreB)
        assertEquals(TeamId.TEAM_A, match.servingTeam)
        assertEquals(1, match.serverNumber)
        assertEquals(CourtSide.RIGHT, match.servingSide)
    }

    @Test
    fun `undo across an odd-score side out restores the corrected RIGHT side`() {
        val start = PickleballGameEngine.createMatch(courtId = 1, teamA = teamA, teamB = teamB)
        // Reach 5-1-1 RIGHT (Team B serving), play one more rally, then undo it.
        var match = play(start, TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_B,
            TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_A, TeamId.TEAM_A, TeamId.TEAM_A,
            TeamId.TEAM_B, TeamId.TEAM_B)
        match = PickleballGameEngine.recordRally(match, TeamId.TEAM_B) // 6-1-1 LEFT
        match = PickleballGameEngine.undoLastRally(match)              // replay back to 5-1-1

        assertEquals(1, match.scoreA)
        assertEquals(5, match.scoreB)
        assertEquals(TeamId.TEAM_B, match.servingTeam)
        assertEquals(1, match.serverNumber)
        assertEquals(CourtSide.RIGHT, match.servingSide) // fix survives event replay
    }
```

- [ ] **Step 7: Run all tests to verify they PASS**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.PickleballEngineTest"
```
Expected: PASS — all four new tests plus the pre-existing suite are green.

- [ ] **Step 8: Commit (via the `commit` skill)**

Stage ONLY these paths, then commit:
- `app/src/main/java/com/example/engine/PickleballGameEngine.kt`
- `app/src/test/java/com/example/PickleballEngineTest.kt`

Suggested message:
```
fix(engine): first serve after a side-out is always from the right court

The side-out branch derived the serve side from the incoming team's score
parity, so an odd score wrongly started the serve on the left. Per official
doubles rules the first serve after a side-out is always from the right/even
court; parity only decides which player stands there. Serve unconditionally
from the right and drop the parity computation.
```

---

## Task 2: Fix callout order (Fix 2)

**Files:**
- Modify: `app/src/main/java/com/example/model/SessionModels.kt:103-105`
- Test: `app/src/test/java/com/example/PickleballEngineTest.kt`

- [ ] **Step 1: Write the failing test (callout leads with serving team)**

Add to `PickleballEngineTest`:

```kotlin
    @Test
    fun `callout leads with the serving team score for both teams`() {
        // Team A serving at start: 0-0-2 (A leads because A serves) — unchanged.
        val startA = PickleballGameEngine.createMatch(courtId = 1, teamA = teamA, teamB = teamB)
        assertEquals("0 - 0 - 2", startA.calloutString())

        // Team B serving, server 2, at scoreB=5 / scoreA=0 -> "5 - 0 - 2" (not "0 - 5 - 2").
        val bServer2 = play(startA, TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_B,
            TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_A)
        assertEquals(TeamId.TEAM_B, bServer2.servingTeam)
        assertEquals(2, bServer2.serverNumber)
        assertEquals("5 - 0 - 2", bServer2.calloutString())

        // Team B serving, server 1, at scoreB=5 / scoreA=1 -> "5 - 1 - 1" (not "1 - 5 - 1").
        val bServer1 = play(startA, TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_B,
            TeamId.TEAM_B, TeamId.TEAM_B, TeamId.TEAM_A, TeamId.TEAM_A, TeamId.TEAM_A,
            TeamId.TEAM_B, TeamId.TEAM_B)
        assertEquals(TeamId.TEAM_B, bServer1.servingTeam)
        assertEquals(1, bServer1.serverNumber)
        assertEquals("5 - 1 - 1", bServer1.calloutString())
    }
```

- [ ] **Step 2: Run the test to verify it FAILS**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.PickleballEngineTest"
```
Expected: FAIL — the current `calloutString()` always leads with `scoreA`, so it returns `"0 - 5 - 2"` / `"1 - 5 - 1"` for the Team-B-serving cases. (The `"0 - 0 - 2"` assertion already passes.)

- [ ] **Step 3: Apply Fix 2 in `calloutString()`**

In `app/src/main/java/com/example/model/SessionModels.kt`, replace:

```kotlin
    fun calloutString(): String {
        return "$scoreA - $scoreB - $serverNumber"
    }
```

with:

```kotlin
    fun calloutString(): String {
        val servingScore = if (servingTeam == TeamId.TEAM_A) scoreA else scoreB
        val receivingScore = if (servingTeam == TeamId.TEAM_A) scoreB else scoreA
        return "$servingScore - $receivingScore - $serverNumber"
    }
```

- [ ] **Step 4: Run the test to verify it PASSES**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.PickleballEngineTest"
```
Expected: PASS — the callout test is green and the pre-existing `"0 - 0 - 2"` assertion (Team A serving) is unaffected.

- [ ] **Step 5: Commit (via the `commit` skill)**

Stage ONLY these paths, then commit:
- `app/src/main/java/com/example/model/SessionModels.kt`
- `app/src/test/java/com/example/PickleballEngineTest.kt`

Suggested message:
```
fix(model): callout leads with the serving team's score

calloutString() always printed Team A's score first, so a Team-B serve was
announced with the scores reversed. Announce serving-score, receiving-score,
server-number as the rules require.
```

---

## Notes & non-goals (from the spec)

- **Player identity after an odd-score side-out is intentionally NOT fixed here** (Scope A). The serve *side* and *numbers* are corrected; `currentServer`/`currentReceiver` may still name `player1` when the physical right-court player is `player2`. This is the natural follow-up slice.
- No UI, DAO, Room, or schema changes. The scoreboards and court diagram inherit both fixes because they already read `calloutString()` and `servingSide`.
- After both tasks: run the full suite once (`./gradlew :app:testDebugUnitTest`) before `/create-pr` to confirm nothing else regressed. CI runs `:app:testDebugUnitTest --tests PickleballEngineTest` + `assembleDebug`.
