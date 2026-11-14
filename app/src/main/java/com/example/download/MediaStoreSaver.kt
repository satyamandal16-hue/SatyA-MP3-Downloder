package com.example.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.MediaCategory
import java.io.File
import java.io.FileInputStream

data class SavedFileResult(
    val uriString: String,
    val localFilePath: String?
)

object MediaStoreSaver {

    fun saveToPublicStorage(
        context: Context,
        sourceFile: File,
        fileName: String,
        mimeType: String,
        category: MediaCategory
    ): SavedFileResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val (collection, relativePath) = when (category) {
                MediaCategory.HD_AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MUSIC}/SatyA"
                MediaCategory.HD_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/SatyA"
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DOWNLOADS}/SatyA"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val insertedUri = resolver.insert(collection, values)
            if (insertedUri != null) {
                try {
                    resolver.openOutputStream(insertedUri)?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out, bufferSize = 64 * 1024)
                        }
                    }
                    val updateValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(insertedUri, updateValues, null, null)
                    // Also keep a small private copy or clean up sourceFile
                    val appCopyDir = File(context.getExternalFilesDir(null), "completed").apply { mkdirs() }
                    val appCopy = File(appCopyDir, "${System.currentTimeMillis()}_$fileName")
                    sourceFile.copyTo(appCopy, overwrite = true)
                    sourceFile.delete()
                    return SavedFileResult(
                        uriString = insertedUri.toString(),
                        localFilePath = appCopy.absolutePath
                    )
                } catch (_: Exception) {
                    resolver.delete(insertedUri, null, null)
                }
            }
        }

        // Fallback for API < 29 or if MediaStore insert failed
        val subDir = when (category) {
            MediaCategory.HD_AUDIO -> Environment.DIRECTORY_MUSIC
            MediaCategory.HD_VIDEO -> Environment.DIRECTORY_MOVIES
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        val destDir = File(context.getExternalFilesDir(subDir), "SatyA").apply { mkdirs() }
        val destFile = uniqueFile(destDir, fileName)
        sourceFile.copyTo(destFile, overwrite = true)
        sourceFile.delete()

        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile
        )
        return SavedFileResult(
            uriString = uri.toString(),
            localFilePath = destFile.absolutePath
        )
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val name = if (ext.isNotBlank()) "${base}_($index).$ext" else "${base}_($index)"
            candidate = File(dir, name)
            index++
        }
        return candidate
    }
}
