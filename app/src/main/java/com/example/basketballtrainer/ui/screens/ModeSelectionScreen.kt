package com.example.basketballtrainer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.basketballtrainer.model.DribbleHeight
import com.example.basketballtrainer.model.TrainingMode

private val AccentOrange = Color(0xFFBA7517)
private val SoftOrangeBg = Color(0xFFFAEEDA)
private val CoralBg      = Color(0xFFFAECE7)
private val Surface      = Color(0xFFF7F5EF)
private val InkPrimary   = Color(0xFF1A1A1A)
private val InkSecondary = Color(0xFF6B6B6B)
private val ChipIdle     = Color(0xFFF1EFE8)

/**
 * 모드 선택 화면.
 * 갤럭시 폴드 펼침 상태(WindowWidthSizeClass.Expanded)에서는 두 카드가 가로로,
 * 일반 폰/접힘 상태에서는 세로로 배치된다.
 */
@Composable
fun ModeSelectionScreen(
    windowSizeClass: WindowSizeClass,
    initialMode: TrainingMode,
    onStart: (TrainingMode) -> Unit,
) {
    var selected by remember { mutableStateOf<TrainingMode>(initialMode) }

    Surface(color = Surface, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 24.dp)
        ) {
            // -------- Header --------
            Text(
                text = "BASKETBALL TRAINER",
                color = AccentOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "훈련 모드 선택",
                color = InkPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "카메라로 자세와 공을 인식합니다",
                color = InkSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(20.dp))

            // -------- Cards --------
            val isWide = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
            val cardModifier = if (isWide) Modifier.weight(1f) else Modifier.fillMaxWidth()
            val cardContainer: @Composable (@Composable () -> Unit) -> Unit = { content ->
                if (isWide) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { content() }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
                }
            }

            cardContainer {
                ShootCard(
                    modifier = cardModifier,
                    isSelected = selected is TrainingMode.Shoot,
                    onClick = { selected = TrainingMode.Shoot },
                )
                DribbleCard(
                    modifier = cardModifier,
                    selectedHeight = (selected as? TrainingMode.Dribble)?.limit,
                    onSelect = { selected = TrainingMode.Dribble(it) },
                )
            }

            Spacer(Modifier.weight(1f))

            // -------- Start button --------
            Button(
                onClick = { onStart(selected) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = InkPrimary,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("훈련 시작", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shoot card
// ---------------------------------------------------------------------------
@Composable
private fun ShootCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    ModeCard(
        modifier = modifier,
        isSelected = isSelected,
        onClick = onClick,
        accentBg = SoftOrangeBg,
        iconTint = Color(0xFF854F0B),
        title = "슛 카운팅",
        subtitle = "손목 위 호 + 골대 ROI 감지",
        iconLetter = "S",
    )
}

// ---------------------------------------------------------------------------
// Dribble card (expanded picker)
// ---------------------------------------------------------------------------
@Composable
private fun DribbleCard(
    modifier: Modifier = Modifier,
    selectedHeight: DribbleHeight?,
    onSelect: (DribbleHeight) -> Unit,
) {
    val isSelected = selectedHeight != null
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(
                width = if (isSelected) 2.dp else 0.5.dp,
                color = if (isSelected) AccentOrange else Color.Black.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable {
                if (selectedHeight == null) onSelect(DribbleHeight.HIP)
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(bg = CoralBg, tint = Color(0xFF993C1D), letter = "D")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("드리블 카운팅", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = InkPrimary)
                Text(
                    "기준 높이를 선택하세요",
                    fontSize = 12.sp,
                    color = InkSecondary,
                )
            }
            if (isSelected) SelectedBadge()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DribbleHeight.values().forEach { h ->
                HeightChip(
                    height = h,
                    selected = h == selectedHeight,
                    onClick = { onSelect(h) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeightChip(
    height: DribbleHeight,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) AccentOrange else ChipIdle
    val fg = if (selected) Color.White   else Color(0xFF444444)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (height) {
                DribbleHeight.KNEE -> "↓"
                DribbleHeight.HIP -> "—"
                DribbleHeight.SHOULDER -> "↑"
            },
            color = fg,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(height.koLabel, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------
@Composable
private fun ModeCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    iconLetter: String,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(
                width = if (isSelected) 2.dp else 0.5.dp,
                color = if (isSelected) AccentOrange else Color.Black.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(bg = accentBg, tint = iconTint, letter = iconLetter)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = InkPrimary)
            Text(subtitle, fontSize = 12.sp, color = InkSecondary)
        }
        Text("›", color = Color(0xFFAAAAAA), fontSize = 22.sp)
    }
}

@Composable
private fun IconBadge(bg: Color, tint: Color, letter: String) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SelectedBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(SoftOrangeBg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text("선택됨", color = Color(0xFF854F0B), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}