package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.fixtures.Fixtures
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.CourtCallScreen
import com.example.ui.screens.SessionHubScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [36])
class CourtCallRouteTest : RobolectricComposeTest() {

    @Before fun cleanDb() = resetSharedDb()

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
