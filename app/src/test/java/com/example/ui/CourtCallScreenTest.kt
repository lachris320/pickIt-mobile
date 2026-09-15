package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PickleballDatabase
import com.example.fixtures.Fixtures
import com.example.ui.screens.CourtCallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SessionViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])
class CourtCallScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun app() = ApplicationProvider.getApplicationContext<Application>()
    private fun vmWith(s: com.example.model.OpenPlaySession) =
        SessionViewModel(app()).apply { loadSessionForTest(s) }

    @Before fun cleanDb() {
        // The Room DB (singleton + file) is shared across Robolectric test classes;
        // clear any session another test persisted so the empty state is shown.
        PickleballDatabase.resetInstanceForTest()
        ApplicationProvider.getApplicationContext<Application>().deleteDatabase("pickleball_sessions.db")
    }

    @Test fun board_hasExactlyOneUpNow_andOneReady() {
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = vmWith(Fixtures.boardSession())) } }
        rule.onAllNodesWithTag("court_call_tile_up_now").assertCountEquals(1)
        rule.onAllNodesWithTag("court_call_tile_ready").assertCountEquals(1)
        rule.onAllNodesWithTag("court_call_tile_live").assertCountEquals(1)
        rule.onAllNodesWithTag("court_call_tile_final").assertCountEquals(1)
        rule.onAllNodesWithTag("court_call_tile_open").assertCountEquals(1)
    }

    @Test fun nullSession_showsEmptyState() {
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = SessionViewModel(app())) } }
        rule.onNodeWithTag("court_call_empty").assertIsDisplayed()
    }

    @Test fun pausedSession_showsPausedBanner() {
        rule.setContent {
            MyApplicationTheme {
                CourtCallScreen(viewModel = vmWith(Fixtures.boardSession().copy(isPaused = true)))
            }
        }
        rule.onNodeWithTag("court_call_paused_banner").assertIsDisplayed()
    }
}
