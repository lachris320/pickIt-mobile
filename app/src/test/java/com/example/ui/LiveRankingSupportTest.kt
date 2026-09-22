package com.example.ui

import com.example.ui.screens.formatDiff
import com.example.ui.screens.nextPage
import com.example.ui.screens.pageCountFor
import com.example.ui.screens.rowsPerPage
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveRankingSupportTest {

    @Test fun `rowsPerPage floors to capacity and never overflows`() {
        assertEquals(5, rowsPerPage(availableHeightPx = 500, rowHeightPx = 100)) // exact fit
        assertEquals(5, rowsPerPage(availableHeightPx = 560, rowHeightPx = 100)) // remainder, no overflow
    }

    @Test fun `rowsPerPage returns 1 for degenerate heights (defensive floor)`() {
        assertEquals(1, rowsPerPage(availableHeightPx = 40, rowHeightPx = 100)) // < one row
        assertEquals(1, rowsPerPage(availableHeightPx = 100, rowHeightPx = 0))  // guard div-by-zero
    }

    @Test fun `pageCountFor divides with ceiling and is at least 1`() {
        assertEquals(1, pageCountFor(itemCount = 0, rowsPerPage = 10))
        assertEquals(1, pageCountFor(itemCount = 10, rowsPerPage = 10))
        assertEquals(2, pageCountFor(itemCount = 11, rowsPerPage = 10))
    }

    @Test fun `nextPage advances and loops`() {
        assertEquals(1, nextPage(current = 0, pageCount = 3))
        assertEquals(0, nextPage(current = 2, pageCount = 3)) // wraps
        assertEquals(0, nextPage(current = 0, pageCount = 1)) // single page never moves
    }

    @Test fun `nextPage advances from the clamped page after a shrink`() {
        // rawPage was 4 on a 5-page board; pageCount just dropped to 2.
        // The displayed page is clamped to 1, and the next tick must advance from there to 0,
        // not compute (4+1)%2 == 1 and stall.
        assertEquals(0, nextPage(current = 4, pageCount = 2))
    }

    @Test fun `formatDiff signs positive, plain zero, intrinsic negative`() {
        assertEquals("+18", formatDiff(18))
        assertEquals("0", formatDiff(0))
        assertEquals("-3", formatDiff(-3))
    }
}
