package com.realitycomposer.android.engine

import android.media.AudioManager
import android.media.ToneGenerator
import com.realitycomposer.android.model.*
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class BehaviorEngine(
    private val scope: CoroutineScope,
    private val getObject: (String) -> SceneObject?,
    private val updateObject: (String, (SceneObject) -> SceneObject) -> Unit,
    private val onSwitchScene: (String) -> Unit,
    private val onStatusMessage: (String) -> Unit
) {
    private val activeJobs = mutableListOf<Job>()
    private val triggeredProximity = mutableSetOf<String>()
    private val recentCollisions = mutableMapOf<String, Long>()

    fun stopAll() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        triggeredProximity.clear()
        recentCollisions.clear()
    }

    fun fireSceneStart(behaviors: List<Behavior>) {
        behaviors
            .filter { it.enabled && it.triggerType == TriggerType.SCENE_START }
            .forEach { runBehavior(it) }
    }

    fun fireTap(objectId: String, behaviors: List<Behavior>) {
        behaviors
            .filter {
                it.enabled &&
                    it.triggerType == TriggerType.TAP &&
                    (it.triggerTargetObjectId == null || it.triggerTargetObjectId == objectId)
            }
            .forEach { runBehavior(it) }
    }

    fun checkProximity(cameraPos: Vec3, objects: List<SceneObject>, behaviors: List<Behavior>) {
        behaviors
            .filter { it.enabled && it.triggerType == TriggerType.PROXIMITY }
            .forEach { behavior ->
                val targetId = behavior.triggerTargetObjectId ?: return@forEach
                val obj = objects.firstOrNull { it.id == targetId } ?: return@forEach
                val dist = cameraPos.distanceTo(obj.position)
                if (dist <= behavior.proximityDistance && targetId !in triggeredProximity) {
                    triggeredProximity.add(targetId)
                    runBehavior(behavior)
                } else if (dist > behavior.proximityDistance * 1.25f) {
                    triggeredProximity.remove(targetId)
                }
            }
    }

    fun fireCollisions(collisions: List<Pair<String, String>>, behaviors: List<Behavior>) {
        if (collisions.isEmpty()) return
        val now = System.currentTimeMillis()
        behaviors
            .filter { it.enabled && it.triggerType == TriggerType.COLLIDE }
            .forEach { behavior ->
                val targetId = behavior.triggerTargetObjectId
                val matched = collisions.any { (a, b) ->
                    targetId == null || a == targetId || b == targetId
                }
                if (matched) {
                    val lastTime = recentCollisions[behavior.id] ?: 0L
                    if (now - lastTime > 600L) {
                        recentCollisions[behavior.id] = now
                        runBehavior(behavior)
                    }
                }
            }
    }

    fun runBehavior(behavior: Behavior) {
        val job = scope.launch {
            onStatusMessage("Поведение: ${behavior.name}")
            for (action in behavior.actions) {
                executeAction(action, behavior.triggerTargetObjectId)
            }
        }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
    }

    private suspend fun executeAction(action: BehaviorAction, fallbackTargetId: String?) {
        val targetId = action.targetObjectId ?: fallbackTargetId
        val durationMs = (action.durationSec.coerceAtLeast(0.05f) * 1000f).toLong()

        when (action.type) {
            ActionType.WAIT -> {
                delay(durationMs)
            }

            ActionType.HIDE -> {
                if (targetId != null) {
                    updateObject(targetId) { it.copy(visible = false) }
                }
            }

            ActionType.SHOW -> {
                if (targetId != null) {
                    updateObject(targetId) { it.copy(visible = true) }
                }
            }

            ActionType.PLAY_SOUND -> {
                runCatching {
                    val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs.toInt().coerceIn(80, 600))
                    delay(durationMs.coerceAtMost(600L))
                    tone.release()
                }
            }

            ActionType.CHANGE_SCENE -> {
                action.targetSceneId?.let { onSwitchScene(it) }
            }

            ActionType.MOVE_BY -> {
                if (targetId == null) return
                val startObj = getObject(targetId) ?: return
                val startPos = startObj.position
                animate(durationMs) { t ->
                    val eased = easeInOutCubic(t)
                    updateObject(targetId) { obj ->
                        obj.copy(position = startPos + action.deltaVec * eased)
                    }
                }
            }

            ActionType.ROTATE_BY -> {
                if (targetId == null) return
                val startObj = getObject(targetId) ?: return
                val startRot = startObj.rotation
                animate(durationMs) { t ->
                    val eased = easeInOutCubic(t)
                    updateObject(targetId) { obj ->
                        obj.copy(rotation = startRot + action.deltaVec * eased)
                    }
                }
            }

            ActionType.SCALE_TO -> {
                if (targetId == null) return
                val startObj = getObject(targetId) ?: return
                val startScale = startObj.scale
                val targetScale = Vec3(
                    action.deltaVec.x.coerceAtLeast(0.05f),
                    action.deltaVec.y.coerceAtLeast(0.05f),
                    action.deltaVec.z.coerceAtLeast(0.05f)
                )
                animate(durationMs) { t ->
                    val eased = easeInOutCubic(t)
                    updateObject(targetId) { obj ->
                        obj.copy(
                            scale = Vec3(
                                lerp(startScale.x, targetScale.x, eased),
                                lerp(startScale.y, targetScale.y, eased),
                                lerp(startScale.z, targetScale.z, eased)
                            )
                        )
                    }
                }
            }

            ActionType.ORBIT -> {
                if (targetId == null) return
                val startObj = getObject(targetId) ?: return
                val startPos = startObj.position
                val radius = kotlin.math.sqrt(startPos.x * startPos.x + startPos.z * startPos.z).coerceAtLeast(0.25f)
                val startAngle = kotlin.math.atan2(startPos.z, startPos.x)
                animate(durationMs) { t ->
                    val angle = startAngle + (2.0 * PI * t).toFloat()
                    updateObject(targetId) { obj ->
                        obj.copy(
                            position = obj.position.copy(
                                x = cos(angle) * radius,
                                z = sin(angle) * radius
                            )
                        )
                    }
                }
            }

            ActionType.EMPHASIZE -> {
                if (targetId == null) return
                val startObj = getObject(targetId) ?: return
                val basePos = startObj.position
                val baseRot = startObj.rotation
                val baseScale = startObj.scale

                when (action.emphasizeStyle) {
                    EmphasizeStyle.BOUNCE -> {
                        animate(durationMs) { t ->
                            val jump = sin(t * PI).toFloat() * 0.35f
                            updateObject(targetId) { it.copy(position = basePos.copy(y = basePos.y + jump)) }
                        }
                    }
                    EmphasizeStyle.POP -> {
                        animate(durationMs) { t ->
                            val pulse = 1f + sin(t * PI).toFloat() * 0.45f
                            updateObject(targetId) { it.copy(scale = baseScale * pulse) }
                        }
                    }
                    EmphasizeStyle.SPIN -> {
                        animate(durationMs) { t ->
                            val eased = easeInOutCubic(t)
                            updateObject(targetId) { it.copy(rotation = baseRot.copy(y = baseRot.y + 360f * eased)) }
                        }
                    }
                    EmphasizeStyle.JIGGLE -> {
                        animate(durationMs) { t ->
                            val shake = sin(t * PI * 6.0).toFloat() * 14f * (1f - t)
                            updateObject(targetId) { it.copy(rotation = baseRot.copy(z = baseRot.z + shake)) }
                        }
                    }
                    EmphasizeStyle.BLINK -> {
                        val blinks = 4
                        val stepMs = (durationMs / (blinks * 2)).coerceAtLeast(50L)
                        repeat(blinks) {
                            updateObject(targetId) { it.copy(visible = false) }
                            delay(stepMs)
                            updateObject(targetId) { it.copy(visible = true) }
                            delay(stepMs)
                        }
                    }
                }
            }
        }
    }

    private suspend fun animate(durationMs: Long, onProgress: (Float) -> Unit) {
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val t = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            onProgress(t)
            if (t >= 1f) break
            delay(16L)
        }
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
