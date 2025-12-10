package com.cs407.cs407project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.cs407project.data.RepCountRepository
import com.cs407.cs407project.data.RepSession
import com.cs407.cs407project.data.RunEntry
import com.cs407.cs407project.data.RunHistoryRepository
import com.cs407.cs407project.data.StrengthWorkout
import com.cs407.cs407project.data.StrengthWorkoutRepository
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun GymRivalsHomeScreen(
    onRunClick: (Long) -> Unit = {} // NEW: callback when a run row is tapped
) {
    val appGradient = Brush.horizontalGradient(
        listOf(Color(0xFF0EA5E9), Color(0xFF7C3AED))
    )

    // ---- Current user display name ----
    val firebaseUser = FirebaseAuth.getInstance().currentUser
    val email = firebaseUser?.email ?: "guest@gymrivals.app"
    val displayName = firebaseUser?.displayName
        ?.takeIf { it.isNotBlank() }
        ?: email.substringBefore("@")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    // ---- Real workout data from repositories ----
    val runs by RunHistoryRepository.runs.collectAsState()
    val lifts by StrengthWorkoutRepository.workouts.collectAsState()
    val repSessions by RepCountRepository.sessions.collectAsState()

    val recentItems = remember(runs, lifts, repSessions) {
        buildRecentWorkoutItems(runs, lifts, repSessions)
            .sortedByDescending { it.timestampMs }
            .take(3) // show the 3 most recent
    }

    // Day streak = consecutive days (ending today) with any workout
    val dayStreak = remember(runs, lifts, repSessions) {
        computeDayStreak(runs, lifts, repSessions)
    }

    Surface(color = Color(0xFFF6F7FB)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Top app bar area (gradient)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appGradient)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Tiny logo chip (emoji so no icon deps)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("🏋️", fontSize = 16.sp) }

                        Spacer(Modifier.width(8.dp))
                        Text(
                            "GymRivals",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "Track. Compete. Dominate.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }
            }

            // Scrollable content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                // Welcome gradient card
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    "Welcome back, $displayName! 💪",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                val streakLine = if (dayStreak > 0) {
                                    "You’re on a $dayStreak day streak. Keep it up!"
                                } else {
                                    "No streak yet — log a workout today to start one!"
                                }
                                Text(
                                    streakLine,
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Stats grid (still mocked for now)
                item { Spacer(Modifier.height(10.dp)) }
                item {
                    StatsGrid(
                        listOf(
                            StatCardData("🔥", "1,250", "Weekly Points", Color(0xFFFFEDD5)),
                            StatCardData("📅", dayStreak.toString(), "Day Streak", Color(0xFFEFFDEE)),
                            StatCardData("🧩", "4", "Workouts", Color(0xFFEFF6FF)),
                            StatCardData("💓", "2,800", "Calories", Color(0xFFFFF1F2))
                        )
                    )
                }

                // Recent workouts (real data)
                item { Spacer(Modifier.height(10.dp)) }
                item {
                    SectionCard(title = "Recent Workouts") {
                        if (recentItems.isEmpty()) {
                            Text(
                                "No workouts yet. Start a run, strength workout, or rep session to see it here.",
                                color = Color(0xFF6B7280),
                                fontSize = 13.sp
                            )
                        } else {
                            recentItems.forEachIndexed { index, item ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = Color(0xFFE8ECF3)
                                    )
                                }
                                RecentWorkoutRow(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    points = item.points,
                                    onClick = if (item.type == RecentWorkoutType.RUN) {
                                        { onRunClick(item.timestampMs) }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

/* ---------------- Components (same visuals as before) ---------------- */

private data class StatCardData(
    val emoji: String,
    val value: String,
    val label: String,
    val badgeColor: Color
)

@Composable
private fun StatsGrid(items: List<StatCardData>) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            StatCard(items[0], Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            StatCard(items[1], Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            StatCard(items[2], Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            StatCard(items[3], Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(item: StatCardData, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(item.badgeColor),
                contentAlignment = Alignment.Center
            ) { Text(item.emoji, fontSize = 18.sp) }

            Spacer(Modifier.height(8.dp))
            Text(item.value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
            Text(item.label, fontSize = 12.sp, color = Color(0xFF6B7280))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF111827))
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun RecentWorkoutRow(
    title: String,
    subtitle: String,
    points: Int,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier.clickable { onClick() }
            } else {
                Modifier
            }
        )
        .padding(vertical = 10.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
            Text(
                subtitle,
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        PointsPill(points)
    }
}

@Composable
private fun PointsPill(points: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, Color(0xFFDDE3ED), RoundedCornerShape(999.dp))
            .background(Color(0xFFF3F6FB))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("+$points pts", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
    }
}

/* ---------------- Recent-workout feed helpers ---------------- */

private enum class RecentWorkoutType { RUN, STRENGTH, REPS }

private data class RecentWorkoutItem(
    val timestampMs: Long,
    val title: String,
    val subtitle: String,
    val points: Int,
    val type: RecentWorkoutType
)

/**
 * Build a unified recent-workouts feed from runs, strength workouts,
 * and rep-counter sessions.
 */
private fun buildRecentWorkoutItems(
    runs: List<RunEntry>,
    lifts: List<StrengthWorkout>,
    repSessions: List<RepSession>
): List<RecentWorkoutItem> {
    val items = mutableListOf<RecentWorkoutItem>()

    // Runs
    runs.forEach { run ->
        val miles = run.miles
        val milesStr = "%.2f mi".format(miles)
        val whenStr = formatShortDate(run.timestampMs)
        val subtitle = "$whenStr • $milesStr"

        // Simple points: 100 per mile
        val points = (miles * 100).toInt().coerceAtLeast(0)

        items += RecentWorkoutItem(
            timestampMs = run.timestampMs,
            title = "Run",
            subtitle = subtitle,
            points = points,
            type = RecentWorkoutType.RUN
        )
    }

    // Strength workouts
    lifts.forEach { workout ->
        val exerciseCount = workout.exercises.size
        val totalReps = workout.exercises.sumOf { it.sets * it.reps }
        val whenStr = formatShortDate(workout.timestampMs)
        val subtitle = "$whenStr • $exerciseCount exercises"

        items += RecentWorkoutItem(
            timestampMs = workout.timestampMs,
            title = workout.title.ifBlank { "Strength Workout" },
            subtitle = subtitle,
            points = totalReps, // use total reps as points
            type = RecentWorkoutType.STRENGTH
        )
    }

    // Rep-counter sessions (AI bodyweight)
    repSessions.forEach { session ->
        val whenStr = formatShortDate(session.timestampMs)
        val subtitle = "$whenStr • ${session.totalReps} reps"

        items += RecentWorkoutItem(
            timestampMs = session.timestampMs,
            title = session.exerciseType.ifBlank { "Bodyweight Session" },
            subtitle = subtitle,
            points = session.totalReps,
            type = RecentWorkoutType.REPS
        )
    }

    return items
}

/**
 * Compute consecutive-day streak ending today.
 * A "workout day" is any day with at least one run, strength workout, or rep session.
 */
private fun computeDayStreak(
    runs: List<RunEntry>,
    lifts: List<StrengthWorkout>,
    repSessions: List<RepSession>
): Int {
    if (runs.isEmpty() && lifts.isEmpty() && repSessions.isEmpty()) return 0

    val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val workoutDays = HashSet<String>()

    runs.forEach { workoutDays += fmt.format(Date(it.timestampMs)) }
    lifts.forEach { workoutDays += fmt.format(Date(it.timestampMs)) }
    repSessions.forEach { workoutDays += fmt.format(Date(it.timestampMs)) }

    val cal = Calendar.getInstance()
    var streak = 0

    while (true) {
        val key = fmt.format(cal.time)
        if (workoutDays.contains(key)) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            break
        }
    }

    return streak
}

private fun formatShortDate(ms: Long): String {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    return fmt.format(Date(ms))
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PreviewGymRivalsHome() {
    MaterialTheme { GymRivalsHomeScreen() }
}