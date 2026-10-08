package app.yougram.feature.chat.ui

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import app.yougram.feature.chat.data.MediaKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Копирует скачанный файл в галерею (фото/видео) или в «Загрузки» (остальное). minSdk 31 — разрешения не нужны. */
suspend fun saveToGallery(context: Context, path: String, name: String, mime: String, kind: MediaKind): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val source = File(path)
            val type = mime.ifBlank {
                when (kind) {
                    MediaKind.PHOTO -> "image/jpeg"
                    MediaKind.VIDEO, MediaKind.ANIMATION -> "video/mp4"
                    else -> "application/octet-stream"
                }
            }
            val fileName = name.ifBlank { source.name }
            val (collection, folder) = when {
                type.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
                type.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, type)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Yougram")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: error("insert failed")
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: error("open failed")
        }.isSuccess
    }

/** Копирует скачанный файл туда, куда пользователь указал в системном проводнике (SAF, [uri] из CreateDocument). */
suspend fun exportToUri(context: Context, path: String, uri: android.net.Uri): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out -> File(path).inputStream().use { it.copyTo(out) } }
                ?: error("open failed")
        }.isSuccess
    }