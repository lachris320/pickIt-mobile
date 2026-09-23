package com.example.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.model.OpenPlaySession
import com.example.model.Player
import com.example.model.RotationPolicy
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.SessionHubScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = "w411dp-h891dp", sdk = [36])
class SessionHistoryNavTest : RobolectricComposeTest() {

    @Before fun cleanDb() { resetSharedDb() }

    @Test fun hubEmptyBranch_opensHistory_withHubOrigin() {
        val vm = SessionViewModel(app()) // no saved session -> Hub empty branch
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }
        rule.onNodeWithTag("session_history_button").performClick()
        assertEquals(AppScreen.SessionHistory(AppScreen.SessionHub), vm.currentScreen.value)
    }

    @Test fun hubActiveBranch_opensHistory_withHubOrigin() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(
            OpenPlaySession(
                id = "s1", name = "Live", rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
                courts = emptyList(),
                roster = listOf(Player(id = "a", name = "Al", matchesPlayed = 1, matchesWon = 1)),
            )
        )
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }
        rule.onNodeWithTag("session_history_button").performClick()
        assertEquals(AppScreen.SessionHistory(AppScreen.SessionHub), vm.currentScreen.value)
    }

    @Test fun setup_opensHistory_withSetupOrigin() {
        val vm = SessionViewModel(app())
        rule.setContent { MyApplicationTheme { SetupScreen(viewModel = vm) } }
        rule.onNodeWithTag("setup_screen_content").performScrollToNode(hasTestTag("setup_history_button"))
        rule.onNodeWithTag("setup_history_button").performClick()
        assertEquals(AppScreen.SessionHistory(AppScreen.Setup), vm.currentScreen.value)
    }
}
