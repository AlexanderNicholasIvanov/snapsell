package com.alexivanov.snapsell.ui.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.R
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.LoadingBox
import com.alexivanov.snapsell.ui.common.LucideIcon
import com.alexivanov.snapsell.ui.common.PrimaryButton
import com.alexivanov.snapsell.ui.common.SnapTopBar
import com.alexivanov.snapsell.ui.common.Spinner
import com.alexivanov.snapsell.ui.theme.SnapType
import com.alexivanov.snapsell.ui.theme.snapColors
import com.alexivanov.snapsell.vision.CameraScreen
import com.alexivanov.snapsell.vision.CaptureSession
import com.alexivanov.snapsell.vision.PhotoStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-bleed camera. Nothing else on screen. */
@Composable
fun CaptureScreen(container: AppContainer, onReview: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val c = snapColors
    var preparing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (preparing) {
        Box(Modifier.fillMaxSize().background(c.bg)) { LoadingBox(label = "Preparing photo") }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                        error = "Could not read the photo. Try again."
                    } else {
                        container.captureSession.current?.workBitmap?.recycle()
                        container.captureSession.current = CaptureSession.Photo(file, bitmap)
                        onReview()
                    }
                }
            },
            onError = { error = it },
            denied = { requestAgain ->
                Column(Modifier.fillMaxSize().background(c.bg)) {
                    SnapTopBar(title = "Capture", onBack = onBack)
                    Column(Modifier.padding(24.dp)) {
                        Text("Camera access needed", style = SnapType.sectionHead, color = c.text)
                        Text("SnapSell photographs your items with the camera. Nothing is uploaded until you confirm what to sell.", style = SnapType.body, color = c.text.copy(alpha = 0.75f), modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                        PrimaryButton("Allow camera", onClick = requestAgain, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
        ) { controls ->
            // Four 28dp white corner brackets, 3dp stroke, inset 20dp; the top pair at y = 72dp.
            Canvas(Modifier.fillMaxSize().statusBarsPadding()) {
                val stroke = 3.dp.toPx()
                val len = 28.dp.toPx()
                val inset = 20.dp.toPx()
                val top = 72.dp.toPx()
                val bottom = size.height - inset - 120.dp.toPx()
                val left = inset
                val right = size.width - inset
                val white = Color.White
                fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(white, Offset(x, y), Offset(x + dx * len, y), stroke, StrokeCap.Square)
                    drawLine(white, Offset(x, y), Offset(x, y + dy * len), stroke, StrokeCap.Square)
                }
                bracket(left, top, 1f, 1f)
                bracket(right, top, -1f, 1f)
                bracket(left, bottom, 1f, -1f)
                bracket(right, bottom, -1f, -1f)
            }

            // Back arrow, white, in a 52dp box.
            Box(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 4.dp, top = 4.dp)
                    .size(52.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                LucideIcon(R.drawable.ic_lucide_arrow_left, "Back", size = 24.dp, tint = Color.White)
            }

            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorText(error, Modifier.padding(bottom = 12.dp))
                Text(
                    "Point at one item, or a pile".uppercase(),
                    style = SnapType.bodySmall.copy(letterSpacing = 0.06.em),
                    color = Color.White.copy(alpha = 0.9f),
                    // Sits just above the shutter, whose top edge is 100dp from the bottom.
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                // Shutter: 76dp circle, 3dp white ring, 6dp gap, white fill, 30dp accent glyph.
                Box(
                    Modifier
                        .padding(bottom = 24.dp)
                        .size(76.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !controls.capturing, onClick = controls.capture),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val ring = 3.dp.toPx()
                        drawCircle(Color.White, radius = size.minDimension / 2 - ring / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(ring))
                        drawCircle(Color.White, radius = size.minDimension / 2 - ring - 6.dp.toPx())
                    }
                    if (controls.capturing) {
                        Spinner(size = 30.dp, color = c.accent)
                    } else {
                        // The shutter fill is white in both themes, so the glyph keeps the light accent.
                        LucideIcon(R.drawable.ic_lucide_camera, "Take photo", size = 30.dp, tint = ShutterGlyph)
                    }
                }
            }
        }
    }
}

private val ShutterGlyph = Color(0xFFEC3013)
