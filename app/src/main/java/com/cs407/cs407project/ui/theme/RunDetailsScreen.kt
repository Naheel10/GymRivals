package com.cs407.cs407project.ui.track

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.cs407project.data.RunHistoryRepository
import com.cs407.cs407project.data.RunEntry
import com.cs407.cs407project.data.RunPathPoint
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RunDetailScreen(
    runTimestampMs: Long,
    onBack: () -> Unit
) {
    val runs by RunHistoryRepository.runs.collectAsState()
    val run = runs.firstOrNull { it.timestampMs == runTimestampMs }

    val headerBrush = Brush.horizontalGradient(
        listOf(Color(0xFF0EA5E9), Color(0xFF7C3AED))
    )

    if (run == null) {
        // Fallback if we somehow don't find it
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Run not found", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text("Try going back and re-opening the run.", fontSize = 14.sp, color = Color(0xFF6B7280))
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBack) {
                    Text("‹ Back")
                }
            }
        }
        return
    }

    val pathLatLng: List<LatLng> = run.path.map { LatLng(it.lat, it.lng) }
    val startCameraPosition = if (pathLatLng.isNotEmpty()) {
        CameraPositionState(
            position = CameraPosition.fromLatLngZoom(pathLatLng.first(), 15f)
        )
    } else {
        // No path recorded (old runs) – just show a default map zoomed out
        CameraPositionState(
            position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 1f)
        )
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 4.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBrush)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column {
                        TextButton(onClick = onBack) {
                            Text("‹ Back", color = Color.White, fontSize = 16.sp)
                        }
                        Text(
                            text = "Run Details",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatRunDate(run.timestampMs),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Map with route
            if (pathLatLng.isNotEmpty()) {
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(16.dp),
                    cameraPositionState = startCameraPosition
                ) {
                    Polyline(
                        points = pathLatLng,
                        width = 8f,
                        color = Color(0xFF3B82F6)
                    )
                    // Start + end markers
                    Marker(
                        state = MarkerState(position = pathLatLng.first()),
                        title = "Start"
                    )
                    if (pathLatLng.size > 1) {
                        Marker(
                            state = MarkerState(position = pathLatLng.last()),
                            title = "Finish"
                        )
                    }
                }
            } else {
                // No recorded path (older runs)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No route recorded for this run.",
                        color = Color(0xFF6B7280),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Stat tiles like TrackRunScreen
            Row(Modifier.padding(horizontal = 16.dp)) {
                StatTile(
                    title = "Pace",
                    value = formatPace(run.avgPaceSecPerMile),
                    subtitle = "min/mi",
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                StatTile(
                    title = "Distance",
                    value = formatMiles(run.miles),
                    subtitle = "miles",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = 16.dp)) {
                StatTile(
                    title = "Time",
                    value = formatElapsed(run.elapsedMillis),
                    subtitle = "",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/* ---- Helpers (copied/adapted from TrackRunScreen style) ---- */

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

private fun formatMiles(miles: Double): String = "%.2f".format(miles.coerceAtLeast(0.0))

private fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatPace(paceSecPerMile: Int?): String {
    if (paceSecPerMile == null || paceSecPerMile <= 0) return "--:--"
    val m = paceSecPerMile / 60
    val s = paceSecPerMile % 60
    return "%d:%02d".format(m, s)
}

private fun formatRunDate(ms: Long): String {
    val fmt = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return fmt.format(Date(ms))
}