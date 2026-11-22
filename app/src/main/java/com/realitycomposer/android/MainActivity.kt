package com.realitycomposer.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.google.ar.core.ArCoreApk
import com.realitycomposer.android.data.ProjectRepository
import com.realitycomposer.android.ui.ar.RealArCameraScreen
import com.realitycomposer.android.ui.camera.Camera3DStudioScreen
import com.realitycomposer.android.ui.theme.RealityComposerTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RealityComposerTheme {
                val repo = remember { ProjectRepository(applicationContext) }
                val scope = rememberCoroutineScope()
                var pendingGlbCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

                val glbPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            val imported = repo.importGlbFromUri(uri)
                            if (imported != null) {
                                pendingGlbCallback?.invoke(imported.second)
                            }
                            pendingGlbCallback = null
                        }
                    }
                }

                var arCoreAvailable by remember {
                    mutableStateOf(
                        runCatching {
                            val avail = ArCoreApk.getInstance().checkAvailability(applicationContext)
                            avail == ArCoreApk.Availability.SUPPORTED_INSTALLED ||
                                avail == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD ||
                                avail == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED
                        }.getOrDefault(false)
                    )
                }

                fun shareImage(file: File, mimeType: String) {
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
                    startActivity(Intent.createChooser(shareIntent, "Экспорт AR-снимка"))
                }

                if (arCoreAvailable) {
                    RealArCameraScreen(
                        onImportGlb = { onImportedPath ->
                            pendingGlbCallback = onImportedPath
                            glbPicker.launch(
                                arrayOf(
                                    "model/gltf-binary",
                                    "model/gltf+json",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        onExportImage = ::shareImage,
                        onArUnsupported = { arCoreAvailable = false }
                    )
                } else {
                    // Automatic live CameraX + 3D Objects fallback for devices without ARCore (e.g., Galaxy A21s)
                    Camera3DStudioScreen(
                        onExportImage = ::shareImage
                    )
                }
            }
        }
    }
}
