package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CompletedMatchEntity
import com.example.data.local.PickleballDatabase
import com.example.data.local.SessionDao
import com.example.data.local.SessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomDatabaseTest {

    private lateinit var db: PickleballDatabase
    private lateinit var dao: SessionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PickleballDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.sessionDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeAndReadSession() = runBlocking {
        val session = SessionEntity(
            id = "sess_1",
            name = "Saturday Open Play",
            startTime = System.currentTimeMillis(),
            rotationPolicy = "FOUR_OFF_FOUR_ON",
            consecutiveGameCap = 2,
            targetScore = 11,
            isPaused = false,
            isCompleted = false
        )

        dao.insertSession(session)
        val loaded = dao.getSessionById("sess_1")

        assertNotNull(loaded)
        assertEquals("Saturday Open Play", loaded?.name)
        assertEquals(2, loaded?.consecutiveGameCap)
    }

    @Test
    fun writeAndReadCompletedMatch() = runBlocking {
        val match = CompletedMatchEntity(
            matchId = "m_1",
            sessionId = "sess_1",
            courtId = 1,
            teamAPlayer1 = "Alice",
            teamAPlayer2 = "Bob",
            teamBPlayer1 = "Charlie",
            teamBPlayer2 = "Dave",
            scoreA = 11,
            scoreB = 8,
            winnerTeam = "TEAM_A",
            startTime = 1000L,
            endTime = 2000L
        )

        dao.insertCompletedMatch(match)
        val matches = dao.getMatchesForSession("sess_1")

        assertEquals(1, matches.size)
        assertEquals(11, matches[0].scoreA)
        assertEquals("Alice", matches[0].teamAPlayer1)
    }
}
