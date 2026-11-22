package com.realitycomposer.android.ui.camera

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.realitycomposer.android.R
import com.realitycomposer.android.ui.theme.RcColors
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class Camera3DShape(val titleRu: String) {
    CUBE("Куб"),
    SPHERE("Сфера"),
    CYLINDER("Цилиндр"),
    PYRAMID("Пирамида"),
    BILLBOARD("Билборд"),
    TEXT_3D("3D-Текст")
}

data class Camera3DObject(
    val id: String = UUID.randomUUID().toString(),
    val shape: Camera3DShape,
    val screenXRatio: Float = 0.5f,
    val screenYRatio: Float = 0.48f,
    val sizePx: Float = 150f,
    val rotX: Float = 12f,
    val rotY: Float = -24f,
    val color: Color = Color(0xFF0A84FF),
    val text: String = "",
    val textColor: Color = Color(0xFFE5E5EA),
    val isVertical: Boolean = false
)

private data class DrawStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float
)

private data class Vec3f(val x: Float, val y: Float, val z: Float)
private data class MeshFace(val verts: List<Vec3f>, val baseColor: Color, val isFrontTextFace: Boolean = false)

private val StudioColors = listOf(
    Color(0xFF0A84FF),
    Color(0xFFFFD60A),
    Color(0xFFFF453A),
    Color(0xFF30D158),
    Color(0xFFFF9F0A),
    Color(0xFFBF5AF2),
    Color(0xFF64D2FF),
    Color(0xFFFFFFFF),
    Color(0xFF2C2C2E)
)

@Composable
fun Camera3DStudioScreen(
    onExportImage: (File, String) -> Unit
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
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var useFrontCamera by remember { mutableStateOf(false) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize(1080, 1920)) }

    // 3D Objects placed directly on the live camera (starts clean with 0 clutter, user adds what they want!)
    val objects = remember { mutableStateListOf<Camera3DObject>() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selectedObj = objects.firstOrNull { it.id == selectedId }

    // Drawing over live camera
    var isDrawMode by remember { mutableStateOf(false) }
    val drawStrokes = remember { mutableStateListOf<DrawStroke>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var brushColor by remember { mutableStateOf(StudioColors[1]) }
    var brushWidth by remember { mutableStateOf(10f) }

    // Modals / Sheets
    var showAddSheet by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showColorBar by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    fun updateSelected(transform: (Camera3DObject) -> Camera3DObject) {
        val idx = objects.indexOfFirst { it.id == selectedId }
        if (idx >= 0) {
            objects[idx] = transform(objects[idx])
        }
    }

    fun addObject(shape: Camera3DShape) {
        val offset = (objects.size % 4) * 0.04f - 0.06f
        val newObj = Camera3DObject(
            shape = shape,
            screenXRatio = (0.5f + offset).coerceIn(0.2f, 0.8f),
            screenYRatio = (0.46f + offset).coerceIn(0.25f, 0.72f),
            sizePx = if (shape == Camera3DShape.BILLBOARD || shape == Camera3DShape.TEXT_3D) 180f else 145f,
            color = when (shape) {
                Camera3DShape.BILLBOARD, Camera3DShape.TEXT_3D -> Color(0xFF1C1C1E)
                else -> StudioColors[objects.size % 6]
            },
            text = when (shape) {
                Camera3DShape.BILLBOARD, Camera3DShape.TEXT_3D -> "NUR ADVERTISING"
                else -> ""
            },
            textColor = Color(0xFFFFD60A)
        )
        objects.add(newObj)
        selectedId = newObj.id
        showAddSheet = false
        isDrawMode = false
        toastMsg = "Добавлено: ${shape.titleRu} — крутите и двигайте пальцем"
    }

    fun exportComposite(format: Bitmap.CompressFormat, ext: String) {
        val w = viewportSize.width.coerceAtLeast(720)
        val h = viewportSize.height.coerceAtLeast(1280)
        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(outBmp)

        // 1. Real live camera frame
        val camBmp = previewViewRef?.bitmap
        if (camBmp != null) {
            canvas.drawBitmap(camBmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
        } else {
            canvas.drawColor(android.graphics.Color.BLACK)
        }

        // 2. All 3D Objects + Text ON them!
        for (obj in objects) {
            draw3DObjectToAndroidCanvas(canvas, obj, w.toFloat(), h.toFloat(), isSelected = false)
        }

        // 3. All Drawings
        for (s in drawStrokes) {
            if (s.points.isEmpty()) continue
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = s.color.toArgb()
                strokeWidth = s.width
                style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
            }
            if (s.points.size == 1) {
                paint.style = AndroidPaint.Style.FILL
                canvas.drawCircle(s.points[0].x, s.points[0].y, s.width / 2f, paint)
            } else {
                val p = AndroidPath().apply {
                    moveTo(s.points[0].x, s.points[0].y)
                    for (i in 1 until s.points.size) lineTo(s.points[i].x, s.points[i].y)
                }
                canvas.drawPath(p, paint)
            }
        }

        val fileName = "NUR_3D_${System.currentTimeMillis()}.$ext"
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, fileName)
        FileOutputStream(file).use { out -> outBmp.compress(format, 95, out) }

        val mime = if (ext == "png") "image/png" else "image/jpeg"
        saveToGallery(context, outBmp, fileName, format, mime)
        toastMsg = "Сохранено в Галерею: $fileName"
        onExportImage(file, mime)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .onSizeChanged { viewportSize = it }
    ) {
        if (!hasCameraPermission) {
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
                    "Разрешите камеру, чтобы размещать 3D-объекты, текст и рисунки прямо на видео.",
                    color = RcColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue)
                ) { Text("Разрешить камеру") }
            }
            return@Box
        }

        // 1. REAL LIVE CAMERA (100% full screen, never black!)
        LiveCameraSurface(
            useFrontCamera = useFrontCamera,
            onPreviewViewReady = { previewViewRef = it },
            modifier = Modifier.fillMaxSize()
        )

        // 2. TRANSPARENT 3D OBJECTS + DRAWING LAYER DIRECTLY OVER LIVE CAMERA
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isDrawMode) {
                        Modifier.pointerInput(brushColor, brushWidth) {
                            detectDragGestures(
                                onDragStart = { currentStroke = listOf(it) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        drawStrokes.add(DrawStroke(currentStroke, brushColor, brushWidth))
                                    }
                                    currentStroke = emptyList()
                                },
                                onDragCancel = { currentStroke = emptyList() }
                            )
                        }
                    } else {
                        // Drag to move selected 3D object, Pinch to scale, or tap to select!
                        Modifier.pointerInput(objects.size, selectedId, viewportSize) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val w = viewportSize.width.coerceAtLeast(1).toFloat()
                                val h = viewportSize.height.coerceAtLeast(1).toFloat()

                                // If nothing selected, pick nearest object to touch
                                if (selectedId == null && objects.isNotEmpty()) {
                                    val nearest = objects.minByOrNull { o ->
                                        val dx = o.screenXRatio * w - centroid.x
                                        val dy = o.screenYRatio * h - centroid.y
                                        dx * dx + dy * dy
                                    }
                                    selectedId = nearest?.id
                                }

                                val idx = objects.indexOfFirst { it.id == selectedId }
                                if (idx >= 0) {
                                    val cur = objects[idx]
                                    objects[idx] = cur.copy(
                                        screenXRatio = (cur.screenXRatio + pan.x / w).coerceIn(0.08f, 0.92f),
                                        screenYRatio = (cur.screenYRatio + pan.y / h).coerceIn(0.12f, 0.85f),
                                        sizePx = (cur.sizePx * zoom).coerceIn(60f, 380f)
                                    )
                                }
                            }
                        }
                    }
                )
        ) {
            // Draw every 3D object directly onto the transparent canvas above the camera
            for (obj in objects) {
                draw3DObjectToAndroidCanvas(
                    canvas = drawContext.canvas.nativeCanvas,
                    obj = obj,
                    viewportW = size.width,
                    viewportH = size.height,
                    isSelected = (obj.id == selectedId && !isDrawMode)
                )
            }

            // Draw finger strokes
            fun drawLineStroke(pts: List<Offset>, col: Color, width: Float) {
                if (pts.size == 1) {
                    drawCircle(col, width / 2f, pts[0])
                } else if (pts.size > 1) {
                    val p = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                    }
                    drawPath(p, col, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            for (s in drawStrokes) drawLineStroke(s.points, s.color, s.width)
            if (currentStroke.isNotEmpty()) drawLineStroke(currentStroke, brushColor, brushWidth)
        }

        // 3. TOP BAR: NUR Logo + Author Sanzhar (Left) & Camera Switch + Save PNG/JPG (Right)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // NUR Brand + Author Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_logo),
                    contentDescription = "NUR",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Column {
                    Text(
                        text = "NUR Reality",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Автор: Sanzhar",
                        color = Color(0xFFD4AF37),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Switch Camera
                CircleGlassButton(
                    icon = Icons.Default.Cameraswitch,
                    active = false,
                    onClick = { useFrontCamera = !useFrontCamera }
                )
                // Save PNG / JPG
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(RcColors.AccentBlue)
                        .clickable { showExportDialog = true }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.SaveAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("PNG/JPG", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4. RIGHT-SIDE FLOATING CAMERA TOOLS: + 3D, Текст, Цвет, Рисовать
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LabeledGlassTool(
                icon = Icons.Default.AddBox,
                label = "+ 3D",
                active = showAddSheet,
                onClick = {
                    isDrawMode = false
                    showColorBar = false
                    showAddSheet = !showAddSheet
                }
            )
            LabeledGlassTool(
                icon = Icons.Default.TextFields,
                label = "Текст",
                active = showTextDialog,
                onClick = {
                    isDrawMode = false
                    showAddSheet = false
                    if (selectedObj == null) {
                        // Automatically add a 3D-Text object if none selected
                        addObject(Camera3DShape.TEXT_3D)
                    }
                    showTextDialog = true
                }
            )
            LabeledGlassTool(
                icon = Icons.Default.Palette,
                label = "Цвет",
                active = showColorBar,
                onClick = {
                    isDrawMode = false
                    showAddSheet = false
                    showColorBar = !showColorBar
                }
            )
            LabeledGlassTool(
                icon = Icons.Default.Brush,
                label = if (isDrawMode) "Готово" else "Рисовать",
                active = isDrawMode,
                onClick = {
                    showAddSheet = false
                    showColorBar = false
                    isDrawMode = !isDrawMode
                }
            )
        }

        // 5. Empty Hint (when no objects added yet)
        if (objects.isEmpty() && !isDrawMode && !showAddSheet) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .clickable { showAddSheet = true }
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Нажмите «+ 3D» справа, чтобы добавить 3D-объект в камеру",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 6. Toast Banner
        toastMsg?.let { msg ->
            LaunchedEffect(msg) {
                delay(2400L)
                toastMsg = null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(msg, color = RcColors.PlayGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        // 7. DRAW MODE TOP PALETTE BAR
        if (isDrawMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, RcColors.AccentBlue, RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(StudioColors) { col ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        if (brushColor == col) 2.5.dp else 1.dp,
                                        if (brushColor == col) RcColors.AccentBlue else Color.White.copy(alpha = 0.4f),
                                        CircleShape
                                    )
                                    .clickable { brushColor = col }
                            )
                        }
                    }
                    IconButton(
                        onClick = { if (drawStrokes.isNotEmpty()) drawStrokes.removeAt(drawStrokes.lastIndex) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { drawStrokes.clear() },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, tint = RcColors.DangerRed, modifier = Modifier.size(20.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Кисть", color = RcColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(44.dp))
                    Slider(
                        value = brushWidth,
                        onValueChange = { brushWidth = it },
                        valueRange = 4f..32f,
                        modifier = Modifier.weight(1f).height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = brushColor, activeTrackColor = brushColor)
                    )
                    Text("${brushWidth.toInt()}px", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        // 8. BOTTOM 3D OBJECT CONTROLS (Rotate 360°, Scale, Switch Object, Delete)
        if (selectedObj != null && !isDrawMode && !showAddSheet) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Color Picker if toggled
                if (showColorBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Цвет 3D:", color = Color.White, fontSize = 11.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(StudioColors) { col ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            if (selectedObj.color == col) 2.5.dp else 1.dp,
                                            if (selectedObj.color == col) Color.White else RcColors.Border,
                                            CircleShape
                                        )
                                        .clickable {
                                            updateSelected {
                                                if (it.shape == Camera3DShape.TEXT_3D) it.copy(textColor = col, color = col)
                                                else it.copy(color = col)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                // Object selector chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(objects, key = { it.id }) { obj ->
                        val active = obj.id == selectedId
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (active) RcColors.AccentBlue else Color.Black.copy(alpha = 0.65f))
                                .border(1.dp, if (active) Color.White else RcColors.Border, RoundedCornerShape(14.dp))
                                .clickable { selectedId = obj.id }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(obj.color))
                            Text(
                                text = obj.text.ifBlank { obj.shape.titleRu }.take(14),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Rotate 360° & Size Sliders + Text + Delete
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .border(1.dp, RcColors.Border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedObj.shape.titleRu} — двигайте пальцем по камере",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { showTextDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Текст", color = Color(0xFFFFD60A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = {
                                    objects.removeAll { it.id == selectedObj.id }
                                    selectedId = objects.lastOrNull()?.id
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = RcColors.DangerRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Поворот", color = RcColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                        Slider(
                            value = selectedObj.rotY,
                            onValueChange = { v -> updateSelected { it.copy(rotY = v) } },
                            valueRange = -180f..180f,
                            modifier = Modifier.weight(1f).height(24.dp),
                            colors = SliderDefaults.colors(thumbColor = RcColors.AccentBlue, activeTrackColor = RcColors.AccentBlue)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Наклон", color = RcColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                        Slider(
                            value = selectedObj.rotX,
                            onValueChange = { v -> updateSelected { it.copy(rotX = v) } },
                            valueRange = -75f..75f,
                            modifier = Modifier.weight(1f).height(24.dp),
                            colors = SliderDefaults.colors(thumbColor = RcColors.PlayGreen, activeTrackColor = RcColors.PlayGreen)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Размер", color = RcColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(56.dp))
                        Slider(
                            value = selectedObj.sizePx,
                            onValueChange = { v -> updateSelected { it.copy(sizePx = v) } },
                            valueRange = 65f..340f,
                            modifier = Modifier.weight(1f).height(24.dp),
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD60A), activeTrackColor = Color(0xFFFFD60A))
                        )
                    }
                }
            }
        }

        // 9. ADD 3D OBJECT BOTTOM SHEET
        if (showAddSheet) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(RcColors.SurfaceTranslucent)
                    .border(1.dp, RcColors.Border, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Добавить 3D-объект в камеру", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { showAddSheet = false }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, null, tint = RcColors.TextSecondary)
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Camera3DShape.entries) { shape ->
                        Column(
                            modifier = Modifier
                                .width(92.dp)
                                .height(86.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(RcColors.SurfaceElevated)
                                .border(1.dp, RcColors.Border, RoundedCornerShape(14.dp))
                                .clickable { addObject(shape) }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (shape) {
                                    Camera3DShape.CUBE -> Icons.Default.ViewInAr
                                    Camera3DShape.SPHERE -> Icons.Default.Circle
                                    Camera3DShape.CYLINDER -> Icons.Default.PanoramaVertical
                                    Camera3DShape.PYRAMID -> Icons.Default.ChangeHistory
                                    Camera3DShape.BILLBOARD -> Icons.Default.Tv
                                    Camera3DShape.TEXT_3D -> Icons.Default.TextFields
                                },
                                contentDescription = shape.titleRu,
                                tint = Color(0xFFFFD60A),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(shape.titleRu, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // 10. TEXT ON 3D OBJECT DIALOG
        if (showTextDialog && selectedObj != null) {
            var textVal by remember(selectedObj.id) { mutableStateOf(selectedObj.text.ifBlank { "NUR" }) }
            var textCol by remember(selectedObj.id) { mutableStateOf(selectedObj.textColor) }
            var verticalMode by remember(selectedObj.id) { mutableStateOf(selectedObj.isVertical) }
            AlertDialog(
                onDismissRequest = { showTextDialog = false },
                containerColor = RcColors.Surface,
                title = { Text("3D-Буквы / Текст", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = textVal,
                            onValueChange = { textVal = it },
                            label = { Text("Введите текст (например: ТДН или NUR)") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (selectedObj.shape == Camera3DShape.TEXT_3D) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !verticalMode,
                                    onClick = { verticalMode = false },
                                    label = { Text("Горизонтально", fontSize = 12.sp) }
                                )
                                FilterChip(
                                    selected = verticalMode,
                                    onClick = { verticalMode = true },
                                    label = { Text("Вертикально (ТДН)", fontSize = 12.sp) }
                                )
                            }
                        }
                        Text("Цвет букв:", color = RcColors.TextSecondary, fontSize = 12.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(StudioColors) { col ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            if (textCol == col) 2.5.dp else 1.dp,
                                            if (textCol == col) RcColors.AccentBlue else RcColors.Border,
                                            CircleShape
                                        )
                                        .clickable { textCol = col }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            updateSelected {
                                it.copy(
                                    text = textVal.trim(),
                                    textColor = textCol,
                                    isVertical = verticalMode
                                )
                            }
                            showTextDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue)
                    ) { Text("Применить") }
                },
                dismissButton = {
                    TextButton(onClick = { showTextDialog = false }) { Text("Отмена") }
                }
            )
        }

        // 11. EXPORT PNG / JPG DIALOG
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                containerColor = RcColors.Surface,
                title = { Text("Сохранить фото с 3D", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Сохранит кадр с камеры вместе со всеми 3D-объектами, текстом и рисунками в Галерею телефона.",
                        color = RcColors.TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExportDialog = false
                            exportComposite(Bitmap.CompressFormat.PNG, "png")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue)
                    ) { Text("Сохранить PNG") }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            exportComposite(Bitmap.CompressFormat.JPEG, "jpg")
                        }
                    ) { Text("Сохранить JPG", color = Color.White) }
                }
            )
        }
    }
}

@Composable
private fun LiveCameraSurface(
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
            update = { pv ->
                onPreviewViewReady(pv)
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    runCatching {
                        val provider = future.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = pv.surfaceProvider
                        }
                        val desired = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        val selector = if (provider.hasCamera(desired)) desired
                        else if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA
                        else CameraSelector.DEFAULT_FRONT_CAMERA
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, preview)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )
    }
}

@Composable
private fun CircleGlassButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) RcColors.AccentBlue else Color.Black.copy(alpha = 0.62f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun LabeledGlassTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (active) RcColors.AccentBlue else Color.Black.copy(alpha = 0.65f))
                .border(1.dp, if (active) Color.White else Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

// --- Pure Hardware-Accelerated 3D Perspective Mesh Renderer (100% Transparent over Camera!) ---

private fun draw3DObjectToAndroidCanvas(
    canvas: AndroidCanvas,
    obj: Camera3DObject,
    viewportW: Float,
    viewportH: Float,
    isSelected: Boolean
) {
    val cx = obj.screenXRatio * viewportW
    val cy = obj.screenYRatio * viewportH
    val s = obj.sizePx

    // Pure 3D Extruded Volumetric Letters (NO black box behind them!) — like ТДН / Aa!
    if (obj.shape == Camera3DShape.TEXT_3D) {
        drawPureExtruded3DLetters(canvas, obj, cx, cy, s)
        return
    }

    val faces = buildShapeMesh(obj.shape, obj.color)

    val radX = obj.rotX * PI.toFloat() / 180f
    val radY = obj.rotY * PI.toFloat() / 180f
    val cosX = cos(radX)
    val sinX = sin(radX)
    val cosY = cos(radY)
    val sinY = sin(radY)

    fun rotate(v: Vec3f): Vec3f {
        // Rotate Y then X
        val x1 = v.x * cosY + v.z * sinY
        val z1 = -v.x * sinY + v.z * cosY
        val y2 = v.y * cosX - z1 * sinX
        val z2 = v.y * sinX + z1 * cosX
        return Vec3f(x1, y2, z2)
    }

    fun project(v: Vec3f): Offset {
        val dist = 3.6f
        val factor = dist / (dist - v.z).coerceAtLeast(0.6f)
        return Offset(cx + v.x * s * factor, cy - v.y * s * factor)
    }

    // Light direction (top-right-front)
    val lightDir = Vec3f(0.45f, 0.65f, 0.75f).let {
        val l = sqrt(it.x * it.x + it.y * it.y + it.z * it.z)
        Vec3f(it.x / l, it.y / l, it.z / l)
    }

    data class RenderFace(
        val screenPts: List<Offset>,
        val avgZ: Float,
        val shadedColor: Int,
        val isFrontTextFace: Boolean
    )

    val renderFaces = faces.mapNotNull { face ->
        val rotVerts = face.verts.map { rotate(it) }
        if (rotVerts.size < 3) return@mapNotNull null

        // Face normal for lighting & backface culling
        val e1 = Vec3f(rotVerts[1].x - rotVerts[0].x, rotVerts[1].y - rotVerts[0].y, rotVerts[1].z - rotVerts[0].z)
        val e2 = Vec3f(rotVerts[2].x - rotVerts[0].x, rotVerts[2].y - rotVerts[0].y, rotVerts[2].z - rotVerts[0].z)
        val nx = e1.y * e2.z - e1.z * e2.y
        val ny = e1.z * e2.x - e1.x * e2.z
        val nz = e1.x * e2.y - e1.y * e2.x
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        val n = Vec3f(nx / len, ny / len, nz / len)

        // Backface culling (keep faces pointing toward viewer +Z)
        if (n.z < -0.08f && !face.isFrontTextFace) return@mapNotNull null

        val dot = (n.x * lightDir.x + n.y * lightDir.y + n.z * lightDir.z).coerceIn(0f, 1f)
        val brightness = 0.42f + dot * 0.68f
        val r = (face.baseColor.red * brightness * 255f).toInt().coerceIn(0, 255)
        val g = (face.baseColor.green * brightness * 255f).toInt().coerceIn(0, 255)
        val b = (face.baseColor.blue * brightness * 255f).toInt().coerceIn(0, 255)

        val avgZ = rotVerts.map { it.z }.average().toFloat()
        RenderFace(
            screenPts = rotVerts.map { project(it) },
            avgZ = avgZ,
            shadedColor = android.graphics.Color.argb(248, r, g, b),
            isFrontTextFace = face.isFrontTextFace
        )
    }.sortedBy { it.avgZ }

    val fillPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { style = AndroidPaint.Style.FILL }
    val strokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeWidth = if (isSelected) 3f else 1.5f
        color = if (isSelected) android.graphics.Color.argb(200, 255, 255, 255) else android.graphics.Color.argb(65, 255, 255, 255)
    }

    var textCenter = Offset(cx, cy)

    for (rf in renderFaces) {
        val path = AndroidPath().apply {
            moveTo(rf.screenPts[0].x, rf.screenPts[0].y)
            for (i in 1 until rf.screenPts.size) lineTo(rf.screenPts[i].x, rf.screenPts[i].y)
            close()
        }
        fillPaint.color = rf.shadedColor
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)

        if (rf.isFrontTextFace) {
            val avgX = rf.screenPts.map { it.x }.average().toFloat()
            val avgY = rf.screenPts.map { it.y }.average().toFloat()
            textCenter = Offset(avgX, avgY)
        }
    }

    // Draw crisp Text directly ON the 3D object!
    if (obj.text.isNotBlank()) {
        val lines = obj.text.split("\n").take(3)
        val maxLen = lines.maxOf { it.length }.coerceAtLeast(1)
        val textSize = (s * 0.42f * (8f / maxLen.coerceAtLeast(5))).coerceIn(22f, s * 0.42f)

        val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = obj.textColor.toArgb()
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = AndroidPaint.Align.CENTER
            setShadowLayer(8f, 0f, 3f, android.graphics.Color.BLACK)
        }

        val lineH = textSize * 1.18f
        val totalH = lineH * lines.size
        var y = textCenter.y - totalH / 2f + textSize * 0.85f
        for (line in lines) {
            canvas.drawText(line, textCenter.x, y, textPaint)
            y += lineH
        }
    }
}

/**
 * Draws pure volumetric 3D extruded letters with dark metallic side-walls (бортики)
 * and NO background box — matching real façade signage (ТДН) and Apple Reality Composer (Aa).
 */
private fun drawPureExtruded3DLetters(
    canvas: AndroidCanvas,
    obj: Camera3DObject,
    cx: Float,
    cy: Float,
    sizePx: Float
) {
    val rawText = obj.text.ifBlank { "NUR" }
    val lines = if (obj.isVertical) {
        rawText.replace("\n", "").take(8).map { it.toString() }
    } else {
        rawText.split("\n").take(3)
    }

    val maxLen = lines.maxOf { it.length }.coerceAtLeast(1)
    val fontSize = if (obj.isVertical) {
        (sizePx * 0.85f).coerceIn(48f, 260f)
    } else {
        (sizePx * 0.95f * (5f / maxLen.coerceAtLeast(3))).coerceIn(42f, 220f)
    }

    val radY = obj.rotY * PI.toFloat() / 180f
    val radX = obj.rotX * PI.toFloat() / 180f

    // 3D extrusion vector based on rotation/tilt sliders
    val depthSteps = 16
    val totalDepth = fontSize * 0.22f
    val stepDx = (-sin(radY) * totalDepth / depthSteps).coerceIn(-6f, 6f)
    val stepDy = (sin(radX) * totalDepth / depthSteps).coerceIn(-6f, 6f)

    val lineH = fontSize * 1.05f
    val totalH = lineH * lines.size

    canvas.save()
    canvas.translate(cx, cy)
    // Perspective foreshortening & wall-angle skew
    val scaleX = cos(radY).coerceIn(0.35f, 1.0f)
    val scaleY = cos(radX).coerceIn(0.45f, 1.0f)
    canvas.scale(scaleX, scaleY)
    canvas.skew(0f, -sin(radY) * 0.22f)

    // 1. Extruded dark metallic side-walls (бортики) from back to front
    val sidePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        textSize = fontSize
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = AndroidPaint.Align.CENTER
        style = AndroidPaint.Style.FILL
    }
    for (step in depthSteps downTo 1) {
        val shade = 36 + ((depthSteps - step) * 28 / depthSteps)
        sidePaint.color = android.graphics.Color.rgb(shade, shade, shade + 6)
        val ox = stepDx * step
        val oy = stepDy * step
        var y = -totalH / 2f + fontSize * 0.85f
        for (line in lines) {
            canvas.drawText(line, ox, y + oy, sidePaint)
            y += lineH
        }
    }

    // 2. Crisp dark outline around the front face (like fabricated signage edge)
    val outlinePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(220, 20, 20, 24)
        textSize = fontSize
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = AndroidPaint.Align.CENTER
        style = AndroidPaint.Style.STROKE
        strokeWidth = (fontSize * 0.045f).coerceIn(2f, 8f)
    }

    // 3. Bright front face of the 3D letters
    val facePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = obj.textColor.toArgb()
        textSize = fontSize
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = AndroidPaint.Align.CENTER
        style = AndroidPaint.Style.FILL
    }

    var y = -totalH / 2f + fontSize * 0.85f
    for (line in lines) {
        canvas.drawText(line, 0f, y, facePaint)
        canvas.drawText(line, 0f, y, outlinePaint)
        y += lineH
    }

    canvas.restore()
}

private fun buildShapeMesh(shape: Camera3DShape, color: Color): List<MeshFace> {
    return when (shape) {
        Camera3DShape.CUBE -> buildBoxMesh(0.75f, 0.75f, 0.75f, 0f, color)

        Camera3DShape.BILLBOARD -> {
            val pole = buildBoxMesh(0.12f, 0.65f, 0.12f, -0.78f, Color(0xFF5A5A5E))
            val board = buildBoxMesh(1.25f, 0.68f, 0.12f, 0.12f, color)
            pole + board
        }

        Camera3DShape.TEXT_3D -> buildBoxMesh(1.35f, 0.56f, 0.14f, 0f, color)

        Camera3DShape.PYRAMID -> {
            val h = 0.85f
            val r = 0.75f
            val apex = Vec3f(0f, h, 0f)
            val v0 = Vec3f(-r, -h * 0.6f, r)
            val v1 = Vec3f(r, -h * 0.6f, r)
            val v2 = Vec3f(r, -h * 0.6f, -r)
            val v3 = Vec3f(-r, -h * 0.6f, -r)
            listOf(
                MeshFace(listOf(v0, v1, apex), color, isFrontTextFace = true),
                MeshFace(listOf(v1, v2, apex), color),
                MeshFace(listOf(v2, v3, apex), color),
                MeshFace(listOf(v3, v0, apex), color),
                MeshFace(listOf(v3, v2, v1, v0), color)
            )
        }

        Camera3DShape.CYLINDER -> {
            val segs = 16
            val r = 0.65f
            val h = 0.82f
            val faces = mutableListOf<MeshFace>()
            val topVerts = mutableListOf<Vec3f>()
            val botVerts = mutableListOf<Vec3f>()
            for (i in 0 until segs) {
                val a1 = (i * 2.0 * PI / segs).toFloat()
                val a2 = ((i + 1) * 2.0 * PI / segs).toFloat()
                val p1t = Vec3f(cos(a1) * r, h, sin(a1) * r)
                val p2t = Vec3f(cos(a2) * r, h, sin(a2) * r)
                val p1b = Vec3f(cos(a1) * r, -h, sin(a1) * r)
                val p2b = Vec3f(cos(a2) * r, -h, sin(a2) * r)
                topVerts.add(p1t)
                botVerts.add(p1b)
                faces.add(MeshFace(listOf(p1b, p2b, p2t, p1t), color, isFrontTextFace = (i == segs / 4)))
            }
            faces.add(MeshFace(topVerts.reversed(), color))
            faces.add(MeshFace(botVerts, color))
            faces
        }

        Camera3DShape.SPHERE -> {
            val latSteps = 8
            val lonSteps = 14
            val r = 0.82f
            val faces = mutableListOf<MeshFace>()
            for (i in 0 until latSteps) {
                val phi1 = (-PI / 2 + i * PI / latSteps).toFloat()
                val phi2 = (-PI / 2 + (i + 1) * PI / latSteps).toFloat()
                for (j in 0 until lonSteps) {
                    val th1 = (j * 2.0 * PI / lonSteps).toFloat()
                    val th2 = ((j + 1) * 2.0 * PI / lonSteps).toFloat()
                    val v00 = Vec3f(r * cos(phi1) * cos(th1), r * sin(phi1), r * cos(phi1) * sin(th1))
                    val v10 = Vec3f(r * cos(phi1) * cos(th2), r * sin(phi1), r * cos(phi1) * sin(th2))
                    val v11 = Vec3f(r * cos(phi2) * cos(th2), r * sin(phi2), r * cos(phi2) * sin(th2))
                    val v01 = Vec3f(r * cos(phi2) * cos(th1), r * sin(phi2), r * cos(phi2) * sin(th1))
                    faces.add(MeshFace(listOf(v00, v10, v11, v01), color))
                }
            }
            faces
        }
    }
}

private fun buildBoxMesh(hx: Float, hy: Float, hz: Float, offsetY: Float, color: Color): List<MeshFace> {
    val v0 = Vec3f(-hx, -hy + offsetY, hz)
    val v1 = Vec3f(hx, -hy + offsetY, hz)
    val v2 = Vec3f(hx, hy + offsetY, hz)
    val v3 = Vec3f(-hx, hy + offsetY, hz)
    val v4 = Vec3f(-hx, -hy + offsetY, -hz)
    val v5 = Vec3f(hx, -hy + offsetY, -hz)
    val v6 = Vec3f(hx, hy + offsetY, -hz)
    val v7 = Vec3f(-hx, hy + offsetY, -hz)
    return listOf(
        MeshFace(listOf(v0, v1, v2, v3), color, isFrontTextFace = true), // Front
        MeshFace(listOf(v5, v4, v7, v6), color), // Back
        MeshFace(listOf(v4, v0, v3, v7), color), // Left
        MeshFace(listOf(v1, v5, v6, v2), color), // Right
        MeshFace(listOf(v3, v2, v6, v7), color), // Top
        MeshFace(listOf(v4, v5, v1, v0), color)  // Bottom
    )
}

private fun saveToGallery(
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
