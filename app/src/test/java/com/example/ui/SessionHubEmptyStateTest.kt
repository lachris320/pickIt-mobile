package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.SessionHubScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [36])
class SessionHubEmptyStateTest : RobolectricComposeTest() {

    @Before fun cleanDb() = resetSharedDb()

    @Test fun nullSession_showsStartCta_thatNavigatesToSetup() {
        val vm = SessionViewModel(app())
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }
        rule.onNodeWithTag("start_session_button").assertIsDisplayed()
        rule.onNodeWithTag("start_session_button").performClick()
        rule.runOnIdle { assertEquals(AppScreen.Setup, vm.currentScreen.value) }
    }
}
