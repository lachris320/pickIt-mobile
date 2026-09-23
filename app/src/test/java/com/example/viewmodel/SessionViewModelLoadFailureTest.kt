package com.example.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SessionRepository
import com.example.model.OpenPlaySession
import com.example.testing.FakeSessionDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Resilience contract for [SessionViewModel]'s init load.
 *
 * Root cause of the intermittent `CourtCallScreenTest > nullSession_showsEmptyState` flake: a VM
 * from an earlier test is never `onCleared()`, so its init coroutine can still be pending; when a
 * later empty-state test tears the shared, file-backed Room singleton down, that leaked coroutine's
 * `loadLatestSession()` query throws. Because the original init had NO error handling, the throw
 * escaped uncaught into `viewModelScope` and surfaced at the next test as
 * `kotlinx.coroutines.test.UncaughtExceptionsBeforeTest`.
 *
 * This test pins the contract deterministically. A fake [SessionRepository] whose
 * `loadLatestSession()` throws is injected through the test-only constructor seam, and the VM's
 * init coroutine is driven on the [runTest] scheduler (installed as `Dispatchers.Main`). `runTest`
 * collects any uncaught exception thrown on that scheduler and rethrows it when the test body
 * finishes — so an unhandled throw in init fails THIS test rather than leaking to the next one.
 *
 * Expectations:
 *  - constructing the VM and running init must NOT crash / leak an uncaught exception; and
 *  - `session.value` stays null, so the Court Call / Hub empty state renders.
 *
 * Note: this is a plain Robolectric + `runTest` test (the same shape as [ThemeModeTest]) rather
 * than a `RobolectricComposeTest`, because detecting the uncaught coroutine leak requires driving
 * init on the test scheduler; the Compose test clock only logs such leaks, so it cannot make this
 * assertion deterministic within a single test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionViewModelLoadFailureTest {

    private fun app(): Application = ApplicationProvider.getApplicationContext()

    /** Fake whose load fails, standing in for a query against a torn-down / closed database. */
    private class FailingRepository : SessionRepository(FakeSessionDao()) {
        override suspend fun loadLatestSession(): OpenPlaySession? =
            throw IllegalStateException("Cannot perform this operation because the connection pool has been closed")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun loadFailure_doesNotCrash_andSessionStaysNull() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = SessionViewModel(app(), FailingRepository())
            advanceUntilIdle()   // run the init coroutine (the failing load) to completion
            assertNull(vm.session.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
