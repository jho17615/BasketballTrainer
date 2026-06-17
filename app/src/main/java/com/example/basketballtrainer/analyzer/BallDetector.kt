package com.example.basketballtrainer.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector.ObjectDetectorOptions

/**
 * MediaPipe Object Detection (EfficientDet-Lite0) 으로 "sports ball" 카테고리만 검출.
 *
 * 검증 결과(실측):
 *  - 공이 화면에 크고 가깝게 보일 때 score 0.7+ 로 정확히 잡힘
 *  - 공이 작거나 멀 때는 score 0.05~0.16 정도로 낮게 나옴 (모델 한계)
 *  - teddy bear, clock 등 오검출은 score 0.4 미만에서 흔함
 *
 * → allowlist 로 "sports ball" 만 후보로 좁히고,
 *    threshold 0.15 로 설정해 "멀어도 진짜 공이면 잡고,
 *    완전히 다른 물체의 노이즈는 거의 거른다".
 *    (단, 같은 점수대에 다른 카테고리 노이즈가 있어도 allowlist가 있으면
 *    sports ball 만 후보로 나오므로 신뢰도가 더 안전해짐)
 */
class BallDetector(context: Context) {

    companion object {
        private const val TAG = "BallDetector"
        private const val DEBUG_LOG = true
        private const val SCORE_THRESHOLD = 0.08f
    }

    private var initError: Throwable? = null

    private val detector: ObjectDetector? by lazy {
        runCatching {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("efficientdet_lite0.tflite")
                .build()
            val options = ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setMaxResults(5)
                .setScoreThreshold(SCORE_THRESHOLD)
                .setCategoryAllowlist(listOf("sports ball"))
                .build()
            ObjectDetector.createFromOptions(context, options).also {
                if (DEBUG_LOG) Log.d(TAG, "ObjectDetector created (threshold=$SCORE_THRESHOLD, allowlist=sports ball)")
            }
        }.onFailure {
            initError = it
            Log.e(TAG, "ObjectDetector init FAILED", it)
        }.getOrNull()
    }

    private var frameCounter = 0

    fun detect(bitmap: Bitmap, timestampMs: Long): RectF? {
        val det = detector
        if (det == null) {
            if (DEBUG_LOG && frameCounter % 30 == 0) {
                Log.e(TAG, "Detector is null, init error: ${initError?.message}")
            }
            frameCounter++
            return null
        }

        return runCatching {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = det.detectForVideo(mpImage, timestampMs)
            val detections = result.detections()

            if (DEBUG_LOG && frameCounter % 15 == 0) {
                if (detections.isEmpty()) {
                    Log.d(TAG, "frame=$frameCounter no ball detected")
                } else {
                    val best = detections.maxByOrNull { it.categories().first().score() }
                    Log.d(TAG, "frame=$frameCounter ball score=${best?.categories()?.first()?.score()} box=${best?.boundingBox()}")
                }
            }
            frameCounter++

            detections
                .maxByOrNull { it.categories().firstOrNull()?.score() ?: 0f }
                ?.boundingBox()
        }.onFailure {
            Log.e(TAG, "detectForVideo failed", it)
        }.getOrNull()
    }

    fun release() {
        detector?.close()
    }
}