package com.example.testing

import android.app.Application
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PickleballDatabase
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Shared base for Robolectric-driven Compose unit tests.
 *
 * Holds the setup that every such test repeats: the Robolectric runner (`@RunWith` is
 * `@Inherited`, so subclasses need not re-declare it), a `createComposeRule()` rule, and the
 * `app()` accessor. `@Config` is intentionally NOT declared here — qualifiers/sdk vary per test,
 * so each subclass keeps its own (Robolectric reads `@Config` from the concrete test class).
 */
@RunWith(RobolectricTestRunner::class)
abstract class RobolectricComposeTest {
    @get:Rule val rule: ComposeContentTestRule = createComposeRule()

    protected fun app(): Application = ApplicationProvider.getApplicationContext()

    /**
     * Resets the shared Room state so a test can assert empty/first-launch behaviour.
     *
     * The file-backed Room singleton is shared across all Robolectric test classes in one JVM
     * run, so a session another test persisted would leak in and defeat an empty-state assertion.
     * This clears both the in-memory singleton and the on-disk DB file. It is intentionally NOT a
     * base-class `@Before` — only empty-state tests need it, so each such test keeps its own
     * per-class `@Before fun cleanDb()` that delegates here.
     */
    protected fun resetSharedDb() {
        PickleballDatabase.resetInstanceForTest()
        app().deleteDatabase("pickleball_sessions.db")
    }
}
