package com.example.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkTokens
import com.example.ui.theme.LightTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TokenAndTypeTest {
    @Test fun darkTokens_haveExpectedCoreValues() {
        assertEquals(Color(0xFF0C110E), DarkTokens.canvas)
        assertEquals(8.dp, DarkTokens.radiusSm)
        assertEquals(12.dp, DarkTokens.radiusMd)
        assertEquals(16.dp, DarkTokens.radiusLg)
    }

    @Test fun lightAndDark_differOnSurfaceAndText() {
        assertNotEquals(DarkTokens.canvas, LightTokens.canvas)
        assertNotEquals(DarkTokens.textPrimary, LightTokens.textPrimary)
    }

    @Test fun typography_hasFullScaleWithFloors() {
        val t = com.example.ui.theme.Typography
        assertEquals(64f, t.displayLarge.fontSize.value)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Black, t.displayLarge.fontWeight)
        assertEquals(16f, t.titleMedium.fontSize.value)   // card titles
        assertEquals(13f, t.bodySmall.fontSize.value)     // meta floor
        assertEquals(12f, t.labelSmall.fontSize.value)    // eyebrow floor (no 9/10sp)
    }
}
