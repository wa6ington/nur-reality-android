package com.realitycomposer.android.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun BehaviorsSheet(
    scene: RealityScene,
    allScenes: List<RealityScene>,
    onAddTemplate: (String) -> Unit,
    onUpdateBehavior: (String, (Behavior) -> Behavior) -> Unit,
    onDeleteBehavior: (String) -> Unit,
    onAddAction: (String, ActionType) -> Unit,
    onDeleteAction: (String, String) -> Unit,
    onTestBehavior: (Behavior) -> Unit,
    onClose: () -> Unit
) {
    var showAddTemplateMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.72f)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = RcColors.WarningOrange)
                Text(
                    text = "Поведения (${scene.behaviors.size})",
                    color = RcColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Button(
                        onClick = { showAddTemplateMenu = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Поведение", fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = showAddTemplateMenu,
                        onDismissRequest = { showAddTemplateMenu = false },
                        containerColor = RcColors.SurfaceElevated
                    ) {
                        listOf(
                            "tap_bounce" to "Нажатие и прыжок",
                            "start_spin" to "Старт и вращение",
                            "proximity_pop" to "Близость и акцент",
                            "collide_jiggle" to "Столкновение и тряска",
                            "custom" to "Свое поведение"
                        ).forEach { (key, title) ->
                            DropdownMenuItem(
                                text = { Text(title, color = RcColors.TextPrimary) },
                                onClick = {
                                    showAddTemplateMenu = false
                                    onAddTemplate(key)
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = RcColors.TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (scene.behaviors.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Поведений пока нет",
                        color = RcColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Нажмите «+ Поведение», чтобы добавить триггер и действия",
                        color = RcColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(scene.behaviors, key = { it.id }) { behavior ->
                    BehaviorCard(
                        behavior = behavior,
                        objects = scene.objects,
                        allScenes = allScenes,
                        onUpdate = { transform -> onUpdateBehavior(behavior.id, transform) },
                        onDelete = { onDeleteBehavior(behavior.id) },
                        onAddAction = { type -> onAddAction(behavior.id, type) },
                        onDeleteAction = { actionId -> onDeleteAction(behavior.id, actionId) },
                        onTest = { onTestBehavior(behavior) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BehaviorCard(
    behavior: Behavior,
    objects: List<SceneObject>,
    allScenes: List<RealityScene>,
    onUpdate: ((Behavior) -> Behavior) -> Unit,
    onDelete: () -> Unit,
    onAddAction: (ActionType) -> Unit,
    onDeleteAction: (String) -> Unit,
    onTest: () -> Unit
) {
    var addActionExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RcColors.SurfaceElevated)
            .border(1.dp, RcColors.Border, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Switch(
                    checked = behavior.enabled,
                    onCheckedChange = { en -> onUpdate { it.copy(enabled = en) } }
                )
                Text(
                    text = behavior.name,
                    color = RcColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            Row {
                IconButton(onClick = onTest, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Тест", tint = RcColors.PlayGreen)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = RcColors.DangerRed)
                }
            }
        }

        // TRIGGER BLOCK
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1F2A38))
                .border(1.dp, RcColors.AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "ТРИГГЕР",
                color = RcColors.AccentBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TriggerType.entries) { trig ->
                    FilterChip(
                        selected = behavior.triggerType == trig,
                        onClick = { onUpdate { it.copy(triggerType = trig) } },
                        label = { Text(trig.displayNameRu, fontSize = 11.sp) }
                    )
                }
            }

            if (objects.isNotEmpty()) {
                Text("Объект-триггер:", color = RcColors.TextSecondary, fontSize = 11.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(objects, key = { it.id }) { obj ->
                        FilterChip(
                            selected = behavior.triggerTargetObjectId == obj.id,
                            onClick = { onUpdate { it.copy(triggerTargetObjectId = obj.id) } },
                            label = { Text(obj.name, fontSize = 11.sp) }
                        )
                    }
                }
            }

            if (behavior.triggerType == TriggerType.PROXIMITY) {
                Text(
                    text = "Дистанция: ${String.format(Locale.US, "%.1f", behavior.proximityDistance)} м",
                    color = RcColors.TextSecondary,
                    fontSize = 11.sp
                )
                Slider(
                    value = behavior.proximityDistance,
                    onValueChange = { d -> onUpdate { it.copy(proximityDistance = d) } },
                    valueRange = 0.3f..4.0f,
                    modifier = Modifier.height(26.dp)
                )
            }
        }

        // ACTION SEQUENCE BLOCK
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ПОСЛЕДОВАТЕЛЬНОСТЬ ДЕЙСТВИЙ (${behavior.actions.size})",
                    color = RcColors.PlayGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Box {
                    TextButton(
                        onClick = { addActionExpanded = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp), tint = RcColors.PlayGreen)
                        Spacer(Modifier.width(3.dp))
                        Text("Действие", color = RcColors.PlayGreen, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = addActionExpanded,
                        onDismissRequest = { addActionExpanded = false },
                        containerColor = RcColors.Surface
                    ) {
                        ActionType.entries.forEach { actionType ->
                            DropdownMenuItem(
                                text = { Text(actionType.displayNameRu, color = RcColors.TextPrimary) },
                                onClick = {
                                    addActionExpanded = false
                                    onAddAction(actionType)
                                }
                            )
                        }
                    }
                }
            }

            behavior.actions.forEachIndexed { idx, action ->
                ActionItemCard(
                    index = idx + 1,
                    action = action,
                    objects = objects,
                    allScenes = allScenes,
                    onUpdateAction = { updated ->
                        onUpdate { b ->
                            b.copy(actions = b.actions.map { if (it.id == action.id) updated else it })
                        }
                    },
                    onDelete = { onDeleteAction(action.id) }
                )
            }
        }
    }
}

@Composable
private fun ActionItemCard(
    index: Int,
    action: BehaviorAction,
    objects: List<SceneObject>,
    allScenes: List<RealityScene>,
    onUpdateAction: (BehaviorAction) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RcColors.Surface)
            .border(1.dp, RcColors.Border.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$index. ${action.type.displayNameRu}",
                color = RcColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Удалить", tint = RcColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        // Target object selector
        if (action.type != ActionType.WAIT && action.type != ActionType.CHANGE_SCENE && objects.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(objects, key = { it.id }) { obj ->
                    FilterChip(
                        selected = action.targetObjectId == obj.id,
                        onClick = { onUpdateAction(action.copy(targetObjectId = obj.id)) },
                        label = { Text(obj.name, fontSize = 11.sp) }
                    )
                }
            }
        }

        // Emphasize style
        if (action.type == ActionType.EMPHASIZE) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(EmphasizeStyle.entries) { style ->
                    FilterChip(
                        selected = action.emphasizeStyle == style,
                        onClick = { onUpdateAction(action.copy(emphasizeStyle = style)) },
                        label = { Text(style.displayNameRu, fontSize = 11.sp) }
                    )
                }
            }
        }

        // Target scene selector
        if (action.type == ActionType.CHANGE_SCENE) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(allScenes, key = { it.id }) { sc ->
                    FilterChip(
                        selected = action.targetSceneId == sc.id,
                        onClick = { onUpdateAction(action.copy(targetSceneId = sc.id)) },
                        label = { Text(sc.name, fontSize = 11.sp) }
                    )
                }
            }
        }

        // Duration slider
        if (action.type != ActionType.HIDE && action.type != ActionType.SHOW && action.type != ActionType.CHANGE_SCENE) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Длительность: ${String.format(Locale.US, "%.1f", action.durationSec)} с",
                    color = RcColors.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.width(110.dp)
                )
                Slider(
                    value = action.durationSec,
                    onValueChange = { onUpdateAction(action.copy(durationSec = it)) },
                    valueRange = 0.2f..5.0f,
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                )
            }
        }
    }
}
