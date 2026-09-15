package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamPlayerNamesTest {

    private val team = Team(
        TeamId.TEAM_A,
        Player("p1", "First"),
        Player("p2", "Second"),
    )

    @Test
    fun `playerNames joins both names with the identity default`() {
        assertEquals("First & Second", team.playerNames())
    }

    @Test
    fun `playerNames applies the transform to each name individually`() {
        assertEquals("FIRST & SECOND", team.playerNames { it.uppercase() })
    }
}
