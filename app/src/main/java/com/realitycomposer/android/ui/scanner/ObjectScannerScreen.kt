package com.realitycomposer.android.ui.scanner

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.realitycomposer.android.model.ColoredPoint3D
import com.realitycomposer.android.model.ScanStep
import com.realitycomposer.android.model.ScannedObject
import com.realitycomposer.android.scanner.CapturedAngleFrame
import com.realitycomposer.android.scanner.ObjectCaptureEngine
import com.realitycomposer.android.ui.theme.RcColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private data class CameraTextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val color: Color,
    val x: Float,
    val y: Float
)

private data class CameraDrawStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float
)

private val ScannerPalette = listOf(
    Color(0xFFFFD60A),
    Color(0xFFFFFFFF),
    Color(0xFF0A84FF),
    Color(0xFFFF453A),
    Color(0xFF30D158),
    Color(0xFFBF5AF2),
    Color(0xFFFF9F0A),
    Color(0xFF000000)
)

@Composable
fun ObjectScannerScreen(
    onCancel: () -> Unit,
    onScanCompleted: (ScannedObject) -> Unit,
    onExportCameraImage: (File, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureEngine = remember { ObjectCaptureEngine(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var step by remember { mutableStateOf(ScanStep.DETECT_OBJECT) }
    var useFrontCamera by remember { mutableStateOf(false) }

    // Bounding Box proportions (adjustable in Step 2)
    var boxWidth by remember { mutableStateOf(0.58f) }
    var boxHeight by remember { mutableStateOf(0.46f) }

    // 360° Scanning State (Step 3)
    val totalSectors = 24
    val capturedSectors = remember { mutableStateListOf<Int>() }
    val capturedFrames = remember { mutableStateListOf<CapturedAngleFrame>() }
    val livePreviewPoints = remember { mutableStateListOf<ColoredPoint3D>() }
    var autoCaptureRunning by remember { mutableStateOf(true) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // Camera Text & Drawing Overlay State (works right inside 3D Scanner!)
    val overlayTexts = remember { mutableStateListOf<CameraTextOverlay>() }
    val drawStrokes = remember { mutableStateListOf<CameraDrawStroke>() }
    var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isDrawMode by remember { mutableStateOf(false) }
    var activeBrushColor by remember { mutableStateOf(ScannerPalette[0]) }
    var brushWidth by remember { mutableStateOf(10f) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var screenSize by remember { mutableStateOf(IntSize(1080, 1920)) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun saveCameraWithOverlays(format: Bitmap.CompressFormat, ext: String) {
        val w = screenSize.width.coerceAtLeast(720)
        val h = screenSize.height.coerceAtLeast(1280)
        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(outBmp)

        // 1. Draw live camera frame
        val camBmp = previewViewRef?.bitmap
        if (camBmp != null) {
            canvas.drawBitmap(camBmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
        } else {
            canvas.drawColor(android.graphics.Color.BLACK)
        }

        // 2. Draw all finger strokes
        for (stroke in drawStrokes) {
            if (stroke.points.isEmpty()) continue
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                strokeWidth = stroke.width
                style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
            }
            if (stroke.points.size == 1) {
                paint.style = AndroidPaint.Style.FILL
                canvas.drawCircle(stroke.points[0].x, stroke.points[0].y, stroke.width / 2f, paint)
            } else {
                val p = AndroidPath().apply {
                    moveTo(stroke.points[0].x, stroke.points[0].y)
                    for (i in 1 until stroke.points.size) lineTo(stroke.points[i].x, stroke.points[i].y)
                }
                canvas.drawPath(p, paint)
            }
        }

        // 3. Draw all floating text labels
        for (item in overlayTexts) {
            val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = item.color.toArgb()
                textSize = 54f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(8f, 0f, 3f, android.graphics.Color.BLACK)
            }
            val padX = 24f
            val padY = 18f
            val textW = textPaint.measureText(item.text)
            val bgRect = RectF(item.x, item.y, item.x + textW + padX * 2, item.y + 72f + padY)
            val bgPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(165, 18, 18, 22)
            }
            canvas.drawRoundRect(bgRect, 20f, 20f, bgPaint)
            canvas.drawText(item.text, item.x + padX, item.y + 56f, textPaint)
        }

        val fileName = "NUR_Cam_${System.currentTimeMillis()}.$ext"
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, fileName)
        FileOutputStream(file).use { out -> outBmp.compress(format, 95, out) }

        val mime = if (ext == "png") "image/png" else "image/jpeg"
        saveScannerBitmapToGallery(context, outBmp, fileName, format, mime)
        toastMessage = "Сохранено в Галерею: $fileName"
        onExportCameraImage(file, mime)
    }

    // Auto-capture loop during SCANNING_360
    LaunchedEffect(step, autoCaptureRunning) {
        if (step != ScanStep.SCANNING_360 || !autoCaptureRunning) return@LaunchedEffect
        while (step == ScanStep.SCANNING_360 && capturedSectors.size < totalSectors && autoCaptureRunning) {
            delay(420L)
            val pv = previewViewRef ?: continue
            val rawBmp = pv.bitmap ?: continue
            val scaledBmp = Bitmap.createScaledBitmap(rawBmp, 160, 210, true)
            val sectorIdx = capturedSectors.size
            val angleDeg = (sectorIdx * 360f) / totalSectors
            capturedSectors.add(sectorIdx)
            capturedFrames.add(CapturedAngleFrame(angleDeg, scaledBmp))

            val slice = captureEngine.extractLiveSlicePoints(scaledBmp, angleDeg, boxWidth, boxHeight)
            if (livePreviewPoints.size > 900) {
                livePreviewPoints.removeRange(0, (livePreviewPoints.size - 700).coerceAtLeast(0))
            }
            livePreviewPoints.addAll(slice)
        }
    }

    fun finishCaptureAndReconstruct() {
        step = ScanStep.PROCESSING
        scope.launch {
            val scan = captureEngine.reconstructAndSave(
                name = "3D-Скан ${System.currentTimeMillis() % 1000}",
                frames = capturedFrames.toList(),
                boxWidthRatio = boxWidth,
                boxHeightRatio = boxHeight
            )
            onScanCompleted(scan)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .onSizeChanged { screenSize = it }
    ) {
        if (!hasCameraPermission) {
            // Permission screen
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(RcColors.Surface, RoundedCornerShape(18.dp))
                    .border(1.dp, RcColors.Border, RoundedCornerShape(18.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.CameraAlt, null, tint = RcColors.AccentBlue, modifier = Modifier.size(44.dp))
                Text("Нужен доступ к камере", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "Для 3D-сканирования объектов камерой (Object Capture) разрешите доступ к камере.",
                    color = RcColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onCancel) { Text("Отмена", color = RcColors.TextSecondary) }
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue)
                    ) { Text("Разрешить") }
                }
            }
            return@Box
        }

        // 1. Live Fullscreen CameraX Feed
        ScannerCameraView(
            useFrontCamera = useFrontCamera,
            onPreviewViewReady = { previewViewRef = it },
            modifier = Modifier.fillMaxSize()
        )

        // 1b. Live Drawing Canvas Overlay directly on top of the Camera!
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isDrawMode) {
                        Modifier.pointerInput(activeBrushColor, brushWidth) {
                            detectDragGestures(
                                onDragStart = { offset -> currentStrokePoints = listOf(offset) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStrokePoints = currentStrokePoints + change.position
                                },
                                onDragEnd = {
                                    if (currentStrokePoints.isNotEmpty()) {
                                        drawStrokes.add(CameraDrawStroke(currentStrokePoints, activeBrushColor, brushWidth))
                                    }
                                    currentStrokePoints = emptyList()
                                },
                                onDragCancel = { currentStrokePoints = emptyList() }
                            )
                        }
                    } else Modifier
                )
        ) {
            fun drawOne(pts: List<Offset>, col: Color, w: Float) {
                if (pts.size == 1) {
                    drawCircle(col, w / 2f, pts[0])
                } else if (pts.size > 1) {
                    val p = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                    }
                    drawPath(p, col, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            for (s in drawStrokes) drawOne(s.points, s.color, s.width)
            if (currentStrokePoints.isNotEmpty()) drawOne(currentStrokePoints, activeBrushColor, brushWidth)
        }

        // 1c. Draggable Text Labels directly on the Camera!
        overlayTexts.forEachIndexed { index, item ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(item.x.roundToInt(), item.y.roundToInt()) }
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(1.dp, item.color.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                    .pointerInput(item.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val cur = overlayTexts.getOrNull(index) ?: return@detectDragGestures
                            overlayTexts[index] = cur.copy(
                                x = (cur.x + dragAmount.x).coerceIn(0f, screenSize.width.toFloat() - 80f),
                                y = (cur.y + dragAmount.y).coerceIn(0f, screenSize.height.toFloat() - 80f)
                            )
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.text,
                        color = item.color,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Удалить текст",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { overlayTexts.removeAt(index) }
                    )
                }
            }
        }

        // 2. Step-specific Overlays (hide scanning hints while user is actively drawing on camera)
        if (!isDrawMode || step == ScanStep.PROCESSING) {
            when (step) {
                ScanStep.DETECT_OBJECT -> {
                    Step1DetectOverlay(
                        onCancel = onCancel,
                        onNext = { step = ScanStep.ADJUST_BOUNDING_BOX },
                        onSwitchCamera = { useFrontCamera = !useFrontCamera }
                    )
                }

                ScanStep.ADJUST_BOUNDING_BOX -> {
                    Step2BoundingBoxOverlay(
                        boxWidth = boxWidth,
                        boxHeight = boxHeight,
                        onBoxWidthChange = { boxWidth = it },
                        onBoxHeightChange = { boxHeight = it },
                        onCancel = onCancel,
                        onStartScan = {
                            capturedSectors.clear()
                            capturedFrames.clear()
                            livePreviewPoints.clear()
                            autoCaptureRunning = true
                            step = ScanStep.SCANNING_360
                        }
                    )
                }

                ScanStep.SCANNING_360 -> {
                    Step3ScanningOverlay(
                        capturedCount = capturedSectors.size,
                        totalSectors = totalSectors,
                        livePoints = livePreviewPoints,
                        autoCapture = autoCaptureRunning,
                        onToggleAuto = { autoCaptureRunning = !autoCaptureRunning },
                        onManualCapture = {
                            val pv = previewViewRef ?: return@Step3ScanningOverlay
                            val rawBmp = pv.bitmap ?: return@Step3ScanningOverlay
                            if (capturedSectors.size < totalSectors) {
                                val scaledBmp = Bitmap.createScaledBitmap(rawBmp, 160, 210, true)
                                val idx = capturedSectors.size
                                val angle = (idx * 360f) / totalSectors
                                capturedSectors.add(idx)
                                capturedFrames.add(CapturedAngleFrame(angle, scaledBmp))
                                livePreviewPoints.addAll(
                                    captureEngine.extractLiveSlicePoints(scaledBmp, angle, boxWidth, boxHeight)
                                )
                            }
                        },
                        onCancel = onCancel,
                        onFinish = { finishCaptureAndReconstruct() }
                    )
                }

                ScanStep.PROCESSING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                            Text(
                                text = "Создание 3D-модели...",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Обработка ${capturedFrames.size} ракурсов и построение 3D-сетки (.OBJ)",
                                color = RcColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                ScanStep.RESULT_VIEWER -> Unit
            }
        }

        // 3. Right-side Floating Camera Tools: Text (T), Draw (Brush), Save Photo (PNG/JPG)
        if (step != ScanStep.PROCESSING) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ScannerCircleToolButton(
                    icon = Icons.Default.TextFields,
                    label = "Текст",
                    active = false,
                    onClick = { showAddTextDialog = true }
                )
                ScannerCircleToolButton(
                    icon = Icons.Default.Brush,
                    label = if (isDrawMode) "Готово" else "Рисовать",
                    active = isDrawMode,
                    onClick = { isDrawMode = !isDrawMode }
                )
                ScannerCircleToolButton(
                    icon = Icons.Default.SaveAlt,
                    label = "PNG/JPG",
                    active = false,
                    onClick = { showExportDialog = true }
                )
            }
        }

        // 4. Draw Mode Palette & Thickness Bar (when drawing on camera)
        if (isDrawMode && step != ScanStep.PROCESSING) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp, start = 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, RcColors.AccentBlue.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(ScannerPalette) { col ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (activeBrushColor == col) 2.5.dp else 1.dp,
                                        color = if (activeBrushColor == col) RcColors.AccentBlue else Color.White.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable { activeBrushColor = col }
                            )
                        }
                    }
                    IconButton(
                        onClick = { if (drawStrokes.isNotEmpty()) drawStrokes.removeAt(drawStrokes.lastIndex) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Отменить", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { drawStrokes.clear() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Очистить", tint = RcColors.DangerRed, modifier = Modifier.size(18.dp))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Кисть", color = RcColors.TextSecondary, fontSize = 11.sp)
                    Slider(
                        value = brushWidth,
                        onValueChange = { brushWidth = it },
                        valueRange = 4f..32f,
                        modifier = Modifier.weight(1f).height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = activeBrushColor, activeTrackColor = activeBrushColor)
                    )
                    Text("${brushWidth.toInt()}px", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        // Toast confirmation banner
        toastMessage?.let { msg ->
            LaunchedEffect(msg) {
                delay(2500L)
                toastMessage = null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 114.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(msg, color = RcColors.PlayGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Dialog: Add Text onto Live Camera
        if (showAddTextDialog) {
            var input by remember { mutableStateOf("NUR ADVERTISING") }
            var chosenColor by remember { mutableStateOf(ScannerPalette[0]) }
            AlertDialog(
                onDismissRequest = { showAddTextDialog = false },
                containerColor = RcColors.Surface,
                title = { Text("Добавить текст на камеру", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            label = { Text("Введите текст") },
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ScannerPalette) { col ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            if (chosenColor == col) 2.5.dp else 1.dp,
                                            if (chosenColor == col) RcColors.AccentBlue else RcColors.Border,
                                            CircleShape
                                        )
                                        .clickable { chosenColor = col }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (input.isNotBlank()) {
                                overlayTexts.add(
                                    CameraTextOverlay(
                                        text = input.trim(),
                                        color = chosenColor,
                                        x = screenSize.width * 0.2f,
                                        y = screenSize.height * 0.32f + overlayTexts.size * 70f
                                    )
                                )
                            }
                            showAddTextDialog = false
                        }
                    ) { Text("Добавить") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTextDialog = false }) { Text("Отмена") }
                }
            )
        }

        // Dialog: Export Camera + Drawing + Text as PNG or JPG
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                containerColor = RcColors.Surface,
                title = { Text("Сохранить снимок с рисунком", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Сохранит кадр с камеры вместе с текстом и рисунками в Галерею телефона и откроет меню «Поделиться».",
                        color = RcColors.TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExportDialog = false
                            saveCameraWithOverlays(Bitmap.CompressFormat.PNG, "png")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue)
                    ) { Text("Сохранить PNG") }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            saveCameraWithOverlays(Bitmap.CompressFormat.JPEG, "jpg")
                        }
                    ) { Text("Сохранить JPG", color = Color.White) }
                }
            )
        }
    }
}

@Composable
private fun ScannerCircleToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (active) RcColors.AccentBlue else Color.Black.copy(alpha = 0.62f))
                .border(1.dp, if (active) Color.White else Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

private fun saveScannerBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    displayName: String,
    format: Bitmap.CompressFormat,
    mimeType: String
) {
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RealityComposer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(format, 95, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
    }
}

@Composable
private fun Step1DetectOverlay(
    onCancel: () -> Unit,
    onNext: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbit")
    val orbitOffset by infiniteTransition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phoneOrbit"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Top Pills: Cancel / Next (exact Reality Composer style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassPillButton(text = "Отмена", onClick = onCancel)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(onClick = onSwitchCamera),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Cameraswitch, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                GlassPillButton(text = "Далее", highlighted = true, onClick = onNext)
            }
        }

        // Center Phone Orbiting Icon + Prompt (matching Screenshot #1)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cy = size.height * 0.56f
                    // Orbit arc behind/around phone
                    drawArc(
                        color = Color.White.copy(alpha = 0.85f),
                        startAngle = 155f,
                        sweepAngle = 230f,
                        useCenter = false,
                        topLeft = Offset(8f, cy - 16f),
                        size = androidx.compose.ui.geometry.Size(size.width - 16f, 36f),
                        style = Stroke(width = 4f, cap = StrokeCap.Round)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .offset(x = orbitOffset.dp)
                        .size(54.dp)
                )
            }

            Text(
                text = "Медленно обходите объект вокруг.",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // Bottom hint
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
        ) {
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text("Выделить объект в рамку", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Step2BoundingBoxOverlay(
    boxWidth: Float,
    boxHeight: Float,
    onBoxWidthChange: (Float) -> Unit,
    onBoxHeightChange: (Float) -> Unit,
    onCancel: () -> Unit,
    onStartScan: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 3D Perspective Bounding Box Wireframe (matching Screenshot #2!)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.46f
            val w = size.width * boxWidth
            val h = size.height * boxHeight
            val depthDx = w * 0.14f
            val depthDy = h * 0.08f

            // Front face
            val flt = Offset(cx - w / 2f, cy - h / 2f)
            val frt = Offset(cx + w / 2f, cy - h / 2f)
            val frb = Offset(cx + w / 2f, cy + h / 2f)
            val flb = Offset(cx - w / 2f, cy + h / 2f)

            // Back face (perspective inset)
            val blt = Offset(flt.x + depthDx, flt.y - depthDy)
            val brt = Offset(frt.x - depthDx, frt.y - depthDy)
            val brb = Offset(frb.x - depthDx, frb.y - depthDy)
            val blb = Offset(flb.x + depthDx, flb.y - depthDy)

            val wireColor = Color.White.copy(alpha = 0.88f)
            val backWire = Color.White.copy(alpha = 0.45f)

            // Back edges
            drawLine(backWire, blt, brt, 3f)
            drawLine(backWire, brt, brb, 3f)
            drawLine(backWire, brb, blb, 3f)
            drawLine(backWire, blb, blt, 3f)

            // Connecting depth edges
            drawLine(wireColor, flt, blt, 3f)
            drawLine(wireColor, frt, brt, 3f)
            drawLine(wireColor, frb, brb, 3f)
            drawLine(wireColor, flb, blb, 3f)

            // Front face edges
            drawLine(wireColor, flt, frt, 4f)
            drawLine(wireColor, frt, frb, 4f)
            drawLine(wireColor, frb, flb, 4f)
            drawLine(wireColor, flb, flt, 4f)

            // Glowing white corner handles (just like Reality Composer!)
            listOf(flt, frt, frb, flb, blt, brt).forEach { pt ->
                drawCircle(Color.White, radius = 9f, center = pt)
            }

            // Top center handle dot
            val topCenter = Offset(cx, (flt.y + blt.y) / 2f)
            drawCircle(Color.White.copy(alpha = 0.35f), radius = 22f, center = topCenter)
            drawCircle(Color.White, radius = 11f, center = topCenter)
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassPillButton(text = "Отмена", onClick = onCancel)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ViewInAr, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Bottom Controls to adjust 3D Box + Start Scan
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Поместите весь объект внутрь белой 3D-рамки",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ширина", color = RcColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                Slider(
                    value = boxWidth,
                    onValueChange = onBoxWidthChange,
                    valueRange = 0.3f..0.85f,
                    modifier = Modifier.weight(1f).height(26.dp),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Высота", color = RcColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                Slider(
                    value = boxHeight,
                    onValueChange = onBoxHeightChange,
                    valueRange = 0.25f..0.68f,
                    modifier = Modifier.weight(1f).height(26.dp),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onStartScan,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Начать 360° сканирование", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Step3ScanningOverlay(
    capturedCount: Int,
    totalSectors: Int,
    livePoints: List<ColoredPoint3D>,
    autoCapture: Boolean,
    onToggleAuto: () -> Unit,
    onManualCapture: () -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit
) {
    val progressPct = ((capturedCount * 100) / totalSectors).coerceIn(0, 100)

    // Subtle rotation of the live point cloud inside the bottom ring
    val infiniteTransition = rememberInfiniteTransition(label = "pcSpin")
    val previewAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "pcAngle"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Top Pills: Cancel / Next (Finish)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassPillButton(text = "Отмена", onClick = onCancel)
            GlassPillButton(
                text = if (capturedCount >= totalSectors) "Готово" else "Далее ($progressPct%)",
                highlighted = capturedCount >= 4,
                onClick = { if (capturedCount >= 2) onFinish() }
            )
        }

        // Center Aiming Dot (exact detail from Screenshot #3)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(10.dp)
                .clip(CircleShape)
                .background(Color.White)
        )

        // Bottom 360° Orbital Capture Ring + Live 3D Point Cloud Preview (exact match to Screenshot #3!)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(210.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height * 0.68f
                    val rx = size.width * 0.44f
                    val ry = size.height * 0.21f

                    // 1. Draw 360° Segmented Capture Ring (tilted ellipse like Reality Composer)
                    for (i in 0 until totalSectors) {
                        val a1 = (i * 2.0 * PI / totalSectors).toFloat()
                        val a2 = ((i + 0.65f) * 2.0 * PI / totalSectors).toFloat()
                        val captured = i < capturedCount
                        val p1 = Offset(cx + cos(a1) * rx, cy + sin(a1) * ry)
                        val p2 = Offset(cx + cos(a2) * rx, cy + sin(a2) * ry)
                        drawLine(
                            color = if (captured) Color.White else Color.White.copy(alpha = 0.25f),
                            start = p1,
                            end = p2,
                            strokeWidth = if (captured) 7f else 4f,
                            cap = StrokeCap.Round
                        )
                    }

                    // 2. Draw Live Colored 3D Point Cloud inside the ring!
                    val rad = previewAngle * PI.toFloat() / 180f
                    val cosR = cos(rad)
                    val sinR = sin(rad)
                    val scale = size.width * 0.56f
                    val pcCenterY = size.height * 0.44f

                    for (pt in livePoints) {
                        val rx3 = pt.x * cosR - pt.z * sinR
                        val rz3 = pt.x * sinR + pt.z * cosR
                        val sx = cx + rx3 * scale
                        val sy = pcCenterY - pt.y * scale + rz3 * scale * 0.25f
                        drawCircle(
                            color = Color(pt.argb).copy(alpha = 0.9f),
                            radius = 3.2f,
                            center = Offset(sx, sy)
                        )
                    }
                }
            }

            // Capture controls (Auto / Pause / Manual frame for Emulator testing)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassPillButton(
                    text = if (autoCapture) "Пауза" else "Авто-скан",
                    onClick = onToggleAuto
                )
                GlassPillButton(
                    text = "+ Кадр ($capturedCount/$totalSectors)",
                    onClick = onManualCapture
                )
                if (capturedCount >= 4) {
                    Button(
                        onClick = onFinish,
                        colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Создать 3D", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPillButton(
    text: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (highlighted) Color.White.copy(alpha = 0.9f)
                else Color.Black.copy(alpha = 0.52f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (highlighted) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ScannerCameraView(
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
                        val desired = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        val selector = if (cameraProvider.hasCamera(desired)) {
                            desired
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
