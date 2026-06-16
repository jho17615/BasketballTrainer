package com.example.basketballtrainer.analyzer

import android.graphics.RectF
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.basketballtrainer.model.DribbleHeight
import com.example.basketballtrainer.model.TrainingMode
import com.example.basketballtrainer.ui.components.PoseOverlayView
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * CameraX ImageAnalysis.Analyzer.
 *
 * 확정 사양:
 *  - MediaPipe Pose Landmarker (RunningMode.VIDEO) — 관절 추적
 *  - MediaPipe Object Detector (EfficientDet-Lite0, "sports ball" 카테고리) — 공 추적
 *    → 색상 기반(OpenCV HSV) 대신 모양/특징 기반 탐지로 전환.
 *      피부색, 옷, 조명에 흔들리지 않음.
 *  - 좌표는 (1 - y, x) 매핑으로 90도 회전 + 반전 보정
 */
class BasketballAnalyzer(
    private val context: android.content.Context,
    private val mode: TrainingMode,
    private val overlayView: PoseOverlayView,
    private val onDribble: () -> Unit,
    private val onAttempt: () -> Unit,
    private val onSuccess: () -> Unit,
    private val onTooHigh: (Boolean) -> Unit,
    private val onFps: (Int) -> Unit,
) : ImageAnalysis.Analyzer {

    private val poseLandmarker: PoseLandmarker by lazy {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.4f)
            .setMinTrackingConfidence(0.4f)
            .build()
        PoseLandmarker.createFromOptions(context, options)
    }

    private val ballDetector = BallDetector(context)

    private val dribbleCounter = if (mode is TrainingMode.Dribble)
        DribbleCounter(mode.limit).also {
            it.onDribble = onDribble
            it.onTooHigh = onTooHigh
        } else null

    private val shotCounter = if (mode is TrainingMode.Shoot)
        ShotCounter().also {
            it.onAttempt = onAttempt
            it.onSuccess = onSuccess
        } else null

    private var goalRoi: RectF? = null
    private val frameCount = AtomicLong(0)
    private var fpsWindowStart = System.currentTimeMillis()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val bitmap = runCatching { imageProxy.toBitmap() }.getOrNull()
        if (bitmap == null) { imageProxy.close(); return }

        // 회전 + 좌우 보정된 최종 표시 크기
        val finalW = bitmap.height
        val finalH = bitmap.width

        overlayView.setSourceSize(finalW, finalH)

        if (goalRoi == null) {
            goalRoi = RectF(finalW * 0.35f, 0f, finalW * 0.65f, finalH * 0.20f)
        }

        val frameTimeMs = imageProxy.imageInfo.timestamp / 1_000_000

        // 1) Pose 분석
        val mpResult: PoseLandmarkerResult? = runCatching {
            val mpImage = BitmapImageBuilder(bitmap).build()
            poseLandmarker.detectForVideo(mpImage, frameTimeMs)
        }.onFailure { Log.e("Analyzer", "Pose failed", it) }.getOrNull()

        val landmarks: FloatArray? = mpResult?.landmarks()?.firstOrNull()?.let { lms ->
            FloatArray(lms.size * 2) { i ->
                val lm = lms[i / 2]
                if (i % 2 == 0) 1f - lm.y() else lm.x()
            }
        }

        // 2) 공 검출 (MediaPipe Object Detector, bitmap 원본 좌표계)
        val rawBallBox = ballDetector.detect(bitmap, frameTimeMs)

        // bitmap(원본) 좌표 → 회전 보정된 표시 좌표로 변환
        // 회전 매핑: displayX = bitmap.height - rawY, displayY = rawX
        val ballBox: RectF? = rawBallBox?.let { r ->
            RectF(
                finalW - r.bottom, r.left,
                finalW - r.top,    r.right,
            )
        }
        val ballCenterY = ballBox?.centerY()

        if (landmarks != null && ballCenterY != null) {
            when (mode) {
                is TrainingMode.Dribble -> {
                    val refY = refJointY(landmarks, mode.limit, finalH)
                    if (refY != null) dribbleCounter?.update(ballCenterY, refY)
                }
                TrainingMode.Shoot -> {
                    val lwY = landmarkY(landmarks, LEFT_WRIST,  finalH)
                    val rwY = landmarkY(landmarks, RIGHT_WRIST, finalH)
                    if (lwY != null && rwY != null) {
                        shotCounter?.update(ballCenterY, lwY, rwY, ballBox, goalRoi!!)
                    }
                }
            }
        }

        val guidelineY: Float? = if (mode is TrainingMode.Dribble && landmarks != null)
            refJointY(landmarks, mode.limit, finalH) else null

        overlayView.setMode(mode)
        overlayView.pushFrame(
            normalizedLandmarks = landmarks,
            ballBoxPx           = ballBox,
            guidelineYpx        = guidelineY,
            roiPx               = if (mode is TrainingMode.Shoot) goalRoi else null,
        )

        val count   = frameCount.incrementAndGet()
        val elapsed = System.currentTimeMillis() - fpsWindowStart
        if (elapsed >= 1000L) {
            onFps((count * 1000L / elapsed).toInt())
            frameCount.set(0)
            fpsWindowStart = System.currentTimeMillis()
        }

        imageProxy.close()
    }

    private fun landmarkY(lm: FloatArray, idx: Int, imgH: Int): Float? {
        val yi = idx * 2 + 1
        if (yi >= lm.size) return null
        val y = lm[yi]
        return if (y.isNaN()) null else y * imgH
    }

    private fun refJointY(lm: FloatArray, limit: DribbleHeight, imgH: Int): Float? {
        val ly = landmarkY(lm, limit.leftLandmark,  imgH) ?: return null
        val ry = landmarkY(lm, limit.rightLandmark, imgH) ?: return null
        return (ly + ry) / 2f
    }

    fun release() {
        ballDetector.release()
        poseLandmarker.close()
        analysisExecutor.shutdown()
    }

    companion object {
        private const val LEFT_WRIST  = 15
        private const val RIGHT_WRIST = 16
    }
}