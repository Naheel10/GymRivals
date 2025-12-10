package com.cs407.cs407project.viewmodel

import android.app.Application
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.cs407.cs407project.data.GymRivalsCloudRepository
import com.cs407.cs407project.data.RepCountRepository
import com.cs407.cs407project.data.RepSession
import com.cs407.cs407project.repcounter.ExerciseType
import com.cs407.cs407project.repcounter.RepCounter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.common.util.concurrent.ListenableFuture

/**
 * UI State for the Rep Counter screen.
 */
data class RepCounterUiState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val repCount: Int = 0,
    val exerciseType: ExerciseType = ExerciseType.PUSH_UP,
    val elapsedSeconds: Int = 0,
    val cameraError: String? = null,
    val permissionGranted: Boolean = false
)

/**
 * ViewModel for ML Kit pose-based rep counter.
 */
class RepCounterViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "RepCounterViewModel"
    }

    // ---------- STATE ----------
    private val _state = MutableStateFlow(RepCounterUiState())
    val state = _state.asStateFlow()

    // ML Kit pose detector
    private var poseDetector: PoseDetector? = null

    // Rep counter logic
    private var repCounter: RepCounter? = null

    // CameraX
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService? = null

    // Timer
    private var sessionStartTime: Long = 0L
    private var timerJob: Job? = null

    init {
        // Accurate ML Kit pose model
        val opts = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()

        poseDetector = PoseDetection.getClient(opts)
        cameraExecutor = Executors.newSingleThreadExecutor()

        Log.d(TAG, "RepCounterViewModel initialized")
    }

    // ---------- PERMISSION ----------
    fun setCameraPermission(granted: Boolean) {
        _state.value = _state.value.copy(permissionGranted = granted)
        if (!granted) {
            _state.value = _state.value.copy(cameraError = "Camera permission required")
        }
    }

    // ---------- EXERCISE TYPE ----------
    fun setExerciseType(type: ExerciseType) {
        if (_state.value.isRunning) return
        _state.value = _state.value.copy(exerciseType = type)
        Log.d(TAG, "Exercise type set to $type")
    }

    // ---------- CAMERA INITIALIZATION ----------
    fun initializeCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(getApplication())

        cameraProviderFuture.addListener({

            try {
                cameraProvider = cameraProviderFuture.get()

                // Preview use case
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Analysis use case
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(cameraExecutor!!) { imageProxy ->
                            processImageProxy(imageProxy)
                        }
                    }

                val selector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )

                Log.d(TAG, "Camera initialized successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization error", e)
                _state.value = _state.value.copy(cameraError = "Camera error: ${e.message}")
            }

        }, ContextCompat.getMainExecutor(getApplication()))
    }

    // ---------- COUNTING ----------
    fun startCounting() {
        if (_state.value.isRunning) return

        repCounter = RepCounter(_state.value.exerciseType) { newCount ->
            _state.value = _state.value.copy(repCount = newCount)
        }

        sessionStartTime = System.currentTimeMillis()

        _state.value = _state.value.copy(
            isRunning = true,
            isPaused = false,
            repCount = 0,
            elapsedSeconds = 0
        )

        startTimer()
        Log.d(TAG, "Started counting.")
    }

    fun pauseCounting() {
        if (!_state.value.isRunning || _state.value.isPaused) return
        _state.value = _state.value.copy(isPaused = true)
    }

    fun resumeCounting() {
        if (!_state.value.isRunning || !_state.value.isPaused) return
        _state.value = _state.value.copy(isPaused = false)
    }

    fun stopCounting() {
        if (!_state.value.isRunning) return

        stopTimer()

        // Build session object from current UI state
        val session = RepSession(
            exerciseType = _state.value.exerciseType.name.replace("_", " ")
                .lowercase()
                .replaceFirstChar { it.uppercase() },
            totalReps = _state.value.repCount,
            timestampMs = System.currentTimeMillis(),
            durationSeconds = _state.value.elapsedSeconds
        )

        // 1) Save locally
        RepCountRepository.add(session)

        // 2) Save to Firestore
        GymRivalsCloudRepository.addRepSession(session)

        _state.value = _state.value.copy(isRunning = false, isPaused = false)

        Log.d(
            TAG,
            "Stopped counting. Session saved: ${session.totalReps} reps in ${session.durationSeconds}s"
        )
    }

    // ---------- TIMER ----------
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.isRunning) {
                if (!_state.value.isPaused) {
                    val elapsed =
                        ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
                    _state.value = _state.value.copy(elapsedSeconds = elapsed)
                }
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // ---------- IMAGE PROCESSING ----------
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val img = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        if (!_state.value.isRunning || _state.value.isPaused) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            img,
            imageProxy.imageInfo.rotationDegrees
        )

        poseDetector
            ?.process(inputImage)
            ?.addOnSuccessListener { pose ->
                repCounter?.processPose(pose)
            }
            ?.addOnFailureListener { e -> Log.e(TAG, "Pose detection failed", e) }
            ?.addOnCompleteListener { imageProxy.close() }
    }

    // ---------- CLEANUP ----------
    override fun onCleared() {
        super.onCleared()
        stopTimer()
        cameraProvider?.unbindAll()
        poseDetector?.close()
        cameraExecutor?.shutdown()
    }
}