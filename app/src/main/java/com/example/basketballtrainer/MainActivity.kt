package com.example.basketballtrainer

import android.os.Bundle
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

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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