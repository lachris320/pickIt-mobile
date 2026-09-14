package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
class RestPlayerTargetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun restPlayerButton_meets48dpTouchTarget() {
        val vm = SessionViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.loadSessionForTest(Fixtures.twoReadyCourtsSession())
        rule.setContent { MyApplicationTheme { SessionHubScreen(viewModel = vm) } }
        rule.onNodeWithTag("rest_rec_player_${Fixtures.KNOWN_PLAYER_ID}")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }
}
