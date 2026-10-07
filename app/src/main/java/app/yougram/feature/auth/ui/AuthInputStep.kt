package app.yougram.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import app.yougram.core.ui.component.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class AuthFieldKind { Phone, Code, Password }

/** Один шаг входа: заголовок с иконкой, поле ввода и кнопка «Далее». */
@Composable
fun AuthInputStep(
    kind: AuthFieldKind,
    title: String,
    subtitle: String,
    label: String,
    busy: Boolean,
    onEdit: () -> Unit,
    onSubmit: (String) -> Unit,
    hint: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var value by rememberSaveable(kind) { mutableStateOf("") }
    var visible by rememberSaveable(kind) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val canSubmit = value.isNotBlank() && !busy

    LaunchedEffect(kind) {
        delay(350)
        runCatching { focus.requestFocus() }
    }

    fun submit() {
        if (!canSubmit) return
        keyboard?.hide()
        onSubmit(value)
    }

    val icon = when (kind) {
        AuthFieldKind.Phone -> Icons.Filled.Phone
        AuthFieldKind.Code -> Icons.Filled.Sms
        AuthFieldKind.Password -> Icons.Filled.Lock
    }
    val trailing: (@androidx.compose.runtime.Composable () -> Unit)? = if (kind == AuthFieldKind.Password) {
        @androidx.compose.runtime.Composable {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Скрыть пароль" else "Показать пароль",
                )
            }
        }
    } else {
        null
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(scheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                value = sanitize(kind, raw)
                onEdit()
            },
            label = { Text(label) },
            supportingText = hint?.let { { Text(it) } },
            singleLine = true,
            enabled = !busy,
            textStyle = if (kind == AuthFieldKind.Code) {
                MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, letterSpacing = 6.sp)
            } else {
                LocalTextStyle.current
            },
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = when (kind) {
                    AuthFieldKind.Phone -> KeyboardType.Phone
                    AuthFieldKind.Code -> KeyboardType.Number
                    AuthFieldKind.Password -> KeyboardType.Password
                },
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            visualTransformation = if (kind == AuthFieldKind.Password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = trailing,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = scheme.surfaceContainerHighest.copy(alpha = 0.5f),
                unfocusedContainerColor = scheme.surfaceContainerHighest.copy(alpha = 0.35f),
                disabledContainerColor = scheme.surfaceContainerHighest.copy(alpha = 0.25f),
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { submit() },
            enabled = canSubmit,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (busy) {
                LoadingIndicator(Modifier.size(22.dp), color = LocalContentColor.current)
            } else {
                Text("Далее", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun sanitize(kind: AuthFieldKind, raw: String): String = when (kind) {
    AuthFieldKind.Phone -> raw.filter { it.isDigit() || it in "+ ()-" }.take(20)
    AuthFieldKind.Code -> raw.filter { it.isDigit() }.take(8)
    AuthFieldKind.Password -> raw
}