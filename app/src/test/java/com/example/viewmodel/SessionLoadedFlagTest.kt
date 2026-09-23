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
