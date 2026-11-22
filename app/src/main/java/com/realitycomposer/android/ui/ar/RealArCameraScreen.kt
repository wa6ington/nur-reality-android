package com.realitycomposer.android.ui.ar

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.realitycomposer.android.R
import com.realitycomposer.android.ui.theme.RcColors
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private data class ArDrawStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float
)

private val ArPalette = listOf(
    0xFFB8B8BE, // Apple Reality Composer metallic gray (like in video!)
    0xFFFFFFFF,
    0xFFFFD60A, // Gold
    0xFF0A84FF, // Blue
    0xFFFF453A, // Red
    0xFF30D158, // Green
    0xFFFF9F0A, // Orange
    0xFFBF5AF2, // Purple
    0xFF1C1C1E  // Matte Black
)

@Composable
fun RealArCameraScreen(
    onImportGlb: ((String) -> Unit) -> Unit,
    onExportImage: (File, String) -> Unit,
    onArUnsupported: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity

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
        // Prompt install/update of Google Play Services for AR on Samsung A55 if needed
        if (activity != null) {
            runCatching { ArCoreApk.getInstance().requestInstall(activity, true) }
        }
    }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)
    val cameraNode = rememberARCameraNode(engine)
    val childNodes = rememberNodes()

    var currentFrame by remember { mutableStateOf<Frame?>(null) }
    var hasDetectedPlane by remember { mutableStateOf(false) }

    // Placed world-anchored 3D objects
    val placedItems = remember { mutableStateListOf<PlacedArItem>() }
    val anchorNodesMap = remember { mutableMapOf<String, AnchorNode>() }
    val visualNodesMap = remember { mutableMapOf<String, Node>() }
    var selectedItemId by remember { mutableStateOf<String?>(null) }
    val selectedItem = placedItems.firstOrNull { it.id == selectedItemId }

    // Next object kind to place on tap (defaults to 3D volumetric signage "NUR")
    var pendingKind by remember { mutableStateOf(ArObjectKind.TEXT_3D) }
    var pendingText by remember { mutableStateOf("NUR") }
    var pendingVertical by remember { mutableStateOf(false) }
    var pendingGlbPath by remember { mutableStateOf<String?>(null) }
    var showPlanes by remember { mutableStateOf(true) }

    // UI states
    var showAddSheet by remember { mutableStateOf(false) }
    var showEditPanel by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    // Drawing over AR camera
    var isDrawMode by remember { mutableStateOf(false) }
    val drawStrokes = remember { mutableStateListOf<ArDrawStroke>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var brushColor by remember { mutableStateOf(Color(0xFFFFD60A)) }
    var brushWidth by remember { mutableStateOf(10f) }

    fun rebuildVisualForItem(item: PlacedArItem) {
        val anchor = anchorNodesMap[item.id] ?: return
        visualNodesMap.remove(item.id)?.let { old ->
            anchor.removeChildNode(old)
            old.destroy()
        }
        val fresh = Extruded3DTextBuilder.buildArObjectNode(
            engine = engine,
            materialLoader = materialLoader,
            modelLoader = modelLoader,
            item = item,
            showSelectionRing = (item.id == selectedItemId)
        ).apply {
            position = Position(0f, item.offsetY, 0f)
            scale = Scale(item.scale)
            rotation = Rotation(0f, item.rotationY, 0f)
        }
        anchor.addChildNode(fresh)
        visualNodesMap[item.id] = fresh
    }

    fun updateSelectedItem(rebuildMesh: Boolean = false, transform: (PlacedArItem) -> PlacedArItem) {
        val idx = placedItems.indexOfFirst { it.id == selectedItemId }
        if (idx < 0) return
        val updated = transform(placedItems[idx])
        placedItems[idx] = updated
        if (rebuildMesh) {
            rebuildVisualForItem(updated)
        } else {
            visualNodesMap[updated.id]?.apply {
                position = Position(0f, updated.offsetY, 0f)
                scale = Scale(updated.scale)
                rotation = Rotation(0f, updated.rotationY, 0f)
            }
        }
    }

    fun deleteSelectedItem() {
        val id = selectedItemId ?: return
        placedItems.removeAll { it.id == id }
        anchorNodesMap.remove(id)?.let { anchor ->
            childNodes.remove(anchor)
            anchor.destroy()
        }
        visualNodesMap.remove(id)
        selectedItemId = placedItems.lastOrNull()?.id
    }

    fun captureArScreen(format: Bitmap.CompressFormat, ext: String) {
        val window = activity?.window ?: return
        val decor = window.decorView
        val w = decor.width.coerceAtLeast(720)
        val h = decor.height.coerceAtLeast(1280)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            window,
            bmp,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    val fileName = "NUR_AR_${System.currentTimeMillis()}.$ext"
                    val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
                    val file = File(exportsDir, fileName)
                    FileOutputStream(file).use { out -> bmp.compress(format, 95, out) }
                    val mime = if (ext == "png") "image/png" else "image/jpeg"
                    saveArBitmapToGallery(context, bmp, fileName, format, mime)
                    toastMsg = "Сохранено в Галерею: $fileName"
                    onExportImage(file, mime)
                } else {
                    toastMsg = "Не удалось сделать снимок экрана"
                }
            },
            Handler(Looper.getMainLooper())
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
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
                    "Разрешите камеру для размещения 3D-объектов и 3D-текста на реальных поверхностях (ARCore).",
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

        // 1. REAL WORLD-TRACKED ARCORE CAMERA + 3D FILAMENT SCENE!
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            view = view,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            collisionSystem = collisionSystem,
            cameraNode = cameraNode,
            childNodes = childNodes,
            planeRenderer = showPlanes && placedItems.isEmpty(),
            sessionConfiguration = { session, config ->
                config.depthMode =
                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        Config.DepthMode.AUTOMATIC
                    } else {
                        Config.DepthMode.DISABLED
                    }
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            },
            onSessionFailed = { _ ->
                onArUnsupported()
            },
            onSessionUpdated = { session, frame ->
                currentFrame = frame
                if (!hasDetectedPlane) {
                    val planes = session.getAllTrackables(Plane::class.java)
                    if (planes.any { it.trackingState == TrackingState.TRACKING }) {
                        hasDetectedPlane = true
                    }
                }
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, hitNode ->
                    if (isDrawMode) return@rememberOnGestureListener

                    // Check if user tapped an already placed 3D object to select it
                    var cursor: Node? = hitNode
                    var tappedId: String? = null
                    while (cursor != null) {
                        val n = cursor.name
                        if (n != null && anchorNodesMap.containsKey(n)) {
                            tappedId = n
                            break
                        }
                        cursor = cursor.parent
                    }

                    if (tappedId != null) {
                        val prevId = selectedItemId
                        selectedItemId = tappedId
                        prevId?.let { id -> placedItems.firstOrNull { it.id == id }?.let(::rebuildVisualForItem) }
                        placedItems.firstOrNull { it.id == tappedId }?.let(::rebuildVisualForItem)
                        return@rememberOnGestureListener
                    }

                    // Otherwise place a new 3D object anchored in real 6DoF space on wall/desk/façade!
                    val frame = currentFrame ?: return@rememberOnGestureListener
                    val hitResult = frame.hitTest(motionEvent).firstOrNull { result ->
                        val trackable = result.trackable
                        trackable is Plane && trackable.isPoseInPolygon(result.hitPose)
                    } ?: frame.hitTestInstantPlacement(motionEvent.x, motionEvent.y, 2.8f).firstOrNull()

                    if (hitResult != null) {
                        val newItem = PlacedArItem(
                            id = UUID.randomUUID().toString(),
                            kind = pendingKind,
                            text = if (pendingKind == ArObjectKind.TEXT_3D) pendingText else "",
                            isVertical = pendingVertical,
                            colorHex = if (pendingKind == ArObjectKind.TEXT_3D) 0xFFE5E5EA else 0xFF0A84FF,
                            modelPath = pendingGlbPath
                        )
                        val anchorNode = AnchorNode(engine, hitResult.createAnchor()).apply {
                            name = newItem.id
                        }
                        val prevId = selectedItemId
                        placedItems.add(newItem)
                        anchorNodesMap[newItem.id] = anchorNode
                        selectedItemId = newItem.id

                        val visual = Extruded3DTextBuilder.buildArObjectNode(
                            engine = engine,
                            materialLoader = materialLoader,
                            modelLoader = modelLoader,
                            item = newItem,
                            showSelectionRing = true
                        )
                        anchorNode.addChildNode(visual)
                        visualNodesMap[newItem.id] = visual
                        childNodes.add(anchorNode)

                        // Refresh previous item to hide its selection ring
                        prevId?.let { id -> placedItems.firstOrNull { it.id == id }?.let(::rebuildVisualForItem) }
                        toastMsg = "Установлено в AR — обойдите вокруг!"
                    } else {
                        toastMsg = "Поводите камерой по столу, чтобы определить поверхность"
                    }
                }
            )
        )

        // 2. Finger Drawing Layer directly over AR Camera
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
                                        drawStrokes.add(ArDrawStroke(currentStroke, brushColor, brushWidth))
                                    }
                                    currentStroke = emptyList()
                                },
                                onDragCancel = { currentStroke = emptyList() }
                            )
                        }
                    } else Modifier
                )
        ) {
            fun drawOne(pts: List<Offset>, col: Color, w: Float) {
                if (pts.size == 1) drawCircle(col, w / 2f, pts[0])
                else if (pts.size > 1) {
                    val p = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                    }
                    drawPath(p, col, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            for (s in drawStrokes) drawOne(s.points, s.color, s.width)
            if (currentStroke.isNotEmpty()) drawOne(currentStroke, brushColor, brushWidth)
        }

        // 3. TOP APPLE-STYLE TRANSLUCENT AR TOOLBAR (matching the video!)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color(0xB818181C))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // NUR Brand + Author Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_logo),
                    contentDescription = "NUR",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(6.dp))
                )
                Column {
                    Text("NUR AR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Sanzhar", color = Color(0xFFD4AF37), fontSize = 10.sp)
                }
            }

            // Center / Right Action Icons (+, Text, Edit, Draw, Save PNG/JPG, Delete)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ArTopIcon(Icons.Default.Add, active = showAddSheet) {
                    isDrawMode = false
                    showEditPanel = false
                    showAddSheet = !showAddSheet
                }
                ArTopIcon(Icons.Default.TextFields, active = showTextDialog) {
                    isDrawMode = false
                    showAddSheet = false
                    showTextDialog = true
                }
                ArTopIcon(Icons.Default.Tune, active = showEditPanel) {
                    isDrawMode = false
                    showAddSheet = false
                    showEditPanel = !showEditPanel
                }
                ArTopIcon(Icons.Default.Brush, active = isDrawMode) {
                    showAddSheet = false
                    showEditPanel = false
                    isDrawMode = !isDrawMode
                }
                ArTopIcon(Icons.Default.SaveAlt, active = false) {
                    showExportDialog = true
                }
                if (selectedItem != null) {
                    IconButton(onClick = ::deleteSelectedItem, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = RcColors.DangerRed, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // 4. Placement Guidance Pill (when no object placed yet)
        if (placedItems.isEmpty() && !showAddSheet && !isDrawMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (hasDetectedPlane) {
                        "Нажмите на стол, чтобы поставить «${if (pendingKind == ArObjectKind.TEXT_3D) pendingText else pendingKind.titleRu}»"
                    } else {
                        "Медленно поводите камерой по столу или полу, затем нажмите на поверхность"
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 5. Toast Banner
        toastMsg?.let { msg ->
            LaunchedEffect(msg) {
                delay(2400L)
                toastMsg = null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(msg, color = RcColors.PlayGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        // 6. Draw Mode Palette Bar
        if (isDrawMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, RcColors.AccentBlue, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(ArPalette) { hex ->
                        val col = Color(hex)
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
                IconButton(onClick = { if (drawStrokes.isNotEmpty()) drawStrokes.removeAt(drawStrokes.lastIndex) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { drawStrokes.clear() }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.DeleteSweep, null, tint = RcColors.DangerRed, modifier = Modifier.size(20.dp))
                }
            }
        }

        // 7. Selected Object Properties Bar (Layout, Face Color, Size up to Building Scale, Height, Depth, Rotate Y)
        if (showEditPanel && selectedItem != null && !isDrawMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xE61C1C1E))
                    .border(1.dp, RcColors.Border, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Настройка 3D-вывески", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (selectedItem.kind == ArObjectKind.TEXT_3D) {
                            FilterChip(
                                selected = selectedItem.isVertical,
                                onClick = {
                                    updateSelectedItem(rebuildMesh = true) { it.copy(isVertical = !it.isVertical) }
                                },
                                label = {
                                    Text(if (selectedItem.isVertical) "Вертикально" else "Горизонтально", fontSize = 11.sp)
                                }
                            )
                        }
                        TextButton(onClick = { showTextDialog = true }) {
                            Text("Текст", color = Color(0xFFFFD60A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ArPalette) { hex ->
                        val active = selectedItem.colorHex == hex
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(if (active) 2.5.dp else 1.dp, if (active) RcColors.AccentBlue else RcColors.Border, CircleShape)
                                .clickable { updateSelectedItem(rebuildMesh = true) { it.copy(colorHex = hex) } }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Размер", color = RcColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                    Slider(
                        value = selectedItem.scale,
                        onValueChange = { s -> updateSelectedItem(rebuildMesh = false) { it.copy(scale = s) } },
                        valueRange = 0.3f..15.0f,
                        modifier = Modifier.weight(1f).height(24.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Высота", color = RcColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                    Slider(
                        value = selectedItem.offsetY,
                        onValueChange = { y -> updateSelectedItem(rebuildMesh = false) { it.copy(offsetY = y) } },
                        valueRange = -1.0f..6.0f,
                        modifier = Modifier.weight(1f).height(24.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Поворот", color = RcColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                    Slider(
                        value = selectedItem.rotationY,
                        onValueChange = { r -> updateSelectedItem(rebuildMesh = false) { it.copy(rotationY = r) } },
                        valueRange = -180f..180f,
                        modifier = Modifier.weight(1f).height(24.dp)
                    )
                }
                if (selectedItem.kind == ArObjectKind.TEXT_3D) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Толщина", color = RcColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(64.dp))
                        Slider(
                            value = selectedItem.extrudeDepth,
                            onValueChange = { d -> updateSelectedItem(rebuildMesh = true) { it.copy(extrudeDepth = d) } },
                            valueRange = 0.01f..0.12f,
                            modifier = Modifier.weight(1f).height(24.dp)
                        )
                    }
                }
            }
        }

        // 8. Add Object Picker Sheet (+)
        if (showAddSheet) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Color(0xEB1C1C1E))
                    .border(1.dp, RcColors.Border, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Выберите 3D-объект и нажмите на стол", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = { showAddSheet = false }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, null, tint = RcColors.TextSecondary)
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ArObjectKind.entries) { kind ->
                        val active = pendingKind == kind
                        Column(
                            modifier = Modifier
                                .width(92.dp)
                                .height(84.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (active) RcColors.AccentBlue.copy(alpha = 0.28f) else RcColors.SurfaceElevated)
                                .border(if (active) 1.5.dp else 1.dp, if (active) RcColors.AccentBlue else RcColors.Border, RoundedCornerShape(14.dp))
                                .clickable {
                                    if (kind == ArObjectKind.GLB) {
                                        showAddSheet = false
                                        onImportGlb { path ->
                                            pendingKind = ArObjectKind.GLB
                                            pendingGlbPath = path
                                            toastMsg = "3D-модель готова — нажмите на стол!"
                                        }
                                    } else {
                                        pendingKind = kind
                                        showAddSheet = false
                                        toastMsg = "Выбрано: ${kind.titleRu} — нажмите на поверхность"
                                    }
                                }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (kind) {
                                    ArObjectKind.TEXT_3D -> Icons.Default.TextFields
                                    ArObjectKind.CUBE -> Icons.Default.ViewInAr
                                    ArObjectKind.SPHERE -> Icons.Default.Circle
                                    ArObjectKind.CYLINDER -> Icons.Default.PanoramaVertical
                                    ArObjectKind.CONE -> Icons.Default.ChangeHistory
                                    ArObjectKind.GLB -> Icons.Default.FileUpload
                                },
                                contentDescription = kind.titleRu,
                                tint = if (active) Color.White else Color(0xFFFFD60A),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(kind.titleRu, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 9. Text Input Dialog (Edits selected 3D signage OR sets next 3D signage to place!)
        if (showTextDialog) {
            var input by remember { mutableStateOf(selectedItem?.text?.ifBlank { pendingText } ?: pendingText) }
            var verticalMode by remember { mutableStateOf(selectedItem?.isVertical ?: pendingVertical) }
            AlertDialog(
                onDismissRequest = { showTextDialog = false },
                containerColor = RcColors.Surface,
                title = { Text("3D-Вывеска на фасад", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            label = { Text("Текст (например: ТДН или NUR)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
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
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val clean = input.trim().ifBlank { "NUR" }
                            pendingText = clean
                            pendingVertical = verticalMode
                            if (selectedItem != null) {
                                updateSelectedItem(rebuildMesh = true) {
                                    it.copy(text = clean, isVertical = verticalMode)
                                }
                            } else {
                                pendingKind = ArObjectKind.TEXT_3D
                                toastMsg = "Вывеска «$clean» готова — нажмите на стену!"
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

        // 10. Export PNG / JPG Dialog
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                containerColor = RcColors.Surface,
                title = { Text("Сохранить AR-снимок", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Сохранит камеру вместе со всеми 3D-объектами на столе и рисунками в Галерею телефона.",
                        color = RcColors.TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExportDialog = false
                            captureArScreen(Bitmap.CompressFormat.PNG, "png")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue)
                    ) { Text("Сохранить PNG") }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            captureArScreen(Bitmap.CompressFormat.JPEG, "jpg")
                        }
                    ) { Text("Сохранить JPG", color = Color.White) }
                }
            )
        }
    }
}

@Composable
private fun ArTopIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) RcColors.AccentBlue else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) Color.White else RcColors.AccentBlue,
            modifier = Modifier.size(22.dp)
        )
    }
}

private fun saveArBitmapToGallery(
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
