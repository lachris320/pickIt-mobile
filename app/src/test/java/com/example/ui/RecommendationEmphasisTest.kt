package com.example.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.example.fixtures.Fixtures
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.SessionHubScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SessionViewModel
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [36])
class RecommendationEmphasisTest : RobolectricComposeTest() {

    @Test fun twoReadyCourts_onlyTheLowestIdIsPrimary() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(Fixtures.twoReadyCourtsSession())
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }
        rule.onAllNodesWithTag("primary_call_button").assertCountEquals(1)
        rule.onNodeWithTag("call_and_start_button_2").assertIsDisplayed()
    }
}
