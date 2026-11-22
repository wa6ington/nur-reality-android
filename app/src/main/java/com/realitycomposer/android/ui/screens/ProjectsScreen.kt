package com.realitycomposer.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realitycomposer.android.R
import com.realitycomposer.android.model.AnchorType
import com.realitycomposer.android.model.RealityProject
import com.realitycomposer.android.model.ScannedObject
import com.realitycomposer.android.ui.theme.RcColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(
    projects: List<RealityProject>,
    scannedObjects: List<ScannedObject>,
    onStartObjectScan: () -> Unit,
    onOpenDrawingStudio: () -> Unit,
    onOpenScannedObject: (ScannedObject) -> Unit,
    onDeleteScannedObject: (String) -> Unit,
    onOpenProject: (RealityProject) -> Unit,
    onCreateProject: (String, AnchorType) -> Unit,
    onDuplicateProject: (RealityProject) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RcColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_logo),
                        contentDescription = "NUR Advertising",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column {
                    Text(
                        text = "NUR Reality",
                        color = RcColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Автор: Sanzhar • AR & 3D Studio",
                        color = Color(0xFFD4AF37),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Новый проект", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }

        HorizontalDivider(color = RcColors.Border.copy(alpha = 0.5f))

        // Projects Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Hero Card: 3D Object Capture Scanner (Camera)
            item {
                Box(
                    modifier = Modifier
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1F2B3D), Color(0xFF141820))
                            )
                        )
                        .border(1.5.dp, Color(0xFFFFD60A).copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onStartObjectScan),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD60A).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color(0xFFFFD60A),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "3D-Сканер камерой",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Захват объекта 360° -> 3D-модель (.OBJ)",
                            color = RcColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Hero Card: Drawing Studio (PNG / JPG export)
            item {
                Box(
                    modifier = Modifier
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF231F35), Color(0xFF15141C))
                            )
                        )
                        .border(1.5.dp, Color(0xFFBF5AF2).copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenDrawingStudio),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFBF5AF2).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Brush,
                                contentDescription = null,
                                tint = Color(0xFFBF5AF2),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "Рисование (PNG / JPG)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Свободный скетч и экспорт в PNG / JPG",
                            color = RcColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Create AR Scene card
            item {
                Box(
                    modifier = Modifier
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(RcColors.Surface)
                        .border(1.5.dp, RcColors.AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable { showCreateDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(RcColors.AccentBlue.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = RcColors.AccentBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "Новая AR-сцена",
                            color = RcColors.AccentBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // Saved 3D Scans
            items(scannedObjects, key = { "scan_${it.id}" }) { scan ->
                ScannedObjectCard(
                    scan = scan,
                    onOpen = { onOpenScannedObject(scan) },
                    onDelete = { onDeleteScannedObject(scan.id) }
                )
            }

            items(projects, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    onOpen = { onOpenProject(project) },
                    onDuplicate = { onDuplicateProject(project) },
                    onDelete = { onDeleteProject(project.id) }
                )
            }
        }

        // Brand & Author Footer
        HorizontalDivider(color = RcColors.Border.copy(alpha = 0.4f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RcColors.Surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "NUR ADVERTISING  •  Автор: Sanzhar",
                color = Color(0xFFD4AF37),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            )
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, anchor ->
                showCreateDialog = false
                onCreateProject(name, anchor)
            }
        )
    }
}

@Composable
private fun ProjectCard(
    project: RealityProject,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateStr = remember(project.updatedAt) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(project.updatedAt))
    }
    val scene = project.activeScene

    Column(
        modifier = Modifier
            .height(190.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(RcColors.Surface)
            .border(1.dp, RcColors.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
    ) {
        // 3D Stage Preview Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF24262E), Color(0xFF17181C))
                    )
                )
                .padding(12.dp)
        ) {
            Icon(
                imageVector = anchorIcon(scene.anchorType),
                contentDescription = null,
                tint = RcColors.AccentBlue.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.Center)
            )

            // Anchor Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = scene.anchorType.displayNameRu,
                    color = RcColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // More Menu
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Меню",
                        tint = RcColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = RcColors.SurfaceElevated
                ) {
                    DropdownMenuItem(
                        text = { Text("Дублировать", color = RcColors.TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = RcColors.TextSecondary) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить", color = RcColors.DangerRed) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = RcColors.DangerRed) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }

        // Info Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = project.name,
                color = RcColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Сцен: ${project.scenes.size} • Объектов: ${scene.objects.size} • $dateStr",
                color = RcColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, AnchorType) -> Unit
) {
    var projectName by remember { mutableStateOf("Новая AR-сцена") }
    var selectedAnchor by remember { mutableStateOf(AnchorType.HORIZONTAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RcColors.Surface,
        title = {
            Text(
                text = "Выберите якорь сцены",
                color = RcColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Название проекта") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RcColors.AccentBlue,
                        unfocusedBorderColor = RcColors.Border,
                        focusedTextColor = RcColors.TextPrimary,
                        unfocusedTextColor = RcColors.TextPrimary
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnchorType.entries.forEach { anchor ->
                        val selected = anchor == selectedAnchor
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) RcColors.AccentBlue.copy(alpha = 0.18f)
                                    else RcColors.SurfaceElevated
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) RcColors.AccentBlue else RcColors.Border,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedAnchor = anchor }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = anchorIcon(anchor),
                                contentDescription = null,
                                tint = if (selected) RcColors.AccentBlue else RcColors.TextSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = anchor.displayNameRu,
                                    color = RcColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = anchor.subtitleRu,
                                    color = RcColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(projectName, selectedAnchor) },
                colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue)
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = RcColors.TextSecondary)
            }
        }
    )
}

fun anchorIcon(type: AnchorType): ImageVector = when (type) {
    AnchorType.HORIZONTAL -> Icons.Default.TableRestaurant
    AnchorType.VERTICAL -> Icons.Default.CropPortrait
    AnchorType.IMAGE -> Icons.Default.Image
    AnchorType.FACE -> Icons.Default.Face
}

@Composable
private fun ScannedObjectCard(
    scan: ScannedObject,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(scan.createdAt) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(scan.createdAt))
    }

    Column(
        modifier = Modifier
            .height(190.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(RcColors.Surface)
            .border(1.dp, Color(0xFFFFD60A).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFF5F5F7))
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ViewInAr,
                contentDescription = null,
                tint = Color(0xFF1C1C1E),
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.Center)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color(0xFF1C1C1E).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("3D-Скан", color = Color(0xFFFFD60A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = RcColors.DangerRed, modifier = Modifier.size(18.dp))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = scan.name,
                color = RcColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Точек: ${scan.points.size} • Кадров: ${scan.capturedFramesCount} • $dateStr",
                color = RcColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
