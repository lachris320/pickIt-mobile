package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SessionViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SetupRosterTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rosterStartsEmpty_andLoadSamplePopulatesIt() {
        val vm = SessionViewModel(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent { MyApplicationTheme { SetupScreen(viewModel = vm) } }

        // Roster starts empty: no chips exist anywhere.
        rule.onAllNodesWithTag("roster_chip", useUnmergedTree = true).assertCountEquals(0)

        // Load sample players populates the roster with 12.
        rule.onNodeWithTag("setup_screen_content")
            .performScrollToNode(hasTestTag("load_sample_players_button"))
        rule.onNodeWithTag("load_sample_players_button").performClick()
        rule.onNodeWithTag("setup_screen_content")
            .performScrollToNode(hasTestTag("roster_chip"))
        rule.onAllNodesWithTag("roster_chip", useUnmergedTree = true).assertCountEquals(12)
    }
}
