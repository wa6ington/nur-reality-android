package com.realitycomposer.android.ui.viewport

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.realitycomposer.android.model.RealityScene
import com.realitycomposer.android.model.Vec3
import com.realitycomposer.android.ui.theme.RcColors

@Composable
fun ArLiveViewport(
    scene: RealityScene,
    selectedObjectId: String?,
    isPlaying: Boolean,
    useFrontCamera: Boolean,
    onPreviewViewReady: (PreviewView) -> Unit,
    onSelectObject: (String?) -> Unit,
    onCameraMoved: (Vec3) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RcColors.Background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .background(RcColors.Surface, RoundedCornerShape(18.dp))
                        .border(1.dp, RcColors.Border, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = RcColors.AccentBlue,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Доступ к камере",
                        color = RcColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Разрешите доступ к камере, чтобы выводить 3D-объекты, текст и рисунки прямо в камере.",
                        color = RcColors.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Разрешить камеру", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            // 1. Live CameraX Video Feed (Back / Front)
            LiveCameraPreview(
                useFrontCamera = useFrontCamera,
                onPreviewViewReady = onPreviewViewReady,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Transparent 3D Filament Layer directly on top of the live camera!
            Editor3DViewport(
                scene = scene,
                selectedObjectId = selectedObjectId,
                isPlaying = isPlaying,
                onSelectObject = onSelectObject,
                onCameraMoved = onCameraMoved,
                transparentBackground = true,
                showGridOverride = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun LiveCameraPreview(
    useFrontCamera: Boolean,
    onPreviewViewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    key(useFrontCamera) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PreviewView(ctx).apply {
                    // COMPATIBLE uses TextureView so Filament translucent 3D draws cleanly on top
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also(onPreviewViewReady)
            },
            update = { previewView ->
                onPreviewViewReady(previewView)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val desiredSelector = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        val selector = if (cameraProvider.hasCamera(desiredSelector)) {
                            desiredSelector
                        } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )
    }
}
