package com.alexivanov.snapsell.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import java.io.File

/**
 * Turns a [Segment] into a listing-ready JPEG: the subject on a white
 * background with a little padding. For manual rectangles (no mask) the
 * original pixels inside the rectangle are used instead.
 */
class CutoutRenderer(private val photoStore: PhotoStore) {

    fun render(source: Bitmap, segment: Segment, paddingFraction: Float = PADDING): File {
        val subject = segment.bitmap ?: crop(source, segment.bounds)
        val pad = (maxOf(subject.width, subject.height) * paddingFraction).toInt()
        val out = createBitmap(subject.width + 2 * pad, subject.height + 2 * pad)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(subject, pad.toFloat(), pad.toFloat(), null)
        val file = photoStore.writeJpeg(out, photoStore.newCutoutFile())
        out.recycle()
        if (subject !== segment.bitmap) subject.recycle()
        return file
    }

    private fun crop(source: Bitmap, rect: Rect): Bitmap {
        val clipped = Rect(rect)
        if (!clipped.intersect(0, 0, source.width, source.height)) {
            // Entirely outside the photo: degrade to a 1x1 corner rather than crash.
            clipped.set(0, 0, 1, 1)
        }
        val w = clipped.width().coerceAtLeast(1)
        val h = clipped.height().coerceAtLeast(1)
        return Bitmap.createBitmap(source, clipped.left, clipped.top, w, h)
    }

    companion object {
        const val PADDING = 0.08f
    }
}
