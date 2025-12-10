package com.cs407.cs407project.ui.strength

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.cs407project.data.GymRivalsCloudRepository
import com.cs407.cs407project.data.StrengthExercise
import com.cs407.cs407project.data.StrengthWorkout
import com.cs407.cs407project.data.StrengthWorkoutRepository

@Composable
fun StrengthWorkoutScreen(
    onBack: () -> Unit,
    onSubmit: (StrengthWorkout) -> Unit
) {
    val gradient = Brush.horizontalGradient(listOf(Color(0xFF0EA5E9), Color(0xFF7C3AED)))

    var title by remember { mutableStateOf("") }
    var exercises by remember { mutableStateOf(listOf<ExerciseDraft>()) }
    var showDiscard by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val isDirty = title.isNotBlank() || exercises.isNotEmpty()

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
                        .noRippleClickable {
                            if (isDirty) showDiscard = true else onBack()
                        }
                )
                Column {
                    Text("Strength Workout", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Add up to 12 exercises", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
        }

        // Scrollable form
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Workout Title") },
                placeholder = { Text("e.g., Push Day A") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Exercises
            exercises.forEachIndexed { index, draft ->
                ExerciseCard(
                    index = index,
                    draft = draft,
                    onChange = { updated ->
                        exercises = exercises.toMutableList().also { it[index] = updated }
                    },
                    onRemove = {
                        exercises = exercises.toMutableList().also { it.removeAt(index) }
                    }
                )
            }

            // Add exercise (limit 12)
            if (exercises.size < 12) {
                PrimaryGradientButton(text = "Add Exercise") {
                    exercises = exercises + ExerciseDraft()
                }
            }

            errorText?.let {
                Text(it, color = Color(0xFFEF4444), fontSize = 13.sp)
            }

            Spacer(Modifier.height(72.dp))
        }

        // Bottom submit
        Surface(shadowElevation = 8.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                PrimaryGradientButton(text = "Add Strength Workout") {
                    val valid = exercises
                        .filter { it.name.isNotBlank() && it.sets > 0 && it.reps > 0 }
                        .take(12)

                    if (title.isBlank()) {
                        errorText = "Please enter a workout title."
                        return@PrimaryGradientButton
                    }
                    if (valid.isEmpty()) {
                        errorText = "Please add at least one valid exercise (name, sets, reps)."
                        return@PrimaryGradientButton
                    }

                    val model = StrengthWorkout(
                        title = title.trim(),
                        timestampMs = System.currentTimeMillis(),
                        exercises = valid.map {
                            StrengthExercise(
                                name = it.name.trim(),
                                sets = it.sets,
                                reps = it.reps,
                                restSec = it.restSec,
                                weightLbs = it.weightLbs       // ✅ map weight
                            )
                        }
                    )
                    GymRivalsCloudRepository.addStrengthWorkout(model)
                    StrengthWorkoutRepository.add(model)
                    onSubmit(model)
                }
            }
        }
    }

    // Discard confirm
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard workout?") },
            text = { Text("You have unsaved changes. If you go back now, they will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    onBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("Keep editing") }
            }
        )
    }
}

/* ----------------- Exercise row/card ----------------- */

private data class ExerciseDraft(
    val name: String = "",
    val sets: Int = 3,
    val reps: Int = 10,
    val restSec: Int = 60,
    val weightLbs: Int = 0        // ✅ NEW
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseCard(
    index: Int,
    draft: ExerciseDraft,
    onChange: (ExerciseDraft) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Exercise ${index + 1}", fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))

            // Searchable dropdown
            var expanded by remember { mutableStateOf(false) }
            var query by remember { mutableStateOf(draft.name) }
            val options = remember(query) {
                defaultExerciseOptions.filter { it.contains(query.trim(), ignoreCase = true) }.take(8)
            }

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onChange(draft.copy(name = it))
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    label = { Text("Exercise") },
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt) },
                            onClick = {
                                query = opt
                                onChange(draft.copy(name = opt))
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Sets / Reps / Weight / Rest  ✅ weight included
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Sets", draft.sets.toString(), { v -> onChange(draft.copy(sets = v)) }, Modifier.weight(1f))
                NumberField("Reps", draft.reps.toString(), { v -> onChange(draft.copy(reps = v)) }, Modifier.weight(1f))
                NumberField("Weight (lb)", draft.weightLbs.toString(), { v -> onChange(draft.copy(weightLbs = v)) }, Modifier.weight(1f))
                NumberField("Rest (sec)", draft.restSec.toString(), { v -> onChange(draft.copy(restSec = v)) }, Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValue: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { txt ->
            val cleaned = txt.filter { it.isDigit() }.take(4)
            val num = cleaned.toIntOrNull() ?: 0
            onValue(num)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/* ----------------- Helpers & style ----------------- */

@Composable
private fun PrimaryGradientButton(text: String, onClick: () -> Unit) {
    val brush = Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(brush, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            indication = null,
            interactionSource = MutableInteractionSource()
        ) { onClick() }
    )

private val defaultExerciseOptions = listOf(
    "Bench Press", "Incline Bench Press", "Overhead Press", "Dumbbell Shoulder Press",
    "Lat Pulldown", "Barbell Row", "Seated Cable Row", "Pull-Up",
    "Squat", "Front Squat", "Romanian Deadlift", "Deadlift", "Leg Press",
    "Lunge", "Bulgarian Split Squat", "Leg Extension", "Leg Curl", "Calf Raise",
    "Biceps Curl", "Hammer Curl", "Triceps Pushdown", "Skull Crushers",
    "Chest Fly", "Cable Fly", "Lateral Raise", "Face Pull", "Rear Delt Fly",
    "Plank", "Hanging Leg Raise", "Ab Wheel", "Cable Crunch",
    "Push Ups", "Pull Ups" // for your bodyweight charts
)