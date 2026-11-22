package com.realitycomposer.android.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.realitycomposer.android.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ProjectRepository(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val projectsDir: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    private val assetsDir: File
        get() = File(context.filesDir, "assets").apply { mkdirs() }

    private val exportsDir: File
        get() = File(context.filesDir, "exports").apply { mkdirs() }

    suspend fun loadAllProjects(): List<RealityProject> = withContext(Dispatchers.IO) {
        val files = projectsDir.listFiles { f -> f.extension == "json" }?.toList().orEmpty()
        if (files.isEmpty()) {
            val defaults = createStarterProjects()
            defaults.forEach { saveProjectSync(it) }
            return@withContext defaults.sortedByDescending { it.updatedAt }
        }
        files.mapNotNull { file ->
            runCatching {
                json.decodeFromString<RealityProject>(file.readText())
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    suspend fun saveProject(project: RealityProject) = withContext(Dispatchers.IO) {
        saveProjectSync(project.copy(updatedAt = System.currentTimeMillis()))
    }

    private fun saveProjectSync(project: RealityProject) {
        val file = File(projectsDir, "${project.id}.json")
        file.writeText(json.encodeToString(project))
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        File(projectsDir, "$projectId.json").delete()
    }

    suspend fun duplicateProject(project: RealityProject): RealityProject = withContext(Dispatchers.IO) {
        val copy = project.copy(
            id = UUID.randomUUID().toString(),
            name = "${project.name} (копия)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        saveProjectSync(copy)
        copy
    }

    suspend fun importGlbFromUri(uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri) ?: "model_${System.currentTimeMillis()}.glb"
            val safeName = "${UUID.randomUUID().toString().take(8)}_${displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
            val targetFile = File(assetsDir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching null
            Pair(displayName.substringBeforeLast('.'), targetFile.absolutePath)
        }.getOrNull()
    }

    suspend fun exportProjectToFile(project: RealityProject): File = withContext(Dispatchers.IO) {
        val safeName = project.name.replace(Regex("[^a-zA-Zа-яА-Я0-9._-]"), "_")
        val file = File(exportsDir, "${safeName}.rcscene.json")
        file.writeText(json.encodeToString(project))
        file
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    }

    fun createNewProject(name: String, anchorType: AnchorType): RealityProject {
        val cubeId = UUID.randomUUID().toString()
        val sceneId = UUID.randomUUID().toString()
        val defaultCube = SceneObject(
            id = cubeId,
            name = "Куб",
            type = ObjectType.CUBE,
            position = Vec3(0f, 0.15f, 0f),
            scale = Vec3(0.3f, 0.3f, 0.3f),
            material = MaterialProperties(
                colorHex = 0xFF0A84FF,
                preset = MaterialPreset.GLOSSY,
                metallic = 0.05f,
                roughness = 0.2f,
                reflectance = 0.7f
            )
        )
        val scene = RealityScene(
            id = sceneId,
            name = "Сцена 1",
            anchorType = anchorType,
            objects = listOf(defaultCube),
            behaviors = listOf(
                Behavior(
                    name = "Нажатие и прыжок",
                    triggerType = TriggerType.TAP,
                    triggerTargetObjectId = cubeId,
                    actions = listOf(
                        BehaviorAction(
                            type = ActionType.EMPHASIZE,
                            targetObjectId = cubeId,
                            durationSec = 0.75f,
                            emphasizeStyle = EmphasizeStyle.BOUNCE
                        )
                    )
                )
            )
        )
        return RealityProject(
            name = name.ifBlank { "Новый проект" },
            scenes = listOf(scene),
            activeSceneId = sceneId
        )
    }

    private fun createStarterProjects(): List<RealityProject> {
        val now = System.currentTimeMillis()

        // Project 1: Physics & Behaviors Playground
        val cubeId = UUID.randomUUID().toString()
        val sphereId = UUID.randomUUID().toString()
        val pedestalId = UUID.randomUUID().toString()
        val scene1Id = UUID.randomUUID().toString()
        val scene2Id = UUID.randomUUID().toString()

        val showcaseScene1 = RealityScene(
            id = scene1Id,
            name = "Интерактивная сцена",
            anchorType = AnchorType.HORIZONTAL,
            objects = listOf(
                SceneObject(
                    id = pedestalId,
                    name = "Пьедестал",
                    type = ObjectType.CYLINDER,
                    position = Vec3(0f, 0.05f, 0f),
                    scale = Vec3(0.55f, 0.1f, 0.55f),
                    material = MaterialProperties(
                        colorHex = 0xFF48484A,
                        preset = MaterialPreset.METAL,
                        metallic = 0.85f,
                        roughness = 0.25f,
                        reflectance = 0.8f
                    ),
                    physics = PhysicsProperties(
                        enabled = true,
                        motionType = PhysicsMotionType.FIXED,
                        material = PhysicsMaterial.CONCRETE,
                        collisionShape = CollisionShape.BOX
                    )
                ),
                SceneObject(
                    id = cubeId,
                    name = "Синий куб",
                    type = ObjectType.CUBE,
                    position = Vec3(-0.2f, 0.35f, 0f),
                    rotation = Vec3(0f, 25f, 0f),
                    scale = Vec3(0.24f, 0.24f, 0.24f),
                    material = MaterialProperties(
                        colorHex = 0xFF0A84FF,
                        preset = MaterialPreset.GLOSSY,
                        metallic = 0.05f,
                        roughness = 0.18f,
                        reflectance = 0.7f
                    ),
                    physics = PhysicsProperties(
                        enabled = false,
                        motionType = PhysicsMotionType.DYNAMIC,
                        material = PhysicsMaterial.WOOD
                    )
                ),
                SceneObject(
                    id = sphereId,
                    name = "Мяч (Физика)",
                    type = ObjectType.SPHERE,
                    position = Vec3(0.22f, 0.9f, 0f),
                    scale = Vec3(0.22f, 0.22f, 0.22f),
                    material = MaterialProperties(
                        colorHex = 0xFFFF9F0A,
                        preset = MaterialPreset.RUBBER,
                        metallic = 0.0f,
                        roughness = 0.65f,
                        reflectance = 0.3f
                    ),
                    physics = PhysicsProperties(
                        enabled = true,
                        motionType = PhysicsMotionType.DYNAMIC,
                        material = PhysicsMaterial.RUBBER,
                        collisionShape = CollisionShape.SPHERE
                    )
                )
            ),
            behaviors = listOf(
                Behavior(
                    name = "Клик по кубу -> Оборот",
                    triggerType = TriggerType.TAP,
                    triggerTargetObjectId = cubeId,
                    actions = listOf(
                        BehaviorAction(
                            type = ActionType.EMPHASIZE,
                            targetObjectId = cubeId,
                            durationSec = 0.9f,
                            emphasizeStyle = EmphasizeStyle.SPIN
                        ),
                        BehaviorAction(
                            type = ActionType.PLAY_SOUND,
                            targetObjectId = cubeId,
                            durationSec = 0.2f
                        )
                    )
                ),
                Behavior(
                    name = "Старт -> Вращение куба",
                    triggerType = TriggerType.SCENE_START,
                    triggerTargetObjectId = cubeId,
                    actions = listOf(
                        BehaviorAction(
                            type = ActionType.ROTATE_BY,
                            targetObjectId = cubeId,
                            durationSec = 2.0f,
                            deltaVec = Vec3(0f, 180f, 0f)
                        )
                    )
                )
            )
        )

        val showcaseScene2 = RealityScene(
            id = scene2Id,
            name = "Вторая сцена",
            anchorType = AnchorType.HORIZONTAL,
            objects = listOf(
                SceneObject(
                    name = "Изумрудный цилиндр",
                    type = ObjectType.CYLINDER,
                    position = Vec3(0f, 0.25f, 0f),
                    scale = Vec3(0.28f, 0.5f, 0.28f),
                    material = MaterialProperties(
                        colorHex = 0xFF30D158,
                        preset = MaterialPreset.METAL,
                        metallic = 0.9f,
                        roughness = 0.2f,
                        reflectance = 0.85f
                    )
                )
            )
        )

        val project1 = RealityProject(
            name = "Интерактивная витрина",
            createdAt = now,
            updatedAt = now,
            scenes = listOf(showcaseScene1, showcaseScene2),
            activeSceneId = scene1Id
        )

        // Project 2: Vertical Wall Poster
        val wallSceneId = UUID.randomUUID().toString()
        val plaqueId = UUID.randomUUID().toString()
        val project2 = RealityProject(
            name = "Стенд на стене",
            createdAt = now - 60_000,
            updatedAt = now - 60_000,
            scenes = listOf(
                RealityScene(
                    id = wallSceneId,
                    name = "Стена",
                    anchorType = AnchorType.VERTICAL,
                    objects = listOf(
                        SceneObject(
                            id = plaqueId,
                            name = "Рамка стенда",
                            type = ObjectType.CUBE,
                            position = Vec3(0f, 0.3f, 0f),
                            scale = Vec3(0.6f, 0.4f, 0.04f),
                            material = MaterialProperties(
                                colorHex = 0xFFBF5AF2,
                                preset = MaterialPreset.GLOSSY,
                                metallic = 0.1f,
                                roughness = 0.2f
                            )
                        )
                    ),
                    behaviors = listOf(
                        Behavior(
                            name = "Нажатие -> Всплеск",
                            triggerType = TriggerType.TAP,
                            triggerTargetObjectId = plaqueId,
                            actions = listOf(
                                BehaviorAction(
                                    type = ActionType.EMPHASIZE,
                                    targetObjectId = plaqueId,
                                    durationSec = 0.6f,
                                    emphasizeStyle = EmphasizeStyle.POP
                                )
                            )
                        )
                    )
                )
            ),
            activeSceneId = wallSceneId
        )

        return listOf(project1, project2)
    }
}
