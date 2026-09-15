package com.example.testing

import android.app.Application
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
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
}
