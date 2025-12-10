package com.cs407.cs407project.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class StrengthExercise(
    val name: String,
    val sets: Int,
    val reps: Int,
    val restSec: Int,
    val weightLbs: Int = 0
)

data class StrengthWorkout(
    val title: String,
    val timestampMs: Long,
    val exercises: List<StrengthExercise>
)

object StrengthWorkoutRepository {
    private val _workouts = MutableStateFlow<List<StrengthWorkout>>(emptyList())
    val workouts: StateFlow<List<StrengthWorkout>> = _workouts

    /** Append a new workout locally. */
    fun add(workout: StrengthWorkout) {
        _workouts.value = _workouts.value + workout
    }

    /** Replace all workouts with Firestore data. */
    fun overwriteAll(newWorkouts: List<StrengthWorkout>) {
        _workouts.value = newWorkouts
    }

    /** Clear all workouts (logout / user switch). */
    fun clear() {
        _workouts.value = emptyList()
    }
}