package com.realitycomposer.android.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realitycomposer.android.model.*
import com.realitycomposer.android.ui.theme.RcColors
import java.util.Locale

private val ColorPalette = listOf(
    0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFFF453A,
    0xFFBF5AF2, 0xFF64D2FF, 0xFFFFD60A, 0xFFFF375F,
    0xFFF5F5F7, 0xFFAEAEB2, 0xFF48484A, 0xFF1C1C1E,
    0xFFAC7F5E, 0xFF5E5CE6
)

@Composable
fun PropertiesSheet(
    selectedObject: SceneObject?,
    onUpdateObject: (Boolean, (SceneObject) -> SceneObject) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.68f)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(RcColors.SurfaceTranslucent)
            .border(1.dp, RcColors.Border, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Свойства",
                color = RcColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = RcColors.TextSecondary)
            }
        }

        if (selectedObject == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Выберите объект на сцене, чтобы изменить его свойства",
                    color = RcColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Object Name & Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = selectedObject.name,
                    onValueChange = { name -> onUpdateObject(false) { it.copy(name = name) } },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    label = { Text("Имя объекта", fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RcColors.AccentBlue,
                        unfocusedBorderColor = RcColors.Border,
                        focusedTextColor = RcColors.TextPrimary,
                        unfocusedTextColor = RcColors.TextPrimary
                    )
                )

                IconButton(onClick = { onUpdateObject(true) { it.copy(visible = !it.visible) } }) {
                    Icon(
                        imageVector = if (selectedObject.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Видимость",
                        tint = if (selectedObject.visible) RcColors.AccentBlue else RcColors.TextTertiary
                    )
                }
                IconButton(onClick = { onUpdateObject(true) { it.copy(locked = !it.locked) } }) {
                    Icon(
                        imageVector = if (selectedObject.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Блокировка",
                        tint = if (selectedObject.locked) RcColors.WarningOrange else RcColors.TextSecondary
                    )
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Дублировать", tint = RcColors.TextSecondary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = RcColors.DangerRed)
                }
            }

            // Text content editor for 3D-Text OR Text Label on ANY 3D object!
            if (selectedObject.type == ObjectType.TEXT_3D) {
                InspectorSectionCard(title = "Содержимое 3D-текста") {
                    OutlinedTextField(
                        value = selectedObject.textContent,
                        onValueChange = { txt ->
                            onUpdateObject(false) { it.copy(textContent = txt) }
                        },
                        label = { Text("Текст на 3D-табличке", fontSize = 11.sp) },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RcColors.AccentBlue,
                            unfocusedBorderColor = RcColors.Border,
                            focusedTextColor = RcColors.TextPrimary,
                            unfocusedTextColor = RcColors.TextPrimary
                        )
                    )
                    Text(
                        text = "Цвет текста меняется в разделе «Внешний вид и материал» ниже",
                        color = RcColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            } else {
                InspectorSectionCard(title = "Текст на этом 3D-объекте") {
                    OutlinedTextField(
                        value = selectedObject.labelText,
                        onValueChange = { txt ->
                            onUpdateObject(false) { it.copy(labelText = txt) }
                        },
                        placeholder = { Text("Например: NUR ADVERTISING", fontSize = 12.sp) },
                        label = { Text("Надпись прямо на объекте", fontSize = 11.sp) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RcColors.AccentBlue,
                            unfocusedBorderColor = RcColors.Border,
                            focusedTextColor = RcColors.TextPrimary,
                            unfocusedTextColor = RcColors.TextPrimary
                        )
                    )
                    if (selectedObject.labelText.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Цвет надписи", color = RcColors.TextSecondary, fontSize = 12.sp)
                            TextButton(
                                onClick = { onUpdateObject(true) { it.copy(labelText = "") } },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Убрать текст", color = RcColors.DangerRed, fontSize = 11.sp)
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ColorPalette) { hex ->
                                val isSelected = selectedObject.labelColorHex == hex
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) Color.White else RcColors.Border,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onUpdateObject(true) { it.copy(labelColorHex = hex) }
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // Section 1: Transform
            InspectorSectionCard(title = "Трансформация") {
                Text("Позиция (м)", color = RcColors.TextSecondary, fontSize = 12.sp)
                AxisSliderRow("X", RcColors.AxisX, selectedObject.position.x, -2.5f..2.5f) { v ->
                    onUpdateObject(false) { it.copy(position = it.position.copy(x = v)) }
                }
                AxisSliderRow("Y", RcColors.AxisY, selectedObject.position.y, 0f..2.5f) { v ->
                    onUpdateObject(false) { it.copy(position = it.position.copy(y = v)) }
                }
                AxisSliderRow("Z", RcColors.AxisZ, selectedObject.position.z, -2.5f..2.5f) { v ->
                    onUpdateObject(false) { it.copy(position = it.position.copy(z = v)) }
                }

                HorizontalDivider(color = RcColors.Border.copy(alpha = 0.5f))

                Text("Поворот (°)", color = RcColors.TextSecondary, fontSize = 12.sp)
                AxisSliderRow("X", RcColors.AxisX, selectedObject.rotation.x, -180f..180f, "%.0f°") { v ->
                    onUpdateObject(false) { it.copy(rotation = it.rotation.copy(x = v)) }
                }
                AxisSliderRow("Y", RcColors.AxisY, selectedObject.rotation.y, -180f..180f, "%.0f°") { v ->
                    onUpdateObject(false) { it.copy(rotation = it.rotation.copy(y = v)) }
                }
                AxisSliderRow("Z", RcColors.AxisZ, selectedObject.rotation.z, -180f..180f, "%.0f°") { v ->
                    onUpdateObject(false) { it.copy(rotation = it.rotation.copy(z = v)) }
                }

                HorizontalDivider(color = RcColors.Border.copy(alpha = 0.5f))

                Text("Масштаб", color = RcColors.TextSecondary, fontSize = 12.sp)
                val uniformScale = (selectedObject.scale.x + selectedObject.scale.y + selectedObject.scale.z) / 3f
                AxisSliderRow("Все", RcColors.AccentBlue, uniformScale, 0.05f..2.0f) { v ->
                    onUpdateObject(false) { it.copy(scale = Vec3(v, v, v)) }
                }
                AxisSliderRow("X", RcColors.AxisX, selectedObject.scale.x, 0.04f..2.0f) { v ->
                    onUpdateObject(false) { it.copy(scale = it.scale.copy(x = v)) }
                }
                AxisSliderRow("Y", RcColors.AxisY, selectedObject.scale.y, 0.04f..2.0f) { v ->
                    onUpdateObject(false) { it.copy(scale = it.scale.copy(y = v)) }
                }
                AxisSliderRow("Z", RcColors.AxisZ, selectedObject.scale.z, 0.04f..2.0f) { v ->
                    onUpdateObject(false) { it.copy(scale = it.scale.copy(z = v)) }
                }
            }

            // Section 2: Material / Look
            InspectorSectionCard(title = "Внешний вид и материал") {
                Text("Цвет", color = RcColors.TextSecondary, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ColorPalette) { hex ->
                        val isSelected = selectedObject.material.colorHex == hex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else RcColors.Border,
                                    shape = CircleShape
                                )
                                .clickable {
                                    onUpdateObject(true) {
                                        it.copy(material = it.material.copy(colorHex = hex))
                                    }
                                }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Тип поверхности", color = RcColors.TextSecondary, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MaterialPreset.entries.filter { it != MaterialPreset.CUSTOM }) { preset ->
                        val selected = selectedObject.material.preset == preset
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onUpdateObject(true) {
                                    it.copy(
                                        material = it.material.copy(
                                            preset = preset,
                                            metallic = preset.metallic,
                                            roughness = preset.roughness,
                                            reflectance = preset.reflectance
                                        )
                                    )
                                }
                            },
                            label = { Text(preset.displayNameRu, fontSize = 12.sp) }
                        )
                    }
                }

                AxisSliderRow("Металл", RcColors.TextSecondary, selectedObject.material.metallic, 0f..1f) { v ->
                    onUpdateObject(false) {
                        it.copy(material = it.material.copy(metallic = v, preset = MaterialPreset.CUSTOM))
                    }
                }
                AxisSliderRow("Шерох.", RcColors.TextSecondary, selectedObject.material.roughness, 0.05f..1f) { v ->
                    onUpdateObject(false) {
                        it.copy(material = it.material.copy(roughness = v, preset = MaterialPreset.CUSTOM))
                    }
                }
            }

            // Section 3: Physics
            InspectorSectionCard(title = "Физика") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Участвует в физике",
                        color = RcColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = selectedObject.physics.enabled,
                        onCheckedChange = { enabled ->
                            onUpdateObject(true) {
                                it.copy(physics = it.physics.copy(enabled = enabled))
                            }
                        }
                    )
                }

                if (selectedObject.physics.enabled) {
                    Text("Тип движения", color = RcColors.TextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhysicsMotionType.entries.forEach { motion ->
                            FilterChip(
                                selected = selectedObject.physics.motionType == motion,
                                onClick = {
                                    onUpdateObject(true) {
                                        it.copy(physics = it.physics.copy(motionType = motion))
                                    }
                                },
                                label = { Text(motion.displayNameRu, fontSize = 12.sp) }
                            )
                        }
                    }

                    Text("Физический материал", color = RcColors.TextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PhysicsMaterial.entries) { mat ->
                            FilterChip(
                                selected = selectedObject.physics.material == mat,
                                onClick = {
                                    onUpdateObject(true) {
                                        it.copy(physics = it.physics.copy(material = mat))
                                    }
                                },
                                label = { Text(mat.displayNameRu, fontSize = 12.sp) }
                            )
                        }
                    }

                    Text("Форма столкновений", color = RcColors.TextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(CollisionShape.entries) { shape ->
                            FilterChip(
                                selected = selectedObject.physics.collisionShape == shape,
                                onClick = {
                                    onUpdateObject(true) {
                                        it.copy(physics = it.physics.copy(collisionShape = shape))
                                    }
                                },
                                label = { Text(shape.displayNameRu, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun InspectorSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RcColors.SurfaceElevated)
            .border(1.dp, RcColors.Border.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = RcColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        content()
    }
}

@Composable
private fun AxisSliderRow(
    label: String,
    labelColor: Color,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String = "%.2f",
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = labelColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.width(46.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = labelColor,
                activeTrackColor = labelColor
            )
        )
        Text(
            text = String.format(Locale.US, format, value),
            color = RcColors.TextPrimary,
            fontSize = 11.sp,
            modifier = Modifier.width(42.dp)
        )
    }
}
