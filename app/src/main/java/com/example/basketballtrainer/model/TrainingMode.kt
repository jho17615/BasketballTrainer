package com.example.basketballtrainer.model

import androidx.compose.runtime.Immutable

/**
 * 사용자가 선택할 수 있는 훈련 모드.
 * Shoot 또는 Dribble(3가지 기준 높이) 중 하나.
 */
sealed interface TrainingMode {
    data object Shoot : TrainingMode

    data class Dribble(val limit: DribbleHeight) : TrainingMode
}

/** 드리블 기준 높이 — MediaPipe 랜드마크 인덱스와 1:1 매핑된다. */
enum class DribbleHeight(
    val label: String,
    val koLabel: String,
    /** 비교 기준이 되는 좌/우 랜드마크 인덱스 (MediaPipe Pose 33-point). */
    val leftLandmark: Int,
    val rightLandmark: Int,
) {
    KNEE("Below Knee", "무릎 아래", leftLandmark = 25, rightLandmark = 26),
    HIP("Below Hip",  "허리 아래", leftLandmark = 23, rightLandmark = 24),
    SHOULDER("Below Shoulder", "가슴 아래", leftLandmark = 11, rightLandmark = 12),
}

/** 화면에 표시되는 카운트/경고 상태. UI는 이 객체만 구독한다. */
@Immutable
data class TrainingUiState(
    val mode: TrainingMode = TrainingMode.Dribble(DribbleHeight.HIP),
    val dribbleCount: Int = 0,
    val shootAttempt: Int = 0,
    val shootSuccess: Int = 0,
    val tooHigh: Boolean = false,
    val fps: Int = 0,
    val elapsedSec: Long = 0L,
)