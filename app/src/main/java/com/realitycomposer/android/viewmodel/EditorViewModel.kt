package com.realitycomposer.android.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.realitycomposer.android.data.ProjectRepository
import com.realitycomposer.android.engine.BehaviorEngine
import com.realitycomposer.android.engine.PhysicsEngine
import com.realitycomposer.android.model.*
import com.realitycomposer.android.scanner.ObjectCaptureEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.round

data class EditorUiState(
    val projects: List<RealityProject> = emptyList(),
    val scannedObjects: List<ScannedObject> = emptyList(),
    val isScanningObject: Boolean = false,
    val isDrawingStudioOpen: Boolean = false,
    val activeScanViewer: ScannedObject? = null,
    val currentProject: RealityProject? = null,
    val selectedObjectId: String? = null,
    val activeTool: TransformTool = TransformTool.MOVE,
    val viewMode: ViewMode = ViewMode.AR_LIVE,
    val activeSheet: InspectorSheet = InspectorSheet.NONE,
    val snapEnabled: Boolean = true,
    val isPlaying: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val statusBanner: String? = null,
    val cameraPosition: Vec3 = Vec3(0f, 0.9f, 1.8f),
    val arPlaced: Boolean = false
) {
    val activeScene: RealityScene?
        get() = currentProject?.activeScene

    val selectedObject: SceneObject?
        get() = activeScene?.objects?.firstOrNull { it.id == selectedObjectId }
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application.applicationContext)
    private val captureEngine = ObjectCaptureEngine(application.applicationContext)
    private val physicsEngine = PhysicsEngine()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<RealityProject>()
    private val redoStack = ArrayDeque<RealityProject>()

    private var prePlayProjectSnapshot: RealityProject? = null
    private var playLoopJob: Job? = null
    private var bannerJob: Job? = null

    private val behaviorEngine = BehaviorEngine(
        scope = viewModelScope,
        getObject = { id -> _uiState.value.activeScene?.objects?.firstOrNull { it.id == id } },
        updateObject = { id, transform -> mutateActiveSceneSilent { scene ->
            scene.copy(objects = scene.objects.map { if (it.id == id) transform(it) else it })
        } },
        onSwitchScene = { sceneId -> switchScene(sceneId) },
        onStatusMessage = { msg -> showBanner(msg) }
    )

    init {
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch {
            val loaded = repository.loadAllProjects()
            val scans = captureEngine.loadAllScans()
            val active = _uiState.value.currentProject ?: loaded.firstOrNull() ?: repository.createNewProject(
                "NUR Камера",
                AnchorType.HORIZONTAL
            )
            _uiState.update {
                it.copy(
                    projects = loaded,
                    scannedObjects = scans,
                    currentProject = active,
                    selectedObjectId = it.selectedObjectId ?: active.activeScene.objects.firstOrNull()?.id,
                    viewMode = ViewMode.AR_LIVE
                )
            }
        }
    }

    fun startObjectScan() {
        stopPlayModeIfRunning()
        _uiState.update { it.copy(isScanningObject = true, activeScanViewer = null) }
    }

    fun cancelObjectScan() {
        _uiState.update { it.copy(isScanningObject = false) }
    }

    fun onScanCompleted(scan: ScannedObject) {
        viewModelScope.launch {
            val scans = captureEngine.loadAllScans()
            val scannedObj = SceneObject(
                name = scan.name,
                type = ObjectType.SCANNED_3D,
                objFilePath = scan.objFilePath,
                scanPoints = scan.points,
                position = Vec3(0f, 0.25f, 0f),
                scale = Vec3(1.0f, 1.0f, 1.0f),
                material = MaterialProperties(colorHex = 0xFFFFD60A)
            )
            mutateActiveScene { scene -> scene.copy(objects = scene.objects + scannedObj) }
            _uiState.update {
                it.copy(
                    scannedObjects = scans,
                    isScanningObject = false,
                    activeScanViewer = null,
                    selectedObjectId = scannedObj.id
                )
            }
            showBanner("3D-модель создана и добавлена в камеру!")
        }
    }

    fun openScannedObject(scan: ScannedObject) {
        _uiState.update { it.copy(activeScanViewer = scan, isScanningObject = false) }
    }

    fun closeScannedObjectViewer() {
        _uiState.update { it.copy(activeScanViewer = null) }
    }

    fun deleteScannedObject(scanId: String) {
        viewModelScope.launch {
            captureEngine.deleteScan(scanId)
            val scans = captureEngine.loadAllScans()
            _uiState.update { it.copy(scannedObjects = scans) }
        }
    }

    fun openScanInArEditor(scan: ScannedObject) {
        _uiState.update { it.copy(activeScanViewer = null) }
        createProject(scan.name, AnchorType.HORIZONTAL)
    }

    fun openDrawingStudio() {
        stopPlayModeIfRunning()
        _uiState.update { it.copy(isDrawingStudioOpen = true, activeSheet = InspectorSheet.NONE) }
    }

    fun closeDrawingStudio() {
        _uiState.update { it.copy(isDrawingStudioOpen = false) }
    }

    fun addTextObject(text: String) {
        val count = (_uiState.value.activeScene?.objects?.count { it.type == ObjectType.TEXT_3D } ?: 0) + 1
        val newObj = SceneObject(
            name = "Текст $count",
            type = ObjectType.TEXT_3D,
            textContent = text.trim().ifBlank { "Текст" },
            position = Vec3(0f, 0.35f, 0f),
            scale = Vec3(0.75f, 0.75f, 0.75f),
            material = MaterialProperties(colorHex = 0xFFFFD60A)
        )
        mutateActiveScene { scene -> scene.copy(objects = scene.objects + newObj) }
        _uiState.update { it.copy(selectedObjectId = newObj.id, activeSheet = InspectorSheet.NONE) }
        showBanner("Добавлен 3D-текст: ${newObj.textContent}")
    }

    fun addDrawingObject(imageFile: File) {
        val count = (_uiState.value.activeScene?.objects?.count { it.type == ObjectType.DRAWING_3D } ?: 0) + 1
        val newObj = SceneObject(
            name = "Рисунок $count",
            type = ObjectType.DRAWING_3D,
            imagePath = imageFile.absolutePath,
            position = Vec3(0f, 0.42f, 0f),
            scale = Vec3(0.8f, 0.8f, 0.8f),
            material = MaterialProperties(colorHex = 0xFFFFFFFF)
        )
        mutateActiveScene { scene -> scene.copy(objects = scene.objects + newObj) }
        _uiState.update {
            it.copy(
                selectedObjectId = newObj.id,
                isDrawingStudioOpen = false,
                activeSheet = InspectorSheet.NONE
            )
        }
        showBanner("Рисунок добавлен в 3D-сцену")
    }

    fun createProject(name: String, anchorType: AnchorType) {
        viewModelScope.launch {
            val newProject = repository.createNewProject(name, anchorType)
            repository.saveProject(newProject)
            val all = repository.loadAllProjects()
            undoStack.clear()
            redoStack.clear()
            _uiState.update {
                it.copy(
                    projects = all,
                    currentProject = newProject,
                    selectedObjectId = newProject.activeScene.objects.firstOrNull()?.id,
                    activeSheet = InspectorSheet.NONE,
                    viewMode = ViewMode.EDITOR_3D,
                    isPlaying = false,
                    canUndo = false,
                    canRedo = false
                )
            }
        }
    }

    fun openProject(project: RealityProject) {
        stopPlayModeIfRunning()
        undoStack.clear()
        redoStack.clear()
        _uiState.update {
            it.copy(
                currentProject = project,
                selectedObjectId = project.activeScene.objects.firstOrNull()?.id,
                activeSheet = InspectorSheet.NONE,
                viewMode = ViewMode.EDITOR_3D,
                canUndo = false,
                canRedo = false,
                arPlaced = false
            )
        }
    }

    fun closeProject() {
        stopPlayModeIfRunning()
        val proj = _uiState.value.currentProject
        viewModelScope.launch {
            if (proj != null) repository.saveProject(proj)
            val all = repository.loadAllProjects()
            _uiState.update {
                it.copy(
                    projects = all,
                    currentProject = null,
                    selectedObjectId = null,
                    activeSheet = InspectorSheet.NONE,
                    viewMode = ViewMode.EDITOR_3D,
                    isPlaying = false
                )
            }
        }
    }

    fun duplicateProject(project: RealityProject) {
        viewModelScope.launch {
            repository.duplicateProject(project)
            loadProjects()
            showBanner("Проект дублирован")
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            loadProjects()
            showBanner("Проект удален")
        }
    }

    fun renameCurrentProject(newName: String) {
        if (newName.isBlank()) return
        mutateProject { it.copy(name = newName.trim()) }
    }

    // --- UI Controls ---

    fun selectObject(objectId: String?) {
        val state = _uiState.value
        if (state.isPlaying && objectId != null) {
            state.activeScene?.let { scene ->
                behaviorEngine.fireTap(objectId, scene.behaviors)
            }
            return
        }
        _uiState.update { it.copy(selectedObjectId = objectId) }
    }

    fun setTransformTool(tool: TransformTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun toggleSnap() {
        _uiState.update { it.copy(snapEnabled = !it.snapEnabled) }
        showBanner(if (_uiState.value.snapEnabled) "Привязка к сетке: ВКЛ" else "Привязка к сетке: ВЫКЛ")
    }

    fun toggleViewMode() {
        _uiState.update {
            val next = if (it.viewMode == ViewMode.EDITOR_3D) ViewMode.AR_LIVE else ViewMode.EDITOR_3D
            it.copy(
                viewMode = next,
                activeSheet = InspectorSheet.NONE,
                arPlaced = false
            )
        }
    }

    fun setArPlaced(placed: Boolean) {
        _uiState.update { it.copy(arPlaced = placed) }
    }

    fun toggleSheet(sheet: InspectorSheet) {
        _uiState.update {
            it.copy(activeSheet = if (it.activeSheet == sheet) InspectorSheet.NONE else sheet)
        }
    }

    fun closeSheet() {
        _uiState.update { it.copy(activeSheet = InspectorSheet.NONE) }
    }

    fun updateCameraPosition(pos: Vec3) {
        _uiState.update { it.copy(cameraPosition = pos) }
    }

    // --- Undo / Redo ---

    fun undo() {
        if (undoStack.isEmpty() || _uiState.value.isPlaying) return
        val current = _uiState.value.currentProject ?: return
        redoStack.addLast(current)
        val prev = undoStack.removeLast()
        applyProjectState(prev, save = true)
    }

    fun redo() {
        if (redoStack.isEmpty() || _uiState.value.isPlaying) return
        val current = _uiState.value.currentProject ?: return
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        applyProjectState(next, save = true)
    }

    // --- Object Operations ---

    fun addPrimitive(type: ObjectType) {
        val count = (_uiState.value.activeScene?.objects?.count { it.type == type } ?: 0) + 1
        val defaultScale = when (type) {
            ObjectType.CUBE -> Vec3(0.25f, 0.25f, 0.25f)
            ObjectType.SPHERE -> Vec3(0.25f, 0.25f, 0.25f)
            ObjectType.CYLINDER -> Vec3(0.22f, 0.35f, 0.22f)
            ObjectType.CONE -> Vec3(0.26f, 0.36f, 0.26f)
            ObjectType.CAPSULE -> Vec3(0.2f, 0.42f, 0.2f)
            ObjectType.PLANE -> Vec3(0.6f, 0.02f, 0.6f)
            ObjectType.TEXT_3D -> Vec3(0.75f, 0.75f, 0.75f)
            ObjectType.DRAWING_3D -> Vec3(0.8f, 0.8f, 0.8f)
            ObjectType.SCANNED_3D -> Vec3(1.0f, 1.0f, 1.0f)
            ObjectType.GLB_MODEL -> Vec3(0.35f, 0.35f, 0.35f)
        }
        val offsetX = ((_uiState.value.activeScene?.objects?.size ?: 0) % 4 - 1.5f) * 0.18f
        val palette = listOf(0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFFF453A, 0xFFBF5AF2, 0xFF64D2FF)
        val color = palette[(_uiState.value.activeScene?.objects?.size ?: 0) % palette.size]

        val newObj = SceneObject(
            name = "${type.displayNameRu} $count",
            type = type,
            position = Vec3(offsetX, defaultScale.y * 0.5f, 0f),
            scale = defaultScale,
            material = MaterialProperties(colorHex = color)
        )
        mutateActiveScene { scene ->
            scene.copy(objects = scene.objects + newObj)
        }
        _uiState.update { it.copy(selectedObjectId = newObj.id, activeSheet = InspectorSheet.NONE) }
        showBanner("Добавлен объект: ${newObj.name}")
    }

    fun addStarterPrefab(prefabKey: String) {
        val newObjects = when (prefabKey) {
            "billboard" -> listOf(
                SceneObject(
                    name = "Экран билборда",
                    type = ObjectType.CUBE,
                    position = Vec3(0f, 0.56f, 0f),
                    scale = Vec3(0.85f, 0.45f, 0.06f),
                    labelText = "NUR ADVERTISING",
                    labelColorHex = 0xFFFFD60A,
                    material = MaterialProperties(colorHex = 0xFF1C1C1E, preset = MaterialPreset.GLOSSY, roughness = 0.2f)
                ),
                SceneObject(
                    name = "Стойка билборда",
                    type = ObjectType.CYLINDER,
                    position = Vec3(0f, 0.17f, 0f),
                    scale = Vec3(0.08f, 0.34f, 0.08f),
                    material = MaterialProperties(colorHex = 0xFF48484A, preset = MaterialPreset.METAL, metallic = 0.9f, roughness = 0.25f)
                )
            )
            "branded_cube" -> listOf(
                SceneObject(
                    name = "Куб с текстом",
                    type = ObjectType.CUBE,
                    position = Vec3(0f, 0.2f, 0f),
                    scale = Vec3(0.4f, 0.4f, 0.4f),
                    labelText = "NUR",
                    labelColorHex = 0xFFFFD60A,
                    material = MaterialProperties(colorHex = 0xFF0A84FF, preset = MaterialPreset.GLOSSY, roughness = 0.2f)
                )
            )
            "arch" -> listOf(
                SceneObject(
                    name = "Арка (Левая колонна)",
                    type = ObjectType.CUBE,
                    position = Vec3(-0.28f, 0.25f, 0f),
                    scale = Vec3(0.12f, 0.5f, 0.12f),
                    material = MaterialProperties(colorHex = 0xFFE5E5EA, preset = MaterialPreset.MATTE, roughness = 0.8f)
                ),
                SceneObject(
                    name = "Арка (Правая колонна)",
                    type = ObjectType.CUBE,
                    position = Vec3(0.28f, 0.25f, 0f),
                    scale = Vec3(0.12f, 0.5f, 0.12f),
                    material = MaterialProperties(colorHex = 0xFFE5E5EA, preset = MaterialPreset.MATTE, roughness = 0.8f)
                ),
                SceneObject(
                    name = "Арка (Балка)",
                    type = ObjectType.CUBE,
                    position = Vec3(0f, 0.54f, 0f),
                    scale = Vec3(0.72f, 0.1f, 0.14f),
                    material = MaterialProperties(colorHex = 0xFF0A84FF, preset = MaterialPreset.GLOSSY, roughness = 0.2f)
                )
            )
            "table" -> listOf(
                SceneObject(
                    name = "Столешница",
                    type = ObjectType.CUBE,
                    position = Vec3(0f, 0.36f, 0f),
                    scale = Vec3(0.7f, 0.05f, 0.45f),
                    material = MaterialProperties(colorHex = 0xFFAC7F5E, preset = MaterialPreset.MATTE, roughness = 0.7f),
                    physics = PhysicsProperties(enabled = true, motionType = PhysicsMotionType.FIXED, material = PhysicsMaterial.WOOD)
                ),
                SceneObject(
                    name = "Опора стола",
                    type = ObjectType.CYLINDER,
                    position = Vec3(0f, 0.17f, 0f),
                    scale = Vec3(0.14f, 0.34f, 0.14f),
                    material = MaterialProperties(colorHex = 0xFF3A3A3C, preset = MaterialPreset.METAL, metallic = 0.9f, roughness = 0.25f)
                )
            )
            else -> listOf(
                SceneObject(
                    name = "Башня маяка",
                    type = ObjectType.CYLINDER,
                    position = Vec3(0f, 0.28f, 0f),
                    scale = Vec3(0.2f, 0.56f, 0.2f),
                    material = MaterialProperties(colorHex = 0xFFFF453A, preset = MaterialPreset.GLOSSY, roughness = 0.25f)
                ),
                SceneObject(
                    name = "Купол маяка",
                    type = ObjectType.SPHERE,
                    position = Vec3(0f, 0.6f, 0f),
                    scale = Vec3(0.22f, 0.22f, 0.22f),
                    material = MaterialProperties(colorHex = 0xFFFFD60A, preset = MaterialPreset.METAL, metallic = 0.8f, roughness = 0.15f)
                )
            )
        }
        mutateActiveScene { scene ->
            scene.copy(objects = scene.objects + newObjects)
        }
        _uiState.update { it.copy(selectedObjectId = newObjects.first().id, activeSheet = InspectorSheet.NONE) }
        showBanner("Добавлен набор объектов")
    }

    fun importGlbModel(uri: Uri) {
        viewModelScope.launch {
            val imported = repository.importGlbFromUri(uri)
            if (imported == null) {
                showBanner("Не удалось импортировать 3D-файл")
                return@launch
            }
            val (name, path) = imported
            val obj = SceneObject(
                name = name,
                type = ObjectType.GLB_MODEL,
                modelPath = path,
                position = Vec3(0f, 0.15f, 0f),
                scale = Vec3(0.4f, 0.4f, 0.4f)
            )
            mutateActiveScene { scene ->
                scene.copy(objects = scene.objects + obj)
            }
            _uiState.update { it.copy(selectedObjectId = obj.id, activeSheet = InspectorSheet.NONE) }
            showBanner("Модель импортирована: $name")
        }
    }

    fun updateSelectedObject(recordUndo: Boolean = true, transform: (SceneObject) -> SceneObject) {
        val id = _uiState.value.selectedObjectId ?: return
        if (recordUndo) {
            mutateActiveScene { scene ->
                scene.copy(objects = scene.objects.map { if (it.id == id) transform(it) else it })
            }
        } else {
            mutateActiveSceneSilent { scene ->
                scene.copy(objects = scene.objects.map { if (it.id == id) transform(it) else it })
            }
        }
    }

    fun nudgeSelectedObject(axis: Char, delta: Float) {
        val state = _uiState.value
        val obj = state.selectedObject ?: return
        if (obj.locked) {
            showBanner("Объект заблокирован")
            return
        }
        val snap = state.snapEnabled
        when (state.activeTool) {
            TransformTool.MOVE, TransformTool.SELECT -> {
                val step = if (snap) (if (delta > 0) 0.05f else -0.05f) else delta * 0.05f
                val p = obj.position
                val newPos = when (axis) {
                    'X' -> p.copy(x = snapVal(p.x + step, snap, 0.05f))
                    'Y' -> p.copy(y = snapVal(p.y + step, snap, 0.05f).coerceAtLeast(0f))
                    else -> p.copy(z = snapVal(p.z + step, snap, 0.05f))
                }
                updateSelectedObject { it.copy(position = newPos) }
            }
            TransformTool.ROTATE -> {
                val step = if (snap) (if (delta > 0) 15f else -15f) else delta * 10f
                val r = obj.rotation
                val newRot = when (axis) {
                    'X' -> r.copy(x = (r.x + step) % 360f)
                    'Y' -> r.copy(y = (r.y + step) % 360f)
                    else -> r.copy(z = (r.z + step) % 360f)
                }
                updateSelectedObject { it.copy(rotation = newRot) }
            }
            TransformTool.SCALE -> {
                val step = if (snap) (if (delta > 0) 0.05f else -0.05f) else delta * 0.04f
                val s = obj.scale
                val newScale = when (axis) {
                    'X' -> s.copy(x = (s.x + step).coerceIn(0.03f, 3.0f))
                    'Y' -> s.copy(y = (s.y + step).coerceIn(0.03f, 3.0f))
                    else -> s.copy(z = (s.z + step).coerceIn(0.03f, 3.0f))
                }
                updateSelectedObject { it.copy(scale = newScale) }
            }
        }
    }

    fun duplicateSelectedObject() {
        val obj = _uiState.value.selectedObject ?: return
        val copy = obj.copy(
            id = UUID.randomUUID().toString(),
            name = "${obj.name} (копия)",
            position = obj.position + Vec3(0.2f, 0f, 0.2f)
        )
        mutateActiveScene { scene -> scene.copy(objects = scene.objects + copy) }
        _uiState.update { it.copy(selectedObjectId = copy.id) }
        showBanner("Объект дублирован")
    }

    fun deleteSelectedObject() {
        val id = _uiState.value.selectedObjectId ?: return
        mutateActiveScene { scene ->
            scene.copy(
                objects = scene.objects.filterNot { it.id == id },
                behaviors = scene.behaviors.map { b ->
                    b.copy(
                        triggerTargetObjectId = if (b.triggerTargetObjectId == id) null else b.triggerTargetObjectId,
                        actions = b.actions.map { a ->
                            if (a.targetObjectId == id) a.copy(targetObjectId = null) else a
                        }
                    )
                }
            )
        }
        _uiState.update { it.copy(selectedObjectId = null) }
        showBanner("Объект удален")
    }

    // --- Behaviors ---

    fun addBehaviorTemplate(template: String) {
        val targetId = _uiState.value.selectedObjectId
            ?: _uiState.value.activeScene?.objects?.firstOrNull()?.id

        val behavior = when (template) {
            "tap_bounce" -> Behavior(
                name = "Нажатие и прыжок",
                triggerType = TriggerType.TAP,
                triggerTargetObjectId = targetId,
                actions = listOf(
                    BehaviorAction(type = ActionType.EMPHASIZE, targetObjectId = targetId, durationSec = 0.7f, emphasizeStyle = EmphasizeStyle.BOUNCE)
                )
            )
            "start_spin" -> Behavior(
                name = "Старт и вращение",
                triggerType = TriggerType.SCENE_START,
                triggerTargetObjectId = targetId,
                actions = listOf(
                    BehaviorAction(type = ActionType.ROTATE_BY, targetObjectId = targetId, durationSec = 2.0f, deltaVec = Vec3(0f, 360f, 0f))
                )
            )
            "proximity_pop" -> Behavior(
                name = "Близость и акцент",
                triggerType = TriggerType.PROXIMITY,
                triggerTargetObjectId = targetId,
                proximityDistance = 1.4f,
                actions = listOf(
                    BehaviorAction(type = ActionType.EMPHASIZE, targetObjectId = targetId, durationSec = 0.6f, emphasizeStyle = EmphasizeStyle.POP),
                    BehaviorAction(type = ActionType.PLAY_SOUND, targetObjectId = targetId, durationSec = 0.2f)
                )
            )
            "collide_jiggle" -> Behavior(
                name = "Столкновение и тряска",
                triggerType = TriggerType.COLLIDE,
                triggerTargetObjectId = targetId,
                actions = listOf(
                    BehaviorAction(type = ActionType.EMPHASIZE, targetObjectId = targetId, durationSec = 0.5f, emphasizeStyle = EmphasizeStyle.JIGGLE),
                    BehaviorAction(type = ActionType.PLAY_SOUND, targetObjectId = targetId, durationSec = 0.15f)
                )
            )
            else -> Behavior(
                name = "Свое поведение",
                triggerType = TriggerType.TAP,
                triggerTargetObjectId = targetId,
                actions = listOf(
                    BehaviorAction(type = ActionType.MOVE_BY, targetObjectId = targetId, durationSec = 1.0f, deltaVec = Vec3(0f, 0.3f, 0f))
                )
            )
        }
        mutateActiveScene { scene -> scene.copy(behaviors = scene.behaviors + behavior) }
        showBanner("Добавлено поведение: ${behavior.name}")
    }

    fun updateBehavior(behaviorId: String, transform: (Behavior) -> Behavior) {
        mutateActiveScene { scene ->
            scene.copy(behaviors = scene.behaviors.map { if (it.id == behaviorId) transform(it) else it })
        }
    }

    fun deleteBehavior(behaviorId: String) {
        mutateActiveScene { scene ->
            scene.copy(behaviors = scene.behaviors.filterNot { it.id == behaviorId })
        }
    }

    fun addActionToBehavior(behaviorId: String, type: ActionType) {
        val targetId = _uiState.value.selectedObjectId
            ?: _uiState.value.activeScene?.objects?.firstOrNull()?.id
        val newAction = BehaviorAction(
            type = type,
            targetObjectId = targetId,
            durationSec = if (type == ActionType.PLAY_SOUND) 0.2f else 1.0f,
            deltaVec = when (type) {
                ActionType.MOVE_BY -> Vec3(0f, 0.25f, 0f)
                ActionType.ROTATE_BY -> Vec3(0f, 90f, 0f)
                ActionType.SCALE_TO -> Vec3(0.4f, 0.4f, 0.4f)
                else -> Vec3.ZERO
            }
        )
        updateBehavior(behaviorId) { it.copy(actions = it.actions + newAction) }
    }

    fun deleteActionFromBehavior(behaviorId: String, actionId: String) {
        updateBehavior(behaviorId) { it.copy(actions = it.actions.filterNot { a -> a.id == actionId }) }
    }

    fun testBehavior(behavior: Behavior) {
        behaviorEngine.runBehavior(behavior)
    }

    // --- Scenes ---

    fun switchScene(sceneId: String) {
        val proj = _uiState.value.currentProject ?: return
        if (proj.scenes.none { it.id == sceneId }) return
        mutateProject { it.copy(activeSceneId = sceneId) }
        val newScene = _uiState.value.activeScene
        _uiState.update { it.copy(selectedObjectId = newScene?.objects?.firstOrNull()?.id) }
    }

    fun addScene(name: String, anchorType: AnchorType) {
        val newScene = RealityScene(
            name = name.ifBlank { "Сцена ${(_uiState.value.currentProject?.scenes?.size ?: 0) + 1}" },
            anchorType = anchorType,
            objects = listOf(
                SceneObject(
                    name = "Куб",
                    type = ObjectType.CUBE,
                    position = Vec3(0f, 0.15f, 0f),
                    scale = Vec3(0.26f, 0.26f, 0.26f)
                )
            )
        )
        mutateProject { proj ->
            proj.copy(scenes = proj.scenes + newScene, activeSceneId = newScene.id)
        }
        _uiState.update { it.copy(selectedObjectId = newScene.objects.first().id) }
    }

    fun deleteScene(sceneId: String) {
        val proj = _uiState.value.currentProject ?: return
        if (proj.scenes.size <= 1) {
            showBanner("В проекте должна остаться хотя бы одна сцена")
            return
        }
        val remaining = proj.scenes.filterNot { it.id == sceneId }
        val nextActive = if (proj.activeSceneId == sceneId) remaining.first().id else proj.activeSceneId
        mutateProject { it.copy(scenes = remaining, activeSceneId = nextActive) }
    }

    fun updateSceneSettings(gridVisible: Boolean? = null, gravity: Float? = null, anchorType: AnchorType? = null) {
        mutateActiveScene { scene ->
            scene.copy(
                gridVisible = gridVisible ?: scene.gridVisible,
                physicsGravity = gravity ?: scene.physicsGravity,
                anchorType = anchorType ?: scene.anchorType
            )
        }
    }

    // --- Play Mode ---

    fun togglePlayMode() {
        if (_uiState.value.isPlaying) {
            stopPlayModeIfRunning()
        } else {
            startPlayMode()
        }
    }

    private fun startPlayMode() {
        val current = _uiState.value.currentProject ?: return
        prePlayProjectSnapshot = current
        physicsEngine.reset()
        behaviorEngine.stopAll()

        _uiState.update {
            it.copy(
                isPlaying = true,
                activeSheet = InspectorSheet.NONE,
                selectedObjectId = null
            )
        }
        showBanner("Режим «Играть» запущен — касайтесь объектов")

        // Trigger SCENE_START behaviors
        current.activeScene.let { scene ->
            behaviorEngine.fireSceneStart(scene.behaviors)
        }

        // Start 60fps simulation loop
        playLoopJob?.cancel()
        playLoopJob = viewModelScope.launch {
            var lastTime = System.nanoTime()
            while (isActive && _uiState.value.isPlaying) {
                val now = System.nanoTime()
                val dt = ((now - lastTime) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
                lastTime = now

                val state = _uiState.value
                val scene = state.activeScene
                if (scene != null) {
                    val result = physicsEngine.step(scene.objects, scene.physicsGravity, dt)
                    if (result.updatedObjects != scene.objects) {
                        mutateActiveSceneSilent { it.copy(objects = result.updatedObjects) }
                    }
                    if (result.collisions.isNotEmpty()) {
                        behaviorEngine.fireCollisions(result.collisions, scene.behaviors)
                    }
                    behaviorEngine.checkProximity(state.cameraPosition, result.updatedObjects, scene.behaviors)
                }
                delay(16L)
            }
        }
    }

    private fun stopPlayModeIfRunning() {
        if (!_uiState.value.isPlaying) return
        playLoopJob?.cancel()
        playLoopJob = null
        behaviorEngine.stopAll()
        physicsEngine.reset()

        val snapshot = prePlayProjectSnapshot
        prePlayProjectSnapshot = null
        _uiState.update {
            it.copy(
                isPlaying = false,
                currentProject = snapshot ?: it.currentProject
            )
        }
        showBanner("Режим «Играть» остановлен — сцена восстановлена")
    }

    // --- Export ---

    fun exportCurrentProject(onExported: (File) -> Unit) {
        val proj = _uiState.value.currentProject ?: return
        viewModelScope.launch {
            val file = repository.exportProjectToFile(proj)
            showBanner("Экспортировано: ${file.name}")
            onExported(file)
        }
    }

    // --- Helpers ---

    private fun mutateProject(transform: (RealityProject) -> RealityProject) {
        val current = _uiState.value.currentProject ?: return
        if (!_uiState.value.isPlaying) {
            undoStack.addLast(current)
            if (undoStack.size > 40) undoStack.removeFirst()
            redoStack.clear()
        }
        val updated = transform(current)
        applyProjectState(updated, save = !_uiState.value.isPlaying)
    }

    private fun mutateActiveScene(transform: (RealityScene) -> RealityScene) {
        mutateProject { proj ->
            val updatedScenes = proj.scenes.map {
                if (it.id == proj.activeSceneId) transform(it) else it
            }
            proj.copy(scenes = updatedScenes)
        }
    }

    private fun mutateActiveSceneSilent(transform: (RealityScene) -> RealityScene) {
        val current = _uiState.value.currentProject ?: return
        val updatedScenes = current.scenes.map {
            if (it.id == current.activeSceneId) transform(it) else it
        }
        val updated = current.copy(scenes = updatedScenes)
        _uiState.update { it.copy(currentProject = updated) }
    }

    private fun applyProjectState(project: RealityProject, save: Boolean) {
        _uiState.update {
            it.copy(
                currentProject = project,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        if (save) {
            viewModelScope.launch { repository.saveProject(project) }
        }
    }

    private fun snapVal(value: Float, enabled: Boolean, step: Float): Float {
        if (!enabled) return value
        return round(value / step) * step
    }

    fun showBanner(message: String) {
        bannerJob?.cancel()
        _uiState.update { it.copy(statusBanner = message) }
        bannerJob = viewModelScope.launch {
            delay(2600L)
            _uiState.update { it.copy(statusBanner = null) }
        }
    }
}
