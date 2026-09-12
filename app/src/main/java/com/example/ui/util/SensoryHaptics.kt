package com.example.ui.util

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Tactical sensory haptics utility for outdoor pickleball scorekeeping.
 * Provides distinct haptic signatures so the host/player can keep score without looking down:
 * - POINT SCORED: crisp single click (50ms)
 * - SIDE OUT / FAULT: double pulse (40ms on, 50ms off, 40ms on)
 * - MATCH POINT / WIN: triumphant triple burst
 * - UNDO: subtle low-intensity tick
 */
object SensoryHaptics {

    fun performPointHaptic(context: Context) {
        vibratePattern(context, longArrayOf(0, 45), intArrayOf(0, 220))
    }

    fun performSideOutHaptic(context: Context) {
        vibratePattern(context, longArrayOf(0, 50, 60, 50), intArrayOf(0, 180, 0, 220))
    }

    fun performMatchWonHaptic(context: Context) {
        vibratePattern(
            context,
            longArrayOf(0, 80, 50, 80, 50, 140),
            intArrayOf(0, 255, 0, 255, 0, 255)
        )
    }

    fun performUndoHaptic(context: Context) {
        vibratePattern(context, longArrayOf(0, 25), intArrayOf(0, 140))
    }

    private fun vibratePattern(context: Context, timings: LongArray, amplitudes: IntArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                    vibrator.vibrate(effect)
                    return
                }
            }

            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                    vibrator.vibrate(effect)
                } else {
                    vibrator.vibrate(timings[1])
                }
            }
        } catch (_: Exception) {
            // Silently swallow if device does not support or in headless test environment
        }
    }
}
