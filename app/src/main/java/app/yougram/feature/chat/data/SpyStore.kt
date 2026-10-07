package app.yougram.feature.chat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Правка сообщения: [oldText] — текст до правки, [at] — время правки (unix, сек). */
data class EditRecord(val oldText: String, val at: Int)

/** Сохранённое сообщение, которое было удалено у собеседника/в чате. */
data class StoredMessage(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val text: String,
    val summary: String,
    val outgoing: Boolean,
    val date: Int,
)

/**
 * Локальный архив для «режима шпиона»: копии сообщений (чтобы пережить удаление), история правок,
 * время прочтения и последний известный онлайн. Всё хранится только на устройстве.
 */
class SpyStore(context: Context) {

    private class Helper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, 1) {
        override fun onConfigure(db: SQLiteDatabase) {
            // Один файл без -wal/-shm: так базу проще экспортировать и импортировать.
            db.disableWriteAheadLogging()
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE messages (" +
                    "chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL, sender_id INTEGER NOT NULL DEFAULT 0, " +
                    "text TEXT NOT NULL DEFAULT '', summary TEXT NOT NULL DEFAULT '', outgoing INTEGER NOT NULL DEFAULT 0, " +
                    "date INTEGER NOT NULL DEFAULT 0, deleted_at INTEGER NOT NULL DEFAULT 0, read_at INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (chat_id, message_id))"
            )
            db.execSQL(
                "CREATE TABLE edits (id INTEGER PRIMARY KEY AUTOINCREMENT, chat_id INTEGER NOT NULL, " +
                    "message_id INTEGER NOT NULL, old_text TEXT NOT NULL, edited_at INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX edits_by_chat ON edits (chat_id)")
            db.execSQL("CREATE TABLE last_seen (user_id INTEGER PRIMARY KEY, ts INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private val appContext = context.applicationContext
    private val lock = Any()
    private var helper = Helper(appContext)

    private fun <T> db(block: (SQLiteDatabase) -> T): T = synchronized(lock) { block(helper.writableDatabase) }

    /** Сохраняет сообщения; уже известные не перезаписываются (текст меняется только через [recordEdit]). */
    fun upsert(items: List<MessageItem>) {
        if (items.isEmpty()) return
        db { d ->
            d.beginTransaction()
            try {
                for (m in items) {
                    val values = ContentValues().apply {
                        put("chat_id", m.chatId)
                        put("message_id", m.id)
                        put("sender_id", m.senderUserId ?: 0L)
                        put("text", m.text)
                        put("summary", m.summary)
                        put("outgoing", if (m.isOutgoing) 1 else 0)
                        put("date", m.date)
                    }
                    d.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                }
                d.setTransactionSuccessful()
            } finally {
                d.endTransaction()
            }
        }
    }

    /** Помечает сообщения удалёнными; возвращает id тех, что были в архиве (их можно показать). */
    fun markDeleted(chatId: Long, ids: List<Long>, at: Int): Set<Long> = db { d ->
        val kept = HashSet<Long>()
        d.beginTransaction()
        try {
            for (id in ids) {
                val values = ContentValues().apply { put("deleted_at", at) }
                val rows = d.update(
                    "messages", values, "chat_id = ? AND message_id = ? AND deleted_at = 0",
                    arrayOf(chatId.toString(), id.toString()),
                )
                if (rows > 0) kept += id
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
        kept
    }

    fun deleted(chatId: Long): List<StoredMessage> = db { d ->
        d.rawQuery(
            "SELECT message_id, sender_id, text, summary, outgoing, date FROM messages " +
                "WHERE chat_id = ? AND deleted_at > 0 ORDER BY message_id",
            arrayOf(chatId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        StoredMessage(
                            id = c.getLong(0),
                            chatId = chatId,
                            senderId = c.getLong(1),
                            text = c.getString(2),
                            summary = c.getString(3),
                            outgoing = c.getInt(4) == 1,
                            date = c.getInt(5),
                        )
                    )
                }
            }
        }
    }

    /**
     * Обновляет текст сохранённого сообщения. Возвращает прежний текст, если он отличался
     * и [keepHistory] = true (тогда он добавлен в историю правок), иначе null.
     */
    fun recordEdit(chatId: Long, messageId: Long, newText: String, newSummary: String, at: Int, keepHistory: Boolean): String? =
        db { d ->
            val old = d.rawQuery(
                "SELECT text FROM messages WHERE chat_id = ? AND message_id = ?",
                arrayOf(chatId.toString(), messageId.toString()),
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: return@db null
            if (old == newText) return@db null
            val values = ContentValues().apply {
                put("text", newText)
                put("summary", newSummary)
            }
            d.update("messages", values, "chat_id = ? AND message_id = ?", arrayOf(chatId.toString(), messageId.toString()))
            if (!keepHistory) return@db null
            val edit = ContentValues().apply {
                put("chat_id", chatId)
                put("message_id", messageId)
                put("old_text", old)
                put("edited_at", at)
            }
            d.insert("edits", null, edit)
            old
        }

    fun edits(chatId: Long): Map<Long, List<EditRecord>> = db { d ->
        val map = LinkedHashMap<Long, MutableList<EditRecord>>()
        d.rawQuery(
            "SELECT message_id, old_text, edited_at FROM edits WHERE chat_id = ? ORDER BY id",
            arrayOf(chatId.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                map.getOrPut(c.getLong(0)) { mutableListOf() }.add(EditRecord(c.getString(1), c.getInt(2)))
            }
        }
        map
    }

    /** Исходящие сообщения чата с id <= [lastId] получают время прочтения [at], если его ещё нет. */
    fun markRead(chatId: Long, lastId: Long, at: Int) {
        db { d ->
            val values = ContentValues().apply { put("read_at", at) }
            d.update(
                "messages", values, "chat_id = ? AND outgoing = 1 AND message_id <= ? AND read_at = 0",
                arrayOf(chatId.toString(), lastId.toString()),
            )
        }
    }

    fun readTimes(chatId: Long): Map<Long, Int> = db { d ->
        val map = HashMap<Long, Int>()
        d.rawQuery(
            "SELECT message_id, read_at FROM messages WHERE chat_id = ? AND outgoing = 1 AND read_at > 0",
            arrayOf(chatId.toString()),
        ).use { c -> while (c.moveToNext()) map[c.getLong(0)] = c.getInt(1) }
        map
    }

    fun setLastSeen(userId: Long, ts: Int) {
        db { d ->
            d.execSQL(
                "INSERT INTO last_seen (user_id, ts) VALUES (?, ?) " +
                    "ON CONFLICT(user_id) DO UPDATE SET ts = MAX(ts, excluded.ts)",
                arrayOf<Any>(userId, ts),
            )
        }
    }

    fun lastSeen(userId: Long): Int? = db { d ->
        d.rawQuery("SELECT ts FROM last_seen WHERE user_id = ?", arrayOf(userId.toString()))
            .use { c -> if (c.moveToFirst()) c.getInt(0) else null }
    }

    fun clear() {
        db { d ->
            d.beginTransaction()
            try {
                d.delete("messages", null, null)
                d.delete("edits", null, null)
                d.delete("last_seen", null, null)
                d.setTransactionSuccessful()
            } finally {
                d.endTransaction()
            }
        }
    }

    private fun dbFile(): File = appContext.getDatabasePath(DB_NAME)

    fun exportTo(out: OutputStream) {
        synchronized(lock) {
            helper.writableDatabase // гарантируем, что файл существует
            helper.close()
            dbFile().inputStream().use { it.copyTo(out) }
        }
    }

    /** Заменяет архив содержимым файла. Бросает исключение, если это не база SQLite. */
    fun importFrom(input: InputStream) {
        synchronized(lock) {
            val tmp = File(appContext.cacheDir, "spy_import.db")
            try {
                tmp.outputStream().use { input.copyTo(it) }
                val header = ByteArray(16)
                val read = tmp.inputStream().use { it.read(header) }
                require(read == 16 && String(header, Charsets.ISO_8859_1).startsWith("SQLite format 3")) {
                    "Это не файл базы данных"
                }
                helper.close()
                tmp.copyTo(dbFile(), overwrite = true)
                File(dbFile().path + "-wal").delete()
                File(dbFile().path + "-shm").delete()
            } finally {
                tmp.delete()
            }
        }
    }

    private companion object {
        const val DB_NAME = "yougram_spy.db"
    }
}
