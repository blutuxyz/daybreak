package com.bdersolutions.daybreak.data.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Why a picked file cannot be attached. */
enum class AttachmentProblem { TOO_LARGE, UNSUPPORTED, UNREADABLE }

/**
 * Reads a picked picture or file into a [ChatAttachment]. Pictures are scaled down to at most
 * [MAX_IMAGE_EDGE] pixels and saved as JPEG, which keeps them small and drops their metadata (location,
 * camera). PDFs and text files are taken as they are, up to [MAX_FILE_BYTES]. Nothing is written to disk.
 */
object AttachmentReader {
    const val MAX_IMAGE_EDGE = 1600
    const val MAX_FILE_BYTES = 5L * 1024 * 1024
    private val FILE_TYPES = setOf("application/pdf", "text/plain", "text/markdown", "text/csv")

    sealed interface Result {
        data class Ok(val attachment: ChatAttachment) : Result
        data class Problem(val problem: AttachmentProblem) : Result
    }

    suspend fun read(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase().orEmpty()
        val (name, size) = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                (if (nameIndex >= 0) cursor.getString(nameIndex) else null) to (if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L)
            } ?: (null to -1L)
        val displayName = name?.take(120) ?: "file"
        try {
            when {
                mime.startsWith("image/") -> {
                    val bitmap = decodeScaled(context, uri) ?: return@withContext Result.Problem(AttachmentProblem.UNREADABLE)
                    val bytes = ByteArrayOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        out.toByteArray()
                    }
                    bitmap.recycle()
                    val jpegName = displayName.substringBeforeLast('.') + ".jpg"
                    Result.Ok(ChatAttachment(ChatAttachment.Kind.IMAGE, jpegName, "image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP), bytes.size.toLong()))
                }

                mime in FILE_TYPES || (mime == "application/octet-stream" && displayName.endsWith(".txt", ignoreCase = true)) -> {
                    if (size > MAX_FILE_BYTES) return@withContext Result.Problem(AttachmentProblem.TOO_LARGE)
                    val bytes = resolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(chunk)
                            if (read < 0) break
                            total += read
                            if (total > MAX_FILE_BYTES) return@withContext Result.Problem(AttachmentProblem.TOO_LARGE)
                            buffer.write(chunk, 0, read)
                        }
                        buffer.toByteArray()
                    } ?: return@withContext Result.Problem(AttachmentProblem.UNREADABLE)
                    val type = if (mime == "application/octet-stream") "text/plain" else mime
                    Result.Ok(ChatAttachment(ChatAttachment.Kind.FILE, displayName, type, Base64.encodeToString(bytes, Base64.NO_WRAP), bytes.size.toLong()))
                }

                else -> Result.Problem(AttachmentProblem.UNSUPPORTED)
            }
        } catch (_: SecurityException) {
            Result.Problem(AttachmentProblem.UNREADABLE)
        } catch (_: java.io.IOException) {
            Result.Problem(AttachmentProblem.UNREADABLE)
        } catch (_: OutOfMemoryError) {
            Result.Problem(AttachmentProblem.TOO_LARGE)
        }
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            // ImageDecoder also turns the picture upright from its EXIF orientation.
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val edge = max(info.size.width, info.size.height)
                if (edge > MAX_IMAGE_EDGE) {
                    val scale = MAX_IMAGE_EDGE.toFloat() / edge
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_EDGE * 2) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val edge = max(decoded.width, decoded.height)
        if (edge <= MAX_IMAGE_EDGE) return decoded
        val scale = MAX_IMAGE_EDGE.toFloat() / edge
        return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true).also {
            if (it != decoded) decoded.recycle()
        }
    }

    /** A small bitmap for the preview chip, from the attachment's own bytes. */
    fun thumbnail(attachment: ChatAttachment, edge: Int = 160): Bitmap? {
        if (attachment.kind != ChatAttachment.Kind.IMAGE) return null
        return runCatching {
            val bytes = Base64.decode(attachment.base64, Base64.NO_WRAP)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= edge) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}
