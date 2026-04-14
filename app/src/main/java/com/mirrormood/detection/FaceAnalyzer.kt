package com.mirrormood.detection

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.mirrormood.MirrorMoodApp
import com.mirrormood.data.db.MoodEntry
import com.mirrormood.data.repository.MoodRepository
import com.mirrormood.widget.MoodWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FaceAnalyzer(
    private val repository: MoodRepository,
    private val scope: CoroutineScope,
    private val context: Context
) : ImageAnalysis.Analyzer {

    // Configure ML Kit face detector with landmark mode for head angles
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .build()

    private val detector = FaceDetection.getClient(options)

    // Temporal smoothing: sliding window of recent mood results
    private val recentMoods = ArrayDeque<MoodResult>(SMOOTHING_WINDOW)
    private var lastSavedMood: String? = null
    private val consensusThreshold: Int

    // Blinking tracking
    private val blinkTimestamps = ArrayDeque<Long>()
    private var isCurrentlyBlinking = false

    private var baseline: FaceBaseline? = null

    init {
        val prefs = context.getSharedPreferences(MirrorMoodApp.PREFS_NAME, Context.MODE_PRIVATE)

        // Detection sensitivity: 0=Low(4), 1=Medium(3), 2=High(2)
        val sensitivity = prefs.getInt("detection_sensitivity", 1)
        consensusThreshold = when (sensitivity) {
            0 -> 4  // Low — more frames needed, fewer false positives
            2 -> 2  // High — fewer frames, more responsive
            else -> CONSENSUS_THRESHOLD // Medium (default)
        }

        val json = prefs.getString("baseline_metrics", null)
        if (json != null) {
            try {
                val obj = org.json.JSONObject(json)
                baseline = FaceBaseline(
                    smileProb = obj.getDouble("smileProb").toFloat(),
                    leftEyeOpen = obj.getDouble("leftEyeOpen").toFloat(),
                    rightEyeOpen = obj.getDouble("rightEyeOpen").toFloat(),
                    headPitch = obj.getDouble("headPitch").toFloat(),
                    headYaw = obj.getDouble("headYaw").toFloat(),
                    headRoll = obj.getDouble("headRoll").toFloat()
                )
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    companion object {
        private const val SMOOTHING_WINDOW = 5
        private const val CONSENSUS_THRESHOLD = 3 // Default: must appear >= 3 times in window

        internal fun getConsensusMood(moods: List<MoodResult>, threshold: Int = CONSENSUS_THRESHOLD): String? {
            if (moods.size < threshold) return null
            val counts = moods.groupBy { it.mood }.mapValues { it.value.size }
            val best = counts.maxByOrNull { it.value } ?: return null
            return if (best.value >= threshold) best.key else null
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0] // use first detected face

                    val smileProb = face.smilingProbability ?: 0.5f
                    val leftEye = face.leftEyeOpenProbability ?: 0.5f
                    val rightEye = face.rightEyeOpenProbability ?: 0.5f
                    val headX = face.headEulerAngleX // pitch
                    val headY = face.headEulerAngleY // yaw
                    val headZ = face.headEulerAngleZ // roll

                    // Blink/Wink detection logic
                    val isLeftEyeClosed = leftEye < 0.2f
                    val isRightEyeClosed = rightEye < 0.2f
                    val isWinking = (isLeftEyeClosed && rightEye > 0.6f) || (isRightEyeClosed && leftEye > 0.6f)
                    val isBlinkingNow = isLeftEyeClosed && isRightEyeClosed

                    val now = System.currentTimeMillis()
                    if (isBlinkingNow && !isCurrentlyBlinking) {
                        // Just started blinking
                        blinkTimestamps.addLast(now)
                    }
                    isCurrentlyBlinking = isBlinkingNow

                    // Remove blinks older than 5 seconds
                    while (blinkTimestamps.isNotEmpty() && now - blinkTimestamps.first() > 5000) {
                        blinkTimestamps.removeFirst()
                    }

                    val isRapidBlinking = blinkTimestamps.size >= 3

                    val result = MoodClassifier.classify(
                        smileProb, leftEye, rightEye,
                        headX, headY, headZ,
                        isWinking, isRapidBlinking, baseline
                    )

                    // Add to smoothing window
                    if (recentMoods.size >= SMOOTHING_WINDOW) {
                        recentMoods.removeFirst()
                    }
                    recentMoods.addLast(result)

                    // Only save when we have consensus
                    val consensusMood = getConsensusMood(recentMoods.toList(), consensusThreshold)
                    if (consensusMood != null && consensusMood != lastSavedMood) {
                        lastSavedMood = consensusMood
                        val avgConfidence = recentMoods
                            .filter { it.mood == consensusMood }
                            .map { it.confidence }
                            .average()
                            .toFloat()

                        scope.launch(Dispatchers.IO) {
                            repository.saveMood(
                                MoodEntry(
                                    mood = consensusMood,
                                    smileScore = smileProb,
                                    eyeOpenScore = (leftEye + rightEye) / 2f,
                                    confidence = avgConfidence
                                )
                            )
                            // Refresh home screen widget
                            MoodWidgetProvider.updateAllWidgets(context)
                        }
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close() // always close the image!
            }
    }

    /**
     * Returns the consensus mood if one mood appears >= CONSENSUS_THRESHOLD
     * times in the recent window. Returns null if no consensus.
     */
    // getConsensusMood is now in Companion
}