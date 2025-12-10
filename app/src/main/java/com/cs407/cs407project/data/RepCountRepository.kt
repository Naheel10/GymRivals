package com.cs407.cs407project.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a single rep-counting session
 *
 * @property exerciseType The type of exercise performed (e.g., "Push up", "Squat")
 * @property totalReps Total number of reps counted during the session
 * @property timestampMs Timestamp when the session was completed (Unix epoch milliseconds)
 * @property durationSeconds Duration of the session in seconds
 */
data class RepSession(
    val exerciseType: String,
    val totalReps: Int,
    val timestampMs: Long,
    val durationSeconds: Int
)

/**
 * Simple in-memory repository for rep-counting sessions.
 *
 * Backed by a StateFlow<List<RepSession>> so UI (like ProgressScreen) can observe changes.
 */
object RepCountRepository {

    // Backing state for all sessions
    private val _sessions = MutableStateFlow<List<RepSession>>(emptyList())

    /**
     * Public read-only stream of all sessions.
     * Usage: val repSessions by RepCountRepository.sessions.collectAsState()
     */
    val sessions: StateFlow<List<RepSession>> = _sessions

    /**
     * Add a new rep-counting session to the repository.
     *
     * Called from RepCounterViewModel.stopCounting().
     */
    fun add(session: RepSession) {
        _sessions.value = _sessions.value + session
    }

    /**
     * Replace all sessions (handy if you later sync from Firestore).
     */
    fun overwriteAll(newSessions: List<RepSession>) {
        _sessions.value = newSessions
    }

    /**
     * Clear all sessions (for debugging or logout behavior).
     */
    fun clear() {
        _sessions.value = emptyList()
    }

    /**
     * Get all sessions currently in memory.
     */
    fun getAllSessions(): List<RepSession> {
        return _sessions.value
    }

    /**
     * Gets all sessions of a specific exercise type.
     *
     * @param exerciseType The exercise type to filter by (must match stored string)
     */
    fun getSessionsByType(exerciseType: String): List<RepSession> {
        return _sessions.value.filter { it.exerciseType == exerciseType }
    }

    /**
     * Gets the last N sessions for a specific exercise type.
     *
     * @param exerciseType The exercise type to filter by
     * @param count The number of recent sessions to return
     * @return List of the most recent RepSessions for the specified exercise type
     */
    fun getRecentSessions(exerciseType: String, count: Int): List<RepSession> {
        return _sessions.value
            .filter { it.exerciseType == exerciseType }
            .sortedByDescending { it.timestampMs }
            .take(count)
    }
}