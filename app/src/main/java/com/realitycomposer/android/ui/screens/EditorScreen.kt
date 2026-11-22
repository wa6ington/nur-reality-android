package com.realitycomposer.android.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realitycomposer.android.R
import com.realitycomposer.android.model.*
import com.realitycomposer.android.ui.panels.AddObjectSheet
import com.realitycomposer.android.ui.panels.BehaviorsSheet
import com.realitycomposer.android.ui.panels.PropertiesSheet
import com.realitycomposer.android.ui.theme.RcColors
import com.realitycomposer.android.ui.viewport.ArLiveViewport
import com.realitycomposer.android.viewmodel.EditorUiState
import java.io.File
import java.io.FileOutputStream

private data class LiveCameraStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float
)

private val CameraDrawPalette = listOf(
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
fun EditorScreen(
    state: EditorUiState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleSnap: () -> Unit,
    onToggleSheet: (InspectorSheet) -> Unit,
    onCloseSheet: () -> Unit,
    onSelectObject: (String?) -> Unit,
    onSetTransformTool: (TransformTool) -> Unit,
    onNudgeObject: (Char, Float) -> Unit,
    onCameraMoved: (Vec3) -> Unit,
    onAddPrimitive: (ObjectType) -> Unit,
    onAddText: (String) -> Unit,
    onAddPrefab: (String) -> Unit,
    onImportGlb: () -> Unit,
    onUpdateObject: (Boolean, (SceneObject) -> SceneObject) -> Unit,
    onDuplicateObject: () -> Unit,
    onDeleteObject: () -> Unit,
    onAddBehaviorTemplate: (String) -> Unit,
    onUpdateBehavior: (String, (Behavior) -> Behavior) -> Unit,
    onDeleteBehavior: (String) -> Unit,
    onAddAction: (String, ActionType) -> Unit,
    onDeleteAction: (String, String) -> Unit,
    onTestBehavior: (Behavior) -> Unit,
    onStartObjectScan: () -> Unit,
    onExportCameraImage: (File, String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val project = state.currentProject ?: return
    val scene = state.activeScene ?: return

    var useFrontCamera by remember { mutableStateOf(false) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var screenSize by remember { mutableStateOf(IntSize(1080, 1920)) }

    // Live Drawing directly on top of the Camera!
    var isDrawMode by remember { mutableStateOf(false) }
    val drawStrokes = remember { mutableStateListOf<LiveCameraStroke>() }
    var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var activeBrushColor by remember { mutableStateOf(CameraDrawPalette[0]) }
    var brushWidth by remember { mutableStateOf(10f) }
    var showExportDialog by remember { mutableStateOf(false) }

    fun captureAndExport(format: Bitmap.CompressFormat, ext: String) {
        val w = screenSize.width.coerceAtLeast(720)
        val h = screenSize.height.coerceAtLeast(1280)
        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(outBmp)

        val camBmp = previewViewRef?.bitmap
        if (camBmp != null) {
            canvas.drawBitmap(camBmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
        } else {
            canvas.drawColor(android.graphics.Color.BLACK)
        }

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

        val fileName = "NUR_Camera_${System.currentTimeMillis()}.$ext"
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, fileName)
        FileOutputStream(file).use { out -> outBmp.compress(format, 95, out) }

        val mime = if (ext == "png") "image/png" else "image/jpeg"
        saveCameraShotToGallery(context, outBmp, fileName, format, mime)
        onShowMessage("Сохранено в Галерею: $fileName")
        onExportCameraImage(file, mime)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .onSizeChanged { screenSize = it }
    ) {
        // 1. Live Camera + Transparent 3D Filament Objects Viewport
        ArLiveViewport(
            scene = scene,
            selectedObjectId = state.selectedObjectId,
            isPlaying = state.isPlaying,
            useFrontCamera = useFrontCamera,
            onPreviewViewReady = { previewViewRef = it },
            onSelectObject = onSelectObject,
            onCameraMoved = onCameraMoved,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Live Finger Drawing Layer directly on top of Camera + 3D!
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
                                        drawStrokes.add(
                                            LiveCameraStroke(currentStrokePoints, activeBrushColor, brushWidth)
                                        )
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

        // 3. Top Camera-Only Toolbar with ALL Tools
        TopCameraToolbar(
            state = state,
            isDrawMode = isDrawMode,
            onToggleDraw = {
                isDrawMode = !isDrawMode
                if (isDrawMode) onCloseSheet()
            },
            onUndo = onUndo,
            onRedo = onRedo,
            onTogglePlay = {
                isDrawMode = false
                onTogglePlay()
            },
            onToggleSnap = onToggleSnap,
            onToggleSheet = { sheet ->
                isDrawMode = false
                onToggleSheet(sheet)
            },
            onSwitchCamera = { useFrontCamera = !useFrontCamera },
            onStartObjectScan = onStartObjectScan,
            onOpenExport = { showExportDialog = true },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 4. NUR Brand + Author Badge (Top-Left below toolbar)
        if (!isDrawMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 60.dp, start = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_logo),
                    contentDescription = "NUR",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
                Text(
                    text = if (state.isPlaying) "NUR • ИГРАТЬ" else "NUR • Автор: Sanzhar",
                    color = Color(0xFFD4AF37),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 5. Drawing Palette & Brush Size Bar (when Draw Mode is ON)
        if (isDrawMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp, start = 12.dp, end = 12.dp)
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
                        items(CameraDrawPalette) { col ->
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
                        valueRange = 4f..34f,
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = activeBrushColor, activeTrackColor = activeBrushColor)
                    )
                    Text("${brushWidth.toInt()}px", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        // 6. Status Toast Banner
        AnimatedVisibility(
            visible = state.statusBanner != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isDrawMode) 156.dp else 98.dp)
        ) {
            state.statusBanner?.let { msg ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .border(1.dp, RcColors.AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 7. Bottom Object Selector & Axis Controls (hidden when sheet open, playing, or drawing)
        if (state.activeSheet == InspectorSheet.NONE && !state.isPlaying && !isDrawMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (scene.objects.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(scene.objects, key = { it.id }) { obj ->
                            val selected = obj.id == state.selectedObjectId
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selected) RcColors.AccentBlue
                                        else RcColors.SurfaceTranslucent
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) RcColors.AccentBlue else RcColors.Border,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onSelectObject(obj.id) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(obj.material.colorHex))
                                )
                                Text(
                                    text = obj.name,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                if (state.selectedObject != null) {
                    AxisGizmoControlBar(
                        activeTool = state.activeTool,
                        onSetTool = onSetTransformTool,
                        onNudge = onNudgeObject,
                        onOpenProperties = { onToggleSheet(InspectorSheet.PROPERTIES) }
                    )
                }
            }
        }

        // 8. Sliding Inspector Sheets (Add 3D Object, Properties + Text on Object, Behaviors)
        AnimatedVisibility(
            visible = state.activeSheet != InspectorSheet.NONE,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            when (state.activeSheet) {
                InspectorSheet.ADD_OBJECT -> AddObjectSheet(
                    onAddPrimitive = onAddPrimitive,
                    onAddText = onAddText,
                    onOpenDrawing = {
                        onCloseSheet()
                        isDrawMode = true
                    },
                    onAddPrefab = onAddPrefab,
                    onImportGlb = onImportGlb,
                    onClose = onCloseSheet
                )
                InspectorSheet.PROPERTIES -> PropertiesSheet(
                    selectedObject = state.selectedObject,
                    onUpdateObject = onUpdateObject,
                    onDuplicate = onDuplicateObject,
                    onDelete = onDeleteObject,
                    onClose = onCloseSheet
                )
                InspectorSheet.BEHAVIORS -> BehaviorsSheet(
                    scene = scene,
                    allScenes = project.scenes,
                    onAddTemplate = onAddBehaviorTemplate,
                    onUpdateBehavior = onUpdateBehavior,
                    onDeleteBehavior = onDeleteBehavior,
                    onAddAction = onAddAction,
                    onDeleteAction = onDeleteAction,
                    onTestBehavior = onTestBehavior,
                    onClose = onCloseSheet
                )
                else -> Unit
            }
        }

        // 9. Export PNG / JPG Dialog
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                containerColor = RcColors.Surface,
                title = { Text("Сохранить в PNG / JPG", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Сохранит кадр с камеры вместе с рисунками в Галерею телефона (Pictures/RealityComposer) и откроет меню «Поделиться».",
                        color = RcColors.TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExportDialog = false
                            captureAndExport(Bitmap.CompressFormat.PNG, "png")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue)
                    ) { Text("Сохранить PNG") }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            captureAndExport(Bitmap.CompressFormat.JPEG, "jpg")
                        }
                    ) { Text("Сохранить JPG", color = Color.White) }
                }
            )
        }
    }
}

@Composable
private fun TopCameraToolbar(
    state: EditorUiState,
    isDrawMode: Boolean,
    onToggleDraw: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleSnap: () -> Unit,
    onToggleSheet: (InspectorSheet) -> Unit,
    onSwitchCamera: () -> Unit,
    onStartObjectScan: () -> Unit,
    onOpenExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RcColors.SurfaceTranslucent)
            .border(0.5.dp, RcColors.Border)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Add 3D Object / Text / GLB
        ToolbarIconButton(
            icon = Icons.Default.Add,
            label = "3D",
            active = state.activeSheet == InspectorSheet.ADD_OBJECT,
            enabled = !state.isPlaying,
            onClick = { onToggleSheet(InspectorSheet.ADD_OBJECT) }
        )

        // Draw directly on Camera
        ToolbarIconButton(
            icon = Icons.Default.Brush,
            label = if (isDrawMode) "Готово" else "Рисовать",
            active = isDrawMode,
            enabled = !state.isPlaying,
            onClick = onToggleDraw
        )

        // Properties (Text on Object, Color, Size, Physics)
        ToolbarIconButton(
            icon = Icons.Default.Tune,
            label = "Свойства",
            active = state.activeSheet == InspectorSheet.PROPERTIES,
            enabled = !state.isPlaying,
            onClick = { onToggleSheet(InspectorSheet.PROPERTIES) }
        )

        // Export PNG / JPG
        ToolbarIconButton(
            icon = Icons.Default.SaveAlt,
            label = "PNG/JPG",
            active = false,
            onClick = onOpenExport
        )

        // Play / Stop Physics & Behaviors
        Button(
            onClick = onTogglePlay,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isPlaying) RcColors.DangerRed else RcColors.PlayGreen
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (state.isPlaying) Color.White else Color.Black,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (state.isPlaying) "Стоп" else "Играть",
                color = if (state.isPlaying) Color.White else Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Behaviors
        ToolbarIconButton(
            icon = Icons.Default.Bolt,
            label = "Поведения",
            active = state.activeSheet == InspectorSheet.BEHAVIORS,
            enabled = !state.isPlaying,
            onClick = { onToggleSheet(InspectorSheet.BEHAVIORS) }
        )

        // 360° 3D Object Scanner
        ToolbarIconButton(
            icon = Icons.Default.CameraAlt,
            label = "3D-Скан",
            active = false,
            enabled = !state.isPlaying,
            onClick = onStartObjectScan
        )

        // Switch Back / Front Camera
        ToolbarIconButton(
            icon = Icons.Default.Cameraswitch,
            label = null,
            active = false,
            onClick = onSwitchCamera
        )

        // Snap, Undo, Redo
        ToolbarIconButton(
            icon = Icons.Default.GridOn,
            label = null,
            active = state.snapEnabled,
            enabled = !state.isPlaying,
            onClick = onToggleSnap
        )
        ToolbarIconButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            label = null,
            active = false,
            enabled = state.canUndo && !state.isPlaying,
            onClick = onUndo
        )
        ToolbarIconButton(
            icon = Icons.AutoMirrored.Filled.Redo,
            label = null,
            active = false,
            enabled = state.canRedo && !state.isPlaying,
            onClick = onRedo
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    label: String?,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = if (active) RcColors.AccentBlue else RcColors.SurfaceElevated
    val fg = when {
        !enabled -> RcColors.TextTertiary
        active -> Color.White
        else -> RcColors.TextPrimary
    }

    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, if (active) RcColors.AccentBlue else RcColors.Border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (label != null) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(16.dp))
        if (label != null) {
            Text(
                text = label,
                color = fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AxisGizmoControlBar(
    activeTool: TransformTool,
    onSetTool: (TransformTool) -> Unit,
    onNudge: (Char, Float) -> Unit,
    onOpenProperties: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RcColors.SurfaceTranslucent)
            .border(1.dp, RcColors.Border, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(TransformTool.MOVE, TransformTool.ROTATE, TransformTool.SCALE).forEach { tool ->
                    val selected = activeTool == tool
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) RcColors.AccentBlue else RcColors.SurfaceElevated)
                            .clickable { onSetTool(tool) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = tool.displayNameRu,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            TextButton(
                onClick = onOpenProperties,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Текст / Свойства", color = RcColors.AccentBlue, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AxisNudgePair("X", RcColors.AxisX, { onNudge('X', -1f) }, { onNudge('X', 1f) })
            AxisNudgePair("Y", RcColors.AxisY, { onNudge('Y', -1f) }, { onNudge('Y', 1f) })
            AxisNudgePair("Z", RcColors.AxisZ, { onNudge('Z', -1f) }, { onNudge('Z', 1f) })
        }
    }
}

@Composable
private fun AxisNudgePair(
    axis: String,
    color: Color,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RcColors.SurfaceElevated)
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onMinus, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "-$axis", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(axis, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        IconButton(onClick = onPlus, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Default.Add, contentDescription = "+$axis", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

private fun saveCameraShotToGallery(
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
