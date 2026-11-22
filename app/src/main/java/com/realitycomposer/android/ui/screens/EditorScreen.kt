package com.realitycomposer.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realitycomposer.android.model.*
import com.realitycomposer.android.ui.panels.AddObjectSheet
import com.realitycomposer.android.ui.panels.BehaviorsSheet
import com.realitycomposer.android.ui.panels.PropertiesSheet
import com.realitycomposer.android.ui.panels.ScenesSheet
import com.realitycomposer.android.ui.theme.RcColors
import com.realitycomposer.android.ui.viewport.ArLiveViewport
import com.realitycomposer.android.ui.viewport.Editor3DViewport
import com.realitycomposer.android.viewmodel.EditorUiState

@Composable
fun EditorScreen(
    state: EditorUiState,
    onBackToProjects: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleViewMode: () -> Unit,
    onToggleSnap: () -> Unit,
    onToggleSheet: (InspectorSheet) -> Unit,
    onCloseSheet: () -> Unit,
    onSelectObject: (String?) -> Unit,
    onSetTransformTool: (TransformTool) -> Unit,
    onNudgeObject: (Char, Float) -> Unit,
    onCameraMoved: (Vec3) -> Unit,
    onArPlacedChange: (Boolean) -> Unit,
    onAddPrimitive: (ObjectType) -> Unit,
    onAddText: (String) -> Unit,
    onOpenDrawing: () -> Unit,
    onAddPrefab: (String) -> Unit,
    onImportGlb: () -> Unit,
    onUpdateObject: (Boolean, (SceneObject) -> SceneObject) -> Unit,
    onDuplicateObject: () -> Unit,
    onDeleteObject: () -> Unit,
    onAddBehaviorTemplate: (String) -> Unit,
    onUpdateBehavior: (String, (Behavior) -> Behavior) -> Unit,
    onDeleteBehavior: (String) -> Unit,
    onAddAction: (String, ActionType) -> Unit,
    onDeleteAction: (String, String) -> Unit,
    onTestBehavior: (Behavior) -> Unit,
    onSwitchScene: (String) -> Unit,
    onAddScene: (String, AnchorType) -> Unit,
    onDeleteScene: (String) -> Unit,
    onUpdateSceneSettings: (Boolean?, Float?, AnchorType?) -> Unit,
    onExportProject: () -> Unit
) {
    val project = state.currentProject ?: return
    val scene = state.activeScene ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RcColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Main 3D / AR Viewport
        if (state.viewMode == ViewMode.EDITOR_3D) {
            Editor3DViewport(
                scene = scene,
                selectedObjectId = state.selectedObjectId,
                isPlaying = state.isPlaying,
                onSelectObject = onSelectObject,
                onCameraMoved = onCameraMoved,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ArLiveViewport(
                scene = scene,
                selectedObjectId = state.selectedObjectId,
                isPlaying = state.isPlaying,
                arPlaced = state.arPlaced,
                onArPlacedChange = onArPlacedChange,
                onSelectObject = onSelectObject,
                onCameraMoved = onCameraMoved,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Top Reality Composer Toolbar
        TopEditorToolbar(
            state = state,
            onBack = onBackToProjects,
            onUndo = onUndo,
            onRedo = onRedo,
            onTogglePlay = onTogglePlay,
            onToggleViewMode = onToggleViewMode,
            onToggleSnap = onToggleSnap,
            onToggleSheet = onToggleSheet,
            onOpenDrawing = onOpenDrawing,
            onExportProject = onExportProject,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 3. Scene HUD Pill (Top-Left below toolbar)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 64.dp, start = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RcColors.SurfaceTranslucent)
                .border(1.dp, RcColors.Border.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state.isPlaying) RcColors.PlayGreen else RcColors.AccentBlue)
            )
            Text(
                text = if (state.isPlaying) "ИГРАТЬ • ${scene.name}" else "${scene.name} • ${scene.anchorType.displayNameRu}",
                color = RcColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // 4. Status Toast Banner
        AnimatedVisibility(
            visible = state.statusBanner != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        ) {
            state.statusBanner?.let { msg ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .border(1.dp, RcColors.AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 5. Bottom Controls (only when no sheet is open and not playing)
        if (state.activeSheet == InspectorSheet.NONE && !state.isPlaying) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Quick Object Hierarchy Chips
                if (scene.objects.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(scene.objects, key = { it.id }) { obj ->
                            val selected = obj.id == state.selectedObjectId
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selected) RcColors.AccentBlue
                                        else RcColors.SurfaceTranslucent
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) RcColors.AccentBlue else RcColors.Border,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onSelectObject(obj.id) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(obj.material.colorHex))
                                )
                                Text(
                                    text = obj.name,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Precision X / Y / Z Axis Gizmo Bar when an object is selected
                if (state.selectedObject != null) {
                    AxisGizmoControlBar(
                        activeTool = state.activeTool,
                        onSetTool = onSetTransformTool,
                        onNudge = onNudgeObject,
                        onOpenProperties = { onToggleSheet(InspectorSheet.PROPERTIES) }
                    )
                }
            }
        }

        // 6. Sliding Inspector Sheets
        AnimatedVisibility(
            visible = state.activeSheet != InspectorSheet.NONE,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            when (state.activeSheet) {
                InspectorSheet.ADD_OBJECT -> AddObjectSheet(
                    onAddPrimitive = onAddPrimitive,
                    onAddText = onAddText,
                    onOpenDrawing = onOpenDrawing,
                    onAddPrefab = onAddPrefab,
                    onImportGlb = onImportGlb,
                    onClose = onCloseSheet
                )
                InspectorSheet.PROPERTIES -> PropertiesSheet(
                    selectedObject = state.selectedObject,
                    onUpdateObject = onUpdateObject,
                    onDuplicate = onDuplicateObject,
                    onDelete = onDeleteObject,
                    onClose = onCloseSheet
                )
                InspectorSheet.BEHAVIORS -> BehaviorsSheet(
                    scene = scene,
                    allScenes = project.scenes,
                    onAddTemplate = onAddBehaviorTemplate,
                    onUpdateBehavior = onUpdateBehavior,
                    onDeleteBehavior = onDeleteBehavior,
                    onAddAction = onAddAction,
                    onDeleteAction = onDeleteAction,
                    onTestBehavior = onTestBehavior,
                    onClose = onCloseSheet
                )
                InspectorSheet.SCENES -> ScenesSheet(
                    project = project,
                    onSwitchScene = onSwitchScene,
                    onAddScene = onAddScene,
                    onDeleteScene = onDeleteScene,
                    onUpdateSettings = onUpdateSceneSettings,
                    onClose = onCloseSheet
                )
                InspectorSheet.NONE -> Unit
            }
        }
    }
}

@Composable
private fun TopEditorToolbar(
    state: EditorUiState,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleViewMode: () -> Unit,
    onToggleSnap: () -> Unit,
    onToggleSheet: (InspectorSheet) -> Unit,
    onOpenDrawing: () -> Unit,
    onExportProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RcColors.SurfaceTranslucent)
            .border(0.5.dp, RcColors.Border)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ToolbarIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            label = "Проекты",
            active = false,
            onClick = onBack
        )

        ToolbarIconButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            label = null,
            active = false,
            enabled = state.canUndo && !state.isPlaying,
            onClick = onUndo
        )
        ToolbarIconButton(
            icon = Icons.AutoMirrored.Filled.Redo,
            label = null,
            active = false,
            enabled = state.canRedo && !state.isPlaying,
            onClick = onRedo
        )

        Spacer(Modifier.width(4.dp))

        // Play / Stop Button
        Button(
            onClick = onTogglePlay,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isPlaying) RcColors.DangerRed else RcColors.PlayGreen
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (state.isPlaying) Color.White else Color.Black,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (state.isPlaying) "Стоп" else "Играть",
                color = if (state.isPlaying) Color.White else Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // AR Camera / 3D Toggle
        ToolbarIconButton(
            icon = if (state.viewMode == ViewMode.AR_LIVE) Icons.Default.ViewInAr else Icons.Default.CameraAlt,
            label = if (state.viewMode == ViewMode.AR_LIVE) "AR Камера" else "Камера AR",
            active = state.viewMode == ViewMode.AR_LIVE,
            onClick = onToggleViewMode
        )

        Spacer(Modifier.width(4.dp))

        ToolbarIconButton(
            icon = Icons.Default.GridOn,
            label = null,
            active = state.snapEnabled,
            enabled = !state.isPlaying,
            onClick = onToggleSnap
        )

        ToolbarIconButton(
            icon = Icons.Default.Add,
            label = null,
            active = state.activeSheet == InspectorSheet.ADD_OBJECT,
            enabled = !state.isPlaying,
            onClick = { onToggleSheet(InspectorSheet.ADD_OBJECT) }
        )

        ToolbarIconButton(
            icon = Icons.Default.Brush,
            label = "Рисовать",
            active = false,
            enabled = !state.isPlaying,
            onClick = onOpenDrawing
        )

        ToolbarIconButton(
            icon = Icons.Default.Bolt,
            label = "Поведения",
            active = state.activeSheet == InspectorSheet.BEHAVIORS,
            enabled = !state.isPlaying,
            onClick = { onToggleSheet(InspectorSheet.BEHAVIORS) }
        )

        ToolbarIconButton(
            icon = Icons.Default.Tune,
            label = "Свойства",
            active = state.activeSheet == InspectorSheet.PROPERTIES,
            enabled = !state.isPlaying,
            onClick = { onToggleSheet(InspectorSheet.PROPERTIES) }
        )

        ToolbarIconButton(
            icon = Icons.Default.Layers,
            label = "Сцены",
            active = state.activeSheet == InspectorSheet.SCENES,
            enabled = !state.isPlaying,
            onClick = { onToggleSheet(InspectorSheet.SCENES) }
        )

        ToolbarIconButton(
            icon = Icons.Default.Share,
            label = null,
            active = false,
            onClick = onExportProject
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    label: String?,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = when {
        active -> RcColors.AccentBlue
        else -> RcColors.SurfaceElevated
    }
    val fg = when {
        !enabled -> RcColors.TextTertiary
        active -> Color.White
        else -> RcColors.TextPrimary
    }

    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, if (active) RcColors.AccentBlue else RcColors.Border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (label != null) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(16.dp))
        if (label != null) {
            Text(
                text = label,
                color = fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AxisGizmoControlBar(
    activeTool: TransformTool,
    onSetTool: (TransformTool) -> Unit,
    onNudge: (Char, Float) -> Unit,
    onOpenProperties: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RcColors.SurfaceTranslucent)
            .border(1.dp, RcColors.Border, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mode tabs: Move / Rotate / Scale + Properties shortcut
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(TransformTool.MOVE, TransformTool.ROTATE, TransformTool.SCALE).forEach { tool ->
                    val selected = activeTool == tool
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) RcColors.AccentBlue else RcColors.SurfaceElevated)
                            .clickable { onSetTool(tool) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = tool.displayNameRu,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            TextButton(
                onClick = onOpenProperties,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Инспектор", color = RcColors.AccentBlue, fontSize = 12.sp)
            }
        }

        // X / Y / Z Nudge Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AxisNudgePair("X", RcColors.AxisX, { onNudge('X', -1f) }, { onNudge('X', 1f) })
            AxisNudgePair("Y", RcColors.AxisY, { onNudge('Y', -1f) }, { onNudge('Y', 1f) })
            AxisNudgePair("Z", RcColors.AxisZ, { onNudge('Z', -1f) }, { onNudge('Z', 1f) })
        }
    }
}

@Composable
private fun AxisNudgePair(
    axis: String,
    color: Color,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RcColors.SurfaceElevated)
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onMinus, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Default.Remove, contentDescription = "-$axis", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(axis, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        IconButton(onClick = onPlus, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Default.Add, contentDescription = "+$axis", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
