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

class BasketballAnalyzer(
    private val context: android.content.Context,
    private val mode: TrainingMode,
    private val overlayView: PoseOverlayView,
    private val isFrontCamera: Boolean, // ✅ 전면 카메라 여부
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

        // ✅ 90도 회전을 위해 가로/세로 길이를 스왑하여 뷰에 전달합니다.
        val finalW = bitmap.height
        val finalH = bitmap.width

        overlayView.setSourceSize(finalW, finalH)

        if (goalRoi == null || goalRoi?.right != finalW * 0.65f) {
            goalRoi = RectF(finalW * 0.35f, 0f, finalW * 0.65f, finalH * 0.20f)
        }

        val frameTimeMs = imageProxy.imageInfo.timestamp / 1_000_000

        // 1) Pose(스켈레톤) 분석
        val mpResult: PoseLandmarkerResult? = runCatching {
            val mpImage = BitmapImageBuilder(bitmap).build()
            poseLandmarker.detectForVideo(mpImage, frameTimeMs)
        }.onFailure { Log.e("Analyzer", "Pose failed", it) }.getOrNull()

        // ✅ 핵심: 물구나무는 이미 고쳤고, 이제 좌우 꼬임(이중 거울 현상)을 완벽하게 풉니다.
        val landmarks: FloatArray? = mpResult?.landmarks()?.firstOrNull()?.let { lms ->
            FloatArray(lms.size * 2) { i ->
                val lm = lms[i / 2]
                if (isFrontCamera) {
                    // [전면 카메라] X를 그냥 lm.y()로 넘기면 PoseOverlayView가 알아서 예쁘게 거울모드로 만들어줍니다!
                    if (i % 2 == 0) lm.y() else 1f - lm.x()
                } else {
                    // [후면 카메라]
                    if (i % 2 == 0) 1f - lm.y() else lm.x()
                }
            }
        }

        // 2) 공 검출
        val rawBallBox = ballDetector.detect(bitmap, frameTimeMs)

        // ✅ 농구공 박스도 스켈레톤의 X축 변경 사항과 100% 동일하게 매핑하여 공과 손이 딱 맞게 수정했습니다.
        val ballBox: RectF? = rawBallBox?.let { r ->
            if (isFrontCamera) {
                // 전면 카메라의 바운딩 박스 변환 (X = Y, Y = 1 - X)
                RectF(
                    r.top,             finalH - r.right,
                    r.bottom,          finalH - r.left
                )
            } else {
                // 후면 카메라의 바운딩 박스 변환 (X = 1 - Y, Y = X)
                RectF(
                    finalW - r.bottom, r.left,
                    finalW - r.top,    r.right
                )
            }
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