package com.cs407.cs407project.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*
import com.google.android.gms.maps.model.LatLng

data class RunUiState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedMillis: Long = 0L,
    val distanceMeters: Double = 0.0,
    val paceSecPerMile: Int? = null, // null until we have movement/time
    val pathPoints: List<LatLng> = emptyList(),
    val currentLocation: LatLng? = null
)

class RunTrackerViewModel(app: Application) : AndroidViewModel(app) {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(app) }
    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
        .setMinUpdateIntervalMillis(800L)
        .setMaxUpdateDelayMillis(1500L)
        .build()

    private var lastLocation: Location? = null
    private var timerJob: Job? = null
    private var baseStartTime: Long = 0L
    private var pausedAccumulated: Long = 0L
    private var pauseStartedAt: Long = 0L

    private val _state = MutableStateFlow(RunUiState())
    val state = _state.asStateFlow()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            onNewLocation(loc)
        }
    }

    fun startRun() {
        if (_state.value.isRunning) return
        baseStartTime = System.currentTimeMillis()
        pausedAccumulated = 0L
        pauseStartedAt = 0L
        lastLocation = null
        _state.value = RunUiState(
            isRunning = true,
            isPaused = false,
            elapsedMillis = 0L,
            distanceMeters = 0.0,
            paceSecPerMile = null,
            pathPoints = emptyList()  // reset path
        )
        startTimer()
        startLocationUpdates()
    }

    fun pauseRun() {
        if (!_state.value.isRunning || _state.value.isPaused) return
        _state.value = _state.value.copy(isPaused = true)
        pauseStartedAt = System.currentTimeMillis()
        stopLocationUpdates()
    }

    fun resumeRun() {
        if (!_state.value.isRunning || !_state.value.isPaused) return
        val now = System.currentTimeMillis()
        pausedAccumulated += (now - pauseStartedAt)
        pauseStartedAt = 0L
        _state.value = _state.value.copy(isPaused = false)
        startLocationUpdates()
    }

    fun stopRun() {
        stopLocationUpdates()
        stopTimer()
        _state.value = _state.value.copy(isRunning = false, isPaused = false)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.isRunning) {
                if (!_state.value.isPaused) {
                    val now = System.currentTimeMillis()
                    val elapsed = now - baseStartTime - pausedAccumulated
                    val newPace = computePaceSecPerMile(elapsed, _state.value.distanceMeters)
                    _state.value = _state.value.copy(elapsedMillis = elapsed, paceSecPerMile = newPace)
                }
                delay(250L)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
    // Test location
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fused.requestLocationUpdates(locationRequest, callback, null)
    }

    private fun stopLocationUpdates() {
        fused.removeLocationUpdates(callback)
    }

    private fun onNewLocation(loc: Location) {
        if (_state.value.isPaused) return

        val newPoint = LatLng(loc.latitude, loc.longitude)

        // Always update current location
        _state.value = _state.value.copy(currentLocation = newPoint)

        val prev = lastLocation
        lastLocation = loc

        if (prev != null) {
            val d = prev.distanceTo(loc).toDouble() // meters
            // Filter tiny jitter (< 1m) to reduce noise
            if (d > 1.0) {
                val total = _state.value.distanceMeters + d
                val newPace = computePaceSecPerMile(_state.value.elapsedMillis, total)

                val newPath = _state.value.pathPoints + newPoint

                _state.value = _state.value.copy(
                    distanceMeters = total,
                    paceSecPerMile = newPace,
                    pathPoints = newPath
                )
            }
        } else {
            // First location - add to path
            _state.value = _state.value.copy(
                pathPoints = listOf(newPoint)
            )
        }
    }

    private fun metersToMiles(meters: Double): Double = meters / 1609.344

    private fun computePaceSecPerMile(elapsedMillis: Long, distanceMeters: Double): Int? {
        val miles = metersToMiles(distanceMeters)
        if (miles <= 0.0) return null
        val sec = elapsedMillis / 1000.0
        val paceSecPerMile = sec / miles
        return paceSecPerMile.roundToInt().coerceAtLeast(0)
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation() {
        fused.lastLocation.addOnSuccessListener { location ->
            location?.let {
                _state.value = _state.value.copy(
                    currentLocation = LatLng(it.latitude, it.longitude)
                )
            }
        }
    }
}