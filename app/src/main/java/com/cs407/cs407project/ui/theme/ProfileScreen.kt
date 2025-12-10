package com.cs407.cs407project.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.cs407project.data.GymRivalsCloudRepository
import com.cs407.cs407project.data.GymRivalsCloudRepository.UserProfile
import com.cs407.cs407project.data.RepCountRepository
import com.cs407.cs407project.data.RepSession
import com.cs407.cs407project.data.RunEntry
import com.cs407.cs407project.data.RunHistoryRepository
import com.cs407.cs407project.data.StrengthWorkout
import com.cs407.cs407project.data.StrengthWorkoutRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    onSettingsClick: () -> Unit,
) {
    // ----------------------
    // FirebaseAuth basics
    // ----------------------
    val firebaseUser = FirebaseAuth.getInstance().currentUser
    val email = firebaseUser?.email ?: "guest@gymrivals.app"
    val currentUid = firebaseUser?.uid

    // ----------------------
    // Firestore Profile State
    // ----------------------
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var loading by remember { mutableStateOf(true) }

    // ----------------------
    // Firestore listener
    // ----------------------
    DisposableEffect(Unit) {
        var registration: ListenerRegistration? = null

        registration = GymRivalsCloudRepository.listenUserProfile { loaded ->
            profile = loaded
            loading = false
        }

        onDispose {
            registration?.remove()
        }
    }

    // ----------------------
    // Workout data (local repos kept in sync via UserDataSyncEffect)
    // ----------------------
    val runs by RunHistoryRepository.runs.collectAsState()
    val lifts by StrengthWorkoutRepository.workouts.collectAsState()
    val repSessions by RepCountRepository.sessions.collectAsState()

    // ----------------------
    // Derived stats for badges
    // ----------------------
    val weekStartMs = remember { currentWeekStartMs() }

    // Weekly miles from runs
    val myMilesThisWeek by remember(runs, weekStartMs) {
        mutableStateOf(
            runs
                .filter { it.timestampMs >= weekStartMs }
                .sumOf { it.miles }
        )
    }

    // Weekly AI push-ups & squats from rep sessions
    val myPushupsThisWeek by remember(repSessions, weekStartMs) {
        mutableStateOf(
            totalRepsForWeek(repSessions, weekStartMs, "Push up")
        )
    }

    val mySquatsThisWeek by remember(repSessions, weekStartMs) {
        mutableStateOf(
            totalRepsForWeek(repSessions, weekStartMs, "Squat")
        )
    }

    // 7-day streak based on any activity (run / strength / bodyweight)
    val dayStreak = remember(runs, lifts, repSessions) {
        computeDayStreak(runs, lifts, repSessions)
    }

    // ----------------------
    // Rivals-based achievements (multi-user)
    // ----------------------
    var rivalsParticipated by remember { mutableStateOf(false) }
    var rivalsWon by remember { mutableStateOf(false) }

    DisposableEffect(currentUid, weekStartMs, myMilesThisWeek, myPushupsThisWeek, mySquatsThisWeek) {
        if (currentUid == null) {
            rivalsParticipated = false
            rivalsWon = false
            return@DisposableEffect onDispose {}
        }

        var runReg: ListenerRegistration? = null
        var pushReg: ListenerRegistration? = null
        var squatReg: ListenerRegistration? = null

        // Listen to weekly run leaderboard
        runReg = GymRivalsCloudRepository.listenWeeklyRunLeaderboard(weekStartMs) { entries ->
            val you = entries.firstOrNull { it.userId == currentUid }
            if (you != null || myMilesThisWeek > 0.0) {
                rivalsParticipated = true
            }
            val maxMiles = entries.maxOfOrNull { it.totalMiles } ?: 0.0
            if (you != null && you.totalMiles > 0.0 && you.totalMiles == maxMiles) {
                rivalsWon = true
            }
        }

        // Weekly AI push-up leaderboard
        pushReg = GymRivalsCloudRepository.listenWeeklyRepLeaderboard(
            weekStartMs = weekStartMs,
            exerciseType = "PUSH_UP"
        ) { entries ->
            val you = entries.firstOrNull { it.userId == currentUid }
            if (you != null || myPushupsThisWeek > 0) {
                rivalsParticipated = true
            }
            val maxReps = entries.maxOfOrNull { it.totalReps } ?: 0
            if (you != null && you.totalReps > 0 && you.totalReps == maxReps) {
                rivalsWon = true
            }
        }

        // Weekly AI squat leaderboard
        squatReg = GymRivalsCloudRepository.listenWeeklyRepLeaderboard(
            weekStartMs = weekStartMs,
            exerciseType = "SQUAT"
        ) { entries ->
            val you = entries.firstOrNull { it.userId == currentUid }
            if (you != null || mySquatsThisWeek > 0) {
                rivalsParticipated = true
            }
            val maxReps = entries.maxOfOrNull { it.totalReps } ?: 0
            if (you != null && you.totalReps > 0 && you.totalReps == maxReps) {
                rivalsWon = true
            }
        }

        onDispose {
            runReg?.remove()
            pushReg?.remove()
            squatReg?.remove()
        }
    }

    // ----------------------
    // Build badge list
    // ----------------------
    val badges = remember(
        runs,
        lifts,
        repSessions,
        dayStreak,
        rivalsParticipated,
        rivalsWon
    ) {
        listOf(
            AchievementBadge(
                id = "first_rivals_participation",
                title = "Joined Rivals",
                description = "Participate in your first weekly Rivals challenge.",
                emoji = "⚔️",
                unlocked = rivalsParticipated
            ),
            AchievementBadge(
                id = "first_rivals_win",
                title = "Rivals Winner",
                description = "Place 1st in any weekly Rivals category.",
                emoji = "👑",
                unlocked = rivalsWon
            ),
            AchievementBadge(
                id = "log_run",
                title = "First Run Logged",
                description = "Track your first run with GymRivals.",
                emoji = "🏃",
                unlocked = runs.isNotEmpty()
            ),
            AchievementBadge(
                id = "log_workout",
                title = "Strength Starter",
                description = "Save your first strength workout.",
                emoji = "🏋️",
                unlocked = lifts.isNotEmpty()
            ),
            AchievementBadge(
                id = "log_bodyweight",
                title = "Bodyweight Grinder",
                description = "Complete your first AI bodyweight session.",
                emoji = "🤸",
                unlocked = repSessions.isNotEmpty()
            ),
            AchievementBadge(
                id = "streak_7",
                title = "7-Day Streak",
                description = "Log activity 7 days in a row.",
                emoji = "🔥",
                unlocked = dayStreak >= 7
            )
        )
    }
    val unlockedBadgeCount = badges.count { it.unlocked }
    // ----------------------
    // Display name + initials fallback
    // ----------------------
    val displayName =
        profile?.displayName
            ?: firebaseUser?.displayName
            ?: email.substringBefore("@")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    val initials =
        displayName.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "GR" }

    val handle =
        profile?.handle ?: "@${email.substringBefore("@")}"

    val totalPoints = profile?.totalPoints ?: 0
    //val achievementsCount = profile?.achievements ?: 0
    val rivalsCount = profile?.rivals ?: 0

    val headerGradient = Brush.horizontalGradient(
        listOf(Color(0xFF0EA5E9), Color(0xFF7C3AED))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(Color(0xFFF6F7FB))
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerGradient)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GymRivals",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Track. Compete. Dominate.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = onSettingsClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Top profile card
        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val avatarGradient = Brush.verticalGradient(
                    listOf(Color(0xFF38BDF8), Color(0xFF6366F1))
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(avatarGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(10.dp))

                if (loading) {
                    Text(
                        text = "Loading...",
                        fontSize = 16.sp,
                        color = Color(0xFF6B7280)
                    )
                } else {
                    Text(
                        text = displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF111827)
                    )
                    Text(
                        text = handle,
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Stats (from Firestore)
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Total Points",
                value = totalPoints.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Badges",
                value = unlockedBadgeCount.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Rivals",
                value = rivalsCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(18.dp))

        // Achievements / Badges – now data-driven
        SectionCard(
            title = "Badges",
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "${badges.count { it.unlocked }} / ${badges.size} unlocked",
                fontSize = 12.sp,
                color = Color(0xFF6B7280)
            )
            Spacer(Modifier.height(8.dp))
            BadgeGrid(badges = badges)
        }

        Spacer(Modifier.height(18.dp))

        // Mock goals — can later be stored per user.
        SectionCard(
            title = "Current Goals",
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            GoalRow("Bench Press 225 lbs", 0.85f, "85%")
            Spacer(Modifier.height(10.dp))
            GoalRow("Run 10 miles per week", 0.6f, "60%")
            Spacer(Modifier.height(10.dp))
            GoalRow("3 Gym Sessions / week", 0.4f, "40%")
        }

        Spacer(Modifier.height(18.dp))
    }
}

// ---------------------------------------------------------------------------
// UI helpers
// ---------------------------------------------------------------------------

@Composable
private fun AccountRow(
    title: String,
    subtitle: String,
    isDestructive: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (isDestructive) Color(0xFFDC2626) else Color(0xFF111827)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF9FAFB))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF6B7280)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = Color(0xFF6B7280)
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827)
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

// -------------------- Badges UI --------------------

private data class AchievementBadge(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val unlocked: Boolean
)

@Composable
private fun BadgeGrid(badges: List<AchievementBadge>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        badges.chunked(2).forEach { rowBadges ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowBadges.forEach { badge ->
                    BadgeTile(
                        badge = badge,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowBadges.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(
    badge: AchievementBadge,
    modifier: Modifier = Modifier
) {
    val bgColor = if (badge.unlocked) Color(0xFFECFDF5) else Color(0xFFF3F4F6)
    val borderColor = if (badge.unlocked) Color(0xFF10B981) else Color(0xFFE5E7EB)
    val titleColor = if (badge.unlocked) Color(0xFF065F46) else Color(0xFF6B7280)
    val subtitleColor = if (badge.unlocked) Color(0xFF047857) else Color(0xFF9CA3AF)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = badge.emoji,
                fontSize = 22.sp
            )
            Text(
                text = badge.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Text(
                text = if (badge.unlocked) "Unlocked · ${badge.description}" else "Locked · ${badge.description}",
                fontSize = 11.sp,
                color = subtitleColor
            )
        }
    }
}

@Composable
private fun GoalRow(title: String, progress: Float, label: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF111827)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color(0xFF6B7280)
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = Color(0xFF6366F1),
            trackColor = Color(0xFFE5E7EB)
        )
    }
}

// ---------------------------------------------------------------------------
// Shared helpers (mirrors logic from other screens)
// ---------------------------------------------------------------------------

/**
 * Compute current week start (Monday 00:00) in ms.
 */
private fun currentWeekStartMs(): Long {
    val cal = Calendar.getInstance().apply {
        // Force Monday as first day of week
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }
    return cal.timeInMillis
}

/**
 * Total reps for a given exercise name within the current week.
 */
private fun totalRepsForWeek(
    sessions: List<RepSession>,
    weekStartMs: Long,
    exerciseName: String
): Int {
    return sessions
        .filter { it.timestampMs >= weekStartMs && it.exerciseType.equals(exerciseName, ignoreCase = true) }
        .sumOf { it.totalReps }
}

/**
 * Day streak logic: count consecutive days (ending today) where the user did
 * at least one run, strength workout, or rep session.
 */
private fun computeDayStreak(
    runs: List<RunEntry>,
    lifts: List<StrengthWorkout>,
    repSessions: List<RepSession>
): Int {
    // Build a set of all dates (yyyy-MM-dd) where the user had any activity
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val activeDates = mutableSetOf<String>()

    runs.forEach { run ->
        activeDates.add(sdf.format(Date(run.timestampMs)))
    }
    lifts.forEach { workout ->
        activeDates.add(sdf.format(Date(workout.timestampMs)))
    }
    repSessions.forEach { session ->
        activeDates.add(sdf.format(Date(session.timestampMs)))
    }

    if (activeDates.isEmpty()) return 0

    // Walk backwards from today, counting consecutive active days
    var streak = 0
    val cal = Calendar.getInstance()

    while (true) {
        val key = sdf.format(cal.time)
        if (activeDates.contains(key)) {
            streak += 1
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            break
        }
    }

    return streak
}
