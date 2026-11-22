package com.realitycomposer.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.realitycomposer.android.ui.drawing.DrawingStudioScreen
import com.realitycomposer.android.ui.scanner.ObjectScannerScreen
import com.realitycomposer.android.ui.scanner.ScannedModelViewerScreen
import com.realitycomposer.android.ui.screens.EditorScreen
import com.realitycomposer.android.ui.screens.ProjectsScreen
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
                    state.isDrawingStudioOpen -> {
                        DrawingStudioScreen(
                            canInsertIntoScene = state.currentProject != null,
                            onClose = vm::closeDrawingStudio,
                            onExportImage = { file, mime ->
                                shareFile(file, mime, "Экспорт рисунка")
                            },
                            onInsertDrawingIntoScene = { file ->
                                vm.addDrawingObject(file)
                            }
                        )
                    }

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
                            onOpenInArEditor = { vm.openScanInArEditor(scan) }
                        )
                    }

                    state.currentProject == null -> {
                        ProjectsScreen(
                            projects = state.projects,
                            scannedObjects = state.scannedObjects,
                            onStartObjectScan = vm::startObjectScan,
                            onOpenDrawingStudio = vm::openDrawingStudio,
                            onOpenScannedObject = vm::openScannedObject,
                            onDeleteScannedObject = vm::deleteScannedObject,
                            onOpenProject = vm::openProject,
                            onCreateProject = vm::createProject,
                            onDuplicateProject = vm::duplicateProject,
                            onDeleteProject = vm::deleteProject
                        )
                    }

                    else -> {
                        EditorScreen(
                            state = state,
                            onBackToProjects = vm::closeProject,
                            onUndo = vm::undo,
                            onRedo = vm::redo,
                            onTogglePlay = vm::togglePlayMode,
                            onToggleViewMode = vm::toggleViewMode,
                            onToggleSnap = vm::toggleSnap,
                            onToggleSheet = vm::toggleSheet,
                            onCloseSheet = vm::closeSheet,
                            onSelectObject = vm::selectObject,
                            onSetTransformTool = vm::setTransformTool,
                            onNudgeObject = vm::nudgeSelectedObject,
                            onCameraMoved = vm::updateCameraPosition,
                            onArPlacedChange = vm::setArPlaced,
                            onAddPrimitive = vm::addPrimitive,
                            onAddText = vm::addTextObject,
                            onOpenDrawing = vm::openDrawingStudio,
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
                            onSwitchScene = vm::switchScene,
                            onAddScene = vm::addScene,
                            onDeleteScene = vm::deleteScene,
                            onUpdateSceneSettings = vm::updateSceneSettings,
                            onExportProject = {
                                vm.exportCurrentProject { file ->
                                    shareFile(file, "application/json", "Экспорт сцены Reality Composer")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
