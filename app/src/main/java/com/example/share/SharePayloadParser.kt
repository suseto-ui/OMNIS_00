package com.example.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

object SharePayloadParser {
    private const val TAG = "SharePayloadParser"
    private const val MAX_ATTACHMENT_BYTES = 15L * 1024L * 1024L // 15 MB per file
    private val URL_REGEX = Regex("""https?://[^\s<>"'\)\]]+""", RegexOption.IGNORE_CASE)

    fun buildIntentFingerprint(intent: Intent?): String? {
        if (intent == null) return null
        val action = intent.action ?: return null
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val text = extractSharedText(intent).orEmpty()
        val uris = extractUris(intent).joinToString("|") { it.toString() }
        if (subject.isBlank() && text.isBlank() && uris.isBlank()) return null
        return "$action::${intent.type.orEmpty()}::${subject.hashCode()}::${text.hashCode()}::${uris.hashCode()}"
    }

    suspend fun parse(context: Context, intent: Intent?): PendingSharePayload? = withContext(Dispatchers.IO) {
        if (intent == null) return@withContext null
        val action = intent.action ?: return@withContext null
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return@withContext null
        }

        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotBlank() }
        val rawText = extractSharedText(intent)?.trim()?.takeIf { it.isNotBlank() }
        val detectedUrls = rawText?.let { text ->
            URL_REGEX.findAll(text).map { it.value.trimEnd('.', ',', ';', ')', ']', '!', '?') }.distinct().toList()
        } ?: emptyList()

        val uris = extractUris(intent)
        val cacheDir = File(context.cacheDir, "shared_inputs").apply { mkdirs() }
        pruneStaleCache(cacheDir)
        val canonicalCacheDir = cacheDir.canonicalFile

        val attachments = uris.mapNotNull { uri ->
            copyUriToCache(context, uri, intent.type, canonicalCacheDir)
        }

        if (subject == null && rawText == null && attachments.isEmpty()) {
            return@withContext null
        }

        val hasPdf = attachments.any { it.isPdf }
        val hasImage = attachments.any { it.isImage }
        val hasText = !rawText.isNullOrBlank()
        val hasUrl = detectedUrls.isNotEmpty()

        val contentType = when {
            (hasPdf || hasImage) && hasText -> SharedContentType.MIXED
            hasPdf && hasImage -> SharedContentType.MIXED
            hasPdf -> SharedContentType.PDF
            hasImage -> SharedContentType.IMAGE
            hasUrl -> SharedContentType.URL
            else -> SharedContentType.TEXT
        }

        PendingSharePayload(
            subject = subject,
            sharedText = rawText,
            detectedUrls = detectedUrls,
            attachments = attachments,
            contentType = contentType
        )
    }

    private fun extractSharedText(intent: Intent): String? {
        val singleText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (!singleText.isNullOrBlank()) return singleText

        val multiTexts = intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
            ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            ?.joinToString("\n")
        if (!multiTexts.isNullOrBlank()) return multiTexts

        val clipData = intent.clipData ?: return null
        val clipTexts = buildList {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i)?.text?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return clipTexts.joinToString("\n").takeIf { it.isNotBlank() }
    }

    @Suppress("DEPRECATION")
    private fun extractUris(intent: Intent): List<Uri> {
        val result = linkedSetOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) result.add(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list?.forEach { if (it != null) result.add(it) }
            }
        }

        // Sloučíme také případné URI z ClipData (linkedSetOf odstraní duplicity)
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i)?.uri?.let { result.add(it) }
            }
        }
        return result.toList()
    }

    fun pruneStaleCache(cacheDir: File, maxAgeMs: Long = 24L * 60L * 60L * 1000L, nowMs: Long = System.currentTimeMillis()): Int {
        if (!cacheDir.exists() || !cacheDir.isDirectory) return 0
        var removed = 0
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && (nowMs - file.lastModified()) > maxAgeMs) {
                if (file.delete()) removed++
            }
        }
        return removed
    }

    private fun copyUriToCache(
        context: Context,
        uri: Uri,
        fallbackIntentType: String?,
        canonicalCacheDir: File
    ): SharedAttachment? {
        return try {
            val resolver = context.contentResolver
            val resolvedMime = resolver.getType(uri) ?: fallbackIntentType ?: "application/octet-stream"
            val rawName = queryDisplayName(context, uri) ?: "shared_file_${System.currentTimeMillis()}"
            val sanitizedName = rawName.replace(Regex("""[^a-zA-Z0-9._-]"""), "_").take(80).ifBlank { "shared_item" }
            val lowerName = sanitizedName.lowercase(Locale.US)

            val isPdf = resolvedMime.equals("application/pdf", ignoreCase = true) ||
                lowerName.endsWith(".pdf")
            val isImage = resolvedMime.startsWith("image/", ignoreCase = true) ||
                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".webp")

            if (!isPdf && !isImage && !resolvedMime.startsWith("text/", ignoreCase = true)) {
                Log.w(TAG, "Ignoring unsupported shared MIME type: $resolvedMime")
                return null
            }

            val ext = when {
                sanitizedName.contains(".") -> ""
                isPdf -> ".pdf"
                resolvedMime.contains("png", ignoreCase = true) -> ".png"
                isImage -> ".jpg"
                else -> ".txt"
            }
            val destFile = File(canonicalCacheDir, "${UUID.randomUUID().toString().take(8)}_$sanitizedName$ext").canonicalFile
            if (destFile.parentFile != canonicalCacheDir) {
                Log.e(TAG, "Path traversal attempt rejected for $sanitizedName")
                return null
            }

            var totalBytes = 0L
            val opened = resolver.openInputStream(uri) ?: return null
            opened.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        totalBytes += read
                        if (totalBytes > MAX_ATTACHMENT_BYTES) {
                            destFile.delete()
                            Log.w(TAG, "Shared file exceeded 15MB cap: $sanitizedName")
                            return null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }

            if (totalBytes == 0L) {
                destFile.delete()
                return null
            }

            val normalizedMime = when {
                isPdf -> "application/pdf"
                resolvedMime.startsWith("image/", ignoreCase = true) -> resolvedMime
                lowerName.endsWith(".png") -> "image/png"
                lowerName.endsWith(".webp") -> "image/webp"
                isImage -> "image/jpeg"
                else -> resolvedMime
            }

            SharedAttachment(
                sourceUriString = uri.toString(),
                localCachePath = destFile.absolutePath,
                displayName = sanitizedName,
                mimeType = normalizedMime,
                byteSize = totalBytes,
                isPdf = isPdf,
                isImage = isImage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read shared URI stream safely: $uri", e)
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val fromProvider = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
        if (!fromProvider.isNullOrBlank()) return fromProvider
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }
}
