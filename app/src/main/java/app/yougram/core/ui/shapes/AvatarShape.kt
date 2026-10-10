package app.yougram.core.ui.shape

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.rememberDeviceTier
import kotlin.random.Random

/** Соль на запуск приложения: формы случайные, но у одного и того же чата не меняются при прокрутке списка. */
private val SessionSalt = Random.nextLong()

/** Случайная (но стабильная для [key]) форма аватарки; на слабых устройствах — обычный круг. */
@Composable
fun rememberAvatarShape(key: Long): Shape {
    if (rememberDeviceTier() == DeviceTier.Low) return CircleShape
    var h = key xor SessionSalt
    h *= -0x61c8864680b583ebL
    h = h xor (h ushr 32)
    val index = ((h % RandomShapeCount) + RandomShapeCount).toInt() % RandomShapeCount
    return randomPolygonShape(index)
}