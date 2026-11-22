package com.realitycomposer.android.ui.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class ArObjectKind(val titleRu: String) {
    TEXT_3D("3D-Текст"),
    CUBE("Куб"),
    SPHERE("Сфера"),
    CYLINDER("Цилиндр"),
    CONE("Конус"),
    GLB("3D-Модель (.glb)")
}

data class PlacedArItem(
    val id: String,
    val kind: ArObjectKind,
    val text: String = "Aa",
    val colorHex: Long = 0xFFB8B8BE,
    val scale: Float = 1.0f,
    val rotationY: Float = 0f,
    val metallic: Float = 0.25f,
    val roughness: Float = 0.42f,
    val modelPath: String? = null
)

object Extruded3DTextBuilder {

    /**
     * Builds a real 3D extruded solid object standing on y = 0 (table surface),
     * matching Apple Reality Composer's 3D extruded "Aa" text and primitives.
     */
    fun buildArObjectNode(
        engine: Engine,
        materialLoader: MaterialLoader,
        modelLoader: ModelLoader,
        item: PlacedArItem,
        showSelectionRing: Boolean
    ): Node {
        val container = Node(engine).apply {
            name = item.id
        }

        val mat = materialLoader.createColorInstance(
            color = Color(item.colorHex),
            metallic = item.metallic,
            roughness = item.roughness,
            reflectance = 0.55f
        )

        when (item.kind) {
            ArObjectKind.TEXT_3D -> {
                val textNode = buildExtrudedTextNode(
                    engine = engine,
                    materialLoader = materialLoader,
                    text = item.text.ifBlank { "Aa" },
                    colorHex = item.colorHex,
                    metallic = item.metallic,
                    roughness = item.roughness
                )
                container.addChildNode(textNode)
            }

            ArObjectKind.CUBE -> {
                val size = 0.14f
                container.addChildNode(
                    CubeNode(engine, Size(size, size, size), materialInstance = mat).apply {
                        position = Position(0f, size / 2f, 0f)
                    }
                )
                if (item.text.isNotBlank()) {
                    val label = buildExtrudedTextNode(
                        engine = engine,
                        materialLoader = materialLoader,
                        text = item.text,
                        colorHex = 0xFFFFD60A,
                        metallic = 0.2f,
                        roughness = 0.3f,
                        targetHeightMeters = 0.055f,
                        extrudeDepthMeters = 0.01f
                    ).apply {
                        position = Position(0f, size + 0.01f, 0f)
                    }
                    container.addChildNode(label)
                }
            }

            ArObjectKind.SPHERE -> {
                val r = 0.08f
                container.addChildNode(
                    SphereNode(engine, radius = r, materialInstance = mat).apply {
                        position = Position(0f, r, 0f)
                    }
                )
                if (item.text.isNotBlank()) {
                    val label = buildExtrudedTextNode(
                        engine = engine,
                        materialLoader = materialLoader,
                        text = item.text,
                        colorHex = 0xFFFFD60A,
                        metallic = 0.2f,
                        roughness = 0.3f,
                        targetHeightMeters = 0.05f,
                        extrudeDepthMeters = 0.01f
                    ).apply {
                        position = Position(0f, r * 2f + 0.015f, 0f)
                    }
                    container.addChildNode(label)
                }
            }

            ArObjectKind.CYLINDER -> {
                val r = 0.075f
                val h = 0.16f
                container.addChildNode(
                    CylinderNode(engine, radius = r, height = h, materialInstance = mat).apply {
                        position = Position(0f, h / 2f, 0f)
                    }
                )
                if (item.text.isNotBlank()) {
                    val label = buildExtrudedTextNode(
                        engine = engine,
                        materialLoader = materialLoader,
                        text = item.text,
                        colorHex = 0xFFFFD60A,
                        metallic = 0.2f,
                        roughness = 0.3f,
                        targetHeightMeters = 0.05f,
                        extrudeDepthMeters = 0.01f
                    ).apply {
                        position = Position(0f, h + 0.015f, 0f)
                    }
                    container.addChildNode(label)
                }
            }

            ArObjectKind.CONE -> {
                val steps = 10
                val totalH = 0.18f
                val segH = totalH / steps
                for (i in 0 until steps) {
                    val radius = (0.085f * (1f - i.toFloat() / steps)).coerceAtLeast(0.006f)
                    container.addChildNode(
                        CylinderNode(engine, radius = radius, height = segH * 1.05f, materialInstance = mat).apply {
                            position = Position(0f, segH * (i + 0.5f), 0f)
                        }
                    )
                }
            }

            ArObjectKind.GLB -> {
                val file = item.modelPath?.let { File(it) }
                val instance = if (file != null && file.exists()) {
                    runCatching { modelLoader.createModelInstance(file) }.getOrNull()
                } else null
                if (instance != null) {
                    container.addChildNode(
                        ModelNode(modelInstance = instance, scaleToUnits = 0.22f)
                    )
                } else {
                    container.addChildNode(
                        CubeNode(engine, Size(0.14f, 0.14f, 0.14f), materialInstance = mat).apply {
                            position = Position(0f, 0.07f, 0f)
                        }
                    )
                }
            }
        }

        // Subtle ground selection ring on the table (just like Apple Reality Composer in the video!)
        if (showSelectionRing) {
            container.addChildNode(buildGroundRingNode(engine, materialLoader))
        }

        return container
    }

    /**
     * Converts ANY Russian/English string into real 3D solid extruded geometry
     * using greedy horizontal run-length merged PBR bars with real depth & lighting.
     */
    fun buildExtrudedTextNode(
        engine: Engine,
        materialLoader: MaterialLoader,
        text: String,
        colorHex: Long,
        metallic: Float,
        roughness: Float,
        targetHeightMeters: Float = 0.12f,
        extrudeDepthMeters: Float = 0.018f
    ): Node {
        val cleanText = text.trim().take(16).ifBlank { "Aa" }
        val fontSizePx = 38f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = fontSizePx
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val fm = paint.fontMetrics
        val textW = paint.measureText(cleanText).toInt().coerceIn(24, 320)
        val textH = (fm.descent - fm.ascent).toInt().coerceIn(24, 64)

        val bmp = Bitmap.createBitmap(textW + 6, textH + 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawText(cleanText, 3f, -fm.ascent + 2f, paint)

        // Find tight bounding box of non-transparent pixels
        var minX = bmp.width
        var maxX = 0
        var minY = bmp.height
        var maxY = 0
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                if (android.graphics.Color.alpha(bmp.getPixel(x, y)) > 110) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val root = Node(engine)
        if (maxX < minX || maxY < minY) return root

        val mat = materialLoader.createColorInstance(
            color = Color(colorHex),
            metallic = metallic,
            roughness = roughness,
            reflectance = 0.6f
        )

        val contentH = (maxY - minY + 1).toFloat()
        val contentW = (maxX - minX + 1).toFloat()
        val pixelSize = targetHeightMeters / contentH
        val centerX = (minX + maxX) / 2f

        // Greedy horizontal run-length extrusion: each continuous lit span becomes one crisp 3D bar
        for (y in minY..maxY) {
            var x = minX
            while (x <= maxX) {
                if (android.graphics.Color.alpha(bmp.getPixel(x, y)) > 110) {
                    val runStart = x
                    while (x <= maxX && android.graphics.Color.alpha(bmp.getPixel(x, y)) > 110) {
                        x++
                    }
                    val runLen = x - runStart
                    val barW = runLen * pixelSize
                    val barH = pixelSize * 1.08f
                    val posX = ((runStart + runLen / 2f - 0.5f) - centerX) * pixelSize
                    val posY = (maxY - y + 0.5f) * pixelSize

                    root.addChildNode(
                        CubeNode(
                            engine = engine,
                            size = Size(barW, barH, extrudeDepthMeters),
                            materialInstance = mat
                        ).apply {
                            position = Position(posX, posY, 0f)
                        }
                    )
                } else {
                    x++
                }
            }
        }

        bmp.recycle()
        return root
    }

    private fun buildGroundRingNode(engine: Engine, materialLoader: MaterialLoader): Node {
        val ring = Node(engine)
        val ringMat = materialLoader.createColorInstance(
            color = Color(0xFF8E8E93),
            metallic = 0.1f,
            roughness = 0.7f,
            reflectance = 0.2f
        )
        val segs = 28
        val radius = 0.062f
        val segLen = (2.0 * PI * radius / segs).toFloat() * 1.15f
        for (i in 0 until segs) {
            val angle = (i * 2.0 * PI / segs).toFloat()
            ring.addChildNode(
                CubeNode(
                    engine = engine,
                    size = Size(segLen, 0.002f, 0.006f),
                    materialInstance = ringMat
                ).apply {
                    position = Position(cos(angle) * radius, 0.001f, sin(angle) * radius)
                    rotation = io.github.sceneview.math.Rotation(0f, -(angle * 180f / PI.toFloat()) + 90f, 0f)
                }
            )
        }
        return ring
    }
}
