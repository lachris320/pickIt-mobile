# PickIt-mobile — Design-System & UX-Correctness Pass

- **Status:** Approved (brainstorm) — codebase-validated in lieu of the automated gate (Codex not installed and the `claude -p` reviewer not logged in; feasibility claims validated directly against the codebase instead). Pending final user review.
- **Date:** 2026-09-12
- **Author:** Design OS EVALUATE → brainstorm
- **Scope cycle:** A (this spec). B — "Court Call" / big-screen public-display mode — deferred to its own cycle.

---

## 1. Context & problem

PickIt-mobile is a courtside pickleball **open-play session manager + live scorekeeper** (Kotlin/Compose), four screens: Setup → Session Hub → Live Scoreboard → Standalone Scoreboard, plus Queue and Fast-Final-Score modal sheets.

**Usage context (drives the weighting):** a **mixed audience leaning kiosk** — the phone is passed hand-to-hand between players during play, while also supporting a single power organizer who runs the whole session. Environment is **mixed indoor/outdoor**. Per Design OS `20-context-modifiers/contexts.md`, the resolution for a mixed audience is **layering**: a low-load, high-recognition default surface with one clear next action, with the organizer's density available but not in the way.

A Design OS EVALUATE pass found six load-bearing failures:

1. **`von-restorff` — accent spent everywhere.** The lime accent is applied to nearly every emphasizable element; multiple lime/amber accents plus per-card colored borders compete, so nothing stands out (`30-trade-offs/trade-off-map.md`: `aesthetic-minimalism--von-restorff` "many competing accents").
2. **`typography` — no scale.** `Type.kt` defines only `bodyLarge`; sizes are hardcoded inline down to 9–10sp; near-universal `Black`/`Bold` + `ALL CAPS` flattens hierarchy (fails `visual-hierarchy.md` "survive grayscale").
3. **`consistency` — ~12 near-identical greens** hardcoded inline instead of named tokens; ad-hoc corner radii (8/10/12/14/16dp).
4. **`error-recovery` — destructive "Abandon Match"** fires from a bare button with no confirmation, while non-destructive match completion *does* confirm (asymmetry backwards).
5. **`accessibility` — 24dp touch targets** (rest-player buttons), `Theme.kt` forces dark and ignores its own params (dead code), `maxLines = 1` silently clips names at large font scale.
6. **`visual-hierarchy` (Hub) — flat.** All section headers are same-size bold uppercase in three near-equal-salience colors; reading order is carried by color, not weight.

Genuine strengths to preserve: strong feedback (haptics, animated scores, flash feed, match-point badge, completion dialog), low cognitive load on scoring (system computes serving/side-out/match-point/win-by-2), and a good Live-Scoreboard hierarchy (68sp scores dominate).

## 2. Goals / Non-goals

**Goals**
- Fix all six findings via a **disciplined refresh**: keep the court identity (dark, lime, team colors) but re-earn the emphasis.
- Introduce enough of a design system to enforce discipline — **not** a full design framework (YAGNI).
- Add a working light court palette and a persistent Dark/Light/Follow-system preference.

**Non-goals (out of scope this cycle)**
- The "Court Call" / big-screen public-display mode (next cycle).
- Any layout restructure beyond emphasis/hierarchy (the "bolder restyle" option was declined).
- Engine / ViewModel logic changes.
- Material-You dynamic color (stays disabled).

## 3. Approach

**A — Extend Material3 with a small token layer.** `MaterialTheme` still drives standard components; a `PickItTokens` holder is exposed via a `LocalPickItTokens` CompositionLocal, with a full `Typography`. Screens read tokens instead of inline hex, migrating incrementally. This is the "layered default surface" the trade-off map prescribes for a mixed audience, is idiomatic Compose, and keeps the diff proportionate to a 4-screen app.

## 4. Design-token layer

New/updated in `ui/theme/`:
- `PickItTokens` — an immutable holder of the roles below.
- `LocalPickItTokens` — `staticCompositionLocalOf`.
- `DarkTokens`, `LightTokens` — the two instances.
- `ThemeMode { DARK, LIGHT, SYSTEM }`.
- `MyApplicationTheme(themeMode: ThemeMode, content)` — resolves dark/light, provides tokens + `colorScheme` + `Typography`. The dead `darkTheme`/`dynamicColor` params are removed.

**Entrypoint wiring (verified against `MainActivity.kt`).** Today `MainActivity.onCreate` calls `MyApplicationTheme { … }` with **no arguments** and wraps content in `Surface(color = CanvasDark)` (a hardcoded color). This pass:
- Adds `themeMode: StateFlow<ThemeMode>` + a `setThemeMode(mode)` action to `SessionViewModel` (already an `AndroidViewModel(application)` — it has the `Application` for persistence and already exposes `StateFlow`s).
- `MainActivity` reads it via `collectAsState()` inside `setContent` and passes it to `MyApplicationTheme(themeMode = …)`.
- The hardcoded `Surface(color = CanvasDark)` migrates to `tokens.canvas` (or is dropped once the theme paints the background).

**Token roles**

| Role group | Tokens |
|---|---|
| Surfaces | `canvas`, `surface`, `surfaceElevated`, `surfaceInset`, `border`, `borderSubtle` |
| Text (semantic) | `textPrimary`, `textSecondary`, `textMuted`, `textOnAccent`, `textAccent`, `textDanger` |
| Accent | `accent`, `onAccent` |
| Status (semantic) | `statusOpen`, `statusLive`, `statusPaused`, `attention` |
| Team | `teamA`, `teamB` |
| Radii (scale) | `radiusSm = 8.dp`, `radiusMd = 12.dp`, `radiusLg = 16.dp` |

**Starting palette values** (exact hex validated for WCAG AA contrast during implementation; these consolidate the existing inline greens):

*Dark (default):*
`canvas #0C110E` · `surface #161F19` · `surfaceElevated #1B241F` · `surfaceInset #0F1713` · `border #2E3D35` · `borderSubtle #223029` · `textPrimary #FFFFFF` · `textSecondary #B7C4B5` · `textMuted #7E8C7C` · `accent #D4E157` / `onAccent #1B3700` / `textAccent #C7E04A` · `textDanger #EF5350` · `statusOpen #4CAF50` · `statusLive #29B6F6` · `statusPaused #78909C` · `attention #FFB300` · `teamA #4FC3F7` · `teamB #FF8A65`

*Light (indoor):*
`canvas #F3F5EF` · `surface #FFFFFF` · `surfaceElevated #FFFFFF` · `surfaceInset #ECF0E6` · `border #D3DBCE` · `borderSubtle #E4E9DE` · `textPrimary #14201A` · `textSecondary #465049` · `textMuted #6E7A6F` · `accent #C6DB3A` / `onAccent #1B3700` / `textAccent #3B6D11` · `textDanger #C0362F` · `statusOpen #2E7D32` · `statusLive #0277BD` · `statusPaused #607D8B` · `attention #B26A00` · `teamA #0277BD` · `teamB #D84315`

Radii retire the ad-hoc 10/14dp: controls use `radiusSm`/`radiusMd`, cards use `radiusMd`/`radiusLg`.

## 5. Type scale

Fill `Typography` completely. Rules: **floor at 12sp** (no 9/10sp), **sentence case everywhere except `labelSmall`** (the single uppercase "eyebrow"), **weight discipline** — `Bold` reserved for one structural level, `Medium` for labels, `Normal` for body, `Black` only on the scoreboard number.

| Style | Size | Weight | Case | Use |
|---|---|---|---|---|
| `displayLarge` | 64sp | Black | — | Live scoreboard number |
| `headlineSmall` | 28sp | Bold | Sentence | Big emphasis (rare) |
| `titleLarge` | 20sp | Bold | Sentence | Screen / scoreboard headings |
| `titleMedium` | 16sp | Bold | Sentence | Card titles |
| `titleSmall` | 14sp | Medium | Sentence | Sub-headings |
| `bodyLarge` | 16sp | Normal | Sentence | Primary body |
| `bodyMedium` | 14sp | Normal | Sentence | Secondary body |
| `bodySmall` | 13sp | Normal | Sentence | Meta (floor) |
| `labelLarge` | 14sp | Medium | Sentence | Buttons |
| `labelMedium` | 12sp | Medium | Sentence | Small labels |
| `labelSmall` | 12sp | Medium | UPPERCASE | Section eyebrows (only caps) |

## 6. Emphasis discipline (the von-Restorff rule)

A documented ladder the whole app obeys:

- **Primary (lime filled) — exactly one per screen:** Hub → `Call & start` on the next ready court only; Setup → `Launch session`; Fast-Final-Score sheet → `Confirm & rotate`.
- **Secondary (neutral outlined/tonal):** Live score, Final score, Swap partners, Queue, Launch (standalone), etc.
- **Status (small dot + shape + label + color):** court status, on-deck, side-out — grouped by color via `similarity`, never a full colored card border.
- **Section labels:** quiet muted `labelSmall` eyebrow.

**Two legitimate multi-color exceptions** (grounded, not violations):
1. The two rally buttons on the Live Scoreboard remain team-colored **equals** — choosing between them *is* the task, not two competing CTAs.
2. A genuine attention state (a court ready to call) gets one **amber dot** — von-Restorff's "reserve emphasis for real exceptions."

## 7. Per-screen mapping

**Session Hub**
- Section labels → quiet sentence-case eyebrows.
- Only the **next** ready court gets the lime `Call & start`; additional ready courts list with neutral "Call" actions, so a single lime standout survives.
- Court cards lose full colored borders → neutral `surface` + subtle `border` + status dot (shape+label+color).
- "Live score" / "Final score" → neutral secondary.
- Standalone block demotes from a bordered surface to a quiet row/link.

**CourtStatusCard**
- `surface` + subtle `border`; status via the locked **dot-shape + label + color** (filled dot "Live" / outlined dot "Open" / pause glyph "Paused"); actions neutral.

**RecommendationCard**
- Attention header = amber dot + "Court N ready".
- The four rest-player icon buttons grow to **≥48dp** touch targets.
- `Call & start` lime; `Swap partners` neutral outlined.
- Player names wrap or ellipsize gracefully (no `maxLines = 1` clip).

**Live Scoreboard**
- Score uses the `displayLarge` token.
- 3-column callout bar floored to ≥12sp, sentence case.
- Two rally buttons stay team-colored equals (documented exception).
- `Finalize` → neutral text button.
- **`Abandon match` opens a confirmation dialog** (mirrors the completion dialog); Undo stays the reversible path.
- Match-point badge keeps red **plus** its "Match point" text (not color-only).

**Setup**
- Quiet eyebrows; `Launch session` lime primary.
- Court-count selection shows **checkmark + fill** (not color alone).
- **The 12 hardcoded fake players and the pre-filled session name stop being live default state.** New flow: empty session → add players → optionally **Load sample players** → Launch session. The sample affordance keeps demo/testing convenience without contaminating real operational state.

**Sheets (FastFinalScore, QueueRoster)**
- Tokens + type applied; `Confirm & rotate` lime primary; selected score pills use **fill + shape**, not color alone.

## 8. Behavioral fixes (cross-cutting)

- **Abandon confirmation** dialog before the destructive action.
- **Appearance preference — application-level, subordinated to setup.** A persistent Dark / Light / Follow-system preference, held as `ThemeMode` state on `SessionViewModel` and read by `MyApplicationTheme` via the entrypoint (see §4). **Persistence uses `SharedPreferences`** via the `Application` context — read **synchronously** on VM init so the correct theme is applied before first composition (no theme flash). DataStore is *not* used: it is currently commented out in `app/build.gradle.kts`, and its async read would flash the default theme at startup; `SharedPreferences` needs no new dependency. The stored value is the `ThemeMode` enum name; `SYSTEM` defers to `isSystemInDarkTheme()`. Because there is no dedicated Settings screen, a compact, clearly-secondary **Appearance** control lives in the Setup screen's **peripheral/header area** — outside the main setup form, and it **must not compete visually with `Launch session`**. (If a Settings surface is added later, the control moves there.)
- All interactive controls ≥48dp.
- Text wraps/ellipsizes gracefully at large system font scale.
- Semantic text roles applied throughout (kills inline `WhiteHighContrast`/`TextMuted`; guarantees light-palette legibility).

## 9. Accessibility requirements

Accessibility is part of the design, not a final compliance step:
- 48dp minimum interactive target on every control.
- No information by color alone — status carries shape + label; selections carry a checkmark/fill.
- Graceful behavior at large system font sizes (no clipping).
- Contrast validated to WCAG AA for text roles in **both** palettes.

## 10. Testing strategy

- **Behavior (TDD, via existing `testTag`s):**
  - Abandon shows the confirmation dialog; the match is abandoned **only** on confirm, not on the initial tap.
  - Theme toggle changes `ThemeMode` and the choice **persists** across app restart.
  - Primary interactive controls (incl. rest-player buttons) meet the 48dp minimum.
  - A composable resolves `PickItTokens` / `Typography` correctly (no crash, expected role values).
  - Setup starts with an empty roster; "Load sample players" populates it.
- **Screenshot baselines (Roborazzi — already configured):** the repo already wires Roborazzi (`app/build.gradle.kts`: `roborazzi` plugin, `roborazzi.compose`, `roborazzi.junit.rule`) with an existing `app/src/test/java/com/example/GreetingScreenshotTest.kt` and a `app/src/test/screenshots/greeting.png` baseline. Follow that pattern: capture key screens (Hub, Live Scoreboard, Setup, both sheets) in **both dark and light**, verified in CI. No new screenshot dependency is needed.

**Test-safety note (verified):** no existing test depends on Setup's 12 sample players. The `Alice`/`Bob` names in `PickleballEngineTest` and `RoomDatabaseTest` are independent fixtures, not the Setup UI state — so removing the sample roster as live default state (§7) breaks no current test.

## 11. Acceptance criteria (mapped to findings)

- **#1** Each screen has exactly one lime primary action (or none); a "count the accents" check finds a single standout. Court cards carry no full colored border.
- **#2** `Typography` is fully defined; no rendered text below 12sp; caps confined to `labelSmall`; hierarchy legible in grayscale.
- **#3** Zero inline surface/greens hex in screen/component files — all via tokens; radii ∈ {8,12,16}.
- **#4** Abandon requires confirmation.
- **#5** No interactive target < 48dp; theme params are live (no dead code); no `maxLines = 1` clip on names; Appearance preference persists across restart (`SharedPreferences`), is held on `SessionViewModel`, and `MainActivity` reads it and passes it to `MyApplicationTheme`.
- **#6** Hub reading order carried by weight+size, not color; passes the squint test.

## 12. Design OS citations

`10-principles/perception-attention/visual-hierarchy.md`, `.../von-restorff.md`; `10-principles/cognition-memory/cognitive-load.md`, `.../recognition-over-recall.md`; `10-principles/accessibility/README.md`; `10-principles/feedback-status/README.md`; `20-context-modifiers/contexts.md` (mobile, kiosk, public-display); `30-trade-offs/trade-off-map.md` (`aesthetic-minimalism--von-restorff`, `cognitive-load--flexibility-efficiency`, `similarity--von-restorff`); `50-evaluation/README.md` (16 dimensions).
