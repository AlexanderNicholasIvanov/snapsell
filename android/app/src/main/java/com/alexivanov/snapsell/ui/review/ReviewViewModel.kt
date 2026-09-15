package com.alexivanov.snapsell.ui.review

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexivanov.snapsell.AppContainer
import com.alexivanov.snapsell.core.Ids
import com.alexivanov.snapsell.data.local.ItemEntity
import com.alexivanov.snapsell.domain.Condition
import com.alexivanov.snapsell.vision.CaptureSession
import com.alexivanov.snapsell.vision.Segment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReviewUiState(
    val bitmap: Bitmap? = null,
    val segments: List<Segment> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val segmenting: Boolean = false,
    val committing: Boolean = false,
    val manualMode: Boolean = false,
    /** ML Kit could not run (model missing, emulator GL, ...): boxes are the only way in. */
    val outlinerUnavailable: Boolean = false,
    val error: String? = null,
    /** Cutouts kept from earlier photos in this capture session. */
    val pendingCount: Int = 0,
) {
    val selectedCount: Int get() = selected.size + pendingCount
}

/**
 * Runs segmentation on the current photo, tracks which subjects the user
 * wants, and on confirm renders cutouts + inserts placeholder items that the
 * Confirm screen then identifies.
 */
class ReviewViewModel(private val container: AppContainer) : ViewModel() {
    private val session: CaptureSession get() = container.captureSession

    private val _state = MutableStateFlow(ReviewUiState(pendingCount = session.pendingCutouts.size))
    val state: StateFlow<ReviewUiState> = _state

    init {
        val photo = session.current
        if (photo == null) {
            _state.update { it.copy(error = "No photo to review. Go back and take one.") }
        } else {
            _state.update { it.copy(bitmap = photo.workBitmap, segments = photo.segments, selected = photo.selected.toSet()) }
            if (photo.segments.isEmpty()) segment(photo)
        }
    }

    private fun segment(photo: CaptureSession.Photo) {
        _state.update { it.copy(segmenting = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { container.segmenter.segment(photo.workBitmap) }
            result.onSuccess { segments ->
                photo.segments = segments
                // Everything ML Kit found is selected by default; the user deselects.
                photo.selected.clear()
                photo.selected.addAll(segments.map { it.index })
                _state.update {
                    it.copy(
                        segmenting = false,
                        segments = segments,
                        selected = photo.selected.toSet(),
                        manualMode = segments.isEmpty(),
                        error = if (segments.isEmpty()) "No objects found. Draw a box around each thing you want to sell." else null,
                    )
                }
            }.onFailure { e ->
                // Typical cause: the segmentation model has not been downloaded by Play services yet.
                _state.update {
                    it.copy(segmenting = false, manualMode = true, outlinerUnavailable = true, error = null)
                }
            }
        }
    }

    fun toggle(index: Int) {
        val photo = session.current ?: return
        if (!photo.selected.remove(index)) photo.selected.add(index)
        _state.update { it.copy(selected = photo.selected.toSet()) }
    }

    /** Toggle whichever segment contains the tapped bitmap point (smallest wins). */
    fun tapAt(x: Int, y: Int) {
        val hit = _state.value.segments
            .filter { it.contains(x, y) }
            .minByOrNull { it.width.toLong() * it.height }
            ?: return
        toggle(hit.index)
    }

    fun setManualMode(enabled: Boolean) = _state.update { it.copy(manualMode = enabled) }

    /** Adds a user-drawn rectangle (bitmap coordinates) as a selected segment. */
    fun addManualRect(rect: Rect) {
        val photo = session.current ?: return
        val bmp = photo.workBitmap
        val clipped = Rect(rect).apply { sort() }
        // intersect() returns false (and leaves the rect alone) when the box is fully outside the photo.
        if (!clipped.intersect(0, 0, bmp.width, bmp.height)) return
        if (clipped.width() < MIN_MANUAL_SIZE || clipped.height() < MIN_MANUAL_SIZE) return
        val index = (photo.segments.maxOfOrNull { it.index } ?: -1) + 1
        photo.segments = photo.segments + Segment.manual(index, clipped)
        photo.selected.add(index)
        _state.update { it.copy(segments = photo.segments, selected = photo.selected.toSet(), manualMode = false, error = null) }
    }

    fun remove(index: Int) {
        val photo = session.current ?: return
        photo.segments = photo.segments.filterNot { it.index == index }
        photo.selected.remove(index)
        _state.update { it.copy(segments = photo.segments, selected = photo.selected.toSet()) }
    }

    /**
     * "Retake" for one item: drop that segment, stash every other selected
     * cutout from this photo, then let the caller open the camera again.
     */
    fun retake(index: Int, onReady: () -> Unit) {
        remove(index)
        viewModelScope.launch {
            stashSelected()
            onReady()
        }
    }

    private suspend fun stashSelected() {
        val photo = session.current ?: return
        val toRender = photo.segments.filter { it.index in photo.selected }
        if (toRender.isEmpty()) return
        val cutouts = withContext(container.dispatchers.default) {
            toRender.map { seg ->
                CaptureSession.Cutout(container.cutoutRenderer.render(photo.workBitmap, seg), photo.file)
            }
        }
        session.pendingCutouts.addAll(cutouts)
        photo.selected.clear()
        _state.update { it.copy(selected = emptySet(), pendingCount = session.pendingCutouts.size) }
    }

    /** Render every selected cutout, insert placeholder items, hand their ids to Confirm. */
    fun identify(onItems: (List<String>) -> Unit) {
        if (_state.value.committing) return
        _state.update { it.copy(committing = true, error = null) }
        viewModelScope.launch {
            try {
                stashSelected()
                val cutouts = session.pendingCutouts.toList()
                if (cutouts.isEmpty()) {
                    _state.update { it.copy(committing = false, error = "Select at least one item.") }
                    return@launch
                }
                val now = container.clock.nowMillis()
                val items = cutouts.map { c ->
                    ItemEntity(
                        id = Ids.newId(),
                        // Blank name marks "not identified yet"; ConfirmViewModel fills it in.
                        name = "",
                        category = "",
                        condition = Condition.GOOD,
                        searchQuery = "",
                        confidence = 0.0,
                        photoPath = c.file.absolutePath,
                        cutoutPath = c.file.absolutePath,
                        originalPhotoPath = c.originalPhoto.absolutePath,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
                container.inventory.saveItems(items)
                session.clear()
                onItems(items.map { it.id })
            } catch (e: Exception) {
                _state.update { it.copy(committing = false, error = "Could not prepare cutouts: ${e.message}") }
            }
        }
    }

    companion object {
        const val MIN_MANUAL_SIZE = 24
    }
}
