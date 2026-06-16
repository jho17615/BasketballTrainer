package com.example.basketballtrainer.analyzer

import android.graphics.RectF

/**
 * 슛 카운팅 알고리즘.
 *
 * [슛 시도 감지]
 *  - 공의 Y가 양손 손목 Y 평균보다 높아지면(Y값 작아지면) 상승 시작으로 판정.
 *  - 이후 공이 하강 전환될 때(Y 최솟값 통과) → Shoot_Attempt 카운트.
 *
 * [슛 성공 감지]
 *  - 화면 상단에 고정된 골대 ROI(사각형)를 공 바운딩 박스가 통과하면 → Shoot_Success.
 *  - 한 번의 시도에서 중복 성공 카운트 방지를 위해 attemptCooldown 사용.
 */
class ShotCounter {

    var onAttempt: (() -> Unit)? = null
    var onSuccess: (() -> Unit)? = null

    private val historySize = 8
    private val yHistory    = ArrayDeque<Float>(historySize)

    private var segmentMinY    = Float.MAX_VALUE
    private var lastAttemptMs  = 0L
    private val debounceMs     = 500L

    // 성공 판정 후 쿨다운 (같은 슛에서 여러 번 성공 카운트 방지)
    private var successCooldownMs = 0L
    private val successCooldown   = 1500L

    /**
     * @param ballCenterY  공 중심 Y (이미지 픽셀)
     * @param leftWristY   왼손 손목 Y
     * @param rightWristY  오른손 손목 Y
     * @param ballBox      공 바운딩 박스 (null 이면 공 미감지)
     * @param goalRoi      골대 ROI (이미지 픽셀 좌표)
     */
    fun update(
        ballCenterY: Float,
        leftWristY: Float,
        rightWristY: Float,
        ballBox: RectF?,
        goalRoi: RectF,
    ) {
        val wristAvgY = (leftWristY + rightWristY) / 2f
        yHistory.addLast(ballCenterY)
        if (yHistory.size > historySize) yHistory.removeFirst()
        if (yHistory.size < 4) return

        segmentMinY = minOf(segmentMinY, ballCenterY)

        // 방향 전환 감지
        val recent = yHistory.takeLast(3).average().toFloat()
        val prev   = yHistory.dropLast(3).takeLast(3).let {
            if (it.size < 2) return
            it.average().toFloat()
        }

        val wasAscending  = prev   > recent   // 이전: Y 감소(상승)
        val isDescending  = recent > prev      // 현재: Y 증가(하강)

        // 슛 시도: 공이 손목보다 높이 올라갔다가 내려오는 전환점
        if (wasAscending && isDescending && segmentMinY < wristAvgY) {
            val now = System.currentTimeMillis()
            if (now - lastAttemptMs > debounceMs) {
                lastAttemptMs = now
                onAttempt?.invoke()
            }
            segmentMinY = Float.MAX_VALUE
        }

        // 슛 성공: 공 박스가 골대 ROI 와 겹침
        if (ballBox != null) {
            val now = System.currentTimeMillis()
            if (now - successCooldownMs > successCooldown && RectF.intersects(ballBox, goalRoi)) {
                successCooldownMs = now
                onSuccess?.invoke()
            }
        }
    }

    fun reset() {
        yHistory.clear()
        segmentMinY   = Float.MAX_VALUE
        lastAttemptMs = 0L
        successCooldownMs = 0L
    }
}