package com.realitycomposer.android.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realitycomposer.android.model.ObjectType
import com.realitycomposer.android.ui.theme.RcColors

private data class LibraryItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun AddObjectSheet(
    onAddPrimitive: (ObjectType) -> Unit,
    onAddText: (String) -> Unit,
    onOpenDrawing: () -> Unit,
    onAddPrefab: (String) -> Unit,
    onImportGlb: () -> Unit,
    onClose: () -> Unit
) {
    var showTextDialog by remember { mutableStateOf(false) }

    val primitives = listOf(
        LibraryItem("3D-Текст", Icons.Default.TextFields) { showTextDialog = true },
        LibraryItem("Рисовать", Icons.Default.Brush) { onOpenDrawing() },
        LibraryItem("Куб", Icons.Default.ViewInAr) { onAddPrimitive(ObjectType.CUBE) },
        LibraryItem("Сфера", Icons.Default.Circle) { onAddPrimitive(ObjectType.SPHERE) },
        LibraryItem("Цилиндр", Icons.Default.PanoramaVertical) { onAddPrimitive(ObjectType.CYLINDER) },
        LibraryItem("Конус", Icons.Default.ChangeHistory) { onAddPrimitive(ObjectType.CONE) },
        LibraryItem("Капсула", Icons.Default.Medication) { onAddPrimitive(ObjectType.CAPSULE) },
        LibraryItem("Плоскость", Icons.Default.Layers) { onAddPrimitive(ObjectType.PLANE) }
    )

    val prefabs = listOf(
        LibraryItem("Билборд", Icons.Default.FeaturedVideo) { onAddPrefab("billboard") },
        LibraryItem("Куб + Текст", Icons.Default.Inventory2) { onAddPrefab("branded_cube") },
        LibraryItem("Арка", Icons.Default.AccountBalance) { onAddPrefab("arch") },
        LibraryItem("Стол", Icons.Default.TableRestaurant) { onAddPrefab("table") },
        LibraryItem("Маяк", Icons.Default.CellTower) { onAddPrefab("lighthouse") }
    )

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
                text = "Библиотека объектов",
                color = RcColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = RcColors.TextSecondary)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Import GLB / GLTF button
        Button(
            onClick = onImportGlb,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Импортировать 3D-модель (.glb / .gltf)", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Базовые фигуры",
            color = RcColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 92.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(primitives) { item ->
                LibraryTile(item)
            }
            items(prefabs) { item ->
                LibraryTile(item)
            }
        }
    }

    if (showTextDialog) {
        var textInput by remember { mutableStateOf("NUR ADVERTISING") }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            containerColor = RcColors.Surface,
            title = {
                Text("Добавить 3D-Текст", color = RcColors.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Введите текст") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RcColors.AccentBlue,
                        unfocusedBorderColor = RcColors.Border,
                        focusedTextColor = RcColors.TextPrimary,
                        unfocusedTextColor = RcColors.TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTextDialog = false
                        onAddText(textInput.ifBlank { "Текст" })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RcColors.AccentBlue)
                ) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("Отмена", color = RcColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun LibraryTile(item: LibraryItem) {
    Column(
        modifier = Modifier
            .height(86.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RcColors.SurfaceElevated)
            .border(1.dp, RcColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = item.onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = RcColors.AccentBlue,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            color = RcColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
