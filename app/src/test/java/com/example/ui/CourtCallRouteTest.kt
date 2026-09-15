package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.fixtures.Fixtures
import com.example.ui.screens.CourtCallScreen
import com.example.ui.screens.SessionHubScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CourtCallRouteTest {
    @get:Rule val rule = createComposeRule()

    private fun app() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun hubButton_navigatesToCourtCall() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(Fixtures.twoReadyCourtsSession())
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }

        rule.onNodeWithTag("court_call_button").assertIsDisplayed()
        rule.onNodeWithTag("court_call_button").performClick()
        rule.runOnIdle { assertEquals(AppScreen.CourtCall, vm.currentScreen.value) }
    }

    @Test fun nullSessionHub_hasNoCourtCallButton() {
        val vm = SessionViewModel(app())
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }
        rule.onNodeWithTag("court_call_button").assertDoesNotExist()
    }

    @Test fun closeButton_returnsToHub() {
        val vm = SessionViewModel(app())
        vm.navigateTo(AppScreen.CourtCall)
        rule.setContent { MyApplicationTheme { CourtCallScreen(viewModel = vm) } }

        rule.onNodeWithTag("court_call_close").assertIsDisplayed()
        rule.onNodeWithTag("court_call_close").performClick()
        rule.runOnIdle { assertEquals(AppScreen.SessionHub, vm.currentScreen.value) }
    }
}
