package com.mirrormood.detection

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Lightweight face-presence analyzer for the calibration flow.
 * Captures baseline metrics over a sequence of frames.
 */
class CalibrationFaceAnalyzer(
    private val onFaceResult: (Boolean, FaceBaseline?) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    private val detector = FaceDetection.getClient(options)

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces.first()
                    val baseline = FaceBaseline(
                        smileProb = face.smilingProbability ?: 0f,
                        leftEyeOpen = face.leftEyeOpenProbability ?: 0f,
                        rightEyeOpen = face.rightEyeOpenProbability ?: 0f,
                        headPitch = face.headEulerAngleX,
                        headYaw = face.headEulerAngleY,
                        headRoll = face.headEulerAngleZ
                    )
                    onFaceResult(true, baseline)
                } else {
                    onFaceResult(false, null)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
