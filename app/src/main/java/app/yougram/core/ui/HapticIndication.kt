package app.yougram.core.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch

/**
 * Оборачивает системную индикацию (ripple): каждый успешный клик по любому элементу, который использует
 * [androidx.compose.foundation.LocalIndication] (clickable, кнопки, строки, вкладки, чипы и т.д.), даёт тактильный отклик.
 * Включается и настраивается теми же параметрами, что и остальной отклик ([Haptics.enabled], [Haptics.intensity]).
 */
@Composable
fun rememberHapticIndication(base: Indication): Indication = remember(base) {
    if (base is IndicationNodeFactory) HapticIndication(base) else base
}

private class HapticIndication(private val base: IndicationNodeFactory) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        HapticIndicationNode(interactionSource, base.create(interactionSource))

    override fun equals(other: Any?): Boolean = other is HapticIndication && other.base == base

    override fun hashCode(): Int = base.hashCode() * 31 + 1
}

private class HapticIndicationNode(
    private val interactionSource: InteractionSource,
    inner: DelegatableNode,
) : DelegatingNode(), CompositionLocalConsumerModifierNode {

    init {
        delegate(inner)
    }

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                if (interaction is PressInteraction.Release) {
                    Haptics.perform(currentValueOf(LocalView), Haptics.Kind.Click)
                }
            }
        }
    }
}
