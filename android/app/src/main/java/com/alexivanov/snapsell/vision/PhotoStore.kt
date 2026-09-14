package com.alexivanov.snapsell.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * App-private photo files under filesDir/photos. Originals, cutouts and
 * album staging all live here; nothing touches shared storage until the
 * user hands off to Marketplace (see handoff/ResaleAlbum).
 */
class PhotoStore(context: Context) {
    val dir: File = File(context.filesDir, "photos").apply { mkdirs() }

    fun newPhotoFile(): File = File(dir, "photo_${System.currentTimeMillis()}.jpg")
    fun newCutoutFile(): File = File(dir, "cutout_${System.currentTimeMillis()}_${(0..9999).random()}.jpg")

    /**
     * Rewrites [file] so the pixels are upright and the EXIF orientation is
     * gone. CameraX records orientation as EXIF rather than rotating pixels;
     * ML Kit and the backend both want upright pixels. Also caps the long edge
     * at [maxLongEdge] to keep memory and upload size sane.
     */
    fun normalizeRotation(file: File, maxLongEdge: Int = MAX_LONG_EDGE) {
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        val needsDownscale = longEdge > maxLongEdge
        if (rotation == 0f && !needsDownscale) return

        val decoded = decodeScaled(file, maxLongEdge) ?: return
        val upright = if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        } else {
            decoded
        }
        FileOutputStream(file).use { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (upright !== decoded) decoded.recycle()
        upright.recycle()
        // The pixels are upright now; make sure nothing re-applies the rotation.
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            saveAttributes()
        }
    }

    /** Decodes [file] with inSampleSize so the long edge is at most ~[maxLongEdge]. */
    fun decodeScaled(file: File, maxLongEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        var longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / 2 >= maxLongEdge) {
            sample *= 2
            longEdge /= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    fun writeJpeg(bitmap: Bitmap, file: File, quality: Int = JPEG_QUALITY): File {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return file
    }

    companion object {
        const val MAX_LONG_EDGE = 2048
        /** Segmentation and identification do not need more than this. */
        const val WORK_LONG_EDGE = 1600
        const val JPEG_QUALITY = 92
    }
}
