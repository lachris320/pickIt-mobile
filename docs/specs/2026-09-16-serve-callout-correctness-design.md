# Serve-Side & Callout Correctness — Design Spec

- **Date:** 2026-09-16
- **Status:** Approved (brainstorming gate passed)
- **Scope:** Two narrow correctness fixes in the pure engine/model layer. No UI, persistence, or schema changes.
- **Source:** User-reported bug + user-provided rule doc (`Pickleball Serving Side After Side-Out Rule.md`).

## Problem

Two related defects in how the game engine reports serve state after a side-out.

### Bug 1 — Serve side after a side-out uses score parity (wrong)

In `PickleballGameEngine.recordRally`, the side-out branch derives the serving side from the incoming team's score parity:

```kotlin
val currentServingScore = if (newServingTeam == TeamId.TEAM_A) newScoreA else newScoreB
// WRONG: parity does not decide the FIRST serve side after a side-out
newServingSide = if (currentServingScore % 2 == 0) CourtSide.RIGHT else CourtSide.LEFT
```

Per official USA Pickleball doubles rules, **the incoming team's first serve after a side-out is always from the RIGHT (even) court.** Score parity determines *which player* stands in the right court, not *which side the first serve comes from*. The current code sends the first serve to the LEFT whenever the incoming team's score is odd.

**Reproduction (user-reported):** at `1(serving) – 5(receiving) – 2(server)` a side-out occurs. The new server's callout is `5 – 1 – 1`, and 5 is odd, so the engine incorrectly places the first server on the LEFT. It should be RIGHT.

### Bug 2 — Callout always leads with Team A's score (wrong)

`Match.calloutString()` hard-codes Team A first:

```kotlin
fun calloutString(): String {
    return "$scoreA - $scoreB - $serverNumber"
}
```

The correct pickleball callout is **serving-team score, then receiving-team score, then server number**. When Team B serves, the current output reverses the first two numbers (e.g. shows `1 – 5 – 1` when it should announce `5 – 1 – 1`).

## Design

### Fix 1 — `PickleballGameEngine.recordRally`, side-out branch

Replace the parity computation with an unconditional RIGHT. Server number stays `1`, server stays the incoming team's `player1` (Scope A — player identity unchanged). The receiver still follows from the serving side.

```kotlin
// Side-out: the incoming team's FIRST serve is ALWAYS from the right/even court.
// Score parity picks which PLAYER stands right, not which side serves first.
newServingSide = CourtSide.RIGHT
newServer = newServingTeamObj.player1
newReceiver = newReceivingTeamObj.player1   // Scope-A: player1 = right-court receiver by convention
```

**Also delete** the now-unused `val currentServingScore = ...` line (currently
`PickleballGameEngine.kt:108`) — with parity gone it is dead and would raise an unused-variable warning.

The two other branches are **unchanged**:
- **Continuing serve** (serving team scored): still alternates `RIGHT <-> LEFT` — this is correct.
- **Server 1 → Server 2** (first server faulted, same team): still flips side — correct.

Because undo is event-sourced (`undoLastRally` replays through `recordRally`), the fix propagates to undo/redo automatically.

### Fix 2 — `Match.calloutString()`

Lead with the serving team's score:

```kotlin
fun calloutString(): String {
    val servingScore = if (servingTeam == TeamId.TEAM_A) scoreA else scoreB
    val receivingScore = if (servingTeam == TeamId.TEAM_A) scoreB else scoreA
    return "$servingScore - $receivingScore - $serverNumber"
}
```

## No UI changes

Both scoreboards (`LiveScoreboardScreen`, `StandaloneScoreboardScreen`) already render `match.calloutString()`, and `TacticalPickleballCourtDiagram` reads `match.servingSide`. They inherit both fixes with no edits.

## Non-goals

- **Which player** serves/receives after a side-out (Scope A keeps `player1`; no name-position rework).
- The `0-0-2` game-start convention (already correct).
- Any UI, persistence, DAO, or schema change.

### Known limitation (forward-note, not a bug in this slice)

Scope A corrects the serve **side** and the **numbers** only. It does not correct **player identity**:
after an odd-score side-out the *physically* right-court player may be `player2`, but the engine keeps
naming `player1` as server/receiver. So `currentServer`/`currentReceiver` — and therefore the court
diagram's player labels — can still show the wrong *name* even though the side is now right. This is
internally consistent with the locked Scope-A decision; correcting player identity is the natural
follow-up slice once this lands.

## Test plan (TDD, `PickleballEngineTest`)

**Existing tests stay green** — verified: every current side-out assertion is at score 0 (even), where old parity and the fix both yield RIGHT; the only `calloutString` assertion is Team-A-serving `"0 - 0 - 2"`, unchanged. The fixes are purely corrective on paths no test currently covers.

New regression tests:

1. **Odd-score side-out → RIGHT (the reported bug).** Drive to `1 – 5 – 2` (Team A serving, Team B leading), force the side-out, assert incoming state `5 – 1 – 1` with `servingSide == RIGHT`, `serverNumber == 1`, **and `currentReceiver == receivingTeam.player1`** (locks the receiver path). The implementer must construct a *legal* `recordRally` sequence to reach `1 – 5 – 2` — the spec does not prescribe one; derive it (e.g. alternate the required side-outs/points), do not hand-wave it.
2. **Alternation after the corrected side-out.** From `5 – 1 – 1` RIGHT: serving team wins → `6 – 1 – 1` LEFT; wins again → `7 – 1 – 1` RIGHT.
3. **Mirror direction (symmetry guard).** Same odd-score side-out but with **Team A** as the incoming team → assert RIGHT. Guards against a future asymmetric edit regressing one direction silently.
4. **Even-score side-out still RIGHT.** Guards against a naive "always flip" regression.
5. **Undo across an odd-score side-out** restores prior state exactly (event replay integrity).
6. **Callout leads with serving team, both teams.** Team A serving → `scoreA` first; Team B serving → `scoreB` first; verify server number `1` vs `2` renders in the third slot.

## Acceptance criteria (from user rule doc)

- Side-out always starts the incoming serve from the right/even court. ✔ Fix 1
- Incoming first server is Server 1. ✔ (unchanged; already `1`)
- An odd score does not force the serve to the left. ✔ Fix 1
- Server switches courts on each point won. ✔ (continuing-serve branch unchanged)
- Regression `1 – 5 – 2 → 5 – 1 – 1 → RIGHT` passes. ✔ Test 1
- Existing valid serving/player-position behavior is not changed unnecessarily. ✔ (Scope A; only the buggy parity path changes)
- Callout order is serving-score, receiving-score, server-number. ✔ Fix 2
