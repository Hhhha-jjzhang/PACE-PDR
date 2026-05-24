package com.example.pdr_zjj.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

class PublicExportManager(private val context: Context) {

    fun exportSession(sessionDir: File): ExportResult {
        if (!sessionDir.exists() || !sessionDir.isDirectory) {
            return ExportResult(false, null, 0)
        }

        val files = sessionDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            .orEmpty()

        if (files.isEmpty()) {
            return ExportResult(false, null, 0)
        }

        var exportedCount = 0

        for (file in files) {
            val exported = exportFile(sessionDir.name, file)
            if (exported) {
                exportedCount += 1
            }
        }

        return ExportResult(
            success = exportedCount > 0,
            relativePath = buildRelativePath(sessionDir.name),
            exportedCount = exportedCount
        )
    }

    private fun exportFile(sessionName: String, sourceFile: File): Boolean {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, buildRelativePath(sessionName))
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, contentValues) ?: return false

        return try {
            resolver.openOutputStream(itemUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            }

            true
        } catch (_: Exception) {
            resolver.delete(itemUri, null, null)
            false
        }
    }

    private fun buildRelativePath(sessionName: String): String {
        return "${Environment.DIRECTORY_DOWNLOADS}/PDR_Zjj/$sessionName"
    }
}

data class ExportResult(
    val success: Boolean,
    val relativePath: String?,
    val exportedCount: Int
)
