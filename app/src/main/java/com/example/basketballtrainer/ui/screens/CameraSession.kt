package com.example.basketballtrainer.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.basketballtrainer.analyzer.BasketballAnalyzer
import com.example.basketballtrainer.model.TrainingMode
import com.example.basketballtrainer.ui.components.PoseOverlayView
import com.example.basketballtrainer.viewmodel.TrainingViewModel
import java.util.concurrent.Executors

@Composable
fun CameraSession(
    mode: TrainingMode,
    onClose: () -> Unit,
    viewModel: TrainingViewModel = viewModel(),
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state          by viewModel.state.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(mode) {
        viewModel.startSession(mode)
        onDispose { viewModel.stopSession() }
    }

    if (!hasPermission) {
        PermissionDeniedScreen(
            onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onClose = onClose,
        )
        return
    }

    var isFrontCamera by remember { mutableStateOf(true) } // 기본 셀카 모드 시작

    val cameraSelector = if (isFrontCamera)
        CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val overlayView = remember { PoseOverlayView(context) }
    val cameraRef   = remember { mutableStateOf<Camera?>(null) }
    var zoomLevel   by remember { mutableFloatStateOf(1f) }

    val scaleDetector = remember {
        ScaleGestureDetector(context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val camera    = cameraRef.value ?: return true
                    val zoomState = camera.cameraInfo.zoomState.value ?: return true
                    val newZoom   = (zoomLevel * detector.scaleFactor)
                        .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                    camera.cameraControl.setZoomRatio(newZoom)
                    zoomLevel = newZoom
                    return true
                }
            })
    }

    LaunchedEffect(mode, isFrontCamera) {
        zoomLevel = 1f
        overlayView.setFrontCamera(isFrontCamera)

        bindCamera(
            context, lifecycleOwner, previewView, overlayView,
            mode, cameraSelector, viewModel, isFrontCamera // ✅ Analyzer에 전면 카메라 상태 전달
        ) { cam ->
            cameraRef.value = cam
            zoomLevel = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TrainingScreen(
            uiState       = state,
            onClose       = onClose,
            onBindPreview = { },
            onBindOverlay = { },
            previewView   = previewView,
            overlayView   = overlayView,
            scaleDetector = scaleDetector,
            zoomLevel     = zoomLevel,
            isFrontCamera = isFrontCamera,
            onFlipCamera  = { isFrontCamera = !isFrontCamera },
        )
    }
}

private fun bindCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    overlayView: PoseOverlayView,
    mode: TrainingMode,
    cameraSelector: CameraSelector,
    viewModel: TrainingViewModel,
    isFrontCamera: Boolean, // ✅ 추가됨
    onCameraReady: (Camera) -> Unit,
) {
    val analysisExecutor = Executors.newSingleThreadExecutor()
    val analyzer = BasketballAnalyzer(
        context       = context,
        mode          = mode,
        overlayView   = overlayView,
        isFrontCamera = isFrontCamera, // ✅ 추가됨
        onDribble     = { viewModel.onDribbleDetected() },
        onAttempt     = { viewModel.onShootAttempt() },
        onSuccess     = { viewModel.onShootSuccess() },
        onTooHigh     = { value -> viewModel.setTooHigh(value) },
        onFps         = { fps   -> viewModel.reportFps(fps) },
    )

    // 성능 및 모션블러 최적화 해상도
    val resolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(ResolutionStrategy(Size(480, 640), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
        .build()

    val preview = Preview.Builder()
        .setResolutionSelector(resolutionSelector)
        .build()
        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

    val imageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(resolutionSelector)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
        .also { it.setAnalyzer(analysisExecutor, analyzer) }

    ProcessCameraProvider.getInstance(context).addListener({
        try {
            val provider = ProcessCameraProvider.getInstance(context).get()
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, imageAnalysis,
            )
            onCameraReady(camera)
        } catch (e: Exception) {
            Log.e("CameraSession", "Camera bind failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}

@Composable
private fun PermissionDeniedScreen(onRetry: () -> Unit, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF1A1D22)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("카메라 권한이 필요합니다", color = Color.White,
                fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("농구공과 자세를 인식하려면\n카메라 접근 권한을 허용해주세요.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFBA7517), contentColor = Color.White)) {
                Text("권한 다시 요청")
            }
            Button(onClick = onClose, colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent, contentColor = Color.White)) {
                Text("돌아가기")
            }
        }
    }
}