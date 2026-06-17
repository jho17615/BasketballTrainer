package com.example.basketballtrainer.analyzer

import android.graphics.RectF
import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class BallTracker {

    // 농구공 주황색 범위 (현재 아주 잘 잡히고 있으므로 유지)
    private val lowerOrange = Scalar(2.0, 90.0, 80.0)
    private val upperOrange = Scalar(20.0, 255.0, 255.0)

    fun detect(mat: Mat): RectF? {
        val hsv = Mat()
        val mask = Mat()

        try {
            if (mat.channels() == 4) {
                Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_RGBA2RGB)
                Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)
            } else {
                Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_RGB2HSV)
            }

            Core.inRange(hsv, lowerOrange, upperOrange, mask)

            // 모폴로지 연산으로 자잘한 조명 노이즈 제거
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
            kernel.release()

            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            var bestContour: MatOfPoint? = null
            var maxArea = 0.0

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)

                // 📌 [튜닝 1] 최소 면적 완화
                // 멀리 떨어지거나 빠른 이동으로 흐려진 공도 잡을 수 있도록 최소 면적을 150에서 70으로 낮춥니다.
                if (area > 40000.0 || area < 70.0) continue

                val rect = Imgproc.boundingRect(contour)
                val aspectRatio = rect.width.toFloat() / rect.height.toFloat()

                // 📌 [📌 핵심 튜닝 2] 가로세로 비율(Aspect Ratio) 제약 대폭 완화
                // 드리블/슛을 할 때 공이 위아래나 좌우로 길게 늘어나는 '모션 블러' 현상을 수용합니다.
                // 기존 0.65~1.45 -> 변경 0.45~2.20 (길쭉해진 공도 농구공으로 인정)
                if (aspectRatio < 0.45f || aspectRatio > 2.20f) continue

                if (area > maxArea) {
                    maxArea = area
                    bestContour = contour
                }
            }

            if (bestContour == null) {
                return null
            }

            val rect = Imgproc.boundingRect(bestContour)
            Log.d("BallTracker", "🎯 [추적 성공] X: ${rect.x}, Y: ${rect.y}, W: ${rect.width}, H: ${rect.height} (면적: $maxArea)")

            return RectF(
                rect.x.toFloat(),
                rect.y.toFloat(),
                (rect.x + rect.width).toFloat(),
                (rect.y + rect.height).toFloat()
            )

        } catch (e: Exception) {
            Log.e("BallTracker", "🚨 에러 발생: ${e.message}", e)
            return null
        } finally {
            hsv.release()
            mask.release()
        }
    }

    fun release() {
        Log.d("BallTracker", "BallTracker 자원 해제 완료")
    }
}