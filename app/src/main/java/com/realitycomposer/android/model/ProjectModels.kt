package com.realitycomposer.android.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Vec3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vec3(x * scalar, y * scalar, z * scalar)

    fun length(): Float = kotlin.math.sqrt(x * x + y * y + z * z)

    fun distanceTo(other: Vec3): Float = (this - other).length()

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val ONE = Vec3(1f, 1f, 1f)
    }
}

@Serializable
enum class AnchorType(val displayNameRu: String, val subtitleRu: String) {
    HORIZONTAL("Горизонтальный", "Стол, пол или любая ровная поверхность"),
    VERTICAL("Вертикальный", "Стена, дверь или вертикальный стенд"),
    IMAGE("Изображение", "Привязка к постеру, карточке или маркеру"),
    FACE("Лицо", "Привязка к лицу пользователя (фронтальная камера)")
}

@Serializable
enum class ObjectType(val displayNameRu: String) {
    CUBE("Куб"),
    SPHERE("Сфера"),
    CYLINDER("Цилиндр"),
    CONE("Конус"),
    CAPSULE("Капсула"),
    PLANE("Плоскость"),
    TEXT_3D("3D-Текст"),
    DRAWING_3D("3D-Рисунок"),
    GLB_MODEL("3D-Модель (GLB)")
}

@Serializable
enum class MaterialPreset(
    val displayNameRu: String,
    val metallic: Float,
    val roughness: Float,
    val reflectance: Float
) {
    MATTE("Матовый", 0.0f, 0.85f, 0.25f),
    GLOSSY("Глянцевый", 0.05f, 0.18f, 0.7f),
    METAL("Металл", 0.92f, 0.22f, 0.85f),
    PLASTIC("Пластик", 0.0f, 0.35f, 0.5f),
    RUBBER("Резина", 0.0f, 0.95f, 0.1f),
    CUSTOM("Свой", 0.1f, 0.5f, 0.5f)
}

@Serializable
data class MaterialProperties(
    val colorHex: Long = 0xFF0A84FF,
    val preset: MaterialPreset = MaterialPreset.PLASTIC,
    val metallic: Float = 0.0f,
    val roughness: Float = 0.35f,
    val reflectance: Float = 0.5f
)

@Serializable
enum class PhysicsMotionType(val displayNameRu: String) {
    DYNAMIC("Динамический"),
    FIXED("Неподвижный")
}

@Serializable
enum class PhysicsMaterial(
    val displayNameRu: String,
    val restitution: Float,
    val friction: Float,
    val density: Float
) {
    CONCRETE("Бетон", 0.15f, 0.8f, 2.4f),
    WOOD("Дерево", 0.38f, 0.55f, 0.7f),
    RUBBER("Резина", 0.82f, 0.9f, 1.1f),
    ICE("Лед", 0.2f, 0.08f, 0.9f),
    LEAD("Свинец", 0.05f, 0.6f, 11.3f)
}

@Serializable
enum class CollisionShape(val displayNameRu: String) {
    BOX("Параллелепипед"),
    SPHERE("Сфера"),
    CAPSULE("Капсула")
}

@Serializable
data class PhysicsProperties(
    val enabled: Boolean = false,
    val motionType: PhysicsMotionType = PhysicsMotionType.DYNAMIC,
    val material: PhysicsMaterial = PhysicsMaterial.CONCRETE,
    val collisionShape: CollisionShape = CollisionShape.BOX,
    val mass: Float = 1.0f
)

@Serializable
data class SceneObject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ObjectType,
    val modelPath: String? = null,
    val imagePath: String? = null,
    val textContent: String = "Привет AR!",
    val position: Vec3 = Vec3(0f, 0.15f, 0f),
    val rotation: Vec3 = Vec3.ZERO,
    val scale: Vec3 = Vec3.ONE,
    val material: MaterialProperties = MaterialProperties(),
    val physics: PhysicsProperties = PhysicsProperties(),
    val visible: Boolean = true,
    val locked: Boolean = false
)

@Serializable
enum class TriggerType(val displayNameRu: String, val descriptionRu: String) {
    TAP("Нажатие", "Срабатывает при касании объекта"),
    SCENE_START("Старт сцены", "Запускается сразу при старте режима «Играть»"),
    PROXIMITY("Близость к камере", "Когда камера приближается к объекту"),
    COLLIDE("Столкновение", "Когда объект сталкивается с другим физическим телом")
}

@Serializable
enum class ActionType(val displayNameRu: String) {
    MOVE_BY("Переместить"),
    ROTATE_BY("Повернуть"),
    SCALE_TO("Изменить масштаб"),
    EMPHASIZE("Акцент"),
    ORBIT("Вращение по орбите"),
    HIDE("Скрыть"),
    SHOW("Показать"),
    WAIT("Пауза"),
    PLAY_SOUND("Звуковой сигнал"),
    CHANGE_SCENE("Сменить сцену")
}

@Serializable
enum class EmphasizeStyle(val displayNameRu: String) {
    POP("Всплеск"),
    BOUNCE("Прыжок"),
    SPIN("Оборот 360°"),
    JIGGLE("Тряска"),
    BLINK("Мигание")
}

@Serializable
data class BehaviorAction(
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType = ActionType.EMPHASIZE,
    val targetObjectId: String? = null,
    val durationSec: Float = 1.0f,
    val deltaVec: Vec3 = Vec3(0f, 0.25f, 0f),
    val emphasizeStyle: EmphasizeStyle = EmphasizeStyle.BOUNCE,
    val targetSceneId: String? = null
)

@Serializable
data class Behavior(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Новое поведение",
    val enabled: Boolean = true,
    val triggerType: TriggerType = TriggerType.TAP,
    val triggerTargetObjectId: String? = null,
    val proximityDistance: Float = 1.2f,
    val actions: List<BehaviorAction> = emptyList()
)

@Serializable
data class RealityScene(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Сцена 1",
    val anchorType: AnchorType = AnchorType.HORIZONTAL,
    val objects: List<SceneObject> = emptyList(),
    val behaviors: List<Behavior> = emptyList(),
    val gridVisible: Boolean = true,
    val physicsGravity: Float = -9.81f
)

@Serializable
data class RealityProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Без названия",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val scenes: List<RealityScene> = emptyList(),
    val activeSceneId: String = ""
) {
    val activeScene: RealityScene
        get() = scenes.firstOrNull { it.id == activeSceneId } ?: scenes.first()
}

enum class TransformTool(val displayNameRu: String) {
    SELECT("Выбор"),
    MOVE("Сдвиг"),
    ROTATE("Поворот"),
    SCALE("Масштаб")
}

enum class ViewMode {
    EDITOR_3D,
    AR_LIVE
}

enum class InspectorSheet {
    NONE,
    ADD_OBJECT,
    PROPERTIES,
    BEHAVIORS,
    SCENES
}
