package com.alexivanov.snapsell.ui.review

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.R
import com.alexivanov.snapsell.ui.common.ErrorText
import com.alexivanov.snapsell.ui.common.GhostButton
import com.alexivanov.snapsell.ui.common.LoadingBox
import com.alexivanov.snapsell.ui.common.LucideIcon
import com.alexivanov.snapsell.ui.common.PrimaryButton
import com.alexivanov.snapsell.ui.common.SecondaryButton
import com.alexivanov.snapsell.ui.common.SnapBottomBar
import com.alexivanov.snapsell.ui.common.SnapTopBar
import com.alexivanov.snapsell.ui.common.Spinner
import com.alexivanov.snapsell.ui.theme.SnapType
import com.alexivanov.snapsell.ui.theme.snapColors
import com.alexivanov.snapsell.vision.Segment
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Where the photo lands inside its 4:3 box (fit, centred). */
private data class Fit(val scale: Float, val dx: Float, val dy: Float) {
    fun toBitmapX(x: Float) = (x - dx) / scale
    fun toBitmapY(y: Float) = (y - dy) / scale
}

private fun fitInto(bitmapW: Int, bitmapH: Int, boxW: Float, boxH: Float): Fit {
    val scale = min(boxW / bitmapW, boxH / bitmapH)
    return Fit(scale, (boxW - bitmapW * scale) / 2f, (boxH - bitmapH * scale) / 2f)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    container: AppContainer,
    onRetake: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val vm: ReviewViewModel = viewModel { ReviewViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = snapColors

    // Box mode: the dragged rectangle stays as a draft until "Place box" commits it.
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var draft by remember { mutableStateOf<Rect?>(null) }
    fun clearDraft() { dragStart = null; dragEnd = null; draft = null }

    Scaffold(
        containerColor = c.bg,
        topBar = { SnapTopBar(title = "Review items", onBack = onBack) },
        // Pinned so the primary action is never below the fold on a tall photo.
        bottomBar = {
            if (state.bitmap != null && !state.committing) {
                SnapBottomBar {
                    val n = state.selectedCount
                    PrimaryButton(
                        label = if (n == 0) "Select at least one object" else "Identify $n item${if (n == 1) "" else "s"}",
                        onClick = { vm.identify(onConfirm) },
                        enabled = n > 0 && !state.segmenting,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { padding ->
        val bitmap = state.bitmap
        if (bitmap == null) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                ErrorText(state.error ?: "No photo.")
                PrimaryButton("Take a photo", onClick = onRetake, modifier = Modifier.padding(top = 16.dp).fillMaxWidth())
            }
            return@Scaffold
        }
        if (state.committing) {
            LoadingBox(modifier = Modifier.padding(padding), label = "Cutting out ${state.selectedCount} object(s)")
            return@Scaffold
        }

        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (state.outlinerUnavailable) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().border(2.dp, c.accent).padding(14.dp)) {
                    Text("On-device outlining unavailable", style = SnapType.bodySmall.copy(fontWeight = FontWeight.ExtraBold), color = c.accent700)
                    Text(
                        "Your device can't run the object outliner. Draw a box around each thing you want to sell.",
                        style = SnapType.bodySmall,
                        color = c.text,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .border(1.dp, c.divider),
            ) {
                PhotoCanvas(
                    imageBitmap = imageBitmap,
                    bitmapW = bitmap.width,
                    bitmapH = bitmap.height,
                    segments = state.segments,
                    selected = state.selected,
                    manualMode = state.manualMode,
                    dragStart = dragStart,
                    dragEnd = dragEnd,
                    draft = draft,
                    onTap = { x, y -> vm.tapAt(x, y) },
                    onDragStart = { dragStart = it; dragEnd = it; draft = null },
                    onDrag = { dragEnd = it },
                    onDragEnd = { rect -> draft = rect; dragStart = null; dragEnd = null },
                    onDragCancel = { dragStart = null; dragEnd = null },
                )
                if (state.manualMode && draft == null && dragStart == null) {
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        Text("Drag a box around the object", style = SnapType.body.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
                    }
                }
                if (state.segmenting) {
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spinner(color = Color.White)
                            Text("  Finding objects", style = SnapType.bodySmall, color = Color.White)
                        }
                    }
                }
            }
            Text(
                "${state.segments.size} object${if (state.segments.size == 1) "" else "s"} found",
                style = SnapType.fieldLabel.copy(fontSize = 11.sp),
                color = c.text.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ErrorText(state.error)

                if (state.segments.isNotEmpty()) {
                    // Chip pairs: a 44dp "Object N" button and a 40dp retake button divided by a 2dp rule.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.segments.forEach { seg ->
                            val on = seg.index in state.selected
                            val fg = if (on) c.bg else c.text
                            Row(
                                Modifier
                                    .then(if (on) Modifier.background(c.accent).border(2.dp, c.accent) else Modifier.border(2.dp, c.divider))
                                    .height(44.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.fillMaxHeight().clickable { vm.toggle(seg.index) }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                    Text("Object ${seg.index + 1}", style = SnapType.chipLabelSelected.copy(fontSize = 13.sp), color = fg)
                                }
                                Box(Modifier.width(2.dp).fillMaxHeight().background(if (on) c.bg.copy(alpha = 0.5f) else c.divider))
                                Box(Modifier.size(40.dp).clickable { vm.retake(seg.index) { onRetake() } }, contentAlignment = Alignment.Center) {
                                    LucideIcon(R.drawable.ic_lucide_refresh_cw, "Retake object ${seg.index + 1}", size = 16.dp, tint = fg)
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.manualMode) {
                        SecondaryButton(
                            label = "Place box",
                            onClick = { draft?.let { vm.addManualRect(it) }; clearDraft() },
                            enabled = draft != null,
                            minHeight = 48.dp,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        SecondaryButton("Add item", onClick = { clearDraft(); vm.setManualMode(true) }, minHeight = 48.dp, modifier = Modifier.weight(1f))
                    }
                    SecondaryButton("Retake photo", onClick = onRetake, minHeight = 48.dp, modifier = Modifier.weight(1f))
                }
                if (state.manualMode && state.segments.isNotEmpty()) {
                    GhostButton("Cancel box", onClick = { clearDraft(); vm.setManualMode(false) }, minHeight = 36.dp)
                }
            }
        }
    }
}

/**
 * The photo, fit into its box, with bounding-box outlines: 2.5dp solid accent
 * when selected, 2dp dashed white @75% when not, each with a 22dp numbered
 * badge at its top-left corner. In box mode a scrim covers the photo and the
 * drag rectangle is drawn dashed white.
 */
@Composable
private fun PhotoCanvas(
    imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
    bitmapW: Int,
    bitmapH: Int,
    segments: List<Segment>,
    selected: Set<Int>,
    manualMode: Boolean,
    dragStart: Offset?,
    dragEnd: Offset?,
    draft: Rect?,
    onTap: (Int, Int) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Rect) -> Unit,
    onDragCancel: () -> Unit,
) {
    val c = snapColors
    val accent = c.accent
    val ink = c.text
    Canvas(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF111010))
            .pointerInput(manualMode) {
                val fit = fitInto(bitmapW, bitmapH, size.width.toFloat(), size.height.toFloat())
                if (manualMode) {
                    var s: Offset? = null
                    var e: Offset? = null
                    detectDragGestures(
                        onDragStart = { s = it; e = it; onDragStart(it) },
                        onDrag = { change, _ -> e = change.position; onDrag(change.position) },
                        onDragCancel = { onDragCancel() },
                        onDragEnd = {
                            val a = s
                            val b = e
                            if (a != null && b != null) {
                                onDragEnd(
                                    Rect(
                                        fit.toBitmapX(a.x).toInt(), fit.toBitmapY(a.y).toInt(),
                                        fit.toBitmapX(b.x).toInt(), fit.toBitmapY(b.y).toInt(),
                                    ).apply { sort() },
                                )
                            } else {
                                onDragCancel()
                            }
                        },
                    )
                } else {
                    detectTapGestures { pos -> onTap(fit.toBitmapX(pos.x).toInt(), fit.toBitmapY(pos.y).toInt()) }
                }
            },
    ) {
        val fit = fitInto(bitmapW, bitmapH, size.width, size.height)
        drawImage(
            imageBitmap,
            dstOffset = IntOffset(fit.dx.toInt(), fit.dy.toInt()),
            dstSize = IntSize((bitmapW * fit.scale).toInt(), (bitmapH * fit.scale).toInt()),
        )
        val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))
        val badge = 22.dp.toPx()
        val textPaint = android.graphics.Paint().apply {
            textSize = 11.sp.toPx()
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        fun rectOf(seg: Segment): Pair<Offset, Size> = Offset(fit.dx + seg.startX * fit.scale, fit.dy + seg.startY * fit.scale) to
            Size(seg.width * fit.scale, seg.height * fit.scale)

        if (manualMode) {
            drawRect(Color.Black.copy(alpha = 0.5f))
        }
        segments.forEach { seg ->
            val on = seg.index in selected
            val (tl, sz) = rectOf(seg)
            if (on) {
                drawRect(accent, tl, sz, style = Stroke(2.5.dp.toPx()))
            } else {
                drawRect(Color.White.copy(alpha = 0.75f), tl, sz, style = Stroke(2.dp.toPx(), pathEffect = dash))
            }
            // Numbered badge, -2dp offset from the corner.
            val bx = tl.x - 2.dp.toPx()
            val by = tl.y - 2.dp.toPx()
            drawRect(if (on) accent else Color.White, Offset(bx, by), Size(badge, badge))
            textPaint.color = if (on) android.graphics.Color.WHITE else ink.toArgbInt()
            drawContext.canvas.nativeCanvas.drawText("${seg.index + 1}", bx + badge / 2, by + badge / 2 - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
        }
        // Live drag or the placed draft, both dashed white.
        val live = if (dragStart != null && dragEnd != null) {
            Offset(min(dragStart.x, dragEnd.x), min(dragStart.y, dragEnd.y)) to Size(abs(dragEnd.x - dragStart.x), abs(dragEnd.y - dragStart.y))
        } else if (draft != null) {
            Offset(fit.dx + draft.left * fit.scale, fit.dy + draft.top * fit.scale) to Size(max(1, draft.width()) * fit.scale, max(1, draft.height()) * fit.scale)
        } else {
            null
        }
        live?.let { (tl, sz) -> drawRect(Color.White, tl, sz, style = Stroke(2.dp.toPx(), pathEffect = dash)) }
    }
}

private fun Color.toArgbInt(): Int = this.toArgb()
