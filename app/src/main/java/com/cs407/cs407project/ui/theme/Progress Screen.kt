package com.cs407.cs407project.ui.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.cs407project.data.GymRivalsCloudRepository
import com.cs407.cs407project.data.RunEntry
import com.cs407.cs407project.data.RunHistoryRepository
import com.cs407.cs407project.data.StrengthExercise
import com.cs407.cs407project.data.StrengthWorkout
import com.cs407.cs407project.data.StrengthWorkoutRepository
import com.cs407.cs407project.data.RepCountRepository
import com.cs407.cs407project.data.RepSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

@Composable
fun ProgressScreen() {
    val headerGradient = Brush.horizontalGradient(listOf(Color(0xFF0EA5E9), Color(0xFF7C3AED)))

    val runs by RunHistoryRepository.runs.collectAsState()
    val lifts by StrengthWorkoutRepository.workouts.collectAsState()
    val repSessions by RepCountRepository.sessions.collectAsState()

    // Top-level menu: Running / Lifting / Bodyweight
    val tabs = listOf("Running", "Lifting", "Bodyweight")
    var tabIndex by rememberSaveable { mutableStateOf(0) }

    // State used by each tab
    // Running
    val last6Weeks = remember { lastSixIsoWeeks() }
    val weeklyGrouped = remember(runs) { runs.groupByWeek() }
    val weeklyTotals = remember(runs) { last6Weeks.map { key -> weeklyGrouped[key].orEmpty().sumOf { it.miles } } }
    val weekLabels = remember(last6Weeks) { last6Weeks.map { it.labelShort } }
    var selectedWeekIdx by rememberSaveable { mutableStateOf(last6Weeks.lastIndex) }

    // Lifting exercise selection
    val allLiftNames = remember(lifts) {
        lifts.flatMap { it.exercises.map { e -> e.name.trim() } }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    var selectedLift by rememberSaveable { mutableStateOf(allLiftNames.firstOrNull() ?: "") }

    // Bodyweight selection
    val bodyweightOptions = listOf("Push-ups (AI)", "Squats (AI)", "Push Ups (Manual)", "Pull Ups (Manual)")
    var selectedBodyweight by rememberSaveable { mutableStateOf(bodyweightOptions.first()) }

    // Keep local rep sessions in sync with Firestore
    DisposableEffect(Unit) {
        val registration = GymRivalsCloudRepository.listenRepSessions { sessions ->
            RepCountRepository.overwriteAll(sessions)
        }
        onDispose {
            registration?.remove()
        }
    }
    DisposableEffect(Unit) {
        val runListener = GymRivalsCloudRepository.listenRuns { runs ->
            RunHistoryRepository.overwriteAll(runs)
        }
        val strengthListener = GymRivalsCloudRepository.listenStrengthWorkouts { workouts ->
            StrengthWorkoutRepository.overwriteAll(workouts)
        }
        val repListener = GymRivalsCloudRepository.listenRepSessions { sessions ->
            RepCountRepository.overwriteAll(sessions)
        }

        onDispose {
            runListener?.remove()
            strengthListener?.remove()
            repListener?.remove()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(Color(0xFFF6F7FB))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerGradient)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                Text("Progress", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Track your trends by activity", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color.Transparent,
            contentColor = Color(0xFF2563EB),
            divider = {}
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = tabIndex == i,
                    onClick = { tabIndex = i },
                    selectedContentColor = Color(0xFF2563EB),
                    unselectedContentColor = Color(0xFF6B7280),
                    text = {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (tabIndex == i) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when (tabIndex) {
            /* ---------------------- RUNNING ---------------------- */
            0 -> {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ChartCard(
                        title = "Weekly Mileage (last 6 weeks)"
                    ) {
                        WeeklyBarChart(
                            values = weeklyTotals,
                            labels = weekLabels,
                            selectedIndex = selectedWeekIdx,
                            onSelect = { selectedWeekIdx = it }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // Week detail list
                    if (selectedWeekIdx in last6Weeks.indices) {
                        val key = last6Weeks[selectedWeekIdx]
                        val list = weeklyGrouped[key].orEmpty().sortedByDescending { it.timestampMs }
                        val title = if (list.isEmpty())
                            "${key.longRangeLabel} — 0.00 miles"
                        else
                            "${key.longRangeLabel} — ${"%.2f".format(list.sumOf { it.miles })} miles"
                        RunsListCard(title, list)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            /* ---------------------- LIFTING ---------------------- */
            1 -> {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    // Exercise selector
                    ChartCard(title = "Select a lifting exercise") {
                        if (allLiftNames.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                                Text("No strength workouts yet.")
                            }
                        } else {
                            ExposedDropdown(
                                value = selectedLift,
                                options = allLiftNames,
                                onSelect = { selectedLift = it }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // Last 5 workouts for that exercise
                    val last5 = remember(lifts, selectedLift) {
                        last5LiftingSessions(lifts, selectedLift)
                    }
                    ChartCard(
                        title = if (selectedLift.isBlank()) "Exercise history" else "$selectedLift — last 5 workouts",
                        subtitle = "Line = max weight • X-axis = date"
                    ) {
                        SessionLineChart(
                            values = last5.map { it.weightLbs.toDouble() },          // Y: max weight per workout
                            bottomLabels = last5.map { dateShort(it.dateMs) },       // X: workout date
                            topTitle = "Max weight (lbs)",
                            emptyText = if (selectedLift.isBlank()) "Pick an exercise to see history" else "No history found."
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            /* ---------------------- BODYWEIGHT ---------------------- */
            2 -> {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ChartCard(title = "Select a bodyweight exercise") {
                        ExposedDropdown(
                            value = selectedBodyweight,
                            options = bodyweightOptions,
                            onSelect = { selectedBodyweight = it }
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // Determine if this is AI-counted or manual entry
                    val isAiCounted = selectedBodyweight.contains("(AI)")

                    if (isAiCounted) {
                        // Show data from RepCountRepository
                        val exerciseType = when (selectedBodyweight) {
                            "Push-ups (AI)" -> "Push up"
                            "Squats (AI)" -> "Squat"
                            else -> ""
                        }

                        val last5 = remember(repSessions, exerciseType) {
                            last5RepCounterSessions(repSessions, exerciseType)
                        }

                        ChartCard(
                            title = "$selectedBodyweight — last 5 sessions",
                            subtitle = "Line = total reps • X-axis = date"
                        ) {
                            SessionLineChart(
                                values = last5.map { it.totalReps.toDouble() },
                                bottomLabels = last5.map { dateShort(it.dateMs) },
                                topTitle = "Total reps",
                                emptyText = "No sessions found. Start the Rep Counter to track reps!"
                            )
                        }
                    } else {
                        // Show data from StrengthWorkoutRepository (manual entry)
                        val last5 = remember(lifts, selectedBodyweight) {
                            val names = when (selectedBodyweight) {
                                "Push Ups (Manual)" -> listOf("Push Up", "Push-Up", "Push Ups", "Push-ups", "Pushup", "Pushups")
                                else -> listOf("Pull Up", "Pull-Up", "Pull Ups", "Pull-ups", "Pullup", "Pullups")
                            }
                            last5BodyweightSessions(lifts, names)
                        }

                        ChartCard(
                            title = "$selectedBodyweight — last 5 sessions",
                            subtitle = "Line = total reps • X-axis = date"
                        ) {
                            SessionLineChart(
                                values = last5.map { it.totalReps.toDouble() },
                                bottomLabels = last5.map { dateShort(it.dateMs) },
                                topTitle = "Total reps",
                                emptyText = "No sessions found."
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/* ========================= UI blocks ========================= */

@Composable
private fun ChartCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF6B7280))
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/* ---- Bar chart for weeks (with tap select + connected trend line) ---- */
@Composable
private fun WeeklyBarChart(
    values: List<Double>,               // size = 6
    labels: List<String>,               // size = 6, e.g., "Oct 7"
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val maxY = max(1.0, values.maxOrNull() ?: 1.0)
    val padLeft = 32f; val padRight = 16f; val padTop = 8f; val padBottom = 36f
    val barSpacing = 12f

    Box(Modifier.fillMaxWidth().height(220.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        val w = size.width - padLeft - padRight
                        val barCount = values.size
                        val barWidth = (w - barSpacing * (barCount - 1)) / barCount
                        val x0 = padLeft
                        val localX = (offset.x - x0).coerceIn(0f, w)
                        val slot = (barWidth + barSpacing)
                        val idx = (localX / slot).toInt().coerceIn(0, barCount - 1)
                        onSelect(idx)
                    }
                }
        ) {
            val w = size.width; val h = size.height
            val chartW = w - padLeft - padRight; val chartH = h - padTop - padBottom
            val barCount = values.size
            val barWidth = (chartW - barSpacing * (barCount - 1)) / barCount

            fun xOf(i: Int) = padLeft + i * (barWidth + barSpacing)
            fun barH(v: Double) = ((v / maxY) * chartH).toFloat()

            // axes
            drawLine(Color(0xFFE8ECF3), Offset(padLeft, h - padBottom), Offset(w - padRight, h - padBottom), 2f)
            drawLine(Color(0xFFE8ECF3), Offset(padLeft, padTop), Offset(padLeft, h - padBottom), 2f)

            // bars
            values.forEachIndexed { i, v ->
                val color = if (i == selectedIndex) Color(0xFF2563EB) else Color(0xFF93C5FD)
                val top = h - padBottom - barH(v)
                drawRect(
                    color,
                    topLeft = Offset(xOf(i), top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barH(v))
                )
            }

            // connected line & points showing trend across weeks
            if (values.isNotEmpty()) {
                val path = androidx.compose.ui.graphics.Path()
                values.forEachIndexed { i, v ->
                    val cx = xOf(i) + barWidth / 2f
                    val cy = h - padBottom - barH(v)
                    if (i == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
                }
                drawPath(path, color = Color(0xFF2563EB), style = Stroke(width = 4f))

                values.forEachIndexed { i, v ->
                    val cx = xOf(i) + barWidth / 2f
                    val cy = h - padBottom - barH(v)
                    drawCircle(color = Color(0xFF2563EB), radius = 6f, center = Offset(cx, cy))
                }
            }

            // labels
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#6B7280")
                    textSize = 28f; isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
                }
                repeat(barCount) {
                    val x = xOf(it) + barWidth / 2f
                    drawText(labels[it], x, h - padBottom + 24f, paint)
                }
                drawText("0", 8f, (h - padBottom) + 9f, paint)
                drawText("%.1f".format(maxY), 8f, (h - padBottom - chartH) + 9f, paint)
            }
        }
    }
}

/* ---- Simple session bar chart (still available if needed elsewhere) ---- */
@Composable
private fun SessionBarChart(
    values: List<Double>,          // size up to 5 (oldest→newest)
    bottomLabels: List<String>,    // same size (e.g., "135 lb" or "3 sets")
    topTitle: String,
    emptyText: String
) {
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Text(emptyText, color = Color(0xFF6B7280))
        }
        return
    }

    val maxY = max(1.0, values.maxOrNull() ?: 1.0)
    val padLeft = 36f; val padRight = 16f; val padTop = 10f; val padBottom = 40f
    val barSpacing = 16f

    Box(Modifier.fillMaxWidth().height(220.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val chartW = w - padLeft - padRight; val chartH = h - padTop - padBottom
            val count = values.size
            val barWidth = (chartW - barSpacing * (count - 1)) / count
            fun xOf(i: Int) = padLeft + i * (barWidth + barSpacing)
            fun barH(v: Double) = ((v / maxY) * chartH).toFloat()

            drawLine(Color(0xFFE8ECF3), Offset(padLeft, h - padBottom), Offset(w - padRight, h - padBottom), 2f)
            drawLine(Color(0xFFE8ECF3), Offset(padLeft, padTop), Offset(padLeft, h - padBottom), 2f)

            values.forEachIndexed { i, v ->
                val top = h - padBottom - barH(v)
                drawRect(
                    color = Color(0xFF2563EB),
                    topLeft = Offset(xOf(i), top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barH(v))
                )
            }

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#6B7280")
                    textSize = 26f; isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
                }
                repeat(count) {
                    val x = xOf(it) + barWidth / 2f
                    drawText(bottomLabels[it], x, h - padBottom + 26f, paint)
                }
                drawText(topTitle, padLeft, padTop + 4f, android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#111827"); textSize = 30f; isAntiAlias = true
                })
            }
        }
    }
}

@Composable
private fun SessionLineChart(
    values: List<Double>,          // up to 5, oldest → newest
    bottomLabels: List<String>,    // same size (e.g., "135 lb" or date)
    topTitle: String,
    emptyText: String
) {
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Text(emptyText, color = Color(0xFF6B7280))
        }
        return
    }

    val maxY = max(1.0, values.maxOrNull() ?: 1.0)
    val padLeft = 36f; val padRight = 16f; val padTop = 12f; val padBottom = 44f

    Box(Modifier.fillMaxWidth().height(220.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val chartW = w - padLeft - padRight
            val chartH = h - padTop - padBottom
            val n = values.size.coerceAtLeast(1)
            val stepX = if (n == 1) 0f else chartW / (n - 1)

            fun x(i: Int) = padLeft + stepX * i
            fun y(v: Double) = (h - padBottom - (v / maxY * chartH)).toFloat()

            // Axes
            drawLine(Color(0xFFE8ECF3), Offset(padLeft, h - padBottom), Offset(w - padRight, h - padBottom), 2f)
            drawLine(Color(0xFFE8ECF3), Offset(padLeft, padTop), Offset(padLeft, h - padBottom), 2f)

            // Line path
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { i, v ->
                val px = x(i); val py = y(v)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color = Color(0xFF2563EB), style = Stroke(width = 4f))

            // Points
            values.forEachIndexed { i, v ->
                drawCircle(color = Color(0xFF2563EB), radius = 6f, center = Offset(x(i), y(v)))
            }

            // Labels
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#6B7280")
                    textSize = 26f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                // bottom X labels
                bottomLabels.forEachIndexed { i, lab ->
                    drawText(lab, x(i), h - padBottom + 26f, labelPaint)
                }
                // Y labels (0 and max)
                val yPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#6B7280")
                    textSize = 26f
                    isAntiAlias = true
                }
                drawText("0", 8f, (h - padBottom) + 9f, yPaint)
                drawText("%.0f".format(maxY), 8f, y(maxY) + 9f, yPaint)

                // Top title
                val titlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#111827")
                    textSize = 30f
                    isAntiAlias = true
                }
                drawText(topTitle, padLeft, padTop + 4f, titlePaint)
            }
        }
    }
}

/* ========================= Data shaping ========================= */

private data class WeekKey(val year: Int, val week: Int, val startMs: Long) {
    val labelShort: String get() = SimpleDateFormat("MMM d", Locale.getDefault()).format(startMs)
    val longRangeLabel: String
        get() {
            val endMs = startMs + 6 * 24L * 60 * 60 * 1000
            val start = SimpleDateFormat("MMM d", Locale.getDefault()).format(startMs)
            val end = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(endMs)
            return "$start – $end"
        }
}

private fun lastSixIsoWeeks(): List<WeekKey> {
    val cal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY; minimalDaysInFirstWeek = 4
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }
    val list = ArrayList<WeekKey>(6)
    cal.add(Calendar.WEEK_OF_YEAR, -5)
    repeat(6) {
        list += WeekKey(cal.get(Calendar.YEAR), cal.get(Calendar.WEEK_OF_YEAR), cal.timeInMillis)
        cal.add(Calendar.WEEK_OF_YEAR, 1)
    }
    return list
}

private fun List<RunEntry>.groupByWeek(): Map<WeekKey, List<RunEntry>> {
    val cal = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY; minimalDaysInFirstWeek = 4 }
    val map = linkedMapOf<WeekKey, MutableList<RunEntry>>()
    for (r in this) {
        cal.timeInMillis = r.timestampMs
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val key = WeekKey(cal.get(Calendar.YEAR), cal.get(Calendar.WEEK_OF_YEAR), cal.timeInMillis)
        map.getOrPut(key) { mutableListOf() }.add(r)
    }
    return map
}

@Composable
private fun RunsListCard(title: String, runs: List<RunEntry>) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
            Spacer(Modifier.height(10.dp))
            if (runs.isEmpty()) {
                Text("No runs in this week.", color = Color(0xFF6B7280))
            } else {
                runs.sortedByDescending { it.timestampMs }.forEachIndexed { i, r ->
                    if (i > 0) Divider(thickness = 1.dp, color = Color(0xFFE8ECF3))
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dateLong(r.timestampMs), fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                            val pace = r.avgPaceSecPerMile
                            val paceStr = if (pace == null) "--:--" else "%d:%02d min/mi".format(pace / 60, pace % 60)
                            Text("${elapsedText(r.elapsedMillis)} • $paceStr", fontSize = 12.sp, color = Color(0xFF6B7280))
                        }
                        Text("+${"%.2f".format(r.miles)} mi", color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/* ---- Lift/bodyweight extraction ---- */

private data class LiftSession(val dateMs: Long, val totalReps: Int, val sets: Int, val weightLbs: Int)

private fun last5LiftingSessions(workouts: List<StrengthWorkout>, exerciseName: String): List<LiftSession> {
    if (exerciseName.isBlank()) return emptyList()
    val norm = exerciseName.lowercase(Locale.getDefault())
    val hits = workouts.mapNotNull { w ->
        // merge multiple entries with same name in a workout (rare)
        val exs = w.exercises.filter { it.name.lowercase(Locale.getDefault()) == norm }
        if (exs.isEmpty()) null else {
            val reps = exs.sumOf { it.sets * it.reps }
            val sets = exs.sumOf { it.sets }
            val weight = exs.maxOfOrNull { it.weightLbs } ?: 0  // show top weight used that day
            LiftSession(w.timestampMs, reps, sets, weight)
        }
    }
    return hits.sortedBy { it.dateMs }.takeLast(5)
}

private fun last5BodyweightSessions(workouts: List<StrengthWorkout>, anyOfNames: List<String>): List<LiftSession> {
    val norms = anyOfNames.map { it.lowercase(Locale.getDefault()) }.toSet()
    val hits = workouts.mapNotNull { w ->
        val exs = w.exercises.filter { it.name.lowercase(Locale.getDefault()) in norms }
        if (exs.isEmpty()) null else {
            val reps = exs.sumOf { it.sets * it.reps }
            val sets = exs.sumOf { it.sets }
            LiftSession(w.timestampMs, reps, sets, weightLbs = 0)
        }
    }
    return hits.sortedBy { it.dateMs }.takeLast(5)
}

/**
 * Extract last 5 sessions from RepCountRepository for a specific exercise type
 *
 * @param sessions All rep counting sessions
 * @param exerciseType The exercise type to filter (e.g., "Push up", "Squat")
 * @return List of up to 5 most recent sessions
 */
private fun last5RepCounterSessions(sessions: List<RepSession>, exerciseType: String): List<LiftSession> {
    val norm = exerciseType.lowercase(Locale.getDefault())
    val hits = sessions
        .filter { it.exerciseType.lowercase(Locale.getDefault()) == norm }
        .sortedBy { it.timestampMs }
        .takeLast(5)
        .map { repSession ->
            LiftSession(
                dateMs = repSession.timestampMs,
                totalReps = repSession.totalReps,
                sets = repSession.durationSeconds, // still stored here if needed elsewhere
                weightLbs = 0
            )
        }
    return hits
}

/* ---- Tiny dropdown used above ---- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdown(value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(value) { mutableStateOf(value) }
    val filtered = remember(query, options) {
        options.filter { it.contains(query.trim(), ignoreCase = true) }.take(10)
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSelect(it)
            },
            label = { Text("Exercise") },
            singleLine = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            filtered.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = {
                    query = opt; onSelect(opt); expanded = false
                })
            }
        }
    }
}

/* ---- formatting helpers ---- */

private fun dateShort(ms: Long): String =
    SimpleDateFormat("M/d", Locale.getDefault()).format(ms)

private fun dateLong(ms: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(ms)

private fun elapsedText(ms: Long): String {
    val t = (ms / 1000).toInt().coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
