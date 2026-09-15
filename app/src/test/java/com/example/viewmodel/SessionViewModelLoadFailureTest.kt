package com.example.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CompletedMatchEntity
import com.example.data.local.CourtEntity
import com.example.data.local.PlayerEntity
import com.example.data.local.SessionDao
import com.example.data.local.SessionEntity
import com.example.data.repository.SessionRepository
import com.example.model.OpenPlaySession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

    /**
     * Stub dao for the fake repository. `getAllSessions()` returns an empty flow because
     * [SessionRepository] reads it eagerly in a property initializer; every other member throws,
     * since the fake overrides `loadLatestSession()` and nothing else is ever called.
     */
    private object UnusedDao : SessionDao {
        override fun getAllSessions(): Flow<List<SessionEntity>> = emptyFlow()
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

    /** Fake whose load fails, standing in for a query against a torn-down / closed database. */
    private class FailingRepository : SessionRepository(UnusedDao) {
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
