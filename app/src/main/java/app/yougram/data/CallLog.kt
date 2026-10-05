package app.yougram.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import app.yougram.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Пишет лог звонков в Загрузки/call_log.txt (дописывает в конец, права не нужны). */
object CallLog {
    private const val FILE = "call_log.txt"

    @Volatile private var ctx: Context? = null
    @Volatile private var uri: Uri? = null
    private val queue = ConcurrentLinkedQueue<String>()
    private val scheduled = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "call-log").apply { isDaemon = true }
    }
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (ctx == null) ctx = context.applicationContext
    }

    /** Шапка нового звонка: устройство, версия, состояние разрешений. */
    fun header(callId: Int, outgoing: Boolean, video: Boolean) {
        val c = ctx
        val mic = c?.let {
            ContextCompat.checkSelfPermission(it, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        }
        d(
            "CallLog",
            "==== call $callId outgoing=$outgoing video=$video | ${Build.MANUFACTURER} ${Build.MODEL}, " +
                "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), app ${BuildConfig.VERSION_NAME}, RECORD_AUDIO=$mic ====",
        )
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        enqueue("D", tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
        enqueue("E", tag, if (t == null) msg else msg + "\n" + Log.getStackTraceString(t))
    }

    private fun enqueue(level: String, tag: String, msg: String) {
        val time = synchronized(format) { format.format(Date()) }
        queue.add("$time $level/$tag: $msg")
        if (scheduled.compareAndSet(false, true)) {
            executor.schedule({ flush() }, 300, TimeUnit.MILLISECONDS)
        }
    }

    private fun flush() {
        scheduled.set(false)
        val c = ctx ?: return
        val sb = StringBuilder()
        while (true) sb.append(queue.poll() ?: break).append('\n')
        if (sb.isEmpty()) return
        try {
            val target = uri ?: resolve(c) ?: return
            c.contentResolver.openOutputStream(target, "wa")?.use { it.write(sb.toString().toByteArray()) }
        } catch (t: Throwable) {
            uri = null
            Log.e("CallLog", "write failed", t)
        }
    }

    private fun resolve(c: Context): Uri? {
        val resolver = c.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(FILE, Environment.DIRECTORY_DOWNLOADS + "/"),
            null,
        )?.use { cur ->
            if (cur.moveToFirst()) {
                return ContentUris.withAppendedId(collection, cur.getLong(0)).also { uri = it }
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return resolver.insert(collection, values)?.also { uri = it }
    }
}
