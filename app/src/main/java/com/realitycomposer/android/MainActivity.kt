package com.realitycomposer.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.realitycomposer.android.ui.scanner.ObjectScannerScreen
import com.realitycomposer.android.ui.scanner.ScannedModelViewerScreen
import com.realitycomposer.android.ui.screens.EditorScreen
import com.realitycomposer.android.ui.theme.RealityComposerTheme
import com.realitycomposer.android.viewmodel.EditorViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RealityComposerTheme {
                val vm: EditorViewModel = viewModel()
                val state by vm.uiState.collectAsState()

                val glbPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        vm.importGlbModel(uri)
                    }
                }

                fun shareFile(file: File, mimeType: String, title: String) {
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "$packageName.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, title))
                }

                when {
                    state.isScanningObject -> {
                        ObjectScannerScreen(
                            onCancel = vm::cancelObjectScan,
                            onScanCompleted = vm::onScanCompleted,
                            onExportCameraImage = { file, mime ->
                                shareFile(file, mime, "Экспорт фото с камеры")
                            }
                        )
                    }

                    state.activeScanViewer != null -> {
                        val scan = state.activeScanViewer!!
                        ScannedModelViewerScreen(
                            scan = scan,
                            onBackToProjects = vm::closeScannedObjectViewer,
                            onExportObj = { file ->
                                shareFile(file, "text/plain", "Экспорт 3D-модели (.OBJ)")
                            },
                            onOpenInArEditor = vm::closeScannedObjectViewer
                        )
                    }

                    state.currentProject == null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFD4AF37))
                        }
                    }

                    else -> {
                        // Launches straight into the Live Camera with ALL tools!
                        EditorScreen(
                            state = state,
                            onUndo = vm::undo,
                            onRedo = vm::redo,
                            onTogglePlay = vm::togglePlayMode,
                            onToggleSnap = vm::toggleSnap,
                            onToggleSheet = vm::toggleSheet,
                            onCloseSheet = vm::closeSheet,
                            onSelectObject = vm::selectObject,
                            onSetTransformTool = vm::setTransformTool,
                            onNudgeObject = vm::nudgeSelectedObject,
                            onCameraMoved = vm::updateCameraPosition,
                            onAddPrimitive = vm::addPrimitive,
                            onAddText = vm::addTextObject,
                            onAddPrefab = vm::addStarterPrefab,
                            onImportGlb = {
                                glbPicker.launch(
                                    arrayOf(
                                        "model/gltf-binary",
                                        "model/gltf+json",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            onUpdateObject = vm::updateSelectedObject,
                            onDuplicateObject = vm::duplicateSelectedObject,
                            onDeleteObject = vm::deleteSelectedObject,
                            onAddBehaviorTemplate = vm::addBehaviorTemplate,
                            onUpdateBehavior = vm::updateBehavior,
                            onDeleteBehavior = vm::deleteBehavior,
                            onAddAction = vm::addActionToBehavior,
                            onDeleteAction = vm::deleteActionFromBehavior,
                            onTestBehavior = vm::testBehavior,
                            onStartObjectScan = vm::startObjectScan,
                            onExportCameraImage = { file, mime ->
                                shareFile(file, mime, "Экспорт снимка с камеры")
                            },
                            onShowMessage = vm::showBanner
                        )
                    }
                }
            }
        }
    }
}
