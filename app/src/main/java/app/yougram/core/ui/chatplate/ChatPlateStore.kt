package app.yougram.core.ui.chatplate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Своя картинка на плашку конкретного чата в списке чатов.
 * Файл на каждый чат: filesDir/chat_plates/<chatId>.jpg, метка версии лежит в SharedPreferences.
 */
class ChatPlateStore private constructor(private val appContext: Context) {
    private val prefs = appContext.getSharedPreferences("yougram_chat_plates", Context.MODE_PRIVATE)
    private val dir = File(appContext.filesDir, "chat_plates").apply { mkdirs() }

    private val _stamps = MutableStateFlow(load())

    /** chatId -> метка версии файла; чата в карте нет — картинки нет. */
    val stamps: StateFlow<Map<Long, Long>> = _stamps

    private fun load(): Map<Long, Long> = buildMap {
        prefs.all.forEach { (k, v) ->
            val id = k.toLongOrNull()
            if (id != null && v is Long && fileFor(id).exists()) put(id, v)
        }
    }

    fun fileFor(chatId: Long): File = File(dir, "$chatId.jpg")

    suspend fun setImage(chatId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = appContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MaxSide) sample *= 2
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext false
            fileFor(chatId).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            val stamp = System.currentTimeMillis()
            prefs.edit().putLong(chatId.toString(), stamp).apply()
            _stamps.value = _stamps.value + (chatId to stamp)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removeImage(chatId: Long) {
        runCatching { fileFor(chatId).delete() }
        prefs.edit().remove(chatId.toString()).apply()
        _stamps.value = _stamps.value - chatId
    }

    companion object {
        private const val MaxSide = 1280
        @Volatile private var instance: ChatPlateStore? = null
        fun get(context: Context): ChatPlateStore =
            instance ?: synchronized(this) {
                instance ?: ChatPlateStore(context.applicationContext).also { instance = it }
            }
    }
}