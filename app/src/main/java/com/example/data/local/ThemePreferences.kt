package com.example.data.local

import android.content.Context
import com.example.ui.theme.ThemeMode

/**
 * Synchronous SharedPreferences wrapper for the app-level appearance preference.
 * Read on ViewModel init (synchronously) so the correct theme is applied before the
 * first composition — DataStore's async read would flash the default theme at startup.
 */
class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("pickit_prefs", Context.MODE_PRIVATE)

    fun readMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)

    fun writeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
    }

    private companion object { const val KEY = "theme_mode" }
}
