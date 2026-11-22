package com.realitycomposer.android.ui.viewport

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.realitycomposer.android.model.*
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import java.io.File

object SceneNodeBuilder {

    fun buildObjectNode(
        engine: Engine,
        materialLoader: MaterialLoader,
        modelLoader: ModelLoader,
        obj: SceneObject
    ): Node {
        val mat = materialLoader.createColorInstance(
            color = Color(obj.material.colorHex),
            metallic = obj.material.metallic,
            roughness = obj.material.roughness,
            reflectance = obj.material.reflectance
        )

        val root = Node(engine).apply {
            name = obj.id
            position = Position(obj.position.x, obj.position.y, obj.position.z)
            rotation = Rotation(obj.rotation.x, obj.rotation.y, obj.rotation.z)
            scale = Scale(obj.scale.x, obj.scale.y, obj.scale.z)
            isVisible = obj.visible
        }

        val visualChild: Node = when (obj.type) {
            ObjectType.CUBE -> CubeNode(
                engine = engine,
                size = Size(1f, 1f, 1f),
                materialInstance = mat
            )

            ObjectType.SPHERE -> SphereNode(
                engine = engine,
                radius = 0.5f,
                materialInstance = mat
            )

            ObjectType.CYLINDER -> CylinderNode(
                engine = engine,
                radius = 0.5f,
                height = 1f,
                materialInstance = mat
            )

            ObjectType.CONE -> {
                // Stacked tapered cylinder segments for clean cone look
                Node(engine).apply {
                    val steps = 8
                    for (i in 0 until steps) {
                        val t = i.toFloat() / steps
                        val radius = (0.5f * (1f - t)).coerceAtLeast(0.03f)
                        val segHeight = 1f / steps
                        val yPos = -0.5f + segHeight * (i + 0.5f)
                        addChildNode(
                            CylinderNode(
                                engine = engine,
                                radius = radius,
                                height = segHeight * 1.05f,
                                materialInstance = mat
                            ).apply {
                                position = Position(0f, yPos, 0f)
                            }
                        )
                    }
                }
            }

            ObjectType.CAPSULE -> {
                Node(engine).apply {
                    addChildNode(
                        CylinderNode(
                            engine = engine,
                            radius = 0.35f,
                            height = 0.5f,
                            materialInstance = mat
                        )
                    )
                    addChildNode(
                        SphereNode(
                            engine = engine,
                            radius = 0.35f,
                            materialInstance = mat
                        ).apply { position = Position(0f, 0.25f, 0f) }
                    )
                    addChildNode(
                        SphereNode(
                            engine = engine,
                            radius = 0.35f,
                            materialInstance = mat
                        ).apply { position = Position(0f, -0.25f, 0f) }
                    )
                }
            }

            ObjectType.PLANE -> CubeNode(
                engine = engine,
                size = Size(1f, 0.08f, 1f),
                materialInstance = mat
            )

            ObjectType.TEXT_3D -> {
                val textBmp = renderTextBitmap(
                    text = obj.textContent.ifBlank { "Текст" },
                    accentArgb = obj.material.colorHex.toInt()
                )
                ImageNode(
                    materialLoader = materialLoader,
                    bitmap = textBmp,
                    size = Size(1.0f, 0.5f, 0.0f)
                )
            }

            ObjectType.DRAWING_3D -> {
                val bmp = obj.imagePath?.let { path ->
                    val f = File(path)
                    if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                }
                if (bmp != null) {
                    val aspect = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()).coerceIn(0.4f, 2.5f)
                    ImageNode(
                        materialLoader = materialLoader,
                        bitmap = bmp,
                        size = Size(aspect * 0.7f, 0.7f, 0.0f)
                    )
                } else {
                    CubeNode(engine = engine, size = Size(0.6f, 0.6f, 0.04f), materialInstance = mat)
                }
            }

            ObjectType.GLB_MODEL -> {
                val path = obj.modelPath
                val file = if (path != null) File(path) else null
                val modelInstance = if (file != null && file.exists()) {
                    runCatching { modelLoader.createModelInstance(file) }.getOrNull()
                } else null

                if (modelInstance != null) {
                    ModelNode(
                        modelInstance = modelInstance,
                        scaleToUnits = 1.0f
                    )
                } else {
                    CubeNode(
                        engine = engine,
                        size = Size(1f, 1f, 1f),
                        materialInstance = mat
                    )
                }
            }
        }

        visualChild.name = obj.id
        root.addChildNode(visualChild)
        return root
    }

    fun buildGridAndAxesNode(
        engine: Engine,
        materialLoader: MaterialLoader,
        anchorType: AnchorType,
        showGrid: Boolean
    ): Node {
        val container = Node(engine).apply {
            name = "__grid_root__"
            isVisible = showGrid
        }

        val gridMat = materialLoader.createColorInstance(
            color = Color(0xFF48484A),
            metallic = 0f,
            roughness = 0.9f,
            reflectance = 0.1f
        )
        val axisXMat = materialLoader.createColorInstance(
            color = Color(0xFFFF453A),
            metallic = 0f,
            roughness = 0.4f,
            reflectance = 0.5f
        )
        val axisYMat = materialLoader.createColorInstance(
            color = Color(0xFF32D74B),
            metallic = 0f,
            roughness = 0.4f,
            reflectance = 0.5f
        )
        val axisZMat = materialLoader.createColorInstance(
            color = Color(0xFF0A84FF),
            metallic = 0f,
            roughness = 0.4f,
            reflectance = 0.5f
        )

        val gridExtent = 1.5f
        val step = 0.25f
        val lineThickness = 0.004f

        // Grid lines along X and Z
        var coord = -gridExtent
        while (coord <= gridExtent + 0.001f) {
            if (kotlin.math.abs(coord) > 0.01f) {
                container.addChildNode(
                    CubeNode(
                        engine = engine,
                        size = Size(gridExtent * 2f, lineThickness, lineThickness),
                        materialInstance = gridMat
                    ).apply { position = Position(0f, 0f, coord) }
                )
                container.addChildNode(
                    CubeNode(
                        engine = engine,
                        size = Size(lineThickness, lineThickness, gridExtent * 2f),
                        materialInstance = gridMat
                    ).apply { position = Position(coord, 0f, 0f) }
                )
            }
            coord += step
        }

        // Primary X, Y, Z axes
        container.addChildNode(
            CubeNode(
                engine = engine,
                size = Size(gridExtent * 2f, 0.008f, 0.008f),
                materialInstance = axisXMat
            )
        )
        container.addChildNode(
            CubeNode(
                engine = engine,
                size = Size(0.008f, 0.6f, 0.008f),
                materialInstance = axisYMat
            ).apply { position = Position(0f, 0.3f, 0f) }
        )
        container.addChildNode(
            CubeNode(
                engine = engine,
                size = Size(0.008f, 0.008f, gridExtent * 2f),
                materialInstance = axisZMat
            )
        )

        // Anchor visual guide
        if (anchorType == AnchorType.VERTICAL || anchorType == AnchorType.IMAGE) {
            val frameMat = materialLoader.createColorInstance(
                color = Color(0xFF0A84FF),
                metallic = 0.2f,
                roughness = 0.3f,
                reflectance = 0.6f
            )
            val w = 0.8f
            val h = 0.6f
            val t = 0.012f
            container.addChildNode(
                CubeNode(engine, Size(w, t, t), materialInstance = frameMat)
                    .apply { position = Position(0f, h, -0.05f) }
            )
            container.addChildNode(
                CubeNode(engine, Size(w, t, t), materialInstance = frameMat)
                    .apply { position = Position(0f, 0.01f, -0.05f) }
            )
            container.addChildNode(
                CubeNode(engine, Size(t, h, t), materialInstance = frameMat)
                    .apply { position = Position(-w / 2f, h / 2f, -0.05f) }
            )
            container.addChildNode(
                CubeNode(engine, Size(t, h, t), materialInstance = frameMat)
                    .apply { position = Position(w / 2f, h / 2f, -0.05f) }
            )
        }

        return container
    }

    fun buildSelectionGizmoNode(
        engine: Engine,
        materialLoader: MaterialLoader
    ): Node {
        val gizmo = Node(engine).apply {
            name = "__selection_gizmo__"
            isVisible = false
        }
        val xMat = materialLoader.createColorInstance(Color(0xFFFF453A), 0f, 0.2f, 0.8f)
        val yMat = materialLoader.createColorInstance(Color(0xFF32D74B), 0f, 0.2f, 0.8f)
        val zMat = materialLoader.createColorInstance(Color(0xFF0A84FF), 0f, 0.2f, 0.8f)

        val axisLen = 0.38f
        val thickness = 0.014f

        // X Axis + Tip
        gizmo.addChildNode(
            CubeNode(engine, Size(axisLen, thickness, thickness), materialInstance = xMat)
                .apply { position = Position(axisLen / 2f, 0f, 0f) }
        )
        gizmo.addChildNode(
            SphereNode(engine, radius = 0.028f, materialInstance = xMat)
                .apply { position = Position(axisLen, 0f, 0f) }
        )

        // Y Axis + Tip
        gizmo.addChildNode(
            CubeNode(engine, Size(thickness, axisLen, thickness), materialInstance = yMat)
                .apply { position = Position(0f, axisLen / 2f, 0f) }
        )
        gizmo.addChildNode(
            SphereNode(engine, radius = 0.028f, materialInstance = yMat)
                .apply { position = Position(0f, axisLen, 0f) }
        )

        // Z Axis + Tip
        gizmo.addChildNode(
            CubeNode(engine, Size(thickness, thickness, axisLen), materialInstance = zMat)
                .apply { position = Position(0f, 0f, axisLen / 2f) }
        )
        gizmo.addChildNode(
            SphereNode(engine, radius = 0.028f, materialInstance = zMat)
                .apply { position = Position(0f, 0f, axisLen) }
        )

        return gizmo
    }

    private fun renderTextBitmap(text: String, accentArgb: Int): Bitmap {
        val width = 1024
        val height = 512
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Rounded translucent dark studio badge with colored accent border
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(232, 22, 22, 26)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentArgb
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        val rect = RectF(18f, 18f, width - 18f, height - 18f)
        canvas.drawRoundRect(rect, 54f, 54f, bgPaint)
        canvas.drawRoundRect(rect, 54f, 54f, borderPaint)

        // Auto-fit crisp bold text
        val lines = text.split("\n").take(4).ifEmpty { listOf("Текст") }
        val maxLineLen = lines.maxOf { it.length }.coerceAtLeast(1)
        val textSizePx = when {
            maxLineLen <= 6 && lines.size == 1 -> 170f
            maxLineLen <= 12 && lines.size == 1 -> 124f
            maxLineLen <= 20 -> 88f
            else -> 64f
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentArgb
            textSize = textSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(12f, 0f, 4f, android.graphics.Color.argb(180, 0, 0, 0))
        }

        val lineHeight = textSizePx * 1.2f
        val totalHeight = lineHeight * lines.size
        var y = (height - totalHeight) / 2f + textSizePx * 0.85f
        for (line in lines) {
            canvas.drawText(line, width / 2f, y, textPaint)
            y += lineHeight
        }

        return bmp
    }
}
