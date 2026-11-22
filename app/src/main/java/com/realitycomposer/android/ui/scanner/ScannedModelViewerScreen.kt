package com.realitycomposer.android.ui.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realitycomposer.android.model.ScannedObject
import com.realitycomposer.android.ui.theme.RcColors
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Clean White Studio 3D Model Viewer matching Apple Reality Composer Screenshot #4 (< Projects | Rook)
 */
@Composable
fun ScannedModelViewerScreen(
    scan: ScannedObject,
    onBackToProjects: () -> Unit,
    onExportObj: (File) -> Unit,
    onOpenInArEditor: () -> Unit
) {
    var yawDeg by remember { mutableStateOf(32f) }
    var pitchDeg by remember { mutableStateOf(18f) }
    var zoom by remember { mutableStateOf(1.0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Apple-style Top Navigation Bar (< Проекты | Имя модели | Экспорт)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBackToProjects) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Проекты",
                    tint = RcColors.AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Проекты", color = RcColors.AccentBlue, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Text(
                text = scan.name,
                color = Color(0xFF1C1C1E),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(
                onClick = {
                    scan.objFilePath?.let { path ->
                        val f = File(path)
                        if (f.exists()) onExportObj(f)
                    }
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = "Экспорт .OBJ", tint = RcColors.AccentBlue)
            }
        }

        HorizontalDivider(color = Color(0xFFE5E5EA))

        // Interactive 360° 3D Studio Viewport (Drag to Rotate, Pinch to Zoom)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        yawDeg = (yawDeg + pan.x * 0.45f) % 360f
                        pitchDeg = (pitchDeg + pan.y * 0.35f).coerceIn(-75f, 75f)
                        zoom = (zoom * gestureZoom).coerceIn(0.5f, 2.8f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val baseScale = minOf(size.width, size.height) * 0.95f * zoom

                val yaw = yawDeg * PI.toFloat() / 180f
                val pitch = pitchDeg * PI.toFloat() / 180f
                val cosY = cos(yaw)
                val sinY = sin(yaw)
                val cosP = cos(pitch)
                val sinP = sin(pitch)

                // Soft studio ground shadow
                drawOval(
                    color = Color.Black.copy(alpha = 0.08f),
                    topLeft = Offset(cx - baseScale * 0.28f, cy + baseScale * 0.26f),
                    size = Size(baseScale * 0.56f, baseScale * 0.14f)
                )

                // Transform & depth-sort all 3D voxels/points (Painter's Algorithm)
                data class Projected(val sx: Float, val sy: Float, val depth: Float, val color: Color)

                val projected = scan.points.map { p ->
                    val x1 = p.x * cosY - p.z * sinY
                    val z1 = p.x * sinY + p.z * cosY
                    val y2 = p.y * cosP - z1 * sinP
                    val z2 = p.y * sinP + z1 * cosP

                    val persp = 1.6f / (1.6f - z2 * 0.55f).coerceAtLeast(0.4f)
                    val sx = cx + x1 * baseScale * persp
                    val sy = cy - y2 * baseScale * persp

                    // Subtle studio directional shading
                    val shade = (0.78f + (z2 + 0.4f) * 0.35f).coerceIn(0.62f, 1.12f)
                    val baseCol = Color(p.argb)
                    val shaded = Color(
                        red = (baseCol.red * shade).coerceIn(0f, 1f),
                        green = (baseCol.green * shade).coerceIn(0f, 1f),
                        blue = (baseCol.blue * shade).coerceIn(0f, 1f),
                        alpha = 1f
                    )
                    Projected(sx, sy, z2, shaded)
                }.sortedBy { it.depth }

                val voxelPx = (baseScale * 0.052f).coerceIn(8f, 34f)
                for (pt in projected) {
                    drawRect(
                        color = pt.color,
                        topLeft = Offset(pt.sx - voxelPx / 2f, pt.sy - voxelPx / 2f),
                        size = Size(voxelPx, voxelPx)
                    )
                }
            }

            // Hint pill
            Text(
                text = "Вращайте пальцем • Точек: ${scan.points.size} • Кадров: ${scan.capturedFramesCount}",
                color = Color(0xFF8E8E93),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }

        // Bottom Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF2F2F7))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    scan.objFilePath?.let { path ->
                        val f = File(path)
                        if (f.exists()) onExportObj(f)
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp), tint = RcColors.AccentBlue)
                Spacer(Modifier.width(6.dp))
                Text("Экспорт .OBJ", color = RcColors.AccentBlue, fontSize = 13.sp)
            }

            Button(
                onClick = onOpenInArEditor,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ViewInAr, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("В AR-редактор", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
