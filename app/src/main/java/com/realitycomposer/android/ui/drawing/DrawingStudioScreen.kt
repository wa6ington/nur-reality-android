package com.realitycomposer.android.ui.drawing

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas as ComposeCanvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.realitycomposer.android.ui.theme.RcColors
import java.io.File
import java.io.FileOutputStream

private data class DrawStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false
)

enum class CanvasBgMode(val titleRu: String, val color: Color) {
    CAMERA("Камера", Color.Transparent),
    WHITE("Белый", Color.White),
    DARK("Черный", Color(0xFF141416)),
    TRANSPARENT("Прозрачный", Color.Transparent)
}

private val BrushColors = listOf(
    Color(0xFF0A84FF),
    Color(0xFFFFD60A),
    Color(0xFFFF453A),
    Color(0xFF30D158),
    Color(0xFFFF9F0A),
    Color(0xFFBF5AF2),
    Color(0xFF64D2FF),
    Color(0xFFFF375F),
    Color(0xFFFFFFFF),
    Color(0xFF000000)
)

@Composable
fun DrawingStudioScreen(
    canInsertIntoScene: Boolean,
    onClose: () -> Unit,
    onExportImage: (File, String) -> Unit,
    onInsertDrawingIntoScene: ((File) -> Unit)? = null
) {
    val context = LocalContext.current

    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedColor by remember { mutableStateOf(BrushColors[0]) }
    var brushSize by remember { mutableStateOf(12f) }
    var isEraser by remember { mutableStateOf(false) }
    var bgMode by remember { mutableStateOf(CanvasBgMode.CAMERA) }
    var canvasSize by remember { mutableStateOf(IntSize(1080, 1440)) }
    var savedBanner by remember { mutableStateOf<String?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(bgMode) {
        if (bgMode == CanvasBgMode.CAMERA && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun exportDrawing(format: Bitmap.CompressFormat, ext: String, share: Boolean): File {
        val camBmp = if (bgMode == CanvasBgMode.CAMERA) previewViewRef?.bitmap else null
        val bmp = renderStrokesToBitmap(
            strokes = strokes.toList(),
            canvasSize = canvasSize,
            bgMode = if (format == Bitmap.CompressFormat.JPEG && bgMode == CanvasBgMode.TRANSPARENT) {
                CanvasBgMode.WHITE
            } else bgMode,
            cameraBitmap = camBmp
        )

        val fileName = "NUR_Drawing_${System.currentTimeMillis()}.$ext"
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, fileName)
        FileOutputStream(file).use { out ->
            bmp.compress(format, 95, out)
        }

        // Also save to Android Gallery (Pictures/RealityComposer) so it appears immediately in Samsung Gallery
        val mime = if (ext == "png") "image/png" else "image/jpeg"
        saveBitmapToGallery(context, bmp, fileName, format, mime)

        savedBanner = "Сохранено в Галерею: $fileName"
        if (share) {
            onExportImage(file, mime)
        }
        return file
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RcColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RcColors.Surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
                Text(
                    text = "Рисование",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Background toggle pill
                TextButton(
                    onClick = {
                        val all = CanvasBgMode.entries
                        bgMode = all[(all.indexOf(bgMode) + 1) % all.size]
                    }
                ) {
                    Text("Фон: ${bgMode.titleRu}", color = RcColors.AccentBlue, fontSize = 12.sp)
                }

                IconButton(
                    onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                    enabled = strokes.isNotEmpty()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Отменить",
                        tint = if (strokes.isNotEmpty()) Color.White else RcColors.TextTertiary
                    )
                }
                IconButton(
                    onClick = { strokes.clear() },
                    enabled = strokes.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Очистить",
                        tint = if (strokes.isNotEmpty()) RcColors.DangerRed else RcColors.TextTertiary
                    )
                }
            }
        }

        // Drawing Canvas Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(10.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, RcColors.Border, RoundedCornerShape(16.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(selectedColor, brushSize, isEraser) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPoints = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPoints = currentPoints + change.position
                        },
                        onDragEnd = {
                            if (currentPoints.isNotEmpty()) {
                                strokes.add(
                                    DrawStroke(
                                        points = currentPoints,
                                        color = selectedColor,
                                        strokeWidth = if (isEraser) brushSize * 2.2f else brushSize,
                                        isEraser = isEraser
                                    )
                                )
                            }
                            currentPoints = emptyList()
                        },
                        onDragCancel = { currentPoints = emptyList() }
                    )
                }
        ) {
            // Live Camera feed behind the drawing canvas when bgMode == CAMERA
            if (bgMode == CanvasBgMode.CAMERA && hasCameraPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }.also { previewViewRef = it }
                    },
                    update = { pv ->
                        previewViewRef = pv
                        val future = ProcessCameraProvider.getInstance(context)
                        future.addListener({
                            runCatching {
                                val provider = future.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = pv.surfaceProvider
                                }
                                val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                } else {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                }
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, selector, preview)
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                )
            }

            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                // Canvas background (checkerboard when transparent, none when CAMERA)
                if (bgMode == CanvasBgMode.TRANSPARENT) {
                    val tile = 24f
                    var y = 0f
                    var row = 0
                    while (y < size.height) {
                        var x = 0f
                        var col = row % 2
                        while (x < size.width) {
                            drawRect(
                                color = if (col % 2 == 0) Color(0xFF2C2C2E) else Color(0xFF3A3A3C),
                                topLeft = Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(tile, tile)
                            )
                            x += tile
                            col++
                        }
                        y += tile
                        row++
                    }
                } else if (bgMode != CanvasBgMode.CAMERA) {
                    drawRect(color = bgMode.color)
                }

                val eraserColor = if (bgMode == CanvasBgMode.DARK) Color(0xFF141416) else Color.White

                // Draw committed strokes
                for (stroke in strokes) {
                    drawStrokePath(
                        points = stroke.points,
                        color = if (stroke.isEraser) eraserColor else stroke.color,
                        width = stroke.strokeWidth
                    )
                }

                // Draw active stroke
                if (currentPoints.isNotEmpty()) {
                    drawStrokePath(
                        points = currentPoints,
                        color = if (isEraser) eraserColor else selectedColor,
                        width = if (isEraser) brushSize * 2.2f else brushSize
                    )
                }
            }

            savedBanner?.let { msg ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(msg, color = RcColors.PlayGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Brush Controls & Export Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RcColors.Surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Color Swatches + Eraser
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isEraser,
                    onClick = { isEraser = !isEraser },
                    label = { Text("Ластик", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.AutoFixNormal, null, modifier = Modifier.size(15.dp)) }
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(BrushColors) { color ->
                        val selected = !isEraser && selectedColor == color
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) RcColors.AccentBlue else RcColors.Border,
                                    shape = CircleShape
                                )
                                .clickable {
                                    isEraser = false
                                    selectedColor = color
                                }
                        )
                    }
                }
            }

            // Brush size slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Толщина", color = RcColors.TextSecondary, fontSize = 12.sp)
                Slider(
                    value = brushSize,
                    onValueChange = { brushSize = it },
                    valueRange = 4f..38f,
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = if (isEraser) Color.White else selectedColor,
                        activeTrackColor = if (isEraser) Color.White else selectedColor
                    )
                )
                Text("${brushSize.toInt()}px", color = RcColors.TextPrimary, fontSize = 11.sp)
            }

            // Export Buttons (PNG, JPG, and Insert into 3D Scene!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { exportDrawing(Bitmap.CompressFormat.PNG, "png", share = true) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text("Экспорт PNG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { exportDrawing(Bitmap.CompressFormat.JPEG, "jpg", share = true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text("Экспорт JPG", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                if (canInsertIntoScene && onInsertDrawingIntoScene != null) {
                    Button(
                        onClick = {
                            val bmp = renderStrokesToBitmap(
                                strokes = strokes.toList(),
                                canvasSize = canvasSize,
                                bgMode = bgMode
                            )
                            val dir = File(context.filesDir, "drawings").apply { mkdirs() }
                            val file = File(dir, "draw_${System.currentTimeMillis()}.png")
                            FileOutputStream(file).use { out ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
                            }
                            onInsertDrawingIntoScene(file)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RcColors.PlayGreen),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text("В 3D-сцену", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePath(
    points: List<Offset>,
    color: Color,
    width: Float
) {
    if (points.size == 1) {
        drawCircle(color = color, radius = width / 2f, center = points[0])
        return
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun renderStrokesToBitmap(
    strokes: List<DrawStroke>,
    canvasSize: IntSize,
    bgMode: CanvasBgMode,
    cameraBitmap: Bitmap? = null
): Bitmap {
    val w = canvasSize.width.coerceAtLeast(720)
    val h = canvasSize.height.coerceAtLeast(960)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    when (bgMode) {
        CanvasBgMode.CAMERA -> {
            if (cameraBitmap != null) {
                canvas.drawBitmap(cameraBitmap, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
            } else {
                canvas.drawColor(android.graphics.Color.BLACK)
            }
        }
        CanvasBgMode.WHITE -> canvas.drawColor(android.graphics.Color.WHITE)
        CanvasBgMode.DARK -> canvas.drawColor(android.graphics.Color.rgb(20, 20, 22))
        CanvasBgMode.TRANSPARENT -> canvas.drawColor(android.graphics.Color.TRANSPARENT)
    }

    val eraserArgb = if (bgMode == CanvasBgMode.DARK) {
        android.graphics.Color.rgb(20, 20, 22)
    } else {
        android.graphics.Color.WHITE
    }

    for (stroke in strokes) {
        if (stroke.points.isEmpty()) continue
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (stroke.isEraser) eraserArgb else stroke.color.toArgb()
            strokeWidth = stroke.strokeWidth
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (stroke.points.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(stroke.points[0].x, stroke.points[0].y, stroke.strokeWidth / 2f, paint)
        } else {
            val path = AndroidPath().apply {
                moveTo(stroke.points[0].x, stroke.points[0].y)
                for (i in 1 until stroke.points.size) {
                    lineTo(stroke.points[i].x, stroke.points[i].y)
                }
            }
            canvas.drawPath(path, paint)
        }
    }
    return bmp
}

private fun saveBitmapToGallery(
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
