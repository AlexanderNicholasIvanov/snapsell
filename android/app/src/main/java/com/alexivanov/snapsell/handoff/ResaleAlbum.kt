package com.alexivanov.snapsell.handoff

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream

/**
 * Copies listing JPEGs into a shared "Resale" album so the Facebook photo
 * picker finds them together.
 *
 *  - API 29+: MediaStore insert with RELATIVE_PATH = Pictures/Resale. No
 *    permission needed for files the app creates.
 *  - API 24-28: write to the public Pictures/Resale directory and register the
 *    file with MediaStore via the DATA column. Needs WRITE_EXTERNAL_STORAGE,
 *    which the HandOff screen requests at runtime on those versions.
 */
object ResaleAlbum {
    const val ALBUM = "Resale"
    private const val MIME = "image/jpeg"

    fun save(context: Context, files: List<File>): List<Uri> = files.mapNotNull { file ->
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(context, file) else saveLegacy(context, file)
        }.getOrNull()
    }

    private fun displayName(file: File): String {
        val stamp = System.currentTimeMillis()
        return "snapsell_${stamp}_${file.nameWithoutExtension}.jpg"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(context: Context, file: File): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName(file))
            put(MediaStore.Images.Media.MIME_TYPE, MIME)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("openOutputStream returned null")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, file: File): Uri? {
        val albumDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        if (!albumDir.exists() && !albumDir.mkdirs()) return null
        val target = File(albumDir, displayName(file))
        FileOutputStream(target).use { out -> file.inputStream().use { it.copyTo(out) } }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, target.nameWithoutExtension)
            put(MediaStore.Images.Media.DISPLAY_NAME, target.name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME)
            put(MediaStore.Images.Media.DATA, target.absolutePath)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }
}
