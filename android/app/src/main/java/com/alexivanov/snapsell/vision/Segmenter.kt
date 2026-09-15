package com.alexivanov.snapsell.vision

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import com.alexivanov.snapsell.core.await
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * One detected subject, in the coordinate space of the bitmap that was
 * segmented. [bitmap] is the subject with transparent background (from ML
 * Kit) or null for a manually drawn rectangle. [maskOutline] is a coarse
 * polygon traced from the confidence mask; [maskOverlay] is the mask as a
 * tinted ARGB bitmap the size of the bounding box for drawing.
 */
data class Segment(
    val index: Int,
    val startX: Int,
    val startY: Int,
    val width: Int,
    val height: Int,
    val bitmap: Bitmap?,
    val maskOutline: Path,
    val maskOverlay: Bitmap?,
    val manual: Boolean = false,
) {
    val bounds: Rect get() = Rect(startX, startY, startX + width, startY + height)

    fun contains(x: Int, y: Int): Boolean = bounds.contains(x, y)

    companion object {
        /** A user-drawn rectangle: no mask, outline is the rect itself. */
        fun manual(index: Int, rect: Rect): Segment = Segment(
            index = index,
            startX = rect.left,
            startY = rect.top,
            width = rect.width(),
            height = rect.height(),
            bitmap = null,
            maskOutline = Path().apply { addRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), Path.Direction.CW) },
            maskOverlay = null,
            manual = true,
        )
    }
}

/**
 * Wraps ML Kit Subject Segmentation (on-device, beta). Multi-subject mode
 * with per-subject confidence masks and bitmaps; the foreground bitmap is
 * also requested so a single-subject fallback is available.
 *
 * The model is fetched by Play services (see the manifest meta-data). On a
 * device where it has not been downloaded yet, [segment] fails; callers fall
 * back to manual rectangles.
 */
class Segmenter(context: Context) : Closeable {
    /**
     * ML Kit's segmentation runtime needs OpenGL ES 3.1. On a GLES 3.0 device
     * (the Android emulator, some very old phones) it does not fail cleanly:
     * it crashes natively in its GL thread. So the version is checked up front
     * and [segment] throws [UnavailableException] instead of ever calling in.
     */
    private val glEsVersion: Int = context.applicationContext
        .getSystemService(ActivityManager::class.java)
        ?.deviceConfigurationInfo?.reqGlEsVersion ?: 0

    val isAvailable: Boolean get() = glEsVersion >= MIN_GLES_VERSION

    class UnavailableException(message: String) : IllegalStateException(message)

    private val segmenter by lazy {
        val subjectOptions = SubjectSegmenterOptions.SubjectResultOptions.Builder()
            .enableConfidenceMask()
            .enableSubjectBitmap()
            .build()
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableMultipleSubjects(subjectOptions)
                .enableForegroundBitmap()
                .build(),
        )
    }

    suspend fun segment(bitmap: Bitmap): List<Segment> {
        if (!isAvailable) {
            throw UnavailableException(
                "Subject segmentation needs OpenGL ES 3.1; this device reports 0x${Integer.toHexString(glEsVersion)}.",
            )
        }
        val result = segmenter.process(InputImage.fromBitmap(bitmap, 0)).await()
        return result.subjects.mapIndexedNotNull { index, subject ->
            if (subject.width <= 0 || subject.height <= 0) return@mapIndexedNotNull null
            val mask = subject.confidenceMask
            Segment(
                index = index,
                startX = subject.startX,
                startY = subject.startY,
                width = subject.width,
                height = subject.height,
                bitmap = subject.bitmap,
                maskOutline = mask?.let { outlineFromMask(it, subject.startX, subject.startY, subject.width, subject.height) }
                    ?: rectPath(subject.startX, subject.startY, subject.width, subject.height),
                maskOverlay = mask?.let { overlayFromMask(it, subject.width, subject.height) },
            )
        }
    }

    override fun close() {
        runCatching { segmenter.close() }
    }

    companion object {
        /** 0x30001 = OpenGL ES 3.1, the floor for ML Kit's GPU pipeline. */
        const val MIN_GLES_VERSION = 0x30001
        const val MASK_THRESHOLD = 0.5f
        private const val OUTLINE_ROWS = 64

        private fun rectPath(x: Int, y: Int, w: Int, h: Int) = Path().apply {
            addRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), Path.Direction.CW)
        }

        /**
         * A cheap outline: sample rows, take the leftmost and rightmost pixel
         * above threshold on each, walk down the left edge and back up the
         * right. Good enough to show "this is the thing we cut out".
         */
        fun outlineFromMask(mask: FloatBuffer, startX: Int, startY: Int, width: Int, height: Int): Path {
            val stride = maxOf(1, height / OUTLINE_ROWS)
            val left = ArrayList<Pair<Float, Float>>()
            val right = ArrayList<Pair<Float, Float>>()
            var y = 0
            while (y < height) {
                var first = -1
                var last = -1
                val rowOffset = y * width
                for (x in 0 until width) {
                    if (mask.get(rowOffset + x) >= MASK_THRESHOLD) {
                        if (first < 0) first = x
                        last = x
                    }
                }
                if (first >= 0) {
                    val fy = (startY + y).toFloat()
                    left += (startX + first).toFloat() to fy
                    right += (startX + last + 1).toFloat() to fy
                }
                y += stride
            }
            val path = Path()
            if (left.isEmpty()) return rectPath(startX, startY, width, height)
            path.moveTo(left.first().first, left.first().second)
            for ((x, py) in left.drop(1)) path.lineTo(x, py)
            for ((x, py) in right.asReversed()) path.lineTo(x, py)
            path.close()
            return path
        }

        /** Mask as a translucent tinted bitmap (alpha = confidence above threshold). */
        fun overlayFromMask(mask: FloatBuffer, width: Int, height: Int, tint: Int = Color.rgb(0, 200, 120)): Bitmap {
            val pixels = IntArray(width * height)
            val r = Color.red(tint)
            val g = Color.green(tint)
            val b = Color.blue(tint)
            for (i in pixels.indices) {
                val c = mask.get(i)
                val alpha = if (c >= MASK_THRESHOLD) (110 * c).toInt().coerceIn(0, 255) else 0
                pixels[i] = Color.argb(alpha, r, g, b)
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }
    }
}
