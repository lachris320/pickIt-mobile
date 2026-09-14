package com.example.viewmodel

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PickleballDatabase
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirstLaunchTest {
    @get:Rule val rule = createComposeRule()

    @Before fun cleanDb() {
        // The Room DB (singleton + file) is shared across Robolectric test classes;
        // clear any session another test persisted so "fresh install" is truly fresh.
        PickleballDatabase.resetInstanceForTest()
        ApplicationProvider.getApplicationContext<Application>().deleteDatabase("pickleball_sessions.db")
    }

    @Test fun freshInstall_hasNoActiveSession() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SessionViewModel(app)
        rule.setContent { Box(Modifier) {} }   // drive the Compose/Robolectric clock
        rule.waitForIdle()                      // reliably lets the init coroutine (Room load) settle
        rule.runOnIdle { assertNull(vm.session.value) }
    }
}
