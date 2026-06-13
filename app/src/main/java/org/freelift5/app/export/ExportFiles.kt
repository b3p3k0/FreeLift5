package org.freelift5.app.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ExportFiles {
    fun writeToUri(
        context: Context,
        uri: android.net.Uri,
        bytes: ByteArray,
    ) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("Unable to open the selected export destination.")
    }

    fun share(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, fileName).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share FreeLift5 data"))
    }
}
