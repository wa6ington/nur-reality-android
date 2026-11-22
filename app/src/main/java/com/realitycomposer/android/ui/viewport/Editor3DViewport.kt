package com.realitycomposer.android.ui.viewport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.realitycomposer.android.model.*
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView

private data class ObjectVisualKey(
    val type: ObjectType,
    val modelPath: String?,
    val imagePath: String?,
    val textContent: String,
    val labelText: String,
    val labelColorHex: Long,
    val colorHex: Long,
    val metallic: Float,
    val roughness: Float,
    val reflectance: Float
)

@Composable
fun Editor3DViewport(
    scene: RealityScene,
    selectedObjectId: String?,
    isPlaying: Boolean,
    onSelectObject: (String?) -> Unit,
    onCameraMoved: (Vec3) -> Unit,
    modifier: Modifier = Modifier,
    transparentBackground: Boolean = false,
    showGridOverride: Boolean? = null
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)

    val cameraNode = rememberCameraNode(engine) {
        position = Position(x = 0.0f, y = 0.95f, z = 1.85f)
        lookAt(Position(0f, 0.2f, 0f))
    }
    val cameraManipulator = rememberCameraManipulator(
        orbitHomePosition = Position(0.0f, 0.95f, 1.85f),
        targetPosition = Position(0.0f, 0.2f, 0.0f)
    )
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 110_000.0f
        position = Position(2f, 4f, 2f)
    }

    val rootContentNode = remember { Node(engine).apply { name = "__scene_objects__" } }
    val gizmoNode = remember { SceneNodeBuilder.buildSelectionGizmoNode(engine, materialLoader) }
    var gridNode by remember { mutableStateOf<Node?>(null) }

    val objectNodes = remember { mutableMapOf<String, Pair<Node, ObjectVisualKey>>() }

    val childNodes = rememberNodes {
        add(rootContentNode)
        add(gizmoNode)
    }

    // Rebuild grid when anchorType or gridVisible changes
    val effectiveShowGrid = showGridOverride ?: scene.gridVisible
    LaunchedEffect(scene.anchorType, effectiveShowGrid) {
        gridNode?.let { old ->
            childNodes.remove(old)
            old.destroy()
        }
        val freshGrid = SceneNodeBuilder.buildGridAndAxesNode(
            engine = engine,
            materialLoader = materialLoader,
            anchorType = scene.anchorType,
            showGrid = effectiveShowGrid
        )
        gridNode = freshGrid
        childNodes.add(freshGrid)
    }

    // Sync scene objects smoothly without recreating nodes on transform changes
    LaunchedEffect(scene.objects) {
        val currentIds = scene.objects.map { it.id }.toSet()

        // Remove deleted objects
        val toRemove = objectNodes.keys.filter { it !in currentIds }
        toRemove.forEach { id ->
            objectNodes.remove(id)?.first?.let { node ->
                rootContentNode.removeChildNode(node)
                node.destroy()
            }
        }

        // Add or update objects
        scene.objects.forEach { obj ->
            val visualKey = ObjectVisualKey(
                type = obj.type,
                modelPath = obj.modelPath,
                imagePath = obj.imagePath,
                textContent = obj.textContent,
                labelText = obj.labelText,
                labelColorHex = obj.labelColorHex,
                colorHex = obj.material.colorHex,
                metallic = obj.material.metallic,
                roughness = obj.material.roughness,
                reflectance = obj.material.reflectance
            )
            val existing = objectNodes[obj.id]
            if (existing == null || existing.second != visualKey) {
                existing?.first?.let { old ->
                    rootContentNode.removeChildNode(old)
                    old.destroy()
                }
                val newNode = SceneNodeBuilder.buildObjectNode(engine, materialLoader, modelLoader, obj)
                rootContentNode.addChildNode(newNode)
                objectNodes[obj.id] = newNode to visualKey
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

    // Update 3D Selection Gizmo
    LaunchedEffect(selectedObjectId, scene.objects, isPlaying) {
        val selected = if (!isPlaying) scene.objects.firstOrNull { it.id == selectedObjectId } else null
        if (selected != null && selected.visible) {
            gizmoNode.position = Position(selected.position.x, selected.position.y, selected.position.z)
            gizmoNode.isVisible = true
        } else {
            gizmoNode.isVisible = false
        }
    }

    val objectIdSet = remember(scene.objects) { scene.objects.map { it.id }.toSet() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (transparentBackground) Color.Transparent else Color(0xFF1A1A1E))
    ) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            view = view,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            collisionSystem = collisionSystem,
            mainLightNode = mainLightNode,
            cameraNode = cameraNode,
            cameraManipulator = cameraManipulator,
            isOpaque = !transparentBackground,
            childNodes = childNodes,
            onFrame = {
                val p = cameraNode.worldPosition
                onCameraMoved(Vec3(p.x, p.y, p.z))
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { _, hitNode ->
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
            )
        )
    }
}
