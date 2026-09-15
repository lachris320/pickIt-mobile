package com.example.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SessionViewModel
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [36])
class SetupRosterTest : RobolectricComposeTest() {

    @Test fun rosterStartsEmpty_andLoadSamplePopulatesIt() {
        val vm = SessionViewModel(app())
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
