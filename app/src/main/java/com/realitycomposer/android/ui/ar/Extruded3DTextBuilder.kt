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
    TEXT_3D("3D-Вывеска"),
    CUBE("Куб"),
    SPHERE("Сфера"),
    CYLINDER("Цилиндр"),
    CONE("Конус"),
    GLB("3D-Модель (.glb)")
}

data class PlacedArItem(
    val id: String,
    val kind: ArObjectKind,
    val text: String = "NUR",
    val isVertical: Boolean = false,
    val colorHex: Long = 0xFFE5E5EA,      // Front face color
    val sideColorHex: Long = 0xFF232328,  // Contrasting 3D side-wall (бортик) color
    val scale: Float = 1.0f,
    val extrudeDepth: Float = 0.032f,
    val offsetY: Float = 0f,              // Raise up onto building façade!
    val rotationY: Float = 0f,
    val metallic: Float = 0.35f,
    val roughness: Float = 0.32f,
    val modelPath: String? = null
)

object Extruded3DTextBuilder {

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
            reflectance = 0.6f
        )

        when (item.kind) {
            ArObjectKind.TEXT_3D -> {
                val textNode = buildVolumetricSignageNode(
                    engine = engine,
                    materialLoader = materialLoader,
                    text = item.text.ifBlank { "NUR" },
                    isVertical = item.isVertical,
                    faceColorHex = item.colorHex,
                    sideColorHex = item.sideColorHex,
                    metallic = item.metallic,
                    roughness = item.roughness,
                    targetCharHeight = 0.16f,
                    extrudeDepthMeters = item.extrudeDepth
                )
                container.addChildNode(textNode)
            }

            ArObjectKind.CUBE -> {
                val size = 0.18f
                container.addChildNode(
                    CubeNode(engine, Size(size, size, size), materialInstance = mat).apply {
                        position = Position(0f, size / 2f, 0f)
                    }
                )
                if (item.text.isNotBlank()) {
                    val label = buildVolumetricSignageNode(
                        engine = engine,
                        materialLoader = materialLoader,
                        text = item.text,
                        isVertical = false,
                        faceColorHex = 0xFFFFD60A,
                        sideColorHex = 0xFF232328,
                        metallic = 0.3f,
                        roughness = 0.3f,
                        targetCharHeight = 0.06f,
                        extrudeDepthMeters = 0.014f
                    ).apply {
                        position = Position(0f, size + 0.01f, 0f)
                    }
                    container.addChildNode(label)
                }
            }

            ArObjectKind.SPHERE -> {
                val r = 0.1f
                container.addChildNode(
                    SphereNode(engine, radius = r, materialInstance = mat).apply {
                        position = Position(0f, r, 0f)
                    }
                )
            }

            ArObjectKind.CYLINDER -> {
                val r = 0.09f
                val h = 0.2f
                container.addChildNode(
                    CylinderNode(engine, radius = r, height = h, materialInstance = mat).apply {
                        position = Position(0f, h / 2f, 0f)
                    }
                )
            }

            ArObjectKind.CONE -> {
                val steps = 10
                val totalH = 0.22f
                val segH = totalH / steps
                for (i in 0 until steps) {
                    val radius = (0.1f * (1f - i.toFloat() / steps)).coerceAtLeast(0.008f)
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
                        ModelNode(modelInstance = instance, scaleToUnits = 0.35f)
                    )
                } else {
                    container.addChildNode(
                        CubeNode(engine, Size(0.18f, 0.18f, 0.18f), materialInstance = mat).apply {
                            position = Position(0f, 0.09f, 0f)
                        }
                    )
                }
            }
        }

        if (showSelectionRing && item.offsetY <= 0.04f) {
            container.addChildNode(buildGroundRingNode(engine, materialLoader))
        }

        return container
    }

    /**
     * Builds realistic two-tone 3D volumetric outdoor signage letters (объёмные фасадные буквы)
     * supporting BOTH Horizontal ("NUR") and Vertical ("Т\nД\nН") layouts,
     * with contrasting dark side-walls (бортики) and bright front face!
     */
    fun buildVolumetricSignageNode(
        engine: Engine,
        materialLoader: MaterialLoader,
        text: String,
        isVertical: Boolean,
        faceColorHex: Long,
        sideColorHex: Long,
        metallic: Float,
        roughness: Float,
        targetCharHeight: Float = 0.16f,
        extrudeDepthMeters: Float = 0.032f
    ): Node {
        val cleanText = text.trim().take(14).ifBlank { "NUR" }
        val lines = if (isVertical) {
            cleanText.replace("\n", "").map { it.toString() }
        } else {
            cleanText.split("\n").take(3)
        }

        val fontSizePx = if (isVertical) 30f else 34f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = fontSizePx
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val fm = paint.fontMetrics
        val lineH = (fm.descent - fm.ascent).toInt().coerceAtLeast(24)
        val maxLineW = lines.maxOf { paint.measureText(it).toInt() }.coerceIn(24, 280)
        val totalH = (lineH * lines.size + 8).coerceIn(28, 320)

        val bmp = Bitmap.createBitmap(maxLineW + 10, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        lines.forEachIndexed { idx, line ->
            val y = idx * lineH - fm.ascent + 4f
            canvas.drawText(line, bmp.width / 2f, y, paint)
        }

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
        if (maxX < minX || maxY < minY) {
            bmp.recycle()
            return root
        }

        val sideMat = materialLoader.createColorInstance(
            color = Color(sideColorHex),
            metallic = 0.55f,
            roughness = 0.35f,
            reflectance = 0.5f
        )
        val faceMat = materialLoader.createColorInstance(
            color = Color(faceColorHex),
            metallic = metallic,
            roughness = roughness,
            reflectance = 0.7f
        )

        val pixelSize = targetCharHeight / lineH.toFloat()
        val centerX = (minX + maxX) / 2f
        val frontCapDepth = (extrudeDepthMeters * 0.14f).coerceAtLeast(0.004f)

        // Greedy horizontal run-length extrusion with 2-layer volumetric signage look:
        // 1) Deep dark side-wall body (бортик)
        // 2) Bright front face cap (лицевая часть вывески)
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
                    val barH = pixelSize * 1.1f
                    val posX = ((runStart + runLen / 2f - 0.5f) - centerX) * pixelSize
                    val posY = (maxY - y + 0.5f) * pixelSize

                    // Side-wall extruded body
                    root.addChildNode(
                        CubeNode(
                            engine = engine,
                            size = Size(barW, barH, extrudeDepthMeters),
                            materialInstance = sideMat
                        ).apply {
                            position = Position(posX, posY, 0f)
                        }
                    )

                    // Bright front face cap
                    root.addChildNode(
                        CubeNode(
                            engine = engine,
                            size = Size(barW * 0.96f, barH * 0.96f, frontCapDepth),
                            materialInstance = faceMat
                        ).apply {
                            position = Position(posX, posY, extrudeDepthMeters / 2f + frontCapDepth * 0.35f)
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
        val segs = 24
        val radius = 0.07f
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
