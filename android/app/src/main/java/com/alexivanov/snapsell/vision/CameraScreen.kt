package com.alexivanov.snapsell.vision

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executor

/** What the chrome drawn over the preview needs to know. */
class CameraControls(val capture: () -> Unit, val capturing: Boolean)

/**
 * CameraX preview + capture. Asks for CAMERA at runtime with the Activity
 * Result API. The screen chrome (shutter, brackets, back arrow) is supplied
 * by [overlay]; [denied] is shown when the permission was refused. On
 * capture the full-resolution JPEG is written to [photoStore], rotated
 * upright, and handed to [onCaptured].
 */
@Composable
fun CameraScreen(
    photoStore: PhotoStore,
    onCaptured: (File) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    denied: @Composable (requestAgain: () -> Unit) -> Unit,
    overlay: @Composable BoxScope.(CameraControls) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var wasDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        wasDenied = !granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    when {
        hasPermission -> CameraPreview(photoStore, onCaptured, onError, modifier, overlay)
        wasDenied -> denied { launcher.launch(Manifest.permission.CAMERA) }
        else -> Box(modifier.fillMaxSize())
    }
}

@Composable
private fun CameraPreview(
    photoStore: PhotoStore,
    onCaptured: (File) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
    overlay: @Composable BoxScope.(CameraControls) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor: Executor = remember { ContextCompat.getMainExecutor(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    var capturing by remember { mutableStateOf(false) }

    val capture: () -> Unit = capture@{
        if (capturing) return@capture
        capturing = true
        val file = photoStore.newPhotoFile()
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            options,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Rotation fix is a disk + decode round trip; do it off the main thread.
                    Thread {
                        runCatching { photoStore.normalizeRotation(file) }
                        mainExecutor.execute {
                            capturing = false
                            onCaptured(file)
                        }
                    }.start()
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    onError("Capture failed: ${exception.message}")
                }
            },
        )
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                        } catch (e: Exception) {
                            onError("Could not start the camera: ${e.message}")
                        }
                    }, mainExecutor)
                }
            },
        )
        overlay(CameraControls(capture, capturing))
    }
}
