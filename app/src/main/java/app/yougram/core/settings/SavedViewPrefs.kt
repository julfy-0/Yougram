package app.yougram.core.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Показывать «Избранное» списком тем (по источникам пересылки). По умолчанию выключено: открывается обычный чат. */
object SavedViewPrefs {
    private const val FILE = "yougram_saved_view"
    private const val KEY = "by_topics"

    private var state: MutableStateFlow<Boolean>? = null

    private fun mutable(context: Context): MutableStateFlow<Boolean> = synchronized(this) {
        state ?: MutableStateFlow(
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false),
        ).also { state = it }
    }

    fun state(context: Context): StateFlow<Boolean> = mutable(context)

    fun set(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
        mutable(context).value = enabled
    }
}
