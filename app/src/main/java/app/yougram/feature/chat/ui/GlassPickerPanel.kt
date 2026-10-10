package app.yougram.feature.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.GlassSettings
import app.yougram.core.ui.glass.BackdropState
import app.yougram.core.ui.glass.glass

/**
 * Нижняя панель эмодзи / стикеров / GIF в «стеклянном» стиле: размытая копия чата под панелью
 * и поверх неё подкраска, плотность которой задаёт [transparency] (0 — сплошная, 1 — полностью прозрачная).
 *
 * Рисуется внутри основного дерева (а не в отдельном окне, как ModalBottomSheet), поэтому
 * видит тот же [backdrop], что и верхняя и нижняя панели чата. Класть её внутрь backdropSource нельзя.
 */
@Composable
fun GlassPickerPanel(
    visible: Boolean,
    backdrop: BackdropState,
    glass: GlassSettings,
    transparency: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)

    // Блюр берём общий (слой размытия один на экран), прозрачность — своя.
    val panelGlass = remember(glass.blurRadius, glass.blurType, transparency) {
        glass.copy(opacity = (1f - transparency).coerceIn(0f, 1f))
    }

    Box(modifier.fillMaxSize()) {
        // Невидимая область вне панели: тап закрывает меню (пока оно открыто).
        if (visible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } +
                fadeIn(tween(220)),
            exit = slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it } +
                fadeOut(tween(200)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .glass(
                        backdrop,
                        panelGlass,
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        tint = MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                    // Поглощаем тапы по самой панели, чтобы они не доходили до закрывающей области.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 4.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
                content()
                Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
        }
    }
}

/** Цвет «плашек» внутри стеклянной панели: полупрозрачный, чтобы блюр был виден и под ними. */
@Composable
fun pickerChipColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
