package com.example.basketballtrainer.analyzer

import com.example.basketballtrainer.model.DribbleHeight

class DribbleCounter(private val heightLimit: DribbleHeight) {

    var onDribble: (() -> Unit)? = null
    var onTooHigh: ((Boolean) -> Unit)? = null

    private val historySize = 6
    private val yHistory = ArrayDeque<Float>()

    private var segmentMinY = Float.MAX_VALUE
    private var lastCountMs = 0L
    private val debounceMs  = 300L

    fun update(ballCenterY: Float, refJointY: Float) {
        yHistory.addLast(ballCenterY)
        if (yHistory.size > historySize) yHistory.removeFirst()
        if (yHistory.size < 4) return

        segmentMinY = minOf(segmentMinY, ballCenterY)

        val recentList = yHistory.takeLast(3)
        val prevList   = yHistory.dropLast(3).takeLast(3)
        if (prevList.size < 2) return

        val recentAvg = recentList.average().toFloat()
        val prevAvg   = prevList.average().toFloat()

        val wasDescending = prevAvg   < recentAvg
        val isAscending   = recentAvg < prevAvg

        if (wasDescending && isAscending) {
            val tooHigh = segmentMinY < refJointY
            onTooHigh?.invoke(tooHigh)
            if (!tooHigh) {
                val now = System.currentTimeMillis()
                if (now - lastCountMs > debounceMs) {
                    lastCountMs = now
                    onDribble?.invoke()
                }
            }
            segmentMinY = Float.MAX_VALUE
        }
    }

    fun reset() {
        yHistory.clear()
        segmentMinY = Float.MAX_VALUE
        lastCountMs = 0L
    }
}