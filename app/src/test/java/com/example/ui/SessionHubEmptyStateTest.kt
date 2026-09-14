package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PickleballDatabase
import com.example.ui.screens.SessionHubScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionHubEmptyStateTest {
    @get:Rule val rule = createComposeRule()

    @Before fun cleanDb() {
        // The Room DB (singleton + file) is shared across Robolectric test classes;
        // clear any session another test persisted so the empty state is shown.
        PickleballDatabase.resetInstanceForTest()
        ApplicationProvider.getApplicationContext<Application>().deleteDatabase("pickleball_sessions.db")
    }

    @Test fun nullSession_showsStartCta_thatNavigatesToSetup() {
        val vm = SessionViewModel(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }
        rule.onNodeWithTag("start_session_button").assertIsDisplayed()
        rule.onNodeWithTag("start_session_button").performClick()
        rule.runOnIdle { assertEquals(AppScreen.Setup, vm.currentScreen.value) }
    }
}
