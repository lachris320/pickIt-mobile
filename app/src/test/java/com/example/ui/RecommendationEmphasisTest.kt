package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.fixtures.Fixtures
import com.example.ui.screens.SessionHubScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SessionViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecommendationEmphasisTest {
    @get:Rule val rule = createComposeRule()

    @Test fun twoReadyCourts_onlyTheLowestIdIsPrimary() {
        val vm = SessionViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.loadSessionForTest(Fixtures.twoReadyCourtsSession())
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }
        rule.onAllNodesWithTag("primary_call_button").assertCountEquals(1)
        rule.onNodeWithTag("call_and_start_button_2").assertIsDisplayed()
    }
}
