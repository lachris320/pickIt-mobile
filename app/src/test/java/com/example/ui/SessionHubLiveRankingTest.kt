package com.example.ui

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.model.OpenPlaySession
import com.example.model.Player
import com.example.model.RotationPolicy
import com.example.testing.RobolectricComposeTest
import com.example.ui.screens.SessionHubScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SessionViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

@Config(qualifiers = "w1280dp-h720dp-land", sdk = [36])
class SessionHubLiveRankingTest : RobolectricComposeTest() {

    @Test fun hubButton_opensLiveRanking() {
        val vm = SessionViewModel(app())
        vm.loadSessionForTest(
            OpenPlaySession(
                id = "s1", name = "Test", rotationPolicy = RotationPolicy.FOUR_OFF_FOUR_ON,
                courts = emptyList(),
                roster = listOf(Player(id = "a", name = "Al", matchesPlayed = 1, matchesWon = 1)),
            )
        )
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }

        rule.onNodeWithTag("live_ranking_button").performClick()
        assertEquals(AppScreen.LiveRanking, vm.currentScreen.value)
    }
}
