# PickIt-mobile — Court Call Big-Screen Display (Scope B)

- **Status:** Approved (brainstorm) — pending the independent design-spec review gate (`/claude-review` / `/codex-review`, DESIGN-SPEC mode), then final user review, then `/writing-plans`.
- **Date:** 2026-09-15
- **Author:** Design OS APPLY → brainstorm
- **Scope cycle:** B — the "Court Call" / big-screen public-display mode, deferred from the Design-System & UX-Correctness pass (cycle A, PR #2, now on `main`). This spec builds on the token + type + status discipline that cycle A landed and does not revisit it.

---

## 1. Context & problem

PickIt-mobile is a courtside pickleball **open-play session manager + live scorekeeper** (Kotlin / Jetpack Compose / Material3), fully **local and single-device**: one `OpenPlaySession` in Room, four screens (Setup → Session Hub → Live Scoreboard → Standalone Scoreboard) driven by a sealed `AppScreen` + `StateFlow` in `SessionViewModel`. Cycle A fixed the phone-facing hierarchy; it explicitly deferred the venue-facing surface.

The gap: at an actual open-play session the organizer runs everything from the phone in their hand, but the **players standing around the courts cannot see it**. They repeatedly ask "am I up next?" and "what's the score on court 3?", and the organizer becomes a human status board. The phone Hub is tuned for a **reach-distance kiosk**; it is unreadable from across a gym or an outdoor court.

**Usage context (drives the weighting):** a **distance-read, read-only PUBLIC DISPLAY** (Design OS `20-context-modifiers/contexts.md` → **Public Display**), mirrored from the phone to a venue TV via ordinary screen mirroring (Chromecast / AirPlay / HDMI). Environment is **mixed indoor/outdoor**. Two audiences, both passive: **players glancing** from several meters away to answer one question ("am I up?"), and **one organizer** confirming the board reflects the phone. Nobody touches the TV. This is the near-opposite of the kiosk weighting: no interaction, one glanceable message per court, legibility at distance over density.

The public-display resolution is the inverse of cycle A's "layering": strip to the **minimum a passerby needs**, make exactly one thing per screen shout, and encode every state so it survives distance, glare, and color-blindness.

## 2. Goals / Non-goals

**Goals**
- A new **read-only projection** of the live session that is legible across a court: `CourtCall`.
- **Exactly one** von-Restorff standout on the whole board — the court that is **up now** — mirroring cycle A's "one primary, lowest `courtId`" Hub emphasis discipline, scaled up for distance.
- Every court state rendered with a **consistent color + shape + label**, grayscale / color-blind safe, reusing cycle A's status discipline.
- A **distance-legibility contract**: court number is the largest element, TV-tuned type floors, high contrast, stable layout.
- Reuse the existing token + type system verbatim — **no new inline hex**.

**Non-goals (explicitly out of scope)**
- **No networking, no second-device cast protocol, no Android `Presentation` API.** Rejected: `Presentation` only activates with a *physically connected* secondary display and adds display-lifecycle complexity; screen-mirroring a normal full-screen Activity covers the venue-TV case for a fraction of the surface area (**YAGNI**). The phone *is* the source of truth and the thing being mirrored.
- **No interactivity beyond exit.** The board reads state; it never writes. The only touch target is the exit affordance.
- **No domain / Room / `SessionViewModel`-state changes.** No new `StateFlow`, no schema change, no change to how `RotationEngine` computes recommendations.
- No per-court theming, no configuration screen, no auto-advancing scores of its own — every value is derived from the already-live `session`.

## 3. Approach

**A read-only Compose projection over the existing `session` `StateFlow`.** `CourtCallScreen` collects `viewModel.session` (the same flow the Hub reads) and renders `session.courts`, each court's `currentMatch`, and `session.activeRecommendations`. It computes a **derived per-court display state** (§5) at composition time.

**Read-only means no *session/domain* writes — not zero view state.** The board never mutates the session and can never make the phone's data drift; it holds only **ephemeral, local view state** that never feeds back into `SessionViewModel`: the current **page index**, the **auto-cycle timer**, and a remembered **previous derived-state snapshot** used to detect UP-NOW/FINAL transitions for the §8 highlight and the "hold on a just-called court" rule. All of it lives in `remember`/`rememberSaveable` inside `CourtCallScreen`, is discarded on exit, and is derived purely from successive `session` emissions. This is the "strip to the public-display minimum" resolution the context modifier prescribes, and it keeps the diff to two new UI files plus nav wiring, proportionate to the feature.

## 4. Delivery / architecture

- **New route:** add `object CourtCall : AppScreen()` to the sealed `AppScreen` class in `SessionViewModel.kt`. It carries no payload — it reads the live `session` like the Hub does.
- **Navigation:** `SessionViewModel.navigateTo(AppScreen.CourtCall)` / system back to leave. Wire the new branch into the `Crossfade` `when` in `PickleballAppContent` (`MainActivity.kt:53`).
- **Entry point:** a **"Court Call"** action in the **Session Hub top bar**. The Hub renders its top bar only in the non-null session state (its `session == null` branch early-returns a top-bar-less empty Scaffold, `SessionHubScreen.kt:41–71`), so the action is **structurally present only when a session exists** — no separate disabled state is needed, and none should be added.
- **Exit:** system **back**, plus a small, **low-contrast corner close affordance** — the **only touch target on the board**. It is deliberately quiet so it does not compete with the court tiles for a distant eye, but present so a passerby who taps the TV (or the organizer holding the mirrored phone) can leave.
- **Escaping the app-theme frame (required for the fixed dark board, §7).** The root of `MainActivity` wraps all content in `Surface(color = MaterialTheme.colorScheme.background, modifier = …safeDrawingPadding())` (`MainActivity.kt:36–42`). In Light / Follow-system mode that Surface is **light** and `safeDrawingPadding` **insets** the content — so a naive board would render dark-inside-a-light-inset-frame, re-introducing exactly the glare failure §7 exists to remove. Therefore the `CourtCall` branch must render **outside** that themed, padded Surface: special-case `CourtCall` in `PickleballAppContent` so it is composed in its own **full-bleed** container (no `safeDrawingPadding`, no `MaterialTheme.colorScheme.background` Surface) that paints `DarkTokens.canvas` edge-to-edge. The board subtree **must not read any `MaterialTheme.colorScheme.*`** — it draws only from the forced `DarkTokens` provided via `LocalPickItTokens` (§7). All other screens keep the existing padded Surface unchanged.
- **Display hygiene (from `MainActivity`):** while `CourtCall` is the current screen, set `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` and enter **immersive full-screen** (hide the status + navigation bars via `WindowInsetsControllerCompat`). **Clear both on exit** (restore bars, clear the keep-on flag) so the rest of the app is unaffected.
- **Single lifecycle source of truth (not two mechanisms).** Do **not** drive keep-on/immersive from a `DisposableEffect` keyed only on `screen is CourtCall`: that key does not change when the app is backgrounded and resumed while still on `CourtCall`, so the flags would not be re-applied on return to foreground. Instead use **one** owner — a `LifecycleEventObserver` (or `repeatOnLifecycle(RESUMED)`) that reads the *current* screen and, on `ON_RESUME`, applies keep-on + immersive **iff** the current screen is `CourtCall`, and on `ON_PAUSE` / when the screen changes away, clears them. This survives background→foreground and rotation without drift.
- **Orientation:** unconstrained; the layout is responsive (§8) so it works portrait on a phone preview and landscape on a TV.
- **New files:**
  - `ui/screens/CourtCallScreen.kt` — the board container: derives each court's display state, lays out the responsive grid, owns pagination + the paused/empty banners.
  - `ui/components/CourtCallTile.kt` — one court tile; takes a court + its derived display state and renders the correct treatment.
- No changes to `SessionHubScreen` beyond adding the top-bar entry action.

## 5. Derived display state per court

Each tile computes **exactly one** of six states from already-live data and renders it with a **consistent color + shape + label** (reuse the cycle-A status dot + shape + label discipline; never color-only). Precedence resolves top-down:

| State | Derivation (from `Court` + `currentMatch` + `activeRecommendations`) | Treatment |
|---|---|---|
| **UP NOW** | `status == AVAILABLE` **and** `activeRecommendations[id] != null` **and** `id` is the **lowest** such `courtId` | **Accent-FILLED** tile (the one von-Restorff standout) — huge court number, `"UP NOW"` label, next-matchup names |
| **READY** | `status == AVAILABLE` **and** has a recommendation but is **not** the lowest ready `courtId` | Quiet accent **BORDER** (not filled), `"READY"`, matchup names |
| **LIVE** | `status == IN_PROGRESS` **and** `currentMatch?.isCompleted == false` | Neutral tile, `"LIVE"` + live shape, **live score**, team-colored names |
| **FINAL** | `currentMatch != null` **and** `currentMatch.isCompleted == true` (derived — there is **no** `FINAL` `CourtStatus`; in this window the court is still `status == IN_PROGRESS`, so FINAL is tested on `isCompleted` and takes precedence over LIVE) | Muted tile, `"FINAL 11–7"` (final score) |
| **OPEN** | `status == AVAILABLE` **and** **no** recommendation | Quiet / empty tile, `"OPEN"` |
| **PAUSED** | court `status == PAUSED` (session-level `isPaused` raises the board-wide banner, §8) | Paused treatment on the tile + paused banner over the board |

**FINAL is a transient "won, awaiting confirmation" state, not a resting result.** It appears only in the window between the winning rally being recorded (`PickleballGameEngine` sets `Match.isCompleted = true` while `SessionViewModel.recordRallyInMatch` leaves the court `IN_PROGRESS`) and the organizer tapping "Final score" — at which point `completeMatch()` sets the court to `status = AVAILABLE, currentMatch = null` (`SessionViewModel.kt:332–334`) and the tile flips to OPEN / READY / UP NOW. It **is** persisted (rally writes persist), so it survives an app restart, but the board must treat it as a short-lived "come confirm this / clear the court" prompt, **not** a durable end-screen. This is precisely a moment worth the §8 transition highlight.

**The one-standout invariant.** When **multiple** courts are ready at once, **only the lowest `courtId`** gets the filled **UP NOW**; every other ready court is the quieter **READY** border. This is the exact "one primary, lowest `courtId`" rule the Hub uses (`activeRecommendations` is a `Map` with no meaningful iteration order — sort by `courtId` before choosing the standout and before laying out). The result: no matter how many courts free up simultaneously, a distant player sees **one** thing to move toward.

State/label mapping to tokens (all from `PickItTokens`, no inline hex):
- UP NOW → `accent` fill + `onAccent` text.
- READY → `accent` border on `surface`.
- LIVE → `surface` + `statusLive` dot/shape; names in `teamA` / `teamB`; score in the largest number style.
- FINAL → `surfaceInset` / muted with `textMuted`.
- OPEN → `surface` + `borderSubtle`, `textMuted` label.
- PAUSED → `statusPaused` treatment.

## 6. Distance-legibility contract

- **Court NUMBER is the largest element on every tile** — the primary distance anchor. A player finds "court 3" first, then reads its state. It out-sizes the score, the label, and the names on every tile, in every state.
- **TV-tuned type floors.** Do **not** reuse phone-sized secondary text (cycle A's 12sp floor is for reach distance). The board defines its own large floors sized for TV viewing distance: the court number is the display-scale hero; the state label and score are large secondary; names are the smallest text and still comfortably readable across a court. Specify these as multipliers over the existing `Typography` display/headline styles (e.g. court number ≈ `displayLarge` and up, label/score ≈ `headlineSmall`+, names ≈ `titleMedium`+), **not** new arbitrary sp values — so the scale stays in the type system.
- **High contrast throughout**, validated against the fixed board palette (§7).
- **Name truncation for stable layout.** Long player names truncate to a **single line with ellipsis at a fixed character cap** so a tile never reflows or grows at distance — the grid geometry stays constant regardless of name length. A matchup renders as two capped names (reuse `Team.playerNames()` shape, but capped, not the raw string).
- **Names only on idle/next tiles — no reasons.** UP NOW / READY tiles show the **matchup names only**. The board must **not** render `RotationRecommendation.primaryReason` or `detailedReason` — those stay on the phone Hub where the organizer reads them up close. This holds the board's cognitive load to the public-display minimum (one question answered per glance).

## 7. Board theme decision (deliberate)

**Court Call renders in a FIXED high-contrast bright-on-dark board palette derived from `DarkTokens`, INDEPENDENT of the app's `ThemeMode`.** Even when the phone is set to Light or Follow-system, the board is dark.

Rationale (state this as a decision, not an oversight): status boards read best **bright-on-dark** at distance; a dark ground with bright court numbers is the airport/scoreboard convention for exactly this reason. It also sidesteps **light-mode washout under outdoor glare** — a light board on a TV in a sunlit outdoor court is nearly unreadable, and we cannot rely on the venue's theme choice. Fixing the palette removes a whole failure mode and makes the single Roborazzi baseline authoritative. This is a **deliberate deviation** from cycle A's dual-theme convention (§10 calls it out for testing).

Implementation: `CourtCallScreen` provides `DarkTokens` explicitly to its subtree via `LocalPickItTokens` (overriding whatever the app resolved), rather than reading the ambient theme. No new tokens are introduced; the board composes existing dark roles (`canvas`, `surface`, `accent`, `statusLive`, `teamA`/`teamB`, `textPrimary`, etc.). **This alone is not sufficient** — because the enclosing `MaterialTheme` colorScheme is still the app's (possibly light) scheme, the board must (a) render outside `MainActivity`'s themed, safe-drawing-padded Surface and paint `DarkTokens.canvas` full-bleed (see §4, *Escaping the app-theme frame*), and (b) source **every** color from `LocalPickItTokens`, never from `MaterialTheme.colorScheme.*`, so the forced palette is actually what paints.

## 8. Layout, pagination & state-change feedback

- **Responsive grid, fixed positions.** Courts render in **`courtId` order at FIXED grid positions** — a given court **never moves** between renders. A player learns "court 3 is bottom-left" once and relies on it, satisfying selective-attention / spatial stability.
- **Auto-cycle ONLY on overflow.** Per-page capacity is a **deterministic function of the viewport**, expressed as explicit width breakpoints over `BoxWithConstraints.maxWidth` (so it is testable, not a floating "as many as fit"):
  - `columns = 1` when `maxWidth < 600.dp`; `2` when `< 1000.dp`; else `3`.
  - `rows = 1` when `maxHeight < 480.dp`; else `2`.
  - `capacity = columns * rows` (so a phone-portrait mirror → 1×2, a small TV → 2×2, a large landscape TV → 3×2 = 6).
  These thresholds are the single source of truth for both layout and the "does it paginate" decision, and can be tuned in one place. Tiles still honor the §6 legibility floor within the chosen grid.
  - `courtCount ≤ capacity` → **one static screen**, no cycling, no page indicator.
  - `courtCount > capacity` → deterministic page **slices** (courts still in `courtId` order, positions fixed within a page) advanced on a **~10s timer**, with a small **page indicator** (e.g. "1 / 2"). Slicing is deterministic so the same court is always on the same page.
- **State-change signal.** When a tile **transitions to UP NOW or FINAL**, a brief, **non-distracting highlight** (a short pulse / fade, not a flashing loop) pulls the eye to the change — visibility-of-system-status without becoming ambient noise.
- **Auto-rotation HOLDS on a just-called court.** If a court transitions to UP NOW while it sits on an off-screen page, pagination **holds** (surfaces that page / does not cycle it away) long enough for the change to be seen — the board must never announce "up now" on a page nobody is looking at.
- **Empty / paused states.**
  - No active session (`session == null`) → a centered **"No active session"** prompt instead of a grid (the entry action is disabled in this case, but the screen guards defensively).
  - Session paused (`session.isPaused`) or a court `PAUSED` → a **paused banner over the board** (session-level covers the whole board; a single paused court gets the tile-level PAUSED treatment from §5).

## 9. Accessibility requirements

Accessibility here **is** the distance-legibility contract — the public-display context makes "context of ability" the dominant lens:
- **No information by color alone.** Every state carries **shape + label + color** (UP NOW filled + label, READY border + label, LIVE dot/shape + "LIVE", FINAL muted + "FINAL", OPEN + "OPEN", PAUSED glyph + "PAUSED"). The board is fully readable in grayscale and to color-blind viewers.
- **Court number is the largest element** on every tile, guaranteeing the primary anchor is legible first (§6).
- **High contrast** validated to WCAG AA (ideally AAA for the large court numbers) against the **fixed dark board palette** (§7).
- **Stable layout** — fixed positions and capped names mean no reflow, so nothing a viewer is reading jumps.
- **`testTag`s** on the board, each tile, each state label, the page indicator, the paused banner, and the exit affordance, for the UI tests in §10.

## 10. Testing strategy

Test-first (Robolectric + Compose UI Test with `testTag`s, plus Roborazzi — the same stack cycle A used).

**Behavior tests (TDD):**
- **One standout** — with `N > 1` courts `AVAILABLE` and each holding an `activeRecommendations[id]`, **exactly one** tile renders UP NOW (the lowest `courtId`) and the rest render READY.
- **LIVE score** — an `IN_PROGRESS` court with a non-completed `currentMatch` renders the LIVE tile with its `scoreA`–`scoreB`.
- **Idle tile is names-only** — UP NOW / READY tiles show the matchup **names** and **assert the reason text is absent** (neither `primaryReason` nor any `detailedReason` string appears on the board).
- **FINAL derivation** — a court whose `currentMatch.isCompleted == true` renders FINAL with the final score, with no `FINAL` `CourtStatus` involved.
- **Pagination + stability** — with a **pinned test viewport** (Robolectric `@Config(qualifiers = "w1280dp-h720dp-land")` or a fixed-size test container, so the §8 breakpoints resolve deterministically), pagination controls appear **only** when `courtCount > capacity`; court **ordering is stable and positions are fixed** across renders / page turns (a given `courtId` maps to the same slot). The cross-page transition/hold behaviors use Compose's **test clock** (`composeTestRule.mainClock.autoAdvance = false` + `advanceTimeBy(...)`) to drive the ~10s timer deterministically rather than real waits.
- **Keep-awake lifecycle** — this assertion needs the **real Activity** (`createAndroidComposeRule<MainActivity>()`) to reach `activity.window.attributes.flags`: `FLAG_KEEP_SCREEN_ON` is **set** when navigated to `CourtCall`, **re-applied** on a background→foreground `ON_RESUME` while still on `CourtCall`, and **cleared** on exit / when the screen changes away. (Immersive bar-hiding is asserted best-effort; under Robolectric focus is on the keep-on flag, which is observable.)
- **Entry gating** — because the Hub's null-session state early-returns a **top-bar-less** empty Scaffold (`SessionHubScreen.kt:41–71`), the "Court Call" action structurally cannot exist when `session == null`. The test asserts the **empty-state Hub renders no `court_call` action**, and that the action **is** present once a session exists.
- **Empty / paused** — `session == null` renders "No active session"; `isPaused` raises the paused banner.

**Screenshot test (Roborazzi):**
- A single **landscape baseline** of the board in the **fixed dark board palette**, seeded from an injected fixed `OpenPlaySession` fixture (mixed states: one UP NOW, one READY, one LIVE, one FINAL, one OPEN) — following cycle A's deterministic-capture rule (inject a fixture, do not drive the live async VM).
- **Dual-theme is N/A here — call this out as a deliberate deviation** from cycle A's dark+light baseline convention: the board palette is **fixed** (§7), so a light baseline would be dead. Document the single-palette baseline and the reason in the test, so CI reviewers do not read the missing light baseline as an omission.

## 11. Acceptance criteria (mapped to Design OS findings this design resolves)

- **#1** — Exactly **one** von-Restorff standout (UP NOW, lowest ready `courtId`) with a brief state-change highlight on transition. → `visual-hierarchy` / `von-restorff` / `selective-attention` / `feedback`.
- **#2** — Every state uses a **consistent color + shape + label**, grayscale / color-blind safe. → `similarity` / consistency.
- **#3** — Distance-legibility contract: court number largest, TV-tuned type floors, name truncation, high contrast. → `accessibility` / context-of-ability / `typography`.
- **#4** — **Fixed high-contrast board palette** independent of `ThemeMode`, for outdoor glare. → context-appropriateness.
- **#5** — **Every `CourtStatus` plus the derived FINAL** has a defined rendering (AVAILABLE→UP NOW/READY/OPEN, IN_PROGRESS→LIVE, PAUSED→PAUSED, completed match→FINAL). → clarity / feedback.
- **#6** — **Stable court positions** across pagination; a court never moves. → `selective-attention` / spatial-organization.
- **#7** — Idle tiles are **names-only** (no reasons on the board). → `cognitive-load` / `chunking`.

## 12. Design OS citations

`20-context-modifiers/contexts.md` (**Public Display**); `10-principles/perception-attention/visual-hierarchy.md`, `.../von-restorff.md`, `.../selective-attention.md`, `.../similarity.md`; `10-principles/cognition-memory/cognitive-load.md`, `.../chunking.md`; `10-principles/accessibility/README.md` (context-of-ability); `10-principles/feedback-status/README.md` (visibility-of-system-status, feedback-loops); `30-trade-offs/trade-off-map.md` (`similarity--von-restorff`, `aesthetic-minimalism--von-restorff`).
