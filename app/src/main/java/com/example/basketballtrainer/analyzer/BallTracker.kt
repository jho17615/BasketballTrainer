package com.example.basketballtrainer.analyzer

import android.graphics.RectF
import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * OpenCV HSV 컬러 트래킹 + 원형도(circularity) 검증으로 농구공을 추적한다.
 *
 * 디버깅 모드: DEBUG_LOG = true 로 두면 각 단계의 픽셀/후보 개수를
 * Logcat 태그 "BallTracker" 로 출력한다. 문제가 어느 단계에서
 * 막히는지(색 마스킹 / 면적 필터 / 원형도 필터) 확인하기 위함.
 */
class BallTracker {

    companion object {
        private const val DEBUG_LOG = true
    }

    // 오렌지 계열 — 일단 다시 넉넉하게 설정 (디버깅 우선)
    private val lowerOrange = Scalar(0.0,  80.0,  60.0)
    private val upperOrange = Scalar(30.0, 255.0, 255.0)

    private val hsvMat  = Mat()
    private val maskMat = Mat()
    private val kernel  = Imgproc.getStructuringElement(
        Imgproc.MORPH_ELLIPSE, Size(9.0, 9.0)
    )

    private val minCircularity = 0.5

    fun detect(rgbaMat: Mat, minRadiusPx: Float = 18f): RectF? {
        Imgproc.cvtColor(rgbaMat, hsvMat, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(hsvMat,  hsvMat, Imgproc.COLOR_RGB2HSV)
        Core.inRange(hsvMat, lowerOrange, upperOrange, maskMat)

        val maskPixels = Core.countNonZero(maskMat)
        if (DEBUG_LOG) Log.d("BallTracker", "1) mask nonzero pixels = $maskPixels / total ${maskMat.rows() * maskMat.cols()}")

        if (maskPixels == 0) return null

        Imgproc.morphologyEx(maskMat, maskMat, Imgproc.MORPH_OPEN,  kernel)
        Imgproc.morphologyEx(maskMat, maskMat, Imgproc.MORPH_CLOSE, kernel)

        val afterMorph = Core.countNonZero(maskMat)
        if (DEBUG_LOG) Log.d("BallTracker", "2) after morphology nonzero = $afterMorph")

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            maskMat, contours, Mat(),
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )
        if (DEBUG_LOG) Log.d("BallTracker", "3) contours found = ${contours.size}")
        if (contours.isEmpty()) return null

        val candidates = contours
            .map { it to Imgproc.contourArea(it) }
            .filter { it.second >= Math.PI * minRadiusPx * minRadiusPx }
            .sortedByDescending { it.second }
            .take(3)

        if (DEBUG_LOG) Log.d("BallTracker", "4) candidates after area filter = ${candidates.size}, areas = ${candidates.map { it.second }}")
        if (candidates.isEmpty()) return null

        var best: MatOfPoint? = null
        var bestCircularity = 0.0

        for ((contour, area) in candidates) {
            val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            if (perimeter <= 0.0) continue
            val circularity = 4 * Math.PI * area / (perimeter * perimeter)
            if (DEBUG_LOG) Log.d("BallTracker", "5) candidate area=$area perimeter=$perimeter circularity=$circularity")
            if (circularity >= minCircularity && circularity > bestCircularity) {
                bestCircularity = circularity
                best = contour
            }
        }

        val selected = best ?: run {
            if (DEBUG_LOG) Log.d("BallTracker", "6) no candidate passed circularity >= $minCircularity")
            return null
        }

        val point2f = MatOfPoint2f(*selected.toArray())
        val center  = Point()
        val radius  = FloatArray(1)
        Imgproc.minEnclosingCircle(point2f, center, radius)

        val r = radius[0]
        if (DEBUG_LOG) Log.d("BallTracker", "7) selected circle center=$center radius=$r")
        if (r < minRadiusPx) return null

        return RectF(
            (center.x - r).toFloat(),
            (center.y - r).toFloat(),
            (center.x + r).toFloat(),
            (center.y + r).toFloat(),
        )
    }

    fun release() {
        hsvMat.release()
        maskMat.release()
        kernel.release()
    }
}