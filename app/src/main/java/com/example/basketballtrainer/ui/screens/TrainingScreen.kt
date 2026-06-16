package com.example.basketballtrainer.ui.screens

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.basketballtrainer.model.TrainingMode
import com.example.basketballtrainer.model.TrainingUiState
import com.example.basketballtrainer.ui.components.PoseOverlayView

@Composable
fun TrainingScreen(
    uiState: TrainingUiState,
    onClose: () -> Unit,
    onBindPreview: (PreviewView) -> Unit,
    onBindOverlay: (PoseOverlayView) -> Unit,
    previewView: PreviewView? = null,
    overlayView: PoseOverlayView? = null,
    scaleDetector: ScaleGestureDetector? = null,
    zoomLevel: Float = 1f,
    isFrontCamera: Boolean = false,
    onFlipCamera: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1D22))
    ) {
        // 카메라 프리뷰 + 오버레이 + 핀치줌 터치 컨테이너
        AndroidView(
            factory = { ctx ->
                object : FrameLayout(ctx) {
                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        scaleDetector?.onTouchEvent(event)
                        return true
                    }
                }.apply {
                    val pv = previewView ?: PreviewView(ctx).also(onBindPreview)
                    addView(pv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                    val ov = overlayView ?: PoseOverlayView(ctx).also(onBindOverlay)
                    addView(ov, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        TopBar(
            mode          = uiState.mode,
            onClose       = onClose,
            isFrontCamera = isFrontCamera,
            onFlipCamera  = onFlipCamera,
        )
        SideStats(fps = uiState.fps, count = uiState.primaryCount())
        BottomStats(uiState)

        // 줌 레벨 배지
        if (zoomLevel != 1f) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    "%.1fx".format(zoomLevel),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Too High 경고
        AnimatedVisibility(
            visible = uiState.tooHigh,
            enter = fadeIn(),
            exit  = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCC991F1F))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text("Too High!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ---------------------------------------------------------------------------
@Composable
private fun BoxScope.TopBar(
    mode: TrainingMode,
    onClose: () -> Unit,
    isFrontCamera: Boolean,
    onFlipCamera: () -> Unit,
) {
    Row(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 모드 배지
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF5DCAA5)))
            Spacer(Modifier.width(6.dp))
            Text(mode.koLabel(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 카메라 전환 버튼
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onFlipCamera),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isFrontCamera) "후" else "전",
                    color = Color(0xFFFAC775),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            // 닫기 버튼
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun BoxScope.SideStats(fps: Int, count: Int) {
    Box(
        Modifier
            .align(Alignment.TopStart)
            .padding(start = 14.dp, top = 64.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text("$fps FPS", color = Color(0xFF9FE1CB), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .padding(end = 14.dp, top = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text("COUNT", color = Color(0xFFFAC775), fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
        Text(count.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BoxScope.BottomStats(state: TrainingUiState) {
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val (a, b, c) = when (state.mode) {
            is TrainingMode.Shoot -> Triple(
                "시도" to state.shootAttempt.toString(),
                "성공" to state.shootSuccess.toString(),
                "성공률" to "${if (state.shootAttempt == 0) 0 else state.shootSuccess * 100 / state.shootAttempt}%",
            )
            is TrainingMode.Dribble -> Triple(
                "카운트" to state.dribbleCount.toString(),
                "기준" to state.mode.limit.koLabel,
                "세션" to formatElapsed(state.elapsedSec),
            )
        }
        StatChip(a.first, a.second, Modifier.weight(1f))
        StatChip(b.first, b.second, Modifier.weight(1f))
        StatChip(c.first, c.second, Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = Color(0xFFAAAAAA), fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun TrainingMode.koLabel(): String = when (this) {
    TrainingMode.Shoot -> "슛 모드"
    is TrainingMode.Dribble -> "드리블 · ${limit.koLabel}"
}

private fun TrainingUiState.primaryCount(): Int = when (mode) {
    TrainingMode.Shoot -> shootSuccess
    is TrainingMode.Dribble -> dribbleCount
}

private fun formatElapsed(sec: Long): String = "%02d:%02d".format(sec / 60, sec % 60)