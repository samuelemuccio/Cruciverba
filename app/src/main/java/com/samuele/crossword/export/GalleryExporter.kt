package com.samuele.crossword.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object GalleryExporter {

    /**
     * Salva una bitmap ad alta risoluzione nella Galleria dell'utente tramite MediaStore API,
     * con fallback affidabile su storage locale e FileProvider in caso di restrizioni di sistema.
     * Ritorna sempre un Uri valido per la visualizzazione o condivisione.
     */
    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        title: String,
        subfolder: String = "Cruciverba"
    ): Uri? {
        val resolver = context.contentResolver
        val cleanTitle = title.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val filename = "${cleanTitle}_${System.currentTimeMillis()}.png"

        // 1. Salva sempre una copia fisica su disco locale (Pictures/Cruciverba)
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val subDir = File(picturesDir, subfolder).apply { if (!exists()) mkdirs() }
        val fallbackFile = File(subDir, filename)
        try {
            FileOutputStream(fallbackFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
        } catch (_: Exception) {
            // Continua col tentativo MediaStore
        }

        // 2. Tenta l'inserimento ufficiale nel MediaStore (per apparire in Galleria)
        var mediaUri: Uri? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$subfolder")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            mediaUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (mediaUri != null) {
                resolver.openOutputStream(mediaUri)?.use { outStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)) {
                        throw IOException("Impossibile comprimere la bitmap in PNG")
                    }
                    outStream.flush()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(mediaUri, values, null, null)
                }
            }
        } catch (_: Exception) {
            if (mediaUri != null) {
                try { resolver.delete(mediaUri, null, null) } catch (_: Exception) {}
                mediaUri = null
            }
        }

        // Scansione multimediale per l'indicizzazione
        if (fallbackFile.exists()) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(fallbackFile.absolutePath),
                arrayOf("image/png"),
                null
            )
        }

        // Se MediaStore ha avuto successo, ritorna mediaUri; altrimenti ritorna l'URI FileProvider della copia fisica
        return mediaUri ?: if (fallbackFile.exists()) {
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", fallbackFile)
            } catch (_: Exception) {
                Uri.fromFile(fallbackFile)
            }
        } else null
    }

    /**
     * Copia un file PNG già generato all'interno della Galleria di sistema.
     */
    fun saveFileToGallery(
        context: Context,
        sourceFile: File,
        title: String,
        subfolder: String = "Cruciverba"
    ): Uri? {
        val resolver = context.contentResolver
        val cleanTitle = title.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val filename = "${cleanTitle}_${System.currentTimeMillis()}.png"

        var mediaUri: Uri? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$subfolder")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            mediaUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (mediaUri != null) {
                sourceFile.inputStream().use { input ->
                    resolver.openOutputStream(mediaUri)?.use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(mediaUri, values, null, null)
                }
            }
        } catch (_: Exception) {
            if (mediaUri != null) {
                try { resolver.delete(mediaUri, null, null) } catch (_: Exception) {}
                mediaUri = null
            }
        }

        MediaScannerConnection.scanFile(
            context,
            arrayOf(sourceFile.absolutePath),
            arrayOf("image/png"),
            null
        )

        return mediaUri ?: try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", sourceFile)
        } catch (_: Exception) {
            Uri.fromFile(sourceFile)
        }
    }
}
