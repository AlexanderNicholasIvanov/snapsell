package com.alexivanov.snapsell.ui.review

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.LoadingBox
import com.alexivanov.snapsell.ui.common.SnapTopBar

@Composable
fun ReviewScreen(
    container: AppContainer,
    onRetake: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val vm: ReviewViewModel = viewModel { ReviewViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { SnapTopBar(title = "Review items", onBack = onBack) },
        // Pinned so the primary action is never below the fold on a tall photo.
        bottomBar = {
            if (state.bitmap != null && !state.committing) {
                Button(
                    onClick = { vm.identify(onConfirm) },
                    enabled = state.selectedCount > 0 && !state.segmenting,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("Identify ${state.selectedCount} item${if (state.selectedCount == 1) "" else "s"}")
                }
            }
        },
    ) { padding ->
        val bitmap = state.bitmap
        if (bitmap == null) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                ErrorText(state.error ?: "No photo.")
                Button(onClick = onRetake, modifier = Modifier.padding(top = 16.dp)) { Text("Take a photo") }
            }
            return@Scaffold
        }
        if (state.committing) {
            LoadingBox(modifier = Modifier.padding(padding), label = "Cutting out ${state.selectedCount} item(s)…")
            return@Scaffold
        }

        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (state.segmenting) LinearProgressIndicator(Modifier.fillMaxWidth())

            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            var dragStart by remember { mutableStateOf<Offset?>(null) }
            var dragEnd by remember { mutableStateOf<Offset?>(null) }
            val primary = MaterialTheme.colorScheme.primary
            val outline = Color(0xFF00C878)
            val dim = Color.White.copy(alpha = 0.6f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .pointerInput(state.manualMode) {
                        val scale = size.width.toFloat() / bitmap.width
                        if (state.manualMode) {
                            detectDragGestures(
                                onDragStart = { dragStart = it; dragEnd = it },
                                onDrag = { change, _ -> dragEnd = change.position },
                                onDragCancel = { dragStart = null; dragEnd = null },
                                onDragEnd = {
                                    val s = dragStart
                                    val e = dragEnd
                                    if (s != null && e != null) {
                                        vm.addManualRect(
                                            Rect(
                                                (s.x / scale).toInt(), (s.y / scale).toInt(),
                                                (e.x / scale).toInt(), (e.y / scale).toInt(),
                                            ),
                                        )
                                    }
                                    dragStart = null
                                    dragEnd = null
                                },
                            )
                        } else {
                            detectTapGestures { pos -> vm.tapAt((pos.x / scale).toInt(), (pos.y / scale).toInt()) }
                        }
                    },
            ) {
                val scale = size.width / bitmap.width
                drawImage(imageBitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                    val stroke = Stroke(width = 3f / scale)
                    val labelPaint = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 28f / scale
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                    state.segments.forEach { seg ->
                        val selected = seg.index in state.selected
                        seg.maskOverlay?.let { overlay ->
                            drawImage(
                                overlay.asImageBitmap(),
                                topLeft = Offset(seg.startX.toFloat(), seg.startY.toFloat()),
                                alpha = if (selected) 1f else 0.35f,
                            )
                        }
                        drawPath(seg.maskOutline.asComposePath(), color = if (selected) outline else dim, style = stroke)
                        drawRoundRect(
                            color = if (selected) primary else dim,
                            topLeft = Offset(seg.startX.toFloat(), seg.startY.toFloat()),
                            size = Size(seg.width.toFloat(), seg.height.toFloat()),
                            cornerRadius = CornerRadius(12f / scale),
                            style = Stroke(width = (if (selected) 4f else 2f) / scale),
                        )
                        // Number badge in the top-left corner of the box.
                        val badge = 22f / scale
                        drawCircle(
                            color = if (selected) primary else Color.Gray,
                            radius = badge,
                            center = Offset(seg.startX + badge, seg.startY + badge),
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "${seg.index + 1}",
                            seg.startX + badge - labelPaint.measureText("${seg.index + 1}") / 2,
                            seg.startY + badge + labelPaint.textSize / 3,
                            labelPaint,
                        )
                    }
                    val s = dragStart
                    val e = dragEnd
                    if (s != null && e != null) {
                        val left = minOf(s.x, e.x) / scale
                        val top = minOf(s.y, e.y) / scale
                        drawRect(
                            color = primary,
                            topLeft = Offset(left, top),
                            size = Size(kotlin.math.abs(e.x - s.x) / scale, kotlin.math.abs(e.y - s.y) / scale),
                            style = Stroke(width = 4f / scale),
                        )
                    }
                }
            }

            Column(Modifier.padding(16.dp)) {
                ErrorText(state.error)
                Text(
                    if (state.manualMode) "Drag a box around an item." else "Tap an item to select or deselect it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                if (state.segments.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.segments, key = { it.index }) { seg ->
                            FilterChip(
                                selected = seg.index in state.selected,
                                onClick = { vm.toggle(seg.index) },
                                label = { Text("Item ${seg.index + 1}${if (seg.manual) " (box)" else ""}") },
                            )
                        }
                    }
                    // Per-item retake: drop that box and shoot again, keeping the others.
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.segments, key = { "retake-${it.index}" }) { seg ->
                            TextButton(onClick = { vm.retake(seg.index) { onRetake() } }) { Text("Retake ${seg.index + 1}") }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.setManualMode(!state.manualMode) }, modifier = Modifier.weight(1f)) {
                        Text(if (state.manualMode) "Cancel box" else "Add item")
                    }
                    OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("Retake photo") }
                }
            }
        }
    }
}
