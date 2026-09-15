package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.fixtures.Fixtures
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.LiveScoreboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [36])
class AbandonConfirmTest : RobolectricComposeTest() {

    @Test fun abandon_requiresConfirmation() {
        val vm = SessionViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.loadSessionForTest(Fixtures.inProgressSession())
        vm.navigateTo(AppScreen.LiveScoreboard(1))
        rule.setContent { MyApplicationTheme { LiveScoreboardScreen(courtId = 1, viewModel = vm) } }

        rule.onNodeWithTag("abandon_match_button").performClick()
        rule.onNodeWithTag("confirm_abandon_button").assertIsDisplayed()   // dialog shown
        rule.runOnIdle { assertEquals(AppScreen.LiveScoreboard(1), vm.currentScreen.value) }

        rule.onNodeWithTag("confirm_abandon_button").performClick()
        rule.runOnIdle { assertEquals(AppScreen.SessionHub, vm.currentScreen.value) }
    }
}
