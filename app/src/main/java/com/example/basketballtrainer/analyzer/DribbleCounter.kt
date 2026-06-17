package com.example.basketballtrainer.analyzer

import android.util.Log
import com.example.basketballtrainer.model.DribbleHeight

/**
 * 드리블 카운팅 로직 (v2 — 저점 사이클 + 기준선 침범 감지).
 *
 * 규칙:
 *  1. 공이 기준선(refJointY)보다 위로 올라가면 절대 안 됨 → 즉시 onTooHigh(true)
 *  2. 카운트는 "저점(바닥 찍고 다시 올라가기 시작하는 순간)"마다 +1
 *  3. 단, 그 사이클(이전 저점~지금 저점) 동안 한 번이라도 기준선을 넘었다면
 *     이번 카운트는 무효 처리 (사이클 자체를 버림)
 *
 * 좌표계 주의: 이미지 좌표는 아래로 갈수록 Y가 커짐.
 *  - "기준선보다 위" = ballY < refJointY
 *  - "저점(바닥)"     = Y가 가장 큰 지점 (local maximum of Y)
 */
class DribbleCounter(private val heightLimit: DribbleHeight) {

    /** 유효한 드리블 1회가 카운트될 때 */
    var onDribble: (() -> Unit)? = null

    /**
     * 기준선 침범 상태가 바뀔 때 호출.
     * true  = 방금 기준선을 넘어 위로 올라감 (반칙 시작)
     * false = 다시 기준선 아래로 내려와 정상 범위로 복귀
     */
    var onTooHigh: ((Boolean) -> Unit)? = null

    // ---- 스무딩 ----
    private val ballSmoothingSize = 5
    private val ballYHistory = ArrayDeque<Float>(ballSmoothingSize)

    private val lineSmoothingSize = 10
    private val lineYHistory = ArrayDeque<Float>(lineSmoothingSize)

    // ---- 사이클 상태 ----
    private var smoothedBallY: Float = Float.NaN
    private var direction = Direction.UNKNOWN          // 현재 공의 이동 방향
    private var cycleViolated = false                  // 이번 사이클에서 기준선 침범 있었는지
    private var isCurrentlyAboveLine = false            // 현재 기준선 위 상태인지 (이벤트 중복 방지용)

    private val directionChangeThreshold = 2.0f         // px, 노이즈 필터링용 최소 이동량

    private var lastCountMs = 0L
    private val debounceMs = 250L

    private enum class Direction { UNKNOWN, DOWN, UP }

    /**
     * @param ballCenterY 공 중심 Y (이미지 픽셀, 아래로 갈수록 커짐)
     * @param refJointY   기준선 Y (예: HIP/KNEE/SHOULDER 평균, 픽셀)
     */
    fun update(ballCenterY: Float, refJointY: Float) {
        // 1) 공 Y 스무딩
        ballYHistory.addLast(ballCenterY)
        if (ballYHistory.size > ballSmoothingSize) ballYHistory.removeFirst()
        val newSmoothedY = ballYHistory.average().toFloat()

        // 2) 기준선 Y 스무딩 (사람이 살짝 흔들려도 기준선이 덜 떨리게)
        lineYHistory.addLast(refJointY)
        if (lineYHistory.size > lineSmoothingSize) lineYHistory.removeFirst()
        val smoothedLineY = lineYHistory.average().toFloat()

        if (smoothedBallY.isNaN()) {
            smoothedBallY = newSmoothedY
            return
        }

        val delta = newSmoothedY - smoothedBallY
        smoothedBallY = newSmoothedY

        // 3) 기준선 침범 실시간 감지 (Y가 작을수록 위쪽 → 기준선보다 위 = ballY < lineY)
        val isAboveNow = smoothedBallY < smoothedLineY
        if (isAboveNow != isCurrentlyAboveLine) {
            isCurrentlyAboveLine = isAboveNow
            onTooHigh?.invoke(isAboveNow)
            if (isAboveNow) {
                cycleViolated = true
                Log.d("DribbleCounter", "⚠️ 기준선 침범 — 이번 사이클 무효 처리")
            }
        }

        // 4) 방향 전환 감지 (저점/고점 찾기)
        if (delta > directionChangeThreshold) {
            // 공이 아래로 이동 중 (Y 증가)
            direction = Direction.DOWN
        } else if (delta < -directionChangeThreshold) {
            // 공이 위로 이동 중 (Y 감소)
            if (direction == Direction.DOWN) {
                // 저점에서 상승으로 전환 = 바닥을 찍은 시점 → 사이클 종료 판정
                onLowPointReached()
            }
            direction = Direction.UP
        }
        // delta가 threshold 이내면 방향 유지 (노이즈 무시)
    }

    /** 공이 저점(바닥)을 찍고 다시 올라가기 시작하는 순간 호출됨 */
    private fun onLowPointReached() {
        val now = System.currentTimeMillis()
        if (now - lastCountMs < debounceMs) {
            // 너무 빠른 연속 트리거는 무시 (디바운스)
            cycleViolated = false
            return
        }

        if (!cycleViolated) {
            onDribble?.invoke()
            Log.d("DribbleCounter", "🏀 드리블 카운트 +1")
        } else {
            Log.d("DribbleCounter", "❌ 사이클 무효 (기준선 침범) — 카운트 안 함")
        }

        lastCountMs = now
        cycleViolated = false // 다음 사이클을 위해 리셋
    }

    /** 모드 전환/세션 재시작 시 상태 초기화 */
    fun reset() {
        ballYHistory.clear()
        lineYHistory.clear()
        smoothedBallY = Float.NaN
        direction = Direction.UNKNOWN
        cycleViolated = false
        isCurrentlyAboveLine = false
        lastCountMs = 0L
    }
}