package com.mirrormood.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TFLite-based mood classifier that runs a pre-trained facial expression
 * recognition model entirely on-device.
 *
 * Expected model: FER2013-based with 7 output classes mapped to MirrorMood's 6 moods.
 * Input: 48×48 grayscale image. Output: 7 probabilities.
 *
 * Falls back to [MoodClassifier.classifyHeuristic] if the model cannot be loaded.
 */
class TFLiteMoodClassifier(context: Context) {

    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    // FER2013 labels → MirrorMood mood mapping
    // FER: 0=Angry, 1=Disgust, 2=Fear, 3=Happy, 4=Sad, 5=Surprise, 6=Neutral
    private val ferToMirrorMood = mapOf(
        0 to "Stressed",   // Angry → Stressed
        1 to "Stressed",   // Disgust → Stressed
        2 to "Stressed",   // Fear → Stressed
        3 to "Happy",      // Happy → Happy
        4 to "Tired",      // Sad → Tired
        5 to "Focused",    // Surprise → Focused (alert state)
        6 to "Neutral"     // Neutral → Neutral
    )

    init {
        try {
            val model = loadModelFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(model, options)
            isModelLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
            isModelLoaded = false
        }
    }

    fun isAvailable(): Boolean = isModelLoaded

    /**
     * Classify a face crop bitmap into a mood.
     * The bitmap should contain just the face region.
     *
     * @param faceBitmap The face crop to classify
     * @param calibrationOffsets Per-mood adjustments learned from the user's calibration
     * @return MoodResult with the classified mood and confidence
     */
    fun classify(
        faceBitmap: Bitmap,
        calibrationOffsets: Map<String, Float> = emptyMap()
    ): MoodResult? {
        val interp = interpreter ?: return null

        // Resize to model input size
        val resized = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)

        // Convert to grayscale float buffer
        val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = resized.getPixel(x, y)
                // Convert to grayscale and normalize to [0, 1]
                val gray = (Color.red(pixel) * 0.299f +
                           Color.green(pixel) * 0.587f +
                           Color.blue(pixel) * 0.114f) / 255f
                inputBuffer.putFloat(gray)
            }
        }
        inputBuffer.rewind()

        // Run inference
        val output = Array(1) { FloatArray(NUM_CLASSES) }
        try {
            interp.run(inputBuffer, output)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        val probabilities = output[0]

        // Map FER classes to MirrorMood moods and aggregate
        val moodScores = mutableMapOf<String, Float>()
        probabilities.forEachIndexed { index, prob ->
            val mood = ferToMirrorMood[index] ?: "Neutral"
            moodScores[mood] = (moodScores[mood] ?: 0f) + prob
        }

        // Apply calibration offsets
        calibrationOffsets.forEach { (mood, offset) ->
            moodScores[mood] = (moodScores[mood] ?: 0f) + offset
        }

        // Add Bored as a derived class (low confidence in all strong emotions)
        val maxScore = moodScores.values.maxOrNull() ?: 0f
        if (maxScore < 0.4f) {
            moodScores["Bored"] = (moodScores["Bored"] ?: 0f) + 0.3f
        }

        // Select best mood
        val best = moodScores.maxByOrNull { it.value }
        return if (best != null) {
            MoodResult(mood = best.key, confidence = best.value.coerceIn(0f, 1f))
        } else {
            MoodResult(mood = "Neutral", confidence = 0.5f)
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    companion object {
        private const val MODEL_FILE = "mood_model.tflite"
        private const val INPUT_SIZE = 48
        private const val NUM_CLASSES = 7
    }
}
