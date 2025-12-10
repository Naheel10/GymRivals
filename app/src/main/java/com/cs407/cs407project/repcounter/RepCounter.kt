package com.cs407.cs407project.repcounter

import android.util.Log
import com.google.mlkit.vision.common.PointF3D
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Exercise types supported by the rep counter
 */
enum class ExerciseType {
    PUSH_UP,
    SQUAT
}

/**
 * State of a rep during detection
 */
private enum class RepState {
    IDLE,        // Not in a rep
    DOWN,        // In the down position
    UP           // In the up position
}

/**
 * RepCounter - Automatic rep counting using ML Kit Pose Detection
 *
 * This class analyzes body pose landmarks to automatically count reps for exercises.
 * It uses a peak-detection algorithm with threshold and cooldown to detect completed reps.
 *
 * ## Algorithms:
 * - **Push-ups**: Tracks elbow angle. A rep is counted when the user goes from extended arms
 *   (high angle) to bent arms (low angle) and back to extended.
 * - **Squats**: Tracks knee angle. A rep is counted when the user goes from standing
 *   (high angle) to squatting (low angle) and back to standing.
 *
 * ## Noise Filtering:
 * - Uses median smoothing over a rolling window to reduce jitter
 * - Cooldown period prevents double-counting from small oscillations
 *
 * ## Tuneable Parameters:
 * - `SMOOTHING_WINDOW_SIZE`: Number of frames for median smoothing (default: 5)
 * - `COOLDOWN_MS`: Minimum time between rep detections in milliseconds (default: 500ms)
 * - Push-up thresholds: UP_THRESHOLD (140°), DOWN_THRESHOLD (90°)
 * - Squat thresholds: UP_THRESHOLD (160°), DOWN_THRESHOLD (100°)
 *
 * @property exerciseType The type of exercise to count reps for
 * @property onRepCounted Callback invoked when a rep is detected (passes new total count)
 */
class RepCounter(
    private val exerciseType: ExerciseType,
    private val onRepCounted: (Int) -> Unit
) {
    companion object {
        private const val TAG = "RepCounter"

        // Smoothing window size for median filter
        // Higher values = more smoothing but slower response
        private const val SMOOTHING_WINDOW_SIZE = 5

        // Cooldown period to prevent double-counting (milliseconds)
        // Prevents multiple reps from being counted during small oscillations
        private const val COOLDOWN_MS = 500L

        // Push-up angle thresholds (degrees)
        // These define when arms are considered "up" (extended) or "down" (bent)
        private const val PUSH_UP_THRESHOLD = 140.0  // Arms extended
        private const val PUSH_DOWN_THRESHOLD = 90.0  // Arms bent

        // Squat angle thresholds (degrees)
        // These define when legs are considered "up" (standing) or "down" (squatting)
        private const val SQUAT_UP_THRESHOLD = 160.0  // Standing
        private const val SQUAT_DOWN_THRESHOLD = 100.0 // Squatting

        // Minimum confidence score for pose landmarks (0.0 to 1.0)
        // Landmarks below this confidence are ignored
        private const val MIN_CONFIDENCE = 0.5f
    }

    // Current rep count
    private var repCount = 0

    // Current state in the rep cycle
    private var repState = RepState.IDLE

    // Rolling window for angle smoothing
    private val angleHistory = ArrayDeque<Double>(SMOOTHING_WINDOW_SIZE)

    // Timestamp of last detected rep (for cooldown)
    private var lastRepTimestamp = 0L

    /**
     * Gets the current rep count
     *
     * @return The total number of reps counted
     */
    fun getRepCount(): Int = repCount

    /**
     * Resets the rep counter to zero
     */
    fun reset() {
        repCount = 0
        repState = RepState.IDLE
        angleHistory.clear()
        lastRepTimestamp = 0L
        Log.d(TAG, "RepCounter reset")
    }

    /**
     * Processes a new pose from ML Kit Pose Detection
     *
     * This is the main entry point called for each camera frame with detected pose.
     * It extracts the relevant angle, applies smoothing, and checks for rep completion.
     *
     * @param pose The detected pose from ML Kit
     */
    fun processPose(pose: Pose) {
        // Extract the relevant angle based on exercise type
        val rawAngle = when (exerciseType) {
            ExerciseType.PUSH_UP -> calculatePushUpAngle(pose)
            ExerciseType.SQUAT -> calculateSquatAngle(pose)
        }

        // Skip if angle could not be calculated (missing landmarks)
        if (rawAngle == null) {
            Log.d(TAG, "Could not calculate angle - missing landmarks")
            return
        }

        // Apply median smoothing to reduce noise
        val smoothedAngle = applySmoothig(rawAngle)

        // Check for rep completion based on angle thresholds
        checkForRep(smoothedAngle)
    }

    /**
     * Calculates the elbow angle for push-up detection
     *
     * Measures the angle at the elbow joint (shoulder -> elbow -> wrist).
     * A larger angle means extended arms, smaller angle means bent arms.
     *
     * @param pose The detected pose
     * @return The elbow angle in degrees, or null if landmarks are missing/low confidence
     */
    private fun calculatePushUpAngle(pose: Pose): Double? {
        // Get right-side landmarks (we could also use left side or average both)
        val shoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return null
        val elbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW) ?: return null
        val wrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) ?: return null

        // Check confidence scores
        if (shoulder.inFrameLikelihood < MIN_CONFIDENCE ||
            elbow.inFrameLikelihood < MIN_CONFIDENCE ||
            wrist.inFrameLikelihood < MIN_CONFIDENCE) {
            return null
        }

        // Calculate angle at elbow joint
        return calculateAngle(
            shoulder.position3D,
            elbow.position3D,
            wrist.position3D
        )
    }

    /**
     * Calculates the knee angle for squat detection
     *
     * Measures the angle at the knee joint (hip -> knee -> ankle).
     * A larger angle means standing, smaller angle means squatting.
     *
     * @param pose The detected pose
     * @return The knee angle in degrees, or null if landmarks are missing/low confidence
     */
    private fun calculateSquatAngle(pose: Pose): Double? {
        // Get right-side landmarks
        val hip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) ?: return null
        val knee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE) ?: return null
        val ankle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE) ?: return null

        // Check confidence scores
        if (hip.inFrameLikelihood < MIN_CONFIDENCE ||
            knee.inFrameLikelihood < MIN_CONFIDENCE ||
            ankle.inFrameLikelihood < MIN_CONFIDENCE) {
            return null
        }

        // Calculate angle at knee joint
        return calculateAngle(
            hip.position3D,
            knee.position3D,
            ankle.position3D
        )
    }

    /**
     * Calculates the angle between three 3D points
     *
     * Given three points A -> B -> C, calculates the angle at point B.
     * Uses the dot product formula: angle = arccos((BA · BC) / (|BA| × |BC|))
     *
     * @param a First point (e.g., shoulder)
     * @param b Middle point / vertex (e.g., elbow)
     * @param c Third point (e.g., wrist)
     * @return The angle at point B in degrees (0-180)
     */
    private fun calculateAngle(a: PointF3D, b: PointF3D, c: PointF3D): Double {
        // Vector from B to A
        val ba = floatArrayOf(a.x - b.x, a.y - b.y, a.z - b.z)
        // Vector from B to C
        val bc = floatArrayOf(c.x - b.x, c.y - b.y, c.z - b.z)

        // Calculate dot product
        val dotProduct = ba[0] * bc[0] + ba[1] * bc[1] + ba[2] * bc[2]

        // Calculate magnitudes
        val magnitudeBA = sqrt(ba[0] * ba[0] + ba[1] * ba[1] + ba[2] * ba[2])
        val magnitudeBC = sqrt(bc[0] * bc[0] + bc[1] * bc[1] + bc[2] * bc[2])

        // Calculate angle using inverse cosine
        val cosineAngle = dotProduct / (magnitudeBA * magnitudeBC)

        // Clamp to [-1, 1] to avoid NaN from floating point errors
        val clampedCosine = cosineAngle.coerceIn(-1f, 1f)

        // Convert from radians to degrees
        val angleRadians = kotlin.math.acos(clampedCosine)
        return Math.toDegrees(angleRadians.toDouble())
    }

    /**
     * Applies median smoothing to reduce noise in angle measurements
     *
     * Maintains a rolling window of recent angle values and returns the median.
     * This helps filter out sudden spikes or jitter from pose detection.
     *
     * @param rawAngle The raw angle measurement from pose detection
     * @return The smoothed angle after median filtering
     */
    private fun applySmoothig(rawAngle: Double): Double {
        // Add new angle to history
        angleHistory.addLast(rawAngle)

        // Keep window size limited
        if (angleHistory.size > SMOOTHING_WINDOW_SIZE) {
            angleHistory.removeFirst()
        }

        // Return median of window
        val sorted = angleHistory.sorted()
        return sorted[sorted.size / 2]
    }

    /**
     * Checks if a rep has been completed based on the current angle
     *
     * Uses a state machine with three states: IDLE, DOWN, UP.
     * A rep is counted when the user transitions from UP -> DOWN -> UP.
     *
     * ## State Transitions:
     * - **IDLE -> DOWN**: Angle drops below DOWN_THRESHOLD
     * - **DOWN -> UP**: Angle rises above UP_THRESHOLD
     * - **UP -> DOWN**: Angle drops below DOWN_THRESHOLD (counts a rep)
     *
     * ## Cooldown:
     * After counting a rep, a cooldown period prevents double-counting
     * from small oscillations around the threshold.
     *
     * @param angle The current smoothed angle in degrees
     */
    private fun checkForRep(angle: Double) {
        val currentTime = System.currentTimeMillis()

        // Get thresholds based on exercise type
        val (upThreshold, downThreshold) = when (exerciseType) {
            ExerciseType.PUSH_UP -> Pair(PUSH_UP_THRESHOLD, PUSH_DOWN_THRESHOLD)
            ExerciseType.SQUAT -> Pair(SQUAT_UP_THRESHOLD, SQUAT_DOWN_THRESHOLD)
        }

        when (repState) {
            RepState.IDLE -> {
                // Waiting for user to start - look for downward movement
                if (angle < downThreshold) {
                    repState = RepState.DOWN
                    Log.d(TAG, "State: IDLE -> DOWN (angle: ${"%.1f".format(angle)}°)")
                }
            }

            RepState.DOWN -> {
                // User is in down position - look for upward movement
                if (angle > upThreshold) {
                    repState = RepState.UP
                    Log.d(TAG, "State: DOWN -> UP (angle: ${"%.1f".format(angle)}°)")
                }
            }

            RepState.UP -> {
                // User is in up position - look for downward movement to complete rep
                if (angle < downThreshold) {
                    // Check cooldown to prevent double-counting
                    if (currentTime - lastRepTimestamp >= COOLDOWN_MS) {
                        // Rep completed!
                        repCount++
                        lastRepTimestamp = currentTime
                        repState = RepState.DOWN

                        Log.d(TAG, "REP COUNTED! Total: $repCount (angle: ${"%.1f".format(angle)}°)")

                        // Notify callback
                        onRepCounted(repCount)
                    } else {
                        Log.d(TAG, "Rep detected but in cooldown period")
                    }
                }
            }
        }
    }
}