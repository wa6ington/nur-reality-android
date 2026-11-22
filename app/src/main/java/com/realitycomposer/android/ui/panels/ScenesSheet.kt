package com.realitycomposer.android.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realitycomposer.android.model.AnchorType
import com.realitycomposer.android.model.RealityProject
import com.realitycomposer.android.ui.screens.anchorIcon
import com.realitycomposer.android.ui.theme.RcColors
import java.util.Locale

@Composable
fun ScenesSheet(
    project: RealityProject,
    onSwitchScene: (String) -> Unit,
    onAddScene: (String, AnchorType) -> Unit,
    onDeleteScene: (String) -> Unit,
    onUpdateSettings: (Boolean?, Float?, AnchorType?) -> Unit,
    onClose: () -> Unit
) {
    val activeScene = project.activeScene
    var showNewSceneDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.62f)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(RcColors.SurfaceTranslucent)
            .border(1.dp, RcColors.Border, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Сцены и настройки",
                color = RcColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showNewSceneDialog = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Новая сцена")
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = RcColors.TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(project.scenes, key = { it.id }) { scene ->
                val isCurrent = scene.id == project.activeSceneId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isCurrent) RcColors.AccentBlue.copy(alpha = 0.2f)
                            else RcColors.SurfaceElevated
                        )
                        .border(
                            width = if (isCurrent) 1.5.dp else 1.dp,
                            color = if (isCurrent) RcColors.AccentBlue else RcColors.Border,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSwitchScene(scene.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = anchorIcon(scene.anchorType),
                        contentDescription = null,
                        tint = if (isCurrent) RcColors.AccentBlue else RcColors.TextSecondary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = scene.name,
                            color = RcColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${scene.anchorType.displayNameRu} • Объектов: ${scene.objects.size} • Поведений: ${scene.behaviors.size}",
                            color = RcColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    if (project.scenes.size > 1) {
                        IconButton(onClick = { onDeleteScene(scene.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = RcColors.DangerRed)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                InspectorSectionCard(title = "Настройки текущей сцены") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Показывать 3D-сетку", color = RcColors.TextPrimary, fontSize = 14.sp)
                        Switch(
                            checked = activeScene.gridVisible,
                            onCheckedChange = { onUpdateSettings(it, null, null) }
                        )
                    }

                    Text("Тип якоря", color = RcColors.TextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AnchorType.entries) { anchor ->
                            FilterChip(
                                selected = activeScene.anchorType == anchor,
                                onClick = { onUpdateSettings(null, null, anchor) },
                                label = { Text(anchor.displayNameRu, fontSize = 12.sp) }
                            )
                        }
                    }

                    Text(
                        text = "Гравитация: ${String.format(Locale.US, "%.2f", activeScene.physicsGravity)} м/с²",
                        color = RcColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = activeScene.physicsGravity,
                        onValueChange = { onUpdateSettings(null, it, null) },
                        valueRange = -20f..0f,
                        colors = SliderDefaults.colors(
                            thumbColor = RcColors.AccentBlue,
                            activeTrackColor = RcColors.AccentBlue
                        )
                    )
                }
            }
        }
    }

    if (showNewSceneDialog) {
        var name by remember { mutableStateOf("Сцена ${project.scenes.size + 1}") }
        var anchor by remember { mutableStateOf(AnchorType.HORIZONTAL) }
        AlertDialog(
            onDismissRequest = { showNewSceneDialog = false },
            containerColor = RcColors.Surface,
            title = { Text("Новая сцена", color = RcColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название") },
                        singleLine = true
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AnchorType.entries) { a ->
                            FilterChip(
                                selected = anchor == a,
                                onClick = { anchor = a },
                                label = { Text(a.displayNameRu, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNewSceneDialog = false
                        onAddScene(name, anchor)
                    }
                ) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showNewSceneDialog = false }) { Text("Отмена") }
            }
        )
    }
}
