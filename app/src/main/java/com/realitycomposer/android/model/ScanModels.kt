package com.realitycomposer.android.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ColoredPoint3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val argb: Int
)

@Serializable
data class ScannedObject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "3D-Скан",
    val createdAt: Long = System.currentTimeMillis(),
    val capturedFramesCount: Int = 0,
    val objFilePath: String? = null,
    val points: List<ColoredPoint3D> = emptyList()
)

enum class ScanStep {
    DETECT_OBJECT,
    ADJUST_BOUNDING_BOX,
    SCANNING_360,
    PROCESSING,
    RESULT_VIEWER
}
