package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PickleballDatabase
import com.example.data.repository.SessionRepository
import com.example.data.repository.toDomainPlayer
import com.example.model.SessionMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Reactive read-only view of all persisted history. Combines three Room flows (sessions, matches,
 * roster), maps entities to DB-free domain, and groups by session in memory. No per-row queries.
 */
class HistoryViewModel @JvmOverloads constructor(
    application: Application,
    private val repositoryOverride: SessionRepository? = null,
) : AndroidViewModel(application) {

    private val repository: SessionRepository by lazy {
        repositoryOverride ?: SessionRepository(PickleballDatabase.getInstance(application).sessionDao())
    }

    val data: StateFlow<HistoryData> =
        combine(
            repository.allSessions,
            repository.allMatches,
            repository.allRosterEntities,
        ) { sessions, matches, rosterEntities ->
            HistoryData.Loaded(
                sessions = sessions.map { SessionMeta(it.id, it.name, it.startTime) },
                matchesBySession = matches.groupBy { it.sessionId },
                rosterBySession = rosterEntities
                    .groupBy { it.sessionId }
                    .mapValues { (_, rows) -> rows.map { it.toDomainPlayer() } },
            ) as HistoryData
        }
            .flowOn(Dispatchers.Default)
            .catch { emit(HistoryData.Error(it.message ?: "Failed to load history")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryData.Loading)
}
