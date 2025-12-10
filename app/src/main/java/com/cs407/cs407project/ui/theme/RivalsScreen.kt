package com.cs407.cs407project.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.cs407project.data.GymRivalsCloudRepository
import com.cs407.cs407project.data.RepCountRepository
import com.cs407.cs407project.data.RepSession
import com.cs407.cs407project.data.RunHistoryRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Locale

@Composable
fun RivalsScreen() {
    val headerGradient = Brush.horizontalGradient(
        listOf(Color(0xFF0EA5E9), Color(0xFF7C3AED))
    )

    // All runs and rep sessions for this user
    val runs by RunHistoryRepository.runs.collectAsState()
    val repSessions by RepCountRepository.sessions.collectAsState()

    val firebaseUser = FirebaseAuth.getInstance().currentUser
    val currentUid = firebaseUser?.uid
    val email = firebaseUser?.email ?: "user@gymrivals.app"
    val displayName = firebaseUser?.displayName ?: email.substringBefore("@")

    // Start of current week (Monday 00:00)
    val weekStartMs = remember { currentWeekStartMs() }

    // ---------------- Tabs ----------------
    val tabs = listOf("Running", "Push-ups (AI)", "Squats (AI)")
    var tabIndex by rememberSaveable { mutableStateOf(0) }

    val headerSubtitle = when (tabIndex) {
        0 -> "Weekly distance leaderboard"
        1 -> "Weekly AI push-up leaderboard"
        else -> "Weekly AI squat leaderboard"
    }

    // ----- Running miles this week -----
    val myMilesThisWeek by remember(runs, weekStartMs) {
        mutableStateOf(
            runs
                .filter { it.timestampMs >= weekStartMs }
                .sumOf { it.miles }
        )
    }

    // Multi-user leaderboard entries from Firestore (runs)
    var runLeaderboardEntries by remember {
        mutableStateOf<List<GymRivalsCloudRepository.WeeklyRunLeaderboardEntry>>(emptyList())
    }

    // Push your weekly miles into the shared leaderboard whenever they change
    LaunchedEffect(currentUid, myMilesThisWeek, weekStartMs) {
        if (currentUid != null && myMilesThisWeek > 0.0) {
            GymRivalsCloudRepository.updateWeeklyRunLeaderboard(
                weekStartMs = weekStartMs,
                totalMiles = myMilesThisWeek
            )
        }
    }

    // Listen for weekly run leaderboard updates
    // (other users' runs)
    DisposableEffect(weekStartMs) {
        val runReg: ListenerRegistration? =
            GymRivalsCloudRepository.listenWeeklyRunLeaderboard(weekStartMs) { list ->
                runLeaderboardEntries = list
            }

        onDispose {
            runReg?.remove()
        }
    }

    // Build UI models for running
    val combinedRunEntries = remember(runLeaderboardEntries, currentUid, myMilesThisWeek) {
        val list = runLeaderboardEntries.toMutableList()
        if (currentUid != null &&
            myMilesThisWeek > 0.0 &&
            list.none { it.userId == currentUid }
        ) {
            list += GymRivalsCloudRepository.WeeklyRunLeaderboardEntry(
                userId = currentUid,
                displayName = displayName,
                weekStartMs = weekStartMs,
                totalMiles = myMilesThisWeek
            )
        }
        list.sortedByDescending { it.totalMiles }
    }

    val runUiEntries = combinedRunEntries.map { e ->
        val isYou = e.userId == currentUid
        RivalEntry(
            id = e.userId,
            initials = initialsFromName(e.displayName),
            name = if (isYou) "You (${e.displayName})" else e.displayName,
            miles = e.totalMiles
        )
    }

    val yourRunIndex = combinedRunEntries.indexOfFirst { it.userId == currentUid }
    val yourRunRank = if (yourRunIndex >= 0) yourRunIndex + 1 else 0
    val runParticipants = combinedRunEntries.size
    val youRunEntry = runUiEntries.firstOrNull { it.id == currentUid }

    val runChallengeData = ChallengeData(
        title = "Distance Runners",
        subtitle = "Total miles run this week across all GymRivals users.",
        yourRank = yourRunRank,
        totalParticipants = runParticipants,
        periodLabel = "This week",
        you = youRunEntry,
        leaderboard = runUiEntries
    )

    // ---------------- Push-ups & Squats (AI rep counter) ----------------

    // Our local totals for this week
    val myPushupsThisWeek by remember(repSessions, weekStartMs) {
        mutableStateOf(totalRepsForWeek(repSessions, weekStartMs, "Push up"))
    }
    val mySquatsThisWeek by remember(repSessions, weekStartMs) {
        mutableStateOf(totalRepsForWeek(repSessions, weekStartMs, "Squat"))
    }

    var pushLeaderboardEntries by remember {
        mutableStateOf<List<GymRivalsCloudRepository.WeeklyRepLeaderboardEntry>>(emptyList())
    }
    var squatLeaderboardEntries by remember {
        mutableStateOf<List<GymRivalsCloudRepository.WeeklyRepLeaderboardEntry>>(emptyList())
    }

    // Push-ups: upsert weekly total reps
    LaunchedEffect(currentUid, myPushupsThisWeek, weekStartMs) {
        if (currentUid != null && myPushupsThisWeek > 0) {
            GymRivalsCloudRepository.updateWeeklyRepLeaderboard(
                weekStartMs = weekStartMs,
                exerciseType = "PUSH_UP",
                totalReps = myPushupsThisWeek
            )
        }
    }

    // Squats: upsert weekly total reps
    LaunchedEffect(currentUid, mySquatsThisWeek, weekStartMs) {
        if (currentUid != null && mySquatsThisWeek > 0) {
            GymRivalsCloudRepository.updateWeeklyRepLeaderboard(
                weekStartMs = weekStartMs,
                exerciseType = "SQUAT",
                totalReps = mySquatsThisWeek
            )
        }
    }

    // Listen for weekly rep leaderboards (all users) for both exercises
    DisposableEffect(weekStartMs) {
        val pushReg = GymRivalsCloudRepository.listenWeeklyRepLeaderboard(
            weekStartMs = weekStartMs,
            exerciseType = "PUSH_UP"
        ) { list ->
            pushLeaderboardEntries = list
        }

        val squatReg = GymRivalsCloudRepository.listenWeeklyRepLeaderboard(
            weekStartMs = weekStartMs,
            exerciseType = "SQUAT"
        ) { list ->
            squatLeaderboardEntries = list
        }

        onDispose {
            pushReg?.remove()
            squatReg?.remove()
        }
    }

    // Build UI models for push-ups
    val combinedPushEntries = remember(pushLeaderboardEntries, currentUid, myPushupsThisWeek) {
        val list = pushLeaderboardEntries.toMutableList()
        if (currentUid != null &&
            myPushupsThisWeek > 0 &&
            list.none { it.userId == currentUid }
        ) {
            list += GymRivalsCloudRepository.WeeklyRepLeaderboardEntry(
                userId = currentUid,
                displayName = displayName,
                weekStartMs = weekStartMs,
                exerciseType = "PUSH_UP",
                totalReps = myPushupsThisWeek
            )
        }
        list.sortedByDescending { it.totalReps }
    }

    val pushUiEntries = combinedPushEntries.map { e ->
        val isYou = e.userId == currentUid
        RepRivalEntry(
            id = e.userId,
            initials = initialsFromName(e.displayName),
            name = if (isYou) "You (${e.displayName})" else e.displayName,
            reps = e.totalReps
        )
    }

    val yourPushIndex = combinedPushEntries.indexOfFirst { it.userId == currentUid }
    val yourPushRank = if (yourPushIndex >= 0) yourPushIndex + 1 else 0
    val pushParticipants = combinedPushEntries.size
    val youPushEntry = pushUiEntries.firstOrNull { it.id == currentUid }

    val pushChallengeData = ChallengeData(
        title = "Push-up Rivals",
        subtitle = "Total AI-counted push-up reps this week.",
        yourRank = yourPushRank,
        totalParticipants = pushParticipants,
        periodLabel = "This week",
        you = null,            // rank is enough; we don’t reuse miles here
        leaderboard = emptyList()
    )

    // Build UI models for squats
    val combinedSquatEntries = remember(squatLeaderboardEntries, currentUid, mySquatsThisWeek) {
        val list = squatLeaderboardEntries.toMutableList()
        if (currentUid != null &&
            mySquatsThisWeek > 0 &&
            list.none { it.userId == currentUid }
        ) {
            list += GymRivalsCloudRepository.WeeklyRepLeaderboardEntry(
                userId = currentUid,
                displayName = displayName,
                weekStartMs = weekStartMs,
                exerciseType = "SQUAT",
                totalReps = mySquatsThisWeek
            )
        }
        list.sortedByDescending { it.totalReps }
    }

    val squatUiEntries = combinedSquatEntries.map { e ->
        val isYou = e.userId == currentUid
        RepRivalEntry(
            id = e.userId,
            initials = initialsFromName(e.displayName),
            name = if (isYou) "You (${e.displayName})" else e.displayName,
            reps = e.totalReps
        )
    }

    val yourSquatIndex = combinedSquatEntries.indexOfFirst { it.userId == currentUid }
    val yourSquatRank = if (yourSquatIndex >= 0) yourSquatIndex + 1 else 0
    val squatParticipants = combinedSquatEntries.size
    val youSquatEntry = squatUiEntries.firstOrNull { it.id == currentUid }

    val squatChallengeData = ChallengeData(
        title = "Squat Rivals",
        subtitle = "Total AI-counted squat reps this week.",
        yourRank = yourSquatRank,
        totalParticipants = squatParticipants,
        periodLabel = "This week",
        you = null,
        leaderboard = emptyList()
    )

    // ------------------------ UI layout ------------------------

    Surface(color = Color(0xFFF6F7FB)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Top gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerGradient)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Text(
                        "GymRivals",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        headerSubtitle,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = Color.White,
                contentColor = Color(0xFF2563EB),
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = tabIndex == i,
                        onClick = { tabIndex = i },
                        selectedContentColor = Color(0xFF2563EB),
                        unselectedContentColor = Color(0xFF6B7280),
                        text = { Text(label, fontSize = 13.sp) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                item { Spacer(Modifier.height(14.dp)) }

                when (tabIndex) {
                    // ---------------- Running tab ----------------
                    0 -> {
                        // Section title
                        item {
                            Text(
                                "Distance Runners (This Week)",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111827)
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        // Decorative scrubber (for style)
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFFE5E7EB))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.35f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFF9CA3AF))
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        // Highlight card
                        item {
                            ChallengeHighlightCard(runChallengeData, myMilesThisWeek)
                            Spacer(Modifier.height(12.dp))
                        }

                        // Rankings list
                        item {
                            RankingsCard(
                                you = youRunEntry,
                                entries = runUiEntries
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // ---------------- Push-ups tab ----------------
                    1 -> {
                        item {
                            Text(
                                "Push-ups (AI) — This Week",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111827)
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        item {
                            RepChallengeHighlightCard(
                                title = pushChallengeData.title,
                                subtitle = pushChallengeData.subtitle,
                                yourRank = yourPushRank,
                                totalParticipants = pushParticipants,
                                myRepsThisWeek = myPushupsThisWeek,
                                emoji = "💪"
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        item {
                            RepRankingsCard(
                                you = youPushEntry,
                                entries = pushUiEntries,
                                unitLabel = "reps"
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // ---------------- Squats tab ----------------
                    2 -> {
                        item {
                            Text(
                                "Squats (AI) — This Week",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111827)
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        item {
                            RepChallengeHighlightCard(
                                title = squatChallengeData.title,
                                subtitle = squatChallengeData.subtitle,
                                yourRank = yourSquatRank,
                                totalParticipants = squatParticipants,
                                myRepsThisWeek = mySquatsThisWeek,
                                emoji = "🏋️"
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        item {
                            RepRankingsCard(
                                you = youSquatEntry,
                                entries = squatUiEntries,
                                unitLabel = "reps"
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/* ---------------------------- UI Pieces ---------------------------- */

@Composable
private fun ChallengeHighlightCard(
    data: ChallengeData,
    myMilesThisWeek: Double
) {
    val gradient = Brush.horizontalGradient(
        listOf(Color(0xFFFF7A18), Color(0xFFFFB45A))
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        data.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text("🏃‍♂️", fontSize = 18.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    data.subtitle,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(16.dp))
                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Your Rank",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )

                        val rankText = when {
                            data.totalParticipants == 0 -> "No runners yet"
                            data.yourRank <= 0 -> "Unranked"
                            else -> "#${data.yourRank} of ${data.totalParticipants}"
                        }

                        Text(
                            rankText,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (myMilesThisWeek > 0.0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${"%.2f".format(myMilesThisWeek)} mi this week",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Period",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                        Text(
                            data.periodLabel,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingsCard(
    you: RivalEntry?,
    entries: List<RivalEntry>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Rankings", fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
            Spacer(Modifier.height(10.dp))

            if (entries.isEmpty()) {
                Text(
                    "No runs recorded yet this week. Start a run to join the leaderboard!",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280)
                )
                return@Column
            }

            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    Divider(
                        Modifier.padding(vertical = 2.dp),
                        thickness = 1.dp,
                        color = Color(0xFFE8ECF3)
                    )
                }
                val highlight = you != null && entry.id == you.id
                RivalRow(
                    rank = index + 1,
                    entry = entry,
                    highlight = highlight
                )
            }
        }
    }
}

@Composable
private fun RivalRow(rank: Int, entry: RivalEntry, highlight: Boolean) {
    val bg = if (highlight) Color(0xFFEFF6FF) else Color.White
    val border = if (highlight) Color(0xFFBFDBFE) else Color(0xFFE8ECF3)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val medal = when (rank) {
            1 -> "👑"
            2 -> "🥈"
            3 -> "🥉"
            else -> "🏃"
        }
        Text(medal, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFFEDE9FE)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                entry.initials,
                color = Color(0xFF7C3AED),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF111827)
            )
            Text(
                "${"%.2f".format(entry.miles)} mi",
                fontSize = 12.sp,
                color = Color(0xFF6B7280)
            )
        }

        Text(
            "${"%.2f".format(entry.miles)} mi",
            color = Color(0xFF2563EB),
            fontWeight = FontWeight.SemiBold
        )
    }
}

/* ---- Rep leaderboard UI pieces ---- */

@Composable
private fun RepChallengeHighlightCard(
    title: String,
    subtitle: String,
    yourRank: Int,
    totalParticipants: Int,
    myRepsThisWeek: Int,
    emoji: String
) {
    val gradient = Brush.horizontalGradient(
        listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(emoji, fontSize = 18.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(16.dp))
                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Your Rank",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )

                        val rankText = when {
                            totalParticipants == 0 -> "No sessions yet"
                            yourRank <= 0 -> "Unranked"
                            else -> "#$yourRank of $totalParticipants"
                        }

                        Text(
                            rankText,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (myRepsThisWeek > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "$myRepsThisWeek reps this week",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Period",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                        Text(
                            "This week",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepRankingsCard(
    you: RepRivalEntry?,
    entries: List<RepRivalEntry>,
    unitLabel: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Rankings", fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
            Spacer(Modifier.height(10.dp))

            if (entries.isEmpty()) {
                Text(
                    "No sessions recorded yet this week. Start the AI rep counter to join the leaderboard!",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280)
                )
                return@Column
            }

            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    Divider(
                        Modifier.padding(vertical = 2.dp),
                        thickness = 1.dp,
                        color = Color(0xFFE8ECF3)
                    )
                }
                val highlight = you != null && entry.id == you.id
                RepRivalRow(
                    rank = index + 1,
                    entry = entry,
                    highlight = highlight,
                    unitLabel = unitLabel
                )
            }
        }
    }
}

@Composable
private fun RepRivalRow(rank: Int, entry: RepRivalEntry, highlight: Boolean, unitLabel: String) {
    val bg = if (highlight) Color(0xFFEFF6FF) else Color.White
    val border = if (highlight) Color(0xFFBFDBFE) else Color(0xFFE8ECF3)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val medal = when (rank) {
            1 -> "👑"
            2 -> "🥈"
            3 -> "🥉"
            else -> "💪"
        }
        Text(medal, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0ECFF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                entry.initials,
                color = Color(0xFF2563EB),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF111827)
            )
            Text(
                "${entry.reps} $unitLabel",
                fontSize = 12.sp,
                color = Color(0xFF6B7280)
            )
        }

        Text(
            "${entry.reps} $unitLabel",
            color = Color(0xFF2563EB),
            fontWeight = FontWeight.SemiBold
        )
    }
}

/* ---------------------------- Data models ---------------------------- */

private data class RivalEntry(
    val id: String,
    val initials: String,
    val name: String,
    val miles: Double
)

private data class RepRivalEntry(
    val id: String,
    val initials: String,
    val name: String,
    val reps: Int
)

private data class ChallengeData(
    val title: String,
    val subtitle: String,
    val yourRank: Int,
    val totalParticipants: Int,
    val periodLabel: String,
    val you: RivalEntry?,
    val leaderboard: List<RivalEntry>
)

/* ---------------------------- Helpers ---------------------------- */

private fun currentWeekStartMs(): Long {
    val cal = Calendar.getInstance()
    cal.firstDayOfWeek = Calendar.MONDAY
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val diff = (7 + (dayOfWeek - Calendar.MONDAY)) % 7
    cal.add(Calendar.DAY_OF_MONTH, -diff)
    return cal.timeInMillis
}

private fun initialsFromName(name: String): String {
    val parts = name.trim().split(" ")
        .filter { it.isNotBlank() }

    return when {
        parts.isEmpty() -> "??"
        parts.size == 1 -> parts[0].take(2).uppercase(Locale.getDefault())
        else -> (parts[0].first().toString() + parts[1].first().toString())
            .uppercase(Locale.getDefault())
    }
}

/**
 * Sum total reps for a given exercise type in the current week.
 *
 * @param sessions all rep sessions (current user)
 * @param weekStartMs start of week in ms
 * @param exerciseType exercise label stored in RepSession (e.g. "Push up", "Squat")
 */
private fun totalRepsForWeek(
    sessions: List<RepSession>,
    weekStartMs: Long,
    exerciseType: String
): Int {
    val norm = exerciseType.lowercase(Locale.getDefault())
    return sessions
        .filter { it.timestampMs >= weekStartMs && it.exerciseType.lowercase(Locale.getDefault()) == norm }
        .sumOf { it.totalReps }
}