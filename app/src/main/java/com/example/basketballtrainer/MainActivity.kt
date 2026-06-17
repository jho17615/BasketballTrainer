package com.example.basketballtrainer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.basketballtrainer.model.DribbleHeight
import com.example.basketballtrainer.model.TrainingMode
import com.example.basketballtrainer.ui.screens.CameraSession
import com.example.basketballtrainer.ui.screens.ModeSelectionScreen
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. OpenCV 초기화 (가장 중요)
        if (!OpenCVLoader.initDebug()) {
            Log.e("MainActivity", "OpenCV 라이브러리 로드 실패")
        } else {
            Log.d("MainActivity", "OpenCV 라이브러리 로드 성공")
        }

        setContent {
            MaterialTheme {
                Surface(Modifier, color = MaterialTheme.colorScheme.background) {
                    val windowSizeClass = calculateWindowSizeClass(this)
                    var startedMode by remember { mutableStateOf<TrainingMode?>(null) }

                    if (startedMode == null) {
                        ModeSelectionScreen(
                            windowSizeClass = windowSizeClass,
                            initialMode = TrainingMode.Dribble(DribbleHeight.HIP),
                            onStart = { startedMode = it },
                        )
                    } else {
                        CameraSession(
                            mode = startedMode!!,
                            onClose = { startedMode = null },
                        )
                    }
                }
            }
        }
    }
}