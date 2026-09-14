package com.example.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeModeTest {
    @Test fun themeMode_defaultsToSystem_andPersistsAcrossInstances() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm1 = SessionViewModel(app)
        assertEquals(ThemeMode.SYSTEM, vm1.themeMode.value)

        vm1.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, vm1.themeMode.value)

        val vm2 = SessionViewModel(app)   // fresh instance reads persisted value synchronously
        assertEquals(ThemeMode.LIGHT, vm2.themeMode.value)
    }
}
