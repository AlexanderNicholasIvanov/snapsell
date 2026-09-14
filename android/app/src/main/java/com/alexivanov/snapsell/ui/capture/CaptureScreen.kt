package com.alexivanov.snapsell.ui.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.ui.common.LoadingBox
import com.alexivanov.snapsell.ui.common.SnapTopBar
import com.alexivanov.snapsell.vision.CameraScreen
import com.alexivanov.snapsell.vision.CaptureSession
import com.alexivanov.snapsell.vision.PhotoStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CaptureScreen(container: AppContainer, onReview: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var preparing by remember { mutableStateOf(false) }
    val pending = container.captureSession.pendingCutouts.size

    Scaffold(
        topBar = { SnapTopBar(title = if (pending > 0) "Capture ($pending kept)" else "Capture", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (preparing) {
                LoadingBox(label = "Preparing photo…")
            } else {
                CameraScreen(
                    photoStore = container.photoStore,
                    onCaptured = { file ->
                        preparing = true
                        scope.launch {
                            val bitmap = withContext(container.dispatchers.io) {
                                container.photoStore.decodeScaled(file, PhotoStore.WORK_LONG_EDGE)
                            }
                            if (bitmap == null) {
                                preparing = false
                                snackbar.showSnackbar("Could not read the photo. Try again.")
                            } else {
                                container.captureSession.current?.workBitmap?.recycle()
                                container.captureSession.current = CaptureSession.Photo(file, bitmap)
                                onReview()
                            }
                        }
                    },
                    onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                )
            }
        }
    }
}
