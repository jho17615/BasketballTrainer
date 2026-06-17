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
import org.opencv.android.Utils
import org.opencv.core.Mat

class BasketballAnalyzer(
    private val context: android.content.Context,
    private val mode: TrainingMode,
    private val overlayView: PoseOverlayView,
    private val isFrontCamera: Boolean,
    private val onDribble: () -> Unit,
    private val onAttempt: () -> Unit,
    private val onSuccess: () -> Unit,
    private val onTooHigh: (Boolean) -> Unit,
    private val onFps: (Int) -> Unit
) : ImageAnalysis.Analyzer {

    private val poseLandmarker: PoseLandmarker by lazy {
        val baseOptions = BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.4f)
            .setMinTrackingConfidence(0.4f)
            .build()
        PoseLandmarker.createFromOptions(context, options)
    }

    private val ballTracker = BallTracker()

    // 📌 [프로퍼티 에러 완벽 해결] TrainingMode.Dribble의 실제 속성인 'limit'를 안전하게 연동
    private val dribbleHeight: DribbleHeight by lazy {
        if (mode is TrainingMode.Dribble) mode.limit else DribbleHeight.HIP
    }

    private val dribbleCounter by lazy {
        DribbleCounter(dribbleHeight).apply {
            this.onDribble = this@BasketballAnalyzer.onDribble
            this.onTooHigh = this@BasketballAnalyzer.onTooHigh
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val bitmap = runCatching { imageProxy.toBitmap() }.getOrNull()
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        overlayView.setMode(mode)
        overlayView.setFrontCamera(isFrontCamera)

        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val finalH = bitmap.height // 640
        val finalW = bitmap.width  // 480
        overlayView.setSourceSize(finalH, finalW)

        val frameTimeMs = imageProxy.imageInfo.timestamp / 1_000_000
        val mpImage = BitmapImageBuilder(bitmap).build()
        val mpResult: PoseLandmarkerResult? = runCatching {
            poseLandmarker.detectForVideo(mpImage, frameTimeMs)
        }.getOrNull()

        val landmarks = mpResult?.landmarks()?.firstOrNull()?.let { lms ->
            FloatArray(lms.size * 2) { i ->
                val lm = lms[i / 2]
                if (isFrontCamera) if (i % 2 == 0) lm.y() else 1f - lm.x()
                else if (i % 2 == 0) 1f - lm.y() else lm.x()
            }
        }

        val rawBallBox = ballTracker.detect(mat)
        val ballBox = rawBallBox?.let { r ->
            if (isFrontCamera) RectF(r.top, finalW - r.right, r.bottom, finalW - r.left)
            else RectF(finalH - r.bottom, r.left, finalH - r.top, r.right)
        }

        if (mode is TrainingMode.Dribble) {
            // 무릎(KNEE): 25,26번 | 어깨(SHOULDER): 11,12번 | 허리(HIP): 23,24번
            val (idx1, idx2) = when (dribbleHeight.name) {
                "KNEE" -> Pair(25, 26)
                "SHOULDER", "CHEST" -> Pair(11, 12)
                else -> Pair(23, 24)
            }

            val refJointY = landmarks?.let { lms ->
                if (lms.size > idx2 * 2) (lms[idx1 * 2 + 1] + lms[idx2 * 2 + 1]) / 2f else 0.5f
            } ?: 0.5f

            // 세로 화면 픽셀 매핑 스케일 보정 수식 반영 완료 (finalW가 렌더링 세로 스케일)
            val lineYPixels = refJointY * finalW

            overlayView.pushFrame(landmarks, ballBox, lineYPixels, null)

            if (ballBox != null) {
                dribbleCounter.update(ballBox.centerY(), lineYPixels)
            }
        } else {
            overlayView.pushFrame(landmarks, ballBox, null, null)
            if (ballBox != null && mode is TrainingMode.Shoot) {
                onAttempt()
                onSuccess()
            }
        }

        imageProxy.close()
        mat.release()
    }

    fun close() {
        poseLandmarker.close()
        ballTracker.release()
    }
}