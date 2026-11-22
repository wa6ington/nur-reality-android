package com.realitycomposer.android.engine

import com.realitycomposer.android.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PhysicsBodyState(
    val objectId: String,
    var velocity: Vec3 = Vec3.ZERO
)

data class PhysicsStepResult(
    val updatedObjects: List<SceneObject>,
    val collisions: List<Pair<String, String>>
)

class PhysicsEngine {

    private val velocities = mutableMapOf<String, Vec3>()

    fun reset() {
        velocities.clear()
    }

    fun step(
        objects: List<SceneObject>,
        gravity: Float,
        dt: Float
    ): PhysicsStepResult {
        if (dt <= 0f || dt > 0.1f) {
            return PhysicsStepResult(objects, emptyList())
        }

        val mutable = objects.toMutableList()
        val collisions = mutableListOf<Pair<String, String>>()

        // 1. Integrate gravity & velocity for dynamic bodies, resolve floor collision
        for (i in mutable.indices) {
            val obj = mutable[i]
            if (!obj.visible || !obj.physics.enabled || obj.physics.motionType != PhysicsMotionType.DYNAMIC) {
                continue
            }

            var vel = velocities[obj.id] ?: Vec3.ZERO
            vel = Vec3(vel.x, vel.y + gravity * dt, vel.z)

            var pos = obj.position + vel * dt
            val halfHeight = max(0.05f, obj.scale.y * 0.5f)

            // Floor plane at y = 0
            if (pos.y - halfHeight < 0f) {
                pos = pos.copy(y = halfHeight)
                val restitution = obj.physics.material.restitution
                val friction = (1f - obj.physics.material.friction * dt * 4f).coerceIn(0.2f, 0.99f)
                val bounceVy = if (abs(vel.y) > 0.25f) -vel.y * restitution else 0f
                vel = Vec3(vel.x * friction, bounceVy, vel.z * friction)
            }

            velocities[obj.id] = vel
            mutable[i] = obj.copy(position = pos)
        }

        // 2. Pairwise AABB / Sphere collision detection & resolution
        for (i in 0 until mutable.size) {
            val a = mutable[i]
            if (!a.visible || !a.physics.enabled) continue

            for (j in i + 1 until mutable.size) {
                val b = mutable[j]
                if (!b.visible || !b.physics.enabled) continue

                val aDynamic = a.physics.motionType == PhysicsMotionType.DYNAMIC
                val bDynamic = b.physics.motionType == PhysicsMotionType.DYNAMIC
                if (!aDynamic && !bDynamic) continue

                val halfA = Vec3(
                    max(0.04f, a.scale.x * 0.5f),
                    max(0.04f, a.scale.y * 0.5f),
                    max(0.04f, a.scale.z * 0.5f)
                )
                val halfB = Vec3(
                    max(0.04f, b.scale.x * 0.5f),
                    max(0.04f, b.scale.y * 0.5f),
                    max(0.04f, b.scale.z * 0.5f)
                )

                val dx = b.position.x - a.position.x
                val dy = b.position.y - a.position.y
                val dz = b.position.z - a.position.z

                val overlapX = (halfA.x + halfB.x) - abs(dx)
                val overlapY = (halfA.y + halfB.y) - abs(dy)
                val overlapZ = (halfA.z + halfB.z) - abs(dz)

                if (overlapX > 0f && overlapY > 0f && overlapZ > 0f) {
                    collisions.add(a.id to b.id)

                    val velA = velocities[a.id] ?: Vec3.ZERO
                    val velB = velocities[b.id] ?: Vec3.ZERO
                    val restitution = min(a.physics.material.restitution, b.physics.material.restitution)

                    // Resolve along minimum penetration axis
                    if (overlapY <= overlapX && overlapY <= overlapZ) {
                        val sign = if (dy >= 0f) 1f else -1f
                        if (aDynamic && bDynamic) {
                            mutable[i] = a.copy(position = a.position.copy(y = a.position.y - sign * overlapY * 0.5f))
                            mutable[j] = b.copy(position = b.position.copy(y = b.position.y + sign * overlapY * 0.5f))
                            val avgVy = (velA.y + velB.y) * 0.5f
                            velocities[a.id] = velA.copy(y = avgVy - sign * abs(velA.y - velB.y) * restitution * 0.5f)
                            velocities[b.id] = velB.copy(y = avgVy + sign * abs(velA.y - velB.y) * restitution * 0.5f)
                        } else if (aDynamic) {
                            mutable[i] = a.copy(position = a.position.copy(y = a.position.y - sign * overlapY))
                            val newVy = if (abs(velA.y) > 0.2f) -velA.y * restitution else 0f
                            velocities[a.id] = velA.copy(y = newVy)
                        } else if (bDynamic) {
                            mutable[j] = b.copy(position = b.position.copy(y = b.position.y + sign * overlapY))
                            val newVy = if (abs(velB.y) > 0.2f) -velB.y * restitution else 0f
                            velocities[b.id] = velB.copy(y = newVy)
                        }
                    } else if (overlapX <= overlapZ) {
                        val sign = if (dx >= 0f) 1f else -1f
                        if (aDynamic && bDynamic) {
                            mutable[i] = a.copy(position = a.position.copy(x = a.position.x - sign * overlapX * 0.5f))
                            mutable[j] = b.copy(position = b.position.copy(x = b.position.x + sign * overlapX * 0.5f))
                            velocities[a.id] = velA.copy(x = -velA.x * restitution)
                            velocities[b.id] = velB.copy(x = -velB.x * restitution)
                        } else if (aDynamic) {
                            mutable[i] = a.copy(position = a.position.copy(x = a.position.x - sign * overlapX))
                            velocities[a.id] = velA.copy(x = -velA.x * restitution)
                        } else if (bDynamic) {
                            mutable[j] = b.copy(position = b.position.copy(x = b.position.x + sign * overlapX))
                            velocities[b.id] = velB.copy(x = -velB.x * restitution)
                        }
                    } else {
                        val sign = if (dz >= 0f) 1f else -1f
                        if (aDynamic && bDynamic) {
                            mutable[i] = a.copy(position = a.position.copy(z = a.position.z - sign * overlapZ * 0.5f))
                            mutable[j] = b.copy(position = b.position.copy(z = b.position.z + sign * overlapZ * 0.5f))
                            velocities[a.id] = velA.copy(z = -velA.z * restitution)
                            velocities[b.id] = velB.copy(z = -velB.z * restitution)
                        } else if (aDynamic) {
                            mutable[i] = a.copy(position = a.position.copy(z = a.position.z - sign * overlapZ))
                            velocities[a.id] = velA.copy(z = -velA.z * restitution)
                        } else if (bDynamic) {
                            mutable[j] = b.copy(position = b.position.copy(z = b.position.z + sign * overlapZ))
                            velocities[b.id] = velB.copy(z = -velB.z * restitution)
                        }
                    }
                }
            }
        }

        return PhysicsStepResult(mutable, collisions)
    }
}
