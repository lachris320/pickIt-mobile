package com.example.ui.screens

import com.example.model.Court
import com.example.model.CourtStatus
import com.example.model.OpenPlaySession

/** The six mutually-exclusive display states a court tile can render on the Court Call board. */
enum class CourtCallState { UP_NOW, READY, LIVE, FINAL, OPEN, PAUSED }

/**
 * Derive the display state for one court from already-live data. Precedence (top-down):
 * PAUSED, then FINAL (completed match, even while status is still IN_PROGRESS), then LIVE
 * (in-progress, not completed), then for an AVAILABLE court: UP_NOW if it is the single
 * primary ready court, READY if it has a recommendation, else OPEN.
 */
fun courtDisplayState(
    court: Court,
    hasRecommendation: Boolean,
    isPrimaryReady: Boolean,
): CourtCallState = when {
    court.status == CourtStatus.PAUSED -> CourtCallState.PAUSED
    court.currentMatch?.isCompleted == true -> CourtCallState.FINAL
    court.status == CourtStatus.IN_PROGRESS -> CourtCallState.LIVE
    court.status == CourtStatus.AVAILABLE && isPrimaryReady -> CourtCallState.UP_NOW
    court.status == CourtStatus.AVAILABLE && hasRecommendation -> CourtCallState.READY
    else -> CourtCallState.OPEN
}

/**
 * The single von-Restorff standout: the lowest-`courtId` court that is AVAILABLE and has a
 * recommendation. `null` when no court is ready. (`activeRecommendations` is an unordered Map,
 * so selection sorts by `courtId`.)
 */
fun primaryReadyCourtId(session: OpenPlaySession): Int? =
    session.courts
        .filter { it.status == CourtStatus.AVAILABLE && session.activeRecommendations[it.id] != null }
        .minByOrNull { it.id }
        ?.id
