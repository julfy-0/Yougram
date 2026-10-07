package app.yougram.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.TextButton as M3TextButton

/*
 * Кнопки Material 3 Expressive: форма «перетекает» при нажатии (ButtonDefaults.shapes()).
 * Если вызывающий код задаёт свою [shape], используется обычная кнопка с этой формой.
 */

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    if (shape != null) {
        M3Button(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, content = content)
    } else {
        M3Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            content = content,
        )
    }
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    if (shape != null) {
        M3OutlinedButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, content = content)
    } else {
        M3OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            content = content,
        )
    }
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    if (shape != null) {
        M3TextButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, content = content)
    } else {
        M3TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            content = content,
        )
    }
}
