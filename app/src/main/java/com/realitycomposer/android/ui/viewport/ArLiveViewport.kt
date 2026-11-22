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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.realitycomposer.android.model.*
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

@Composable
fun ArLiveViewport(
    scene: RealityScene,
    selectedObjectId: String?,
    isPlaying: Boolean,
    arPlaced: Boolean,
    onArPlacedChange: (Boolean) -> Unit,
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

    val arAvailability = remember {
        runCatching { ArCoreApk.getInstance().checkAvailability(context) }
            .getOrDefault(ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE)
    }
    val arSupported = arAvailability == ArCoreApk.Availability.SUPPORTED_INSTALLED

    var useFrontCamera by remember(scene.anchorType) {
        mutableStateOf(scene.anchorType == AnchorType.FACE)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!hasCameraPermission) {
            // Permission Request Screen
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
                        text = "Доступ к камере для AR",
                        color = RcColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Разрешите доступ к камере, чтобы накладывать 3D-объекты на видеопоток в реальном времени.",
                        color = RcColors.TextSecondary,
                        fontSize = 13.sp
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
        } else if (arSupported) {
            RealArCoreScene(
                scene = scene,
                arPlaced = arPlaced,
                onArPlacedChange = onArPlacedChange,
                onSelectObject = onSelectObject,
                onCameraMoved = onCameraMoved
            )
        } else {
            // Live CameraX Feed + Transparent 3D Filament Scene Overlay!
            // Works on ALL Android phones & Android Studio Emulators (VirtualScene / Webcam)
            LiveCameraPreview(
                useFrontCamera = useFrontCamera,
                modifier = Modifier.fillMaxSize()
            )

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

            // Camera switch button (Back / Front)
            IconButton(
                onClick = { useFrontCamera = !useFrontCamera },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(RcColors.SurfaceTranslucent)
                    .border(1.dp, RcColors.Border, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Переключить камеру",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Compact Live AR Camera indicator pill
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 112.dp, end = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(1.dp, RcColors.PlayGreen.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ViewInAr,
                    contentDescription = null,
                    tint = RcColors.PlayGreen,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (useFrontCamera) "AR Камера (Фронт)" else "AR Камера (Тыл)",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LiveCameraPreview(
    useFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    key(useFrontCamera) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PreviewView(ctx).apply {
                    // COMPATIBLE uses TextureView so Filament translucent SurfaceView draws cleanly on top
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { previewView ->
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

@Composable
private fun RealArCoreScene(
    scene: RealityScene,
    arPlaced: Boolean,
    onArPlacedChange: (Boolean) -> Unit,
    onSelectObject: (String?) -> Unit,
    onCameraMoved: (Vec3) -> Unit
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)
    val cameraNode = rememberARCameraNode(engine)

    val childNodes = rememberNodes()
    var anchorNode by remember { mutableStateOf<AnchorNode?>(null) }
    var currentFrame by remember { mutableStateOf<Frame?>(null) }
    val sceneContainer = remember { Node(engine).apply { name = "__ar_scene_root__" } }
    val objectNodes = remember { mutableMapOf<String, Pair<Node, String>>() }

    LaunchedEffect(scene.objects, arPlaced) {
        if (!arPlaced) return@LaunchedEffect
        val currentIds = scene.objects.map { it.id }.toSet()
        objectNodes.keys.filter { it !in currentIds }.forEach { id ->
            objectNodes.remove(id)?.first?.let { node ->
                sceneContainer.removeChildNode(node)
                node.destroy()
            }
        }
        scene.objects.forEach { obj ->
            val key = "${obj.type}|${obj.modelPath}|${obj.imagePath}|${obj.textContent}|${obj.material.colorHex}|${obj.material.metallic}|${obj.material.roughness}"
            val existing = objectNodes[obj.id]
            if (existing == null || existing.second != key) {
                existing?.first?.let { old ->
                    sceneContainer.removeChildNode(old)
                    old.destroy()
                }
                val created = SceneNodeBuilder.buildObjectNode(engine, materialLoader, modelLoader, obj)
                sceneContainer.addChildNode(created)
                objectNodes[obj.id] = created to key
            } else {
                existing.first.apply {
                    position = Position(obj.position.x, obj.position.y, obj.position.z)
                    rotation = Rotation(obj.rotation.x, obj.rotation.y, obj.rotation.z)
                    scale = Scale(obj.scale.x, obj.scale.y, obj.scale.z)
                    isVisible = obj.visible
                }
            }
        }
    }

    val objectIdSet = remember(scene.objects) { scene.objects.map { it.id }.toSet() }

    Box(modifier = Modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            view = view,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            collisionSystem = collisionSystem,
            cameraNode = cameraNode,
            childNodes = childNodes,
            planeRenderer = !arPlaced,
            sessionConfiguration = { session, config ->
                config.depthMode =
                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        Config.DepthMode.AUTOMATIC
                    } else {
                        Config.DepthMode.DISABLED
                    }
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.planeFindingMode = when (scene.anchorType) {
                    AnchorType.VERTICAL -> Config.PlaneFindingMode.VERTICAL
                    else -> Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                }
            },
            onSessionUpdated = { _, frame ->
                currentFrame = frame
                val pose = frame.camera.pose
                onCameraMoved(Vec3(pose.tx(), pose.ty(), pose.tz()))
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, hitNode ->
                    if (!arPlaced) {
                        val hitResult = currentFrame?.hitTest(motionEvent)
                            ?.firstOrNull { result ->
                                val trackable = result.trackable
                                trackable is Plane && trackable.isPoseInPolygon(result.hitPose)
                            }
                        if (hitResult != null) {
                            val newAnchor = AnchorNode(engine, hitResult.createAnchor()).apply {
                                addChildNode(sceneContainer)
                            }
                            anchorNode?.let { old ->
                                childNodes.remove(old)
                                old.destroy()
                            }
                            anchorNode = newAnchor
                            childNodes.add(newAnchor)
                            onArPlacedChange(true)
                        }
                    } else {
                        var cursor: Node? = hitNode
                        var foundId: String? = null
                        while (cursor != null) {
                            val n = cursor.name
                            if (n != null && n in objectIdSet) {
                                foundId = n
                                break
                            }
                            cursor = cursor.parent
                        }
                        onSelectObject(foundId)
                    }
                }
            )
        )

        if (!arPlaced) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .background(RcColors.SurfaceTranslucent, RoundedCornerShape(20.dp))
                    .border(1.dp, RcColors.AccentBlue.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Наведите камеру на поверхность и нажмите для размещения сцены",
                    color = RcColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
