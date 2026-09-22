package com.example.ui.screens

import kotlin.math.max

/**
 * Max fully-readable rows that fit in [availableHeightPx] at a fixed [rowHeightPx].
 * Plain floor with a defensive `max(1, ...)`: real displays always fit >= 1 row after
 * insets/header/footer, so capacity is >= 1 in practice; the degenerate case returns a single
 * (possibly clipped) row rather than an empty/crashing page.
 */
fun rowsPerPage(availableHeightPx: Int, rowHeightPx: Int): Int =
    if (rowHeightPx <= 0) 1 else max(1, availableHeightPx / rowHeightPx)

/** Fixed page count for [itemCount] rows at [rowsPerPage] per page (always >= 1). */
fun pageCountFor(itemCount: Int, rowsPerPage: Int): Int =
    if (itemCount <= 0 || rowsPerPage <= 0) 1 else (itemCount + rowsPerPage - 1) / rowsPerPage

/**
 * Next page index. Advances from the CLAMPED current page so a page-count shrink self-corrects:
 * if [current] is out of range for the new [pageCount], it advances from the last valid page.
 */
fun nextPage(current: Int, pageCount: Int): Int =
    if (pageCount <= 1) 0 else (current.coerceIn(0, pageCount - 1) + 1) % pageCount

/** Callout-style diff: positive prefixed with '+', zero as '0', negative intrinsic ('-3'). */
fun formatDiff(diff: Int): String = if (diff > 0) "+$diff" else diff.toString()
