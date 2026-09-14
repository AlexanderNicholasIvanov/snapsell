package com.alexivanov.snapsell.vision

import android.graphics.Bitmap
import java.io.File

/**
 * In-memory state carried from Capture -> Review -> Confirm. Lives in the
 * AppContainer rather than in navigation arguments because it holds bitmaps
 * and File handles. Cleared when the Confirm step starts.
 */
class CaptureSession {
    /** One captured photo, downscaled for segmentation and display. */
    class Photo(val file: File, val workBitmap: Bitmap) {
        var segments: List<Segment> = emptyList()
        val selected: MutableSet<Int> = LinkedHashSet()
    }

    /** A cutout already rendered from an earlier photo in this session. */
    data class Cutout(val file: File, val originalPhoto: File)

    var current: Photo? = null
    val pendingCutouts: MutableList<Cutout> = ArrayList()

    fun clear() {
        current?.workBitmap?.recycle()
        current = null
        pendingCutouts.clear()
    }
}
