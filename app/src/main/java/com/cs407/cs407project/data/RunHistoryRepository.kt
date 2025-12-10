package com.cs407.cs407project.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A single GPS point in a run.
 */
data class RunPathPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

/**
 * Summary of a run + its recorded route.
 */
data class RunEntry(
    val timestampMs: Long,              // when the run finished
    val distanceMeters: Double,         // total distance
    val elapsedMillis: Long,            // total time
    val path: List<RunPathPoint> = emptyList()  // full GPS route in order
) {
    val miles: Double get() = distanceMeters / 1609.344
    val avgPaceSecPerMile: Int?
        get() {
            if (miles <= 0.0) return null
            val sec = elapsedMillis / 1000.0
            return (sec / miles).toInt().coerceAtLeast(0)
        }
}

object RunHistoryRepository {
    private val _runs = MutableStateFlow<List<RunEntry>>(emptyList())
    val runs: StateFlow<List<RunEntry>> = _runs

    /** Append a run locally (when you finish a run). */
    fun addRun(entry: RunEntry) {
        _runs.value = _runs.value + entry
    }

    /** Replace all runs with a new list from Firestore. */
    fun overwriteAll(newRuns: List<RunEntry>) {
        _runs.value = newRuns
    }

    /** Clear all runs (logout / user switch). */
    fun clear() {
        _runs.value = emptyList()
    }
}