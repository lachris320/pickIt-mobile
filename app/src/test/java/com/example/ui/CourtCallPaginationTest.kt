package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.fixtures.Fixtures
import com.example.model.OpenPlaySession
import com.example.ui.screens.CourtCallScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SessionViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])
class CourtCallPaginationTest {
    @get:Rule val rule = createComposeRule()

    private fun app() = ApplicationProvider.getApplicationContext<Application>()
    private fun vmWith(s: OpenPlaySession) = SessionViewModel(app()).apply { loadSessionForTest(s) }

    @Test fun underCapacity_showsNoPageIndicator() {
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = vmWith(Fixtures.boardSession())) } }
        rule.onNodeWithTag("court_call_page_indicator").assertDoesNotExist()
    }

    @Test fun overCapacity_showsIndicator_andAutoAdvances() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = vmWith(Fixtures.manyCourtsSession())) } }
        rule.mainClock.advanceTimeBy(50)

        rule.onNodeWithTag("court_call_page_indicator").assertIsDisplayed()
        rule.onNodeWithText("1 / 2").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(10_000)
        rule.onNodeWithText("2 / 2").assertIsDisplayed()
    }
}
