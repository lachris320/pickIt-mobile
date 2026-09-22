package com.example.model

/** One row of the Live Ranking board. `rank` is a competition rank (ties share a number). */
data class RankedPlayer(
    val id: String,
    val rank: Int,
    val name: String,
    val wins: Int,
    val losses: Int,
    val pointDiff: Int,
    val isCheckedOut: Boolean,
)
