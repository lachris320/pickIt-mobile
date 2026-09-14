# Design-System & UX-Correctness Pass — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the subagent-build skill (`/subagent-build`) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the approved disciplined-refresh pass — a Material3 token layer, a full type scale, a von-Restorff emphasis discipline, a persistent Dark/Light/System theme, a real (non-fake) first-launch state, and the accessibility/error-recovery fixes — to the PickIt-mobile Compose app.

**Architecture:** A `PickItTokens` holder is exposed via `LocalPickItTokens` (a `staticCompositionLocalOf`) and provided by `MyApplicationTheme(themeMode)`, which also fills `MaterialTheme.colorScheme` + `Typography`. `ThemeMode` state lives on `SessionViewModel` (an `AndroidViewModel`), read synchronously from `SharedPreferences` on init and passed into the theme from `MainActivity`. Screens migrate off ~12 inline greens to tokens. Behavioral fixes (Abandon confirmation, empty first-launch, one-primary-per-screen emphasis, 48dp targets) are added with `testTag`-based Robolectric tests; the visual pass is guarded by dual-theme Roborazzi baselines.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, `AndroidViewModel` + `StateFlow`, Room (existing), `SharedPreferences` (theme pref), JUnit4 + Robolectric + Compose UI Test (`onNodeWithTag`), Roborazzi (screenshots).

**Spec:** `docs/specs/2026-09-12-design-system-ux-pass-design.md` (APPROVE ×3). **Branch:** `redesign/design-system-ux-pass`.

**Conventions & commands (read once):**
- Tests: `./gradlew :app:testDebugUnitTest --tests "com.example.<TestClass>"`. No wrapper is committed — if `./gradlew` is missing locally, generate it (`gradle wrapper`) or rely on CI; every task still shows the exact command + expected result.
- Roborazzi: record baselines `./gradlew :app:recordRoborazziDebug`; verify `./gradlew :app:verifyRoborazziDebug`.
- Commit via the `commit` skill (Conventional Commits, no Claude co-author trailer). Each task ends with a commit step.
- All new Compose files: package under `com.example.ui.*`; named exports; keep business logic in the VM.

---

## File Structure

**Create:**
- `app/src/main/java/com/example/ui/theme/Tokens.kt` — `PickItTokens`, `DarkTokens`, `LightTokens`, `LocalPickItTokens`, `ThemeMode`.
- `app/src/main/java/com/example/data/local/ThemePreferences.kt` — synchronous `SharedPreferences` wrapper for the theme mode.
- `app/src/test/java/com/example/theme/TokenAndTypeTest.kt` — token/type value tests.
- `app/src/test/java/com/example/theme/ThemeResolutionTest.kt` — `MyApplicationTheme` resolves tokens per mode.
- `app/src/test/java/com/example/viewmodel/ThemeModeTest.kt` — VM theme state + persistence.
- `app/src/test/java/com/example/viewmodel/FirstLaunchTest.kt` — empty first-launch.
- `app/src/test/java/com/example/ui/SessionHubEmptyStateTest.kt`, `AbandonConfirmTest.kt`, `RecommendationEmphasisTest.kt`, `RestPlayerTargetTest.kt`, `SetupRosterTest.kt`, `AppearanceControlTest.kt`.
- `app/src/test/java/com/example/screenshots/ScreenshotBaselinesTest.kt` — fixture-based dual-theme captures.
- `app/src/test/java/com/example/fixtures/Fixtures.kt` — fixed `OpenPlaySession`/`Match` for tests.

**Modify:**
- `ui/theme/Theme.kt` (rewire), `ui/theme/Type.kt` (full scale), `ui/theme/Color.kt` (keep raw hex constants; tokens reference them).
- `MainActivity.kt` (pass `themeMode`, drop hardcoded `CanvasDark`).
- `viewmodel/SessionViewModel.kt` (theme state; empty first-launch).
- `ui/screens/SessionHubScreen.kt`, `SetupScreen.kt`, `LiveScoreboardScreen.kt`, `StandaloneScoreboardScreen.kt`.
- `ui/components/CourtStatusCard.kt`, `RecommendationCard.kt`, `FastFinalScoreSheet.kt`, `QueueRosterSheet.kt`, `OnDeckHorizonBar.kt`, `TacticalPickleballCourtDiagram.kt`.
- `app/src/test/java/com/example/GreetingScreenshotTest.kt` (delete or fold into the new fixture-based test) + remove `app/src/test/screenshots/greeting.png`.

---

## Task 1: Design-token layer (`PickItTokens` + palettes + CompositionLocal)

**Files:**
- Create: `app/src/main/java/com/example/ui/theme/Tokens.kt`
- Test: `app/src/test/java/com/example/theme/TokenAndTypeTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkTokens
import com.example.ui.theme.LightTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TokenAndTypeTest {
    @Test fun darkTokens_haveExpectedCoreValues() {
        assertEquals(Color(0xFF0C110E), DarkTokens.canvas)
        assertEquals(8.dp, DarkTokens.radiusSm)
        assertEquals(12.dp, DarkTokens.radiusMd)
        assertEquals(16.dp, DarkTokens.radiusLg)
    }

    @Test fun lightAndDark_differOnSurfaceAndText() {
        assertNotEquals(DarkTokens.canvas, LightTokens.canvas)
        assertNotEquals(DarkTokens.textPrimary, LightTokens.textPrimary)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.theme.TokenAndTypeTest"`
Expected: FAIL — `DarkTokens`/`LightTokens` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Immutable
data class PickItTokens(
    val canvas: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceInset: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnAccent: Color,
    val textAccent: Color,
    val textDanger: Color,
    val accent: Color,
    val onAccent: Color,
    val statusOpen: Color,
    val statusLive: Color,
    val statusPaused: Color,
    val attention: Color,
    val teamA: Color,
    val teamB: Color,
    val onTeamA: Color,
    val onTeamB: Color,
    val radiusSm: Dp = 8.dp,
    val radiusMd: Dp = 12.dp,
    val radiusLg: Dp = 16.dp,
)

val DarkTokens = PickItTokens(
    canvas = Color(0xFF0C110E),
    surface = Color(0xFF161F19),
    surfaceElevated = Color(0xFF1B241F),
    surfaceInset = Color(0xFF0F1713),
    border = Color(0xFF2E3D35),
    borderSubtle = Color(0xFF223029),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB7C4B5),
    textMuted = Color(0xFF7E8C7C),
    textOnAccent = Color(0xFF1B3700),
    textAccent = Color(0xFFC7E04A),
    textDanger = Color(0xFFEF5350),
    accent = Color(0xFFD4E157),
    onAccent = Color(0xFF1B3700),
    statusOpen = Color(0xFF4CAF50),
    statusLive = Color(0xFF29B6F6),
    statusPaused = Color(0xFF78909C),
    attention = Color(0xFFFFB300),
    teamA = Color(0xFF4FC3F7),
    teamB = Color(0xFFFF8A65),
    onTeamA = Color(0xFF00232B),
    onTeamB = Color(0xFF3A0E00),
)

val LightTokens = PickItTokens(
    canvas = Color(0xFFF3F5EF),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceInset = Color(0xFFECF0E6),
    border = Color(0xFFD3DBCE),
    borderSubtle = Color(0xFFE4E9DE),
    textPrimary = Color(0xFF14201A),
    textSecondary = Color(0xFF465049),
    textMuted = Color(0xFF6E7A6F),
    textOnAccent = Color(0xFF1B3700),
    textAccent = Color(0xFF3B6D11),
    textDanger = Color(0xFFC0362F),
    accent = Color(0xFFC6DB3A),
    onAccent = Color(0xFF1B3700),
    statusOpen = Color(0xFF2E7D32),
    statusLive = Color(0xFF0277BD),
    statusPaused = Color(0xFF607D8B),
    attention = Color(0xFFB26A00),
    teamA = Color(0xFF0277BD),
    teamB = Color(0xFFD84315),
    onTeamA = Color(0xFFFFFFFF),
    onTeamB = Color(0xFFFFFFFF),
)

val LocalPickItTokens = staticCompositionLocalOf { DarkTokens }
```

> **Contrast note (implementation-time):** validate every text-on-fill pair to WCAG AA before recording baselines (esp. `LightTokens.accent` with `onAccent`, and `teamA/teamB` with `onTeamA/onTeamB`). Adjust the hex if a pair fails; keep the token names stable.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.theme.TokenAndTypeTest"`
Expected: PASS.

- [ ] **Step 5: Commit** — `feat(theme): add PickItTokens design-token layer with dark+light palettes`

---

## Task 2: Full type scale

**Files:**
- Modify: `app/src/main/java/com/example/ui/theme/Type.kt`
- Test: `app/src/test/java/com/example/theme/TokenAndTypeTest.kt` (extend)

- [ ] **Step 1: Add the failing assertions**

```kotlin
    @Test fun typography_hasFullScaleWithFloors() {
        val t = com.example.ui.theme.Typography
        assertEquals(64f, t.displayLarge.fontSize.value)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Black, t.displayLarge.fontWeight)
        assertEquals(16f, t.titleMedium.fontSize.value)   // card titles
        assertEquals(13f, t.bodySmall.fontSize.value)     // meta floor
        assertEquals(12f, t.labelSmall.fontSize.value)    // eyebrow floor (no 9/10sp)
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.theme.TokenAndTypeTest"`
Expected: FAIL — `displayLarge` not defined (only `bodyLarge` today).

- [ ] **Step 3: Replace `Type.kt` with the full scale**

```kotlin
package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Black, fontSize = 64.sp, lineHeight = 64.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
```

- [ ] **Step 4: Run to verify it passes** — Expected: PASS.
- [ ] **Step 5: Commit** — `feat(theme): define full Material3 type scale, floor labels at 12sp`

---

## Task 3: `ThemeMode` rewiring of `MyApplicationTheme`

**Files:**
- Modify: `app/src/main/java/com/example/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/example/theme/ThemeResolutionTest.kt`

- [ ] **Step 1: Write the failing test** (Robolectric — probes the provided tokens)

```kotlin
package com.example.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.DarkTokens
import com.example.ui.theme.LightTokens
import com.example.ui.theme.LocalPickItTokens
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeResolutionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun lightMode_providesLightTokens() {
        var captured = DarkTokens
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.LIGHT) {
                captured = LocalPickItTokens.current
            }
        }
        rule.runOnIdle { assertEquals(LightTokens, captured) }
    }

    @Test fun darkMode_providesDarkTokens() {
        var captured = LightTokens
        rule.setContent {
            MyApplicationTheme(themeMode = ThemeMode.DARK) {
                captured = LocalPickItTokens.current
            }
        }
        rule.runOnIdle { assertEquals(DarkTokens, captured) }
    }
}
```

- [ ] **Step 2: Run to verify it fails** — Expected: FAIL — `MyApplicationTheme` has no `themeMode` param.

- [ ] **Step 3: Rewrite `Theme.kt`**

```kotlin
package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkTokens.accent, onPrimary = DarkTokens.onAccent,
    secondary = Color(0xFF00897B), onSecondary = Color.White,
    background = DarkTokens.canvas, onBackground = DarkTokens.textPrimary,
    surface = DarkTokens.surfaceElevated, onSurface = DarkTokens.textPrimary,
    outline = DarkTokens.border,
)

private val LightColorScheme = lightColorScheme(
    primary = LightTokens.accent, onPrimary = LightTokens.onAccent,
    secondary = Color(0xFF00897B), onSecondary = Color.White,
    background = LightTokens.canvas, onBackground = LightTokens.textPrimary,
    surface = LightTokens.surface, onSurface = LightTokens.textPrimary,
    outline = LightTokens.border,
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val tokens = if (dark) DarkTokens else LightTokens
    CompositionLocalProvider(LocalPickItTokens provides tokens) {
        MaterialTheme(
            colorScheme = if (dark) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
```

> `Color.kt` keeps its raw constants for now (tokens don't depend on removing them); they're deleted only after Task 9 migrates every usage. Do NOT delete `Color.kt` constants in this task or the screens won't compile.

- [ ] **Step 4: Run to verify it passes** — Expected: PASS (both tests).
- [ ] **Step 5: Commit** — `feat(theme): resolve tokens+colorScheme by ThemeMode, remove dead params`

---

## Task 4: Theme-mode persistence + `SessionViewModel` state

**Files:**
- Create: `app/src/main/java/com/example/data/local/ThemePreferences.kt`
- Modify: `app/src/main/java/com/example/viewmodel/SessionViewModel.kt`
- Test: `app/src/test/java/com/example/viewmodel/ThemeModeTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeModeTest {
    @Test fun themeMode_defaultsToSystem_andPersistsAcrossInstances() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm1 = SessionViewModel(app)
        assertEquals(ThemeMode.SYSTEM, vm1.themeMode.value)

        vm1.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, vm1.themeMode.value)

        val vm2 = SessionViewModel(app)   // fresh instance reads persisted value synchronously
        assertEquals(ThemeMode.LIGHT, vm2.themeMode.value)
    }
}
```

- [ ] **Step 2: Run to verify it fails** — Expected: FAIL — `themeMode`/`setThemeMode` unresolved.

- [ ] **Step 3: Implement `ThemePreferences` + wire the VM**

`ThemePreferences.kt`:

```kotlin
package com.example.data.local

import android.content.Context
import com.example.ui.theme.ThemeMode

class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("pickit_prefs", Context.MODE_PRIVATE)

    fun readMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)

    fun writeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
    }

    private companion object { const val KEY = "theme_mode" }
}
```

In `SessionViewModel` add (near the other `StateFlow`s, after line 39):

```kotlin
    private val themePrefs = ThemePreferences(application)
    private val _themeMode = MutableStateFlow(themePrefs.readMode()) // synchronous read → no flash
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        themePrefs.writeMode(mode)
    }
```

Add imports: `com.example.data.local.ThemePreferences`, `com.example.ui.theme.ThemeMode`.

- [ ] **Step 4: Run to verify it passes** — Expected: PASS.
- [ ] **Step 5: Commit** — `feat(theme): persist ThemeMode via SharedPreferences on SessionViewModel`

---

## Task 5: `MainActivity` reads and applies the theme mode

**Files:**
- Modify: `app/src/main/java/com/example/MainActivity.kt`

*(No unit test — pure wiring, covered by the screenshot task and manual/CI build. Keep the change minimal.)*

- [ ] **Step 1: Update `setContent` in `MainActivity.onCreate`**

Replace the current body:

```kotlin
        setContent {
            val themeMode by sessionViewModel.themeMode.collectAsState()
            MyApplicationTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PickleballAppContent(viewModel = sessionViewModel)
                }
            }
        }
```

Add imports: `androidx.compose.runtime.getValue`, `androidx.compose.material3.MaterialTheme`, `com.example.ui.theme.ThemeMode`. Remove the now-unused `import com.example.ui.theme.CanvasDark`.

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit** — `feat(theme): apply persisted ThemeMode from MainActivity, drop hardcoded canvas`

---

## Task 6: First-launch empty session + Hub empty state

**Files:**
- Modify: `viewmodel/SessionViewModel.kt`, `ui/screens/SessionHubScreen.kt`
- Test: `viewmodel/FirstLaunchTest.kt`, `ui/SessionHubEmptyStateTest.kt`

- [ ] **Step 1: Write the failing VM test**

```kotlin
package com.example.viewmodel

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirstLaunchTest {
    @get:Rule val rule = createComposeRule()

    @Test fun freshInstall_hasNoActiveSession() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SessionViewModel(app)
        rule.setContent { Box(Modifier) {} }   // drive the Compose/Robolectric clock
        rule.waitForIdle()                      // reliably lets the init coroutine (Room load) settle
        rule.runOnIdle { assertNull(vm.session.value) }
    }
}
```

> **Why the Compose rule, not `advanceUntilIdle`:** `advanceUntilIdle()` only drains the test `Main` dispatcher, but Room's suspend query resumes off its own IO executor, so the `init` continuation may not have run when the assertion fires — the test could pass with the seed still present (a false green). The Compose rule's `waitForIdle` synchronizes the same way `GreetingScreenshotTest` already relies on to render loaded content, so this is a real red→green: with the old `createInitialSession()` present the assertion FAILS; after seed removal it passes. `SessionHubEmptyStateTest` (Steps 5–8) is the companion UI-level proof. Add `import androidx.compose.ui.Modifier`.

- [ ] **Step 2: Run to verify it fails** — Expected: FAIL — today `createInitialSession()` seeds a fake session.

- [ ] **Step 3: Remove the demo seed**

In `SessionViewModel`, change the `init` block's fallback and delete `createInitialSession()` entirely:

```kotlin
    init {
        viewModelScope.launch {
            val savedSession = repository.loadLatestSession()
            if (savedSession != null && savedSession.roster.isNotEmpty()) {
                val recs = mutableMapOf<Int, RotationRecommendation>()
                savedSession.courts.filter { it.status == CourtStatus.AVAILABLE }.forEach { c ->
                    val r = RotationEngine.generateRecommendation(savedSession, c.id, null)
                    if (r != null) recs[c.id] = r
                }
                _session.value = savedSession.copy(activeRecommendations = recs)
            }
            // No saved session → _session stays at its `null` default (SessionViewModel.kt:34).
            // Do NOT add an `else { _session.value = null }`: this coroutine resumes AFTER a
            // test's loadSessionForTest() runs and would clobber the injected fixture with null.
        }
    }
```

Delete the entire `private fun createInitialSession()` (lines 67–115). Keep `frequentPlayers` (reused in Task 10). Removing the demo seed (not adding a null-write) is the whole change — production first-launch stays empty, and an injected test session survives `init`.

- [ ] **Step 4: Run VM test to verify it passes** — Expected: PASS.

- [ ] **Step 5: Write the failing Hub empty-state test**

```kotlin
package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.SessionHubScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionHubEmptyStateTest {
    @get:Rule val rule = createComposeRule()

    @Test fun nullSession_showsStartCta_thatNavigatesToSetup() {
        val vm = SessionViewModel(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }
        rule.onNodeWithTag("start_session_button").assertIsDisplayed()
        rule.onNodeWithTag("start_session_button").performClick()
        rule.runOnIdle { assertEquals(AppScreen.Setup, vm.currentScreen.value) }
    }
}
```

- [ ] **Step 6: Run to verify it fails** — Expected: FAIL — Hub returns early (blank) on null session.

- [ ] **Step 7: Replace the early return in `SessionHubScreen`**

Replace `val activeSession = session ?: return` (line 40) with an empty-state Scaffold when `session == null`:

```kotlin
    val activeSession = session
    if (activeSession == null) {
        Scaffold(containerColor = LocalPickItTokens.current.canvas) { pad ->
            Column(
                modifier = Modifier.fillMaxSize().padding(pad).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No active session", style = MaterialTheme.typography.titleLarge,
                    color = LocalPickItTokens.current.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text("Start a session to line up courts and keep score.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalPickItTokens.current.textSecondary)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.navigateTo(AppScreen.Setup) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalPickItTokens.current.accent,
                        contentColor = LocalPickItTokens.current.onAccent),
                    modifier = Modifier.height(56.dp).testTag("start_session_button"),
                ) { Text("Start a session", fontWeight = FontWeight.Bold) }
            }
        }
        return
    }
```

Add import: `com.example.ui.theme.LocalPickItTokens`.

- [ ] **Step 8: Run to verify it passes** — Expected: PASS.
- [ ] **Step 9: Commit** — `feat(hub): start first launch empty with a Start-a-session empty state`

---

## Task 6A: Test fixtures + VM test seam (must precede Tasks 7–12)

> **Why first:** the debug unit-test source set compiles as one unit, so any test referencing `Fixtures.*` requires `Fixtures.kt` to already exist or the whole `testDebugUnitTest` compilation fails. Create it before the first test that uses it.

**Files:**
- Create: `app/src/test/java/com/example/fixtures/Fixtures.kt`
- Modify: `viewmodel/SessionViewModel.kt` (add a test-only seam)

- [ ] **Step 1: Add the VM test seam**

In `SessionViewModel`:

```kotlin
    @androidx.annotation.VisibleForTesting
    fun loadSessionForTest(s: OpenPlaySession) { _session.value = s }
```

- [ ] **Step 2: Create `Fixtures.kt`** — deterministic (no run-varying timestamps)

```kotlin
package com.example.fixtures

import com.example.engine.PickleballGameEngine
import com.example.model.*

object Fixtures {
    const val KNOWN_PLAYER_ID = "fix_p0"

    private fun player(i: Int) = Player(
        id = "fix_p$i", name = "Player $i",
        status = ParticipantStatus.AVAILABLE, queuedTimestamp = i.toLong(), matchesPlayed = 0,
    )

    fun inProgressSession(): OpenPlaySession {
        val p = (0..7).map { player(it) }
        val m1 = PickleballGameEngine.createMatch(
            courtId = 1,
            teamA = Team(TeamId.TEAM_A, p[0], p[1]),
            teamB = Team(TeamId.TEAM_B, p[2], p[3]),
        ).copy(scoreA = 6, scoreB = 4)
        return OpenPlaySession(
            id = "fix_sess", name = "Fixture Session",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            courts = listOf(Court(id = 1, name = "Court 1", status = CourtStatus.IN_PROGRESS, currentMatch = m1)),
            roster = p,
        )
    }

    fun twoReadyCourtsSession(): OpenPlaySession {
        val p = (0..7).map { player(it) }
        val base = OpenPlaySession(
            id = "fix_sess2", name = "Fixture Session 2",
            rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
            courts = listOf(
                Court(id = 2, name = "Court 2", status = CourtStatus.AVAILABLE),
                Court(id = 3, name = "Court 3", status = CourtStatus.AVAILABLE),
            ),
            roster = p,
        )
        val recs = listOf(2, 3).mapNotNull { id ->
            com.example.engine.RotationEngine.generateRecommendation(base, id, null)?.let { id to it }
        }.toMap()
        return base.copy(activeRecommendations = recs)
    }
}
```

> Verify field/constructor names against `model/SessionModels.kt` while writing this (Player/Team/Court/OpenPlaySession/RotationRecommendation shapes) and adjust to match exactly. If `RotationEngine.generateRecommendation` returns null for these rosters, construct the two `RotationRecommendation`s directly instead so both courts are "ready."

- [ ] **Step 3: Compile the test source set**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL (fixtures + seam resolve).

- [ ] **Step 4: Commit** — `test(fixtures): add deterministic session fixtures + VM test seam`

---

## Task 7: Abandon-match confirmation dialog

**Files:**
- Modify: `ui/screens/LiveScoreboardScreen.kt`
- Test: `ui/AbandonConfirmTest.kt`

- [ ] **Step 1: Write the failing test** (render Live scoreboard from an injected in-progress session)

```kotlin
package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.fixtures.Fixtures
import com.example.ui.screens.LiveScoreboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AbandonConfirmTest {
    @get:Rule val rule = createComposeRule()

    @Test fun abandon_requiresConfirmation() {
        val vm = SessionViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.loadSessionForTest(Fixtures.inProgressSession())   // seam + fixtures from Task 6A
        vm.navigateTo(AppScreen.LiveScoreboard(1))            // so currentScreen isn't the default SessionHub
        rule.setContent { MyApplicationTheme { LiveScoreboardScreen(courtId = 1, viewModel = vm) } }

        rule.onNodeWithTag("abandon_match_button").performClick()
        rule.onNodeWithTag("confirm_abandon_button").assertIsDisplayed()   // dialog shown
        // Not abandoned on first tap:
        rule.runOnIdle { assertEquals(AppScreen.LiveScoreboard(1), vm.currentScreen.value) }

        rule.onNodeWithTag("confirm_abandon_button").performClick()
        rule.runOnIdle { assertEquals(AppScreen.SessionHub, vm.currentScreen.value) }
    }
}
```

> `loadSessionForTest` and `Fixtures` come from Task 6A (already created before this task).

- [ ] **Step 2: Run to verify it fails** — Expected: FAIL — no `confirm_abandon_button`; abandon fires immediately today.

- [ ] **Step 3: Add dialog state to `LiveScoreboardScreen`**

Near the other `remember`s (after line 44):

```kotlin
    var showAbandonDialog by remember { mutableStateOf(false) }
```

Change the Abandon `TextButton` onClick (line ~491) from `viewModel.abandonMatch(courtId)` to `{ showAbandonDialog = true }`. Then add, alongside the completion `AlertDialog`:

```kotlin
    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { showAbandonDialog = false },
            title = { Text("Abandon match?", fontWeight = FontWeight.Bold, color = LocalPickItTokens.current.textPrimary) },
            text = { Text("This ends the match with no result and returns both teams to the queue.",
                color = LocalPickItTokens.current.textSecondary) },
            confirmButton = {
                Button(
                    onClick = { showAbandonDialog = false; viewModel.abandonMatch(courtId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalPickItTokens.current.textDanger, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_abandon_button"),
                ) { Text("Abandon", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAbandonDialog = false },
                    modifier = Modifier.testTag("dismiss_abandon_button"),
                ) { Text("Keep playing") }
            },
            containerColor = LocalPickItTokens.current.surfaceElevated,
        )
    }
```

Add import: `com.example.ui.theme.LocalPickItTokens`.

- [ ] **Step 4: Run to verify it passes** — Expected: PASS.
- [ ] **Step 5: Verify regression** — temporarily revert the onClick to the direct call; confirm the test fails; re-apply.
- [ ] **Step 6: Commit** — `fix(live): confirm before abandoning a match`

---

## Task 8: Hub emphasis (one lime primary) + 48dp rest-player targets

**Files:**
- Modify: `ui/screens/SessionHubScreen.kt`, `ui/components/RecommendationCard.kt`
- Test: `ui/RecommendationEmphasisTest.kt`, `ui/RestPlayerTargetTest.kt`

- [ ] **Step 1: Write the failing emphasis test** (two ready courts → exactly one primary CTA, on the lowest id)

```kotlin
// RecommendationEmphasisTest: render SessionHubScreen with an injected session
// that has ready recommendations on courts 3 and 2.
// Assert onAllNodesWithTag("primary_call_button").fetchSemanticsNodes().size == 1
// Assert onNodeWithTag("call_and_start_button_2").assertExists()  // lowest id is primary
```

Full test body (mirror the render setup from `AbandonConfirmTest`, using `Fixtures.twoReadyCourtsSession()`):

```kotlin
        rule.onAllNodesWithTag("primary_call_button").assertCountEquals(1)
        rule.onNodeWithTag("call_and_start_button_2").assertIsDisplayed()
```

- [ ] **Step 2: Run to verify it fails** — Expected: FAIL — every card is primary today; no `primary_call_button` tag.

- [ ] **Step 3: Add `isPrimary` to `RecommendationCard` + sort in the Hub**

`RecommendationCard` signature gains `isPrimary: Boolean = true`. Card border + CTA switch on it:

```kotlin
        modifier = modifier.fillMaxWidth()
            .border(2.dp, if (isPrimary) LocalPickItTokens.current.accent else LocalPickItTokens.current.border,
                RoundedCornerShape(16.dp))
            .testTag("recommendation_card_${recommendation.courtId}"),
```

CTA button. **A second `Modifier.testTag` replaces the first rather than adding**, so emit the `primary_call_button` tag only on a wrapping `Box` in the primary case (this is why the emphasis test can count exactly one):

```kotlin
        val cta = @Composable {
            Button(
                onClick = onCallAndStart,
                colors = if (isPrimary) ButtonDefaults.buttonColors(
                        containerColor = LocalPickItTokens.current.accent, contentColor = LocalPickItTokens.current.onAccent)
                    else ButtonDefaults.outlinedButtonColors(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .testTag("call_and_start_button_${recommendation.courtId}"),
            ) { Text(if (isPrimary) "Call & start court ${recommendation.courtId}" else "Call court ${recommendation.courtId}", fontWeight = FontWeight.Bold) }
        }
        if (isPrimary) Box(Modifier.testTag("primary_call_button")) { cta() } else cta()
```

In `SessionHubScreen`, sort before rendering (replace `items(recommendations.values.toList())` at line 126):

```kotlin
                val sorted = recommendations.values.sortedBy { it.courtId }
                itemsIndexed(sorted) { index, rec ->
                    RecommendationCard(
                        recommendation = rec,
                        isPrimary = (index == 0),
                        onCallAndStart = { viewModel.confirmRecommendation(rec.courtId) },
                        onSwapPartners = { viewModel.swapRecommendationPartners(rec.courtId) },
                        onRestPlayer = { playerId -> viewModel.togglePlayerRest(playerId) },
                    )
                }
```

Add import `androidx.compose.foundation.lazy.itemsIndexed`.

- [ ] **Step 4: Run emphasis test** — Expected: PASS.

- [ ] **Step 5: Write the failing 48dp test**

```kotlin
        rule.onAllNodesWithTag("rest_rec_player_", useUnmergedTree = true)  // prefix match not supported;
        // instead target a known fixture player id:
        rule.onNodeWithTag("rest_rec_player_${Fixtures.KNOWN_PLAYER_ID}")
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
```

- [ ] **Step 6: Run to verify it fails** — Expected: FAIL — current `IconButton` is `.size(24.dp)`.

- [ ] **Step 7: Expand touch bounds without enlarging the icon**

Replace each rest-player `IconButton(modifier = Modifier.size(24.dp)...)` with:

```kotlin
                                IconButton(
                                    onClick = { onRestPlayer(recommendation.teamA.player1.id) },
                                    modifier = Modifier.size(48.dp).testTag("rest_rec_player_${recommendation.teamA.player1.id}"),
                                ) {
                                    Icon(Icons.Default.Bedtime, contentDescription = "Rest ${recommendation.teamA.player1.name}",
                                        tint = LocalPickItTokens.current.textMuted, modifier = Modifier.size(18.dp))
                                }
```

Apply to all four rest buttons (`teamA.player1/2`, `teamB.player1/2`). The 48dp is the touch target; the 18dp icon keeps the visual small. (If row height grows unacceptably, use `Modifier.minimumInteractiveComponentSize()` on a 24dp icon instead — but `size(48.dp)` with a small icon is simplest and meets the target.)

- [ ] **Step 8: Run to verify it passes** — Expected: PASS.
- [ ] **Step 9: Commit** — `feat(hub): one primary Call CTA by lowest courtId; 48dp rest targets`

---

## Task 9: Token / type / emphasis migration across screens & components

Mechanical migration: replace every hardcoded color with the mapped token via `LocalPickItTokens.current`, switch labels to sentence case (keep only `labelSmall` eyebrows uppercase), and apply status-dot+shape+label and checkmark-selection patterns. **Verification is by Roborazzi baselines (Task 12), not unit tests** — this task has no red-green cycle; it is a per-file refactor with a fixed mapping.

**Canonical color → token map** (named constants from `Color.kt` + the common ad-hoc hex):

| Old value | Token (`LocalPickItTokens.current.…`) |
|---|---|
| `PickleballLime` / `Color(0xFFD4E157)` | `accent` (fills) or `textAccent` (text) |
| `Color(0xFF1B3700)` | `onAccent` |
| `PickleballLimeContainer` / `Color(0xFF263300)` | `surfaceInset` (+ `textAccent` text) |
| `CanvasDark` / `Color(0xFF0C110E)` | `canvas` |
| `PickleballCardSurface` / `Color(0xFF1B241F)` | `surfaceElevated` |
| `PickleballDarkCourt` / `Color(0xFF0F1713)` | `surfaceInset` |
| `Color(0xFF141C17)` / `0xFF141D17` / `0xFF16211A` / `0xFF161F19` | `surfaceInset` |
| `Color(0xFF1D2920)` / `0xFF233027` / `0xFF1F2B23` | `surfaceInset` (selected: add `accent` border) |
| `PickleballCardBorder` / `Color(0xFF2E3D35)` / `0xFF334539` / `0xFF37474F` | `border` |
| `WhiteHighContrast` / `Color(0xFFFFFFFF)` | `textPrimary` |
| `TextMuted` / `Color(0xFFB0BEC5)` | `textSecondary` |
| `CourtAvailableGreen` | `statusOpen` · `CourtActiveBlue` → `statusLive` · `CourtPausedGray` → `statusPaused` · `CourtAttentionAmber` → `attention` |
| `TeamAColor` → `teamA` · `TeamBColor` → `teamB` | on-team text `Color(0xFF00363A)`→`onTeamA`, `0xFF4E1400`→`onTeamB` |
| `Color(0xFFD32F2F)` (match-point badge bg) | `textDanger` (bg) + `Color.White` text |
| `Color(0xFFEF5350)` (abandon text) | `textDanger` |
| Any other ad-hoc dark green | nearest of `surface`/`surfaceElevated`/`surfaceInset` by lightness |

Do each file as its own commit. For every file: (a) swap colors per the table; (b) replace `.uppercase()`/ALL-CAPS section labels with sentence case except `labelSmall` eyebrows; (c) apply the patterns below; (d) **normalize radii to the token scale** — `RoundedCornerShape(8/10.dp)` → `radiusSm`, `(12.dp)` → `radiusMd`, `(14/16/20.dp)` → `radiusLg` (satisfies spec §4 / acceptance #3's "radii ∈ {8,12,16}"; existing offenders include `RecommendationCard.kt:291`, `CourtStatusCard.kt:38`, `SetupScreen.kt:132`).

- [ ] **9a — `CourtStatusCard.kt`:** drop the full colored `border(1.5.dp, borderColor…)`; use `surface` + `border`. Render status as a **dot with distinct shape + label + color**: `AVAILABLE` = outlined ring + "Open" (`statusOpen`); `IN_PROGRESS` = filled dot + "Live" (`statusLive`); `PAUSED` = pause glyph + "Paused" (`statusPaused`). Actions neutral (`Live score` outlined; `Final score` outlined). Commit: `refactor(court-card): tokens + status dot/shape/label, drop colored border`.
- [ ] **9b — `SessionHubScreen.kt`:** section eyebrows → `labelSmall` sentence-case `textSecondary`; queue button tonal (`surfaceInset` + `textAccent`); standalone block → quiet row (no bordered surface). Commit: `refactor(hub): tokens + quiet section labels`.
- [ ] **9c — `LiveScoreboardScreen.kt`:** score `displayLarge` token; callout bar labels floored to `labelSmall` (≥12sp), sentence case; rally buttons keep `teamA`/`teamB` with `onTeamA`/`onTeamB` text (documented exception); match-point badge = `textDanger` bg + white text + "Match point". Replace `maxLines = 1` on player-name `Text`s with `maxLines = 2` + `overflow = TextOverflow.Ellipsis` so names wrap/ellipsize gracefully at large system font scale (also apply wherever `maxLines = 1` clips a name in `CourtStatusCard`/`FastFinalScoreSheet`/`RecommendationCard`). Commit: `refactor(live): tokens + type scale, floor labels to 12sp, graceful name overflow`.
- [ ] **9d — `SetupScreen.kt`:** eyebrows sentence case; court-count selected state shows a check + fill (not color-only); `Launch session` uses `accent`/`onAccent`. (Roster emptiness handled in Task 10.) Commit: `refactor(setup): tokens + checkmark court-count selection`.
- [ ] **9e — `FastFinalScoreSheet.kt`:** tokens; `Confirm & rotate` `accent`; selected score pill = fill + check glyph (shape), not color alone. Commit: `refactor(final-sheet): tokens + shape-based pill selection`.
- [ ] **9f — `QueueRosterSheet.kt`:** tokens + sentence case. Commit: `refactor(queue-sheet): tokens`.
- [ ] **9g — `OnDeckHorizonBar.kt`:** tokens; eyebrow sentence case; floor 10sp meta to `bodySmall`. Commit: `refactor(on-deck): tokens + type floor`.
- [ ] **9h — `TacticalPickleballCourtDiagram.kt`:** tokens for surfaces/lines/team markers. Commit: `refactor(court-diagram): tokens`.
- [ ] **9i — `StandaloneScoreboardScreen.kt`:** tokens + type + emphasis (one primary), mirroring the live scoreboard patterns. Commit: `refactor(standalone): tokens + type + emphasis`.
- [ ] **9j — Delete now-unused constants from `Color.kt`** (only those with zero remaining references — verify with a repo grep). Keep any still referenced. Commit: `refactor(theme): remove inline color constants superseded by tokens`.

After 9a–9j, run `./gradlew :app:compileDebugKotlin` — Expected: BUILD SUCCESSFUL, and a grep for `Color(0x` under `ui/screens` + `ui/components` returns only intentional exceptions (document any).

---

## Task 10: Empty Setup roster + "Load sample players"

**Files:**
- Modify: `ui/screens/SetupScreen.kt`
- Test: `ui/SetupRosterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
        rule.setContent { MyApplicationTheme { SetupScreen(viewModel = vm) } }
        // roster starts empty:
        rule.onNodeWithTag("setup_add_player_input").assertIsDisplayed()
        rule.onAllNodesWithTag("roster_chip", useUnmergedTree = true).assertCountEquals(0)
        // load sample players populates it:
        rule.onNodeWithTag("load_sample_players_button").performClick()
        rule.onAllNodesWithTag("roster_chip", useUnmergedTree = true).assertCountEquals(12)
```

- [ ] **Step 2: Run to verify it fails** — Expected: FAIL — Setup seeds 12 players; no `load_sample_players_button`.

- [ ] **Step 3: Empty the defaults + add the affordance**

In `SetupScreen`: change `sessionName` default to `""` (add a placeholder "Saturday Open Play" in the `OutlinedTextField`), and `selectedPlayers` to `mutableStateListOf<String>()`. Tag each roster `InputChip` with `Modifier.testTag("roster_chip")`. Add a secondary button next to the roster header:

```kotlin
                OutlinedButton(
                    onClick = { if (selectedPlayers.isEmpty()) selectedPlayers.addAll(viewModel.frequentPlayers.take(12)) },
                    modifier = Modifier.testTag("load_sample_players_button"),
                ) { Text("Load sample players") }
```

- [ ] **Step 4: Run to verify it passes** — Expected: PASS.
- [ ] **Step 5: Commit** — `feat(setup): start with empty roster + Load-sample-players affordance`

---

## Task 11: Appearance control in the Setup header (subordinate to Launch)

**Files:**
- Modify: `ui/screens/SetupScreen.kt`
- Test: `ui/AppearanceControlTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
        rule.setContent { MyApplicationTheme { SetupScreen(viewModel = vm) } }
        rule.onNodeWithTag("appearance_button").performClick()          // opens the menu
        rule.onNodeWithTag("appearance_option_LIGHT").performClick()
        rule.runOnIdle { assertEquals(ThemeMode.LIGHT, vm.themeMode.value) }
```

- [ ] **Step 2: Run to verify it fails** — Expected: FAIL — no appearance control.

- [ ] **Step 3: Add a compact Appearance control to the Setup `TopAppBar` `actions`**

A small icon button opening a `DropdownMenu` with Dark / Light / Follow system, each calling `viewModel.setThemeMode(...)`. It lives in the header `actions` (peripheral), styled `textSecondary` — **not** a filled/accent control, so it can't compete with the `Launch session` button:

Add an `actions = { … }` lambda to the Setup `TopAppBar` (it currently has only `title`/`navigationIcon`):

```kotlin
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("appearance_button")) {
                        Icon(Icons.Default.BrightnessMedium, contentDescription = "Appearance",
                            tint = LocalPickItTokens.current.textSecondary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        ThemeMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(when (mode) {
                                    ThemeMode.DARK -> "Dark"; ThemeMode.LIGHT -> "Light"; ThemeMode.SYSTEM -> "Follow system" }) },
                                onClick = { viewModel.setThemeMode(mode); menuOpen = false },
                                modifier = Modifier.testTag("appearance_option_${mode.name}"),
                            )
                        }
                    }
                }
```

Add imports for `DropdownMenu`, `DropdownMenuItem`, `Icons.Default.BrightnessMedium`, `LocalPickItTokens`, `ThemeMode`.

- [ ] **Step 4: Run to verify it passes** — Expected: PASS.
- [ ] **Step 5: Commit** — `feat(setup): app-level Appearance control in the header, subordinate to Launch`

---

## Task 12: Fixtures + deterministic dual-theme Roborazzi baselines

**Files:**
- Create: `app/src/test/java/com/example/screenshots/ScreenshotBaselinesTest.kt`
- Delete: `app/src/test/java/com/example/GreetingScreenshotTest.kt`, `app/src/test/screenshots/greeting.png`

*(`Fixtures.kt` and the `loadSessionForTest` seam already exist from Task 6A.)*

- [ ] **Step 1: Write the baseline test** (render each screen from fixtures, both themes)

```kotlin
    private fun capture(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        composeTestRule.setContent { MyApplicationTheme(themeMode = mode) { content() } }
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name-${mode.name.lowercase()}.png")
    }
    // e.g. capture("hub", ThemeMode.DARK) { SessionHubScreen(viewModel = vmWith(Fixtures.twoReadyCourtsSession())) }
    // repeat for LIGHT, and for live/setup/final-sheet/queue-sheet.
```

Use `loadSessionForTest` to seed the VM synchronously so nothing waits on Room.

> **Deviation (accepted, implemented):** baselines cover **Hub, Live Scoreboard, and Setup** in both themes (6 images). The two modal sheets (`FastFinalScoreSheet`, `QueueRosterSheet`) are **deferred** — standalone `ModalBottomSheet` screenshots are fragile to capture deterministically (sheet state/animation, overlay window), and both sheets are migrated onto tokens like every other surface and exercised through their host flows (finalize path; Hub queue button). Tracked as a follow-up, not a blocker.

- [ ] **Step 2: Delete the stale test + baseline**

Remove `GreetingScreenshotTest.kt` and `src/test/screenshots/greeting.png` (orphaned; the real capture was `app_home.png`).

- [ ] **Step 3: Record baselines**

Run: `./gradlew :app:recordRoborazziDebug`
Expected: writes `hub-dark.png`, `hub-light.png`, `live-dark.png`, … under `app/src/test/screenshots/`. Eyeball each for the emphasis/contrast goals.

- [ ] **Step 4: Verify**

Run: `./gradlew :app:verifyRoborazziDebug`
Expected: PASS (no diff against the just-recorded baselines).

- [ ] **Step 5: Commit** — `test(screenshots): fixture-based dual-theme baselines; drop stale greeting test`

---

## Task 13: Full-suite green + acceptance sweep

- [ ] **Step 1: Run the whole unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all new + existing: `PickleballEngineTest`, `RoomDatabaseTest`, `UsabilityWalkthroughEdgeCasesTest`, etc.). Note: `UsabilityWalkthroughEdgeCasesTest` is pure engine/model (no `SessionViewModel`, no `createInitialSession`), so removing the demo seed does not affect it (confirmed in spec §10). If any test *does* fail, seed via `loadSessionForTest`/`startNewSession` rather than the removed demo.

- [ ] **Step 2: Acceptance sweep against the spec §11**

Verify #1–#7 by grep/inspection: one `primary_call_button` per Hub; no rendered text < 12sp (`grep -rnE "[^0-9](9|10|11)\.sp" ui/screens ui/components` → none); `grep -rn "Color(0x" ui/screens ui/components` → only documented exceptions; `grep -rnE "RoundedCornerShape\((10|14|20)\.dp\)" ui/screens ui/components` → none (card/control radii normalized to 8/12/16). **Documented exceptions:** small pill/dot radii (4dp/6dp — e.g. the match-point and side-out pills at `LiveScoreboardScreen.kt:91,368`, status dots) stay as-is; spec §4 names only 10/14dp, so sub-8 badge radii are intentional and out of the `{8,12,16}` rule. Abandon has a dialog; no `.size(24.dp)` on interactive controls; first launch shows the empty state; both dark+light baselines exist.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** — `chore(design-pass): finalize acceptance sweep`

---

## Notes for the executor
- **DRY:** always read colors/dimensions from `LocalPickItTokens.current`; never reintroduce a hex literal in a screen/component.
- **YAGNI:** do not add tokens beyond the set in Task 1; if a value has no token, it maps to the nearest existing role (see the Task 9 table), don't invent new roles.
- **TDD:** Tasks 1–8, 10, 11 are red-green. Task 9 is a guarded refactor (Roborazzi is its safety net) — keep the app compiling after each sub-file commit.
- **Court Call / big-screen mode is OUT OF SCOPE** (separate cycle).
