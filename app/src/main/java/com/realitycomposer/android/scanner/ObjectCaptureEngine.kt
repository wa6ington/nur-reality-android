package com.realitycomposer.android.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.realitycomposer.android.model.ColoredPoint3D
import com.realitycomposer.android.model.ScannedObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class CapturedAngleFrame(
    val angleDeg: Float,
    val bitmap: Bitmap
)

class ObjectCaptureEngine(private val context: Context) {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    private val scansDir: File
        get() = File(context.filesDir, "scans").apply { mkdirs() }

    /**
     * Extracts a lightweight live point-cloud slice from a single captured camera frame
     * for the real-time 3D preview inside the 360° capture ring.
     */
    fun extractLiveSlicePoints(
        bitmap: Bitmap,
        angleDeg: Float,
        boxWidthRatio: Float,
        boxHeightRatio: Float
    ): List<ColoredPoint3D> {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return emptyList()

        // Estimate background color from outer border pixels
        val bgColor = estimateBorderColor(bitmap)
        val rad = angleDeg * PI.toFloat() / 180f
        val cosA = cos(rad)
        val sinA = sin(rad)

        val minX = ((0.5f - boxWidthRatio * 0.45f) * w).toInt().coerceIn(0, w - 1)
        val maxX = ((0.5f + boxWidthRatio * 0.45f) * w).toInt().coerceIn(minX + 1, w)
        val minY = ((0.5f - boxHeightRatio * 0.45f) * h).toInt().coerceIn(0, h - 1)
        val maxY = ((0.5f + boxHeightRatio * 0.45f) * h).toInt().coerceIn(minY + 1, h)

        val stepX = ((maxX - minX) / 10).coerceAtLeast(1)
        val stepY = ((maxY - minY) / 12).coerceAtLeast(1)

        val slice = mutableListOf<ColoredPoint3D>()
        var y = minY
        while (y < maxY) {
            val ny = 0.5f - (y - minY).toFloat() / (maxY - minY).toFloat() // +0.5 top, -0.5 bottom
            var x = minX
            while (x < maxX) {
                val pixel = bitmap.getPixel(x, y)
                if (colorDistance(pixel, bgColor) > 36) {
                    val nx = (x - minX).toFloat() / (maxX - minX).toFloat() - 0.5f
                    // Project onto 3D cylinder/box surface at this capture angle
                    val depth = (0.28f - abs(nx) * 0.25f).coerceAtLeast(0.06f)
                    val px = nx * 0.55f * cosA - depth * sinA
                    val pz = nx * 0.55f * sinA + depth * cosA
                    val py = ny * 0.65f
                    slice.add(ColoredPoint3D(px, py, pz, pixel))
                }
                x += stepX
            }
            y += stepY
        }
        return slice
    }

    /**
     * Performs on-device Multi-View Visual Hull Carving + RGB Color Projection
     * across all captured 360° camera frames, builds surface voxels, and exports a real .OBJ 3D file.
     */
    suspend fun reconstructAndSave(
        name: String,
        frames: List<CapturedAngleFrame>,
        boxWidthRatio: Float,
        boxHeightRatio: Float
    ): ScannedObject = withContext(Dispatchers.Default) {
        val gridRes = 16
        val half = gridRes / 2f
        val voxels = Array(gridRes) { Array(gridRes) { IntArray(gridRes) { 0 } } }
        val voxelHits = Array(gridRes) { Array(gridRes) { IntArray(gridRes) { 0 } } }
        val voxelR = Array(gridRes) { Array(gridRes) { IntArray(gridRes) { 0 } } }
        val voxelG = Array(gridRes) { Array(gridRes) { IntArray(gridRes) { 0 } } }
        val voxelB = Array(gridRes) { Array(gridRes) { IntArray(gridRes) { 0 } } }

        val validFrames = frames.filter { it.bitmap.width >= 16 && it.bitmap.height >= 16 }

        if (validFrames.isNotEmpty()) {
            val bgColors = validFrames.map { estimateBorderColor(it.bitmap) }

            for (ix in 0 until gridRes) {
                val vx = (ix + 0.5f - half) / half * 0.42f
                for (iy in 0 until gridRes) {
                    val vy = (iy + 0.5f - half) / half * 0.48f
                    for (iz in 0 until gridRes) {
                        val vz = (iz + 0.5f - half) / half * 0.42f

                        var hits = 0
                        var rSum = 0
                        var gSum = 0
                        var bSum = 0

                        for (fIdx in validFrames.indices) {
                            val frame = validFrames[fIdx]
                            val bmp = frame.bitmap
                            val rad = frame.angleDeg * PI.toFloat() / 180f
                            val uNorm = vx * cos(rad) + vz * sin(rad)
                            val vNorm = -vy

                            val px = ((0.5f + uNorm * boxWidthRatio) * bmp.width).toInt()
                            val py = ((0.5f + vNorm * boxHeightRatio) * bmp.height).toInt()

                            if (px in 0 until bmp.width && py in 0 until bmp.height) {
                                val c = bmp.getPixel(px, py)
                                if (colorDistance(c, bgColors[fIdx]) > 32) {
                                    hits++
                                    rSum += Color.red(c)
                                    gSum += Color.green(c)
                                    bSum += Color.blue(c)
                                }
                            }
                        }

                        voxelHits[ix][iy][iz] = hits
                        if (hits > 0) {
                            voxelR[ix][iy][iz] = rSum / hits
                            voxelG[ix][iy][iz] = gSum / hits
                            voxelB[ix][iy][iz] = bSum / hits
                        }
                    }
                }
            }

            // Keep voxels visible in at least 45% of captured views (Visual Hull threshold)
            val threshold = (validFrames.size * 0.42f).toInt().coerceAtLeast(1)
            for (ix in 0 until gridRes) {
                for (iy in 0 until gridRes) {
                    for (iz in 0 until gridRes) {
                        if (voxelHits[ix][iy][iz] >= threshold) {
                            voxels[ix][iy][iz] = Color.rgb(
                                voxelR[ix][iy][iz],
                                voxelG[ix][iy][iz],
                                voxelB[ix][iy][iz]
                            )
                        }
                    }
                }
            }
        }

        // Extract outer surface voxels for crisp, lightweight 3D rendering
        val surfacePoints = mutableListOf<ColoredPoint3D>()
        val scale = 0.52f / gridRes
        for (ix in 0 until gridRes) {
            for (iy in 0 until gridRes) {
                for (iz in 0 until gridRes) {
                    val col = voxels[ix][iy][iz]
                    if (col == 0) continue
                    val isSurface = ix == 0 || ix == gridRes - 1 ||
                        iy == 0 || iy == gridRes - 1 ||
                        iz == 0 || iz == gridRes - 1 ||
                        voxels[ix - 1][iy][iz] == 0 || voxels[ix + 1][iy][iz] == 0 ||
                        voxels[ix][iy - 1][iz] == 0 || voxels[ix][iy + 1][iz] == 0 ||
                        voxels[ix][iy][iz - 1] == 0 || voxels[ix][iy][iz + 1] == 0

                    if (isSurface) {
                        surfacePoints.add(
                            ColoredPoint3D(
                                x = (ix - half + 0.5f) * scale,
                                y = (iy - half + 0.5f) * scale,
                                z = (iz - half + 0.5f) * scale,
                                argb = col
                            )
                        )
                    }
                }
            }
        }

        // Fallback if scene had uniform color (e.g., blank wall) — sample center colors into a solid object shape
        val finalPoints = if (surfacePoints.size >= 24) {
            surfacePoints
        } else {
            buildFallbackSculptedPoints(validFrames)
        }

        val scanId = UUID.randomUUID().toString()
        val objFile = exportVoxelObj(scanId, finalPoints, scale)

        val scan = ScannedObject(
            id = scanId,
            name = name.ifBlank { "3D-Скан" },
            createdAt = System.currentTimeMillis(),
            capturedFramesCount = validFrames.size,
            objFilePath = objFile.absolutePath,
            points = finalPoints
        )

        saveScan(scan)
        scan
    }

    private fun buildFallbackSculptedPoints(frames: List<CapturedAngleFrame>): List<ColoredPoint3D> {
        val pts = mutableListOf<ColoredPoint3D>()
        val sampleBmp = frames.firstOrNull()?.bitmap
        val steps = 10
        val size = 0.36f
        for (ix in 0 until steps) {
            for (iy in 0 until steps) {
                for (iz in 0 until steps) {
                    val isSurface = ix == 0 || ix == steps - 1 || iy == 0 || iy == steps - 1 || iz == 0 || iz == steps - 1
                    if (!isSurface) continue
                    val fx = ix.toFloat() / (steps - 1) - 0.5f
                    val fy = iy.toFloat() / (steps - 1) - 0.5f
                    val fz = iz.toFloat() / (steps - 1) - 0.5f
                    val argb = if (sampleBmp != null) {
                        val px = ((fx + 0.5f) * (sampleBmp.width - 1)).toInt().coerceIn(0, sampleBmp.width - 1)
                        val py = ((0.5f - fy) * (sampleBmp.height - 1)).toInt().coerceIn(0, sampleBmp.height - 1)
                        sampleBmp.getPixel(px, py)
                    } else {
                        Color.rgb(196, 164, 124)
                    }
                    pts.add(ColoredPoint3D(fx * size, fy * size, fz * size, argb))
                }
            }
        }
        return pts
    }

    private fun exportVoxelObj(scanId: String, points: List<ColoredPoint3D>, voxelSize: Float): File {
        val file = File(scansDir, "$scanId.obj")
        val h = voxelSize * 0.52f
        val sb = StringBuilder()
        sb.appendLine("# Reality Composer Android 3D Object Capture")
        sb.appendLine("o ScannedObject_$scanId")
        var vOffset = 1
        for (p in points) {
            val r = Color.red(p.argb) / 255f
            val g = Color.green(p.argb) / 255f
            val b = Color.blue(p.argb) / 255f
            val verts = arrayOf(
                floatArrayOf(p.x - h, p.y - h, p.z + h),
                floatArrayOf(p.x + h, p.y - h, p.z + h),
                floatArrayOf(p.x + h, p.y + h, p.z + h),
                floatArrayOf(p.x - h, p.y + h, p.z + h),
                floatArrayOf(p.x - h, p.y - h, p.z - h),
                floatArrayOf(p.x + h, p.y - h, p.z - h),
                floatArrayOf(p.x + h, p.y + h, p.z - h),
                floatArrayOf(p.x - h, p.y + h, p.z - h)
            )
            for (v in verts) {
                sb.appendLine("v ${v[0]} ${v[1]} ${v[2]} $r $g $b")
            }
            val i = vOffset
            sb.appendLine("f $i ${i + 1} ${i + 2} ${i + 3}")
            sb.appendLine("f ${i + 5} ${i + 4} ${i + 7} ${i + 6}")
            sb.appendLine("f ${i + 4} $i ${i + 3} ${i + 7}")
            sb.appendLine("f ${i + 1} ${i + 5} ${i + 6} ${i + 2}")
            sb.appendLine("f ${i + 3} ${i + 2} ${i + 6} ${i + 7}")
            sb.appendLine("f ${i + 4} ${i + 5} ${i + 1} $i")
            vOffset += 8
        }
        file.writeText(sb.toString())
        return file
    }

    suspend fun loadAllScans(): List<ScannedObject> = withContext(Dispatchers.IO) {
        scansDir.listFiles { f -> f.extension == "json" }?.mapNotNull { file ->
            runCatching { json.decodeFromString<ScannedObject>(file.readText()) }.getOrNull()
        }?.sortedByDescending { it.createdAt } ?: emptyList()
    }

    private fun saveScan(scan: ScannedObject) {
        val file = File(scansDir, "${scan.id}.json")
        file.writeText(json.encodeToString(scan))
    }

    suspend fun deleteScan(scanId: String) = withContext(Dispatchers.IO) {
        File(scansDir, "$scanId.json").delete()
        File(scansDir, "$scanId.obj").delete()
    }

    private fun estimateBorderColor(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        var r = 0
        var g = 0
        var b = 0
        var count = 0
        val samples = 12
        for (i in 0 until samples) {
            val x = (i * (w - 1)) / (samples - 1)
            val y = (i * (h - 1)) / (samples - 1)
            val pixels = intArrayOf(
                bitmap.getPixel(x, 2),
                bitmap.getPixel(x, h - 3),
                bitmap.getPixel(2, y),
                bitmap.getPixel(w - 3, y)
            )
            for (c in pixels) {
                r += Color.red(c)
                g += Color.green(c)
                b += Color.blue(c)
                count++
            }
        }
        return if (count > 0) Color.rgb(r / count, g / count, b / count) else Color.DKGRAY
    }

    private fun colorDistance(c1: Int, c2: Int): Int {
        val dr = abs(Color.red(c1) - Color.red(c2))
        val dg = abs(Color.green(c1) - Color.green(c2))
        val db = abs(Color.blue(c1) - Color.blue(c2))
        return (dr + dg + db) / 3
    }
}
