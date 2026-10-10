package app.yougram.core.ui.shape

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/** Сколько разных форм доступно для значков и аватарок. */
const val RandomShapeCount = 10

/** Форма Expressive по номеру (0 until [RandomShapeCount]): звёзды, лепестки, ромбы, пятиугольники, круги и квадраты. */
@Composable
fun randomPolygonShape(index: Int): Shape = when (((index % RandomShapeCount) + RandomShapeCount) % RandomShapeCount) {
    0 -> MaterialShapes.Cookie6Sided.toShape()
    1 -> MaterialShapes.Circle.toShape()
    2 -> MaterialShapes.Clover4Leaf.toShape()
    3 -> MaterialShapes.Square.toShape()
    4 -> MaterialShapes.Sunny.toShape()
    5 -> MaterialShapes.Pentagon.toShape()
    6 -> MaterialShapes.Flower.toShape()
    7 -> MaterialShapes.Cookie9Sided.toShape()
    8 -> MaterialShapes.SoftBurst.toShape()
    else -> MaterialShapes.Gem.toShape()
}