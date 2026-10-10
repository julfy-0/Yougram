package app.yougram.core.ui.shape

import androidx.compose.runtime.compositionLocalOf
import kotlin.random.Random

/**
 * «Мешок» форм для одной страницы: выдаёт случайный номер формы, но каждая форма встречается не больше [maxUses] раз.
 * Когда все формы выбраны до предела (на странице больше плиток, чем count * maxUses), счётчики начинаются заново.
 */
class ShapeBag(private val count: Int = RandomShapeCount, private val maxUses: Int = 2) {
    private val uses = IntArray(count)

    fun next(): Int {
        var free = (0 until count).filter { uses[it] < maxUses }
        if (free.isEmpty()) {
            uses.fill(0)
            free = (0 until count).toList()
        }
        val index = free[Random.nextInt(free.size)]
        uses[index]++
        return index
    }
}

/** Мешок форм текущей страницы настроек; null — страница его не задала, формы выбираются без ограничений. */
val LocalShapeBag = compositionLocalOf<ShapeBag?> { null }