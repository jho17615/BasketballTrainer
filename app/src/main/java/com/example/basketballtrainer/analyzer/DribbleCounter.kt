package com.example.basketballtrainer.analyzer

import android.util.Log
import com.example.basketballtrainer.model.DribbleHeight

class DribbleCounter(private val heightLimit: DribbleHeight) {

    var onDribble: (() -> Unit)? = null
    var onTooHigh: ((Boolean) -> Unit)? = null

    private val historySize = 8
    private val yHistory = ArrayDeque<Float>()

    private var apexY = Float.MAX_VALUE
    private var lastCountMs = 0L
    private val debounceMs = 350L

    private var currentState = 0
    private var logFrameCount = 0

    fun update(ballCenterY: Float, refJointY: Float) {
        logFrameCount++
        if (logFrameCount % 30 == 0) {
            Log.d("DribbleCounter", "🔄 [실시간 트래킹] 공 중심 Y: ${String.format("%.1f", ballCenterY)}px | 가상 기준선 Y: ${String.format("%.1f", refJointY)}px")
        }

        yHistory.addLast(ballCenterY)
        if (yHistory.size > historySize) yHistory.removeFirst()
        if (yHistory.size < 6) return

        val half = yHistory.size / 2
        val prevAvg = yHistory.take(half).average().toFloat()
        val recentAvg = yHistory.takeLast(half).average().toFloat()

        val moveThreshold = 3.0f

        if (recentAvg - prevAvg > moveThreshold) {
            if (currentState == 2) {
                val now = System.currentTimeMillis()
                if (now - lastCountMs > debounceMs) {
                    val tooHigh = apexY < refJointY
                    onTooHigh?.invoke(tooHigh)

                    if (!tooHigh) {
                        onDribble?.invoke()
                        Log.d("DribbleCounter", "🏀 [성공] 드리블 바운드 한 사이클 정상 포착!")
                    } else {
                        Log.d("DribbleCounter", "⚠️ [무효] 공이 가상 가이드라인 위로 이탈함")
                    }
                    lastCountMs = now
                }
                apexY = Float.MAX_VALUE
            }
            currentState = 1
        } else if (prevAvg - recentAvg > moveThreshold) {
            currentState = 2
            apexY = minOf(apexY, ballCenterY)
        }
    }
}