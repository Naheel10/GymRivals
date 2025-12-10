package com.cs407.cs407project.ui.track

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.cs407project.viewmodel.RunTrackerViewModel
import com.cs407.cs407project.viewmodel.RunUiState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.cs407.cs407project.data.GymRivalsCloudRepository
import com.cs407.cs407project.data.RunHistoryRepository
import com.cs407.cs407project.data.RunEntry
import com.cs407.cs407project.data.RunPathPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState

@Composable
fun TrackRunScreen(
    onBack: () -> Unit,
    runVm: RunTrackerViewModel = viewModel()
) {
    val state by runVm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val gradient = Brush.horizontalGradient(
        listOf(Color(0xFF0EA5E9), Color(0xFF7C3AED))
    )

    // Runtime permissions launcher
    val permissions = remember {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        if (granted) {
            runVm.startRun() // Actually start the run after permission granted
        }
    }

    // Check if we already have permission and get location
    LaunchedEffect(Unit) {
        val hasPermission = permissions.all { permission ->
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            runVm.getCurrentLocation()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(Color(0xFFF6F7FB))
    ) {
        // Header with Back
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "‹ Back",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .noRippleClickable { onBack() }
                )
                Column {
                    Text("Track Run", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Pace • Distance • Time", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ----------------- Map with route visualization -----------------
        // Show map always, not just when pathPoints exist
        val currentLocation = if (state.pathPoints.isNotEmpty()) {
            state.pathPoints.last()
        } else {
            state.currentLocation ?: LatLng(43.0731, -89.4012) // Default to Madison
        }

        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(currentLocation, 16f)
        }

        // Follow the user as new points come in
        LaunchedEffect(currentLocation) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(currentLocation, 16f),
                durationMs = 500
            )
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                // Show polyline only when running and have multiple points
                if (state.pathPoints.size > 1) {
                    Polyline(
                        points = state.pathPoints,
                        width = 12f,
                        color = Color(0xFF3B82F6) // Blue route line
                    )
                }

                // Show markers when running
                if (state.pathPoints.isNotEmpty()) {
                    // Start marker
                    Marker(
                        state = MarkerState(position = state.pathPoints.first()),
                        title = "Start"
                    )
                    // Current position marker (only if we have more than one point)
                    if (state.pathPoints.size > 1) {
                        Marker(
                            state = MarkerState(position = state.pathPoints.last()),
                            title = "Current Location"
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stat tiles
        Row(Modifier.padding(horizontal = 16.dp)) {
            StatTile(
                title = "Pace",
                value = formatPace(state),
                subtitle = "min/mi",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            StatTile(
                title = "Distance",
                value = formatMiles(state.distanceMeters),
                subtitle = "miles",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 16.dp)) {
            StatTile(
                title = "Time",
                value = formatElapsed(state.elapsedMillis),
                subtitle = "",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            StatTile(
                title = "Status",
                value = when {
                    state.isRunning && !state.isPaused -> "Running"
                    state.isRunning && state.isPaused -> "Paused"
                    else -> "Idle"
                },
                subtitle = "",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))

        // Controls
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.isRunning) {
                PrimaryGradientButton(text = "Start") {
                    launcher.launch(permissions)
                }
            } else {
                if (!state.isPaused) {
                    SecondaryButton(text = "Pause") { runVm.pauseRun() }
                } else {
                    SecondaryButton(text = "Resume") { runVm.resumeRun() }
                }
                DestructiveButton(text = "Finish") {
                    // Create a summary entry and save it
                    val now = System.currentTimeMillis()

                    // Convert LatLng path from the ViewModel to our domain RunPathPoint list
                    val path = state.pathPoints.map { latLng ->
                        RunPathPoint(
                            lat = latLng.latitude,
                            lng = latLng.longitude
                        )
                    }

                    val entry = RunEntry(
                        timestampMs = now,
                        distanceMeters = state.distanceMeters,
                        elapsedMillis = state.elapsedMillis,
                        path = path
                    )

                    // 1) Save locally
                    RunHistoryRepository.addRun(entry)

                    // 2) Save to Firestore
                    GymRivalsCloudRepository.addRun(entry)

                    runVm.stopRun()
                    onBack()
                }
            }
        }
    }
}

/* ------- UI atoms (same style language as the rest of your app) ------- */

@Composable
private fun StatTile(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color(0xFF6B7280), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Color(0xFF6B7280), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PrimaryGradientButton(text: String, onClick: () -> Unit) {
    val brush = Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))
    Box(
        modifier = Modifier
            .height(52.dp)
            .background(brush, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun DestructiveButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
    ) { Text(text, fontWeight = FontWeight.SemiBold, color = Color.White) }
}

// noRippleClickable helper
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(indication = null, interactionSource = MutableInteractionSource()) { onClick() })

/* -------------------- Formatters -------------------- */
private fun formatMiles(meters: Double): String = "%.2f".format((meters / 1609.344).coerceAtLeast(0.0))
private fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).toInt().coerceAtLeast(0)
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
private fun formatPace(state: RunUiState): String {
    val p = state.paceSecPerMile ?: return "--:--"
    val min = p / 60; val sec = p % 60
    return "%d:%02d".format(min, sec)
}