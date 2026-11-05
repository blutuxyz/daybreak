package com.bdersolutions.daybreak.data.attachments

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.bdersolutions.daybreak.data.repository.SchoolDataRepository
import com.bdersolutions.daybreak.untis.UntisAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

/** A downloaded attachment. [inDownloads] says whether a copy also went into the Downloads folder. */
data class SavedAttachment(
    val file: File,
    val mimeType: String,
    val inDownloads: Boolean,
)

/**
 * Downloads a message attachment. The file is written to the app's cache and, on Android 10 and
 * newer, copied into the Downloads folder. The cache copy is what gets handed to the app that opens
 * it (through the FileProvider declared in the manifest). The signed address and its headers are
 * used once and never kept or logged.
 */
class AttachmentDownloader(
    private val context: Context,
    private val schoolData: SchoolDataRepository,
) {
    suspend fun download(attachment: UntisAttachment): SavedAttachment = withContext(Dispatchers.IO) {
        removeOldDownloads()
        val link = schoolData.attachmentLink(attachment.id)
        val folder = File(File(context.cacheDir, CACHE_DIR), UUID.randomUUID().toString()).apply { mkdirs() }
        val file = File(folder, safeFileName(attachment.name))
        try {
            file.outputStream().use { out -> schoolData.downloadAttachment(link, out, MAX_BYTES) }
        } catch (error: Throwable) {
            folder.deleteRecursively()
            throw error
        }
        val mime = mimeTypeOf(file.name)
        val inDownloads = runCatching { copyToDownloads(file, mime) }.getOrDefault(false)
        SavedAttachment(file = file, mimeType = mime, inDownloads = inDownloads)
    }

    /** Copies the file into the public Downloads folder. Only possible without a permission from Android 10. */
    private fun copyToDownloads(file: File, mime: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: return false
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            true
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    /** Earlier downloads only stay for a day; the Downloads copy is the lasting one. */
    private fun removeOldDownloads() {
        val cutoff = System.currentTimeMillis() - DAY_MS
        File(context.cacheDir, CACHE_DIR).listFiles()?.forEach { folder ->
            if (folder.lastModified() < cutoff) folder.deleteRecursively()
        }
    }

    companion object {
        private const val CACHE_DIR = "attachments"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val MAX_BYTES = 100L * 1024 * 1024

        /** A name that cannot leave its folder or upset the file system. */
        fun safeFileName(raw: String): String {
            val cleaned = raw.replace(Regex("[\\/:*?\"<>|\u0000-\u001f]"), "_").trim().trim('.').take(120)
            return cleaned.ifBlank { "Anhang" }
        }

        fun mimeTypeOf(fileName: String): String {
            val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }

        private fun baseViewIntent(context: Context, saved: SavedAttachment): Intent {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", saved.file)
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, saved.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            view.clipData = ClipData.newRawUri(saved.file.name, uri)
            return view
        }

        /** Whether any app can open the file. A chooser alone would not say so. */
        fun canOpen(context: Context, saved: SavedAttachment): Boolean =
            context.packageManager.queryIntentActivities(baseViewIntent(context, saved), 0).isNotEmpty()

        /** The chooser that opens a saved attachment. The app that handles it may read this one file. */
        fun viewIntent(context: Context, saved: SavedAttachment): Intent =
            Intent.createChooser(baseViewIntent(context, saved), saved.file.name)
    }
}
