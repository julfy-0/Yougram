package app.yougram.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.yougram.data.AuthStep

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val step by viewModel.step.collectAsState()
    val ui by viewModel.ui.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Yougram", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(32.dp))

            when (val s = step) {
                AuthStep.Loading, AuthStep.Ready -> CircularProgressIndicator()

                AuthStep.MissingCredentials -> Text(
                    "Не заданы TG_API_ID и TG_API_HASH.\n\n" +
                        "1. Получите их на my.telegram.org (API development tools).\n" +
                        "2. Впишите в файл local.properties в корне проекта:\n" +
                        "TG_API_ID=123456\nTG_API_HASH=abcdef...\n" +
                        "3. Пересоберите и запустите приложение.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                AuthStep.EnterPhone -> InputStep(
                    title = "Введите номер телефона",
                    label = "Номер (например, +380...)",
                    keyboardType = KeyboardType.Phone,
                    busy = ui.busy,
                    onSubmit = viewModel::submitPhone,
                )

                is AuthStep.EnterCode -> InputStep(
                    title = "Введите код из Telegram",
                    label = "Код",
                    keyboardType = KeyboardType.Number,
                    busy = ui.busy,
                    onSubmit = viewModel::submitCode,
                )

                is AuthStep.EnterPassword -> InputStep(
                    title = "Облачный пароль (2FA)",
                    label = if (s.hint.isNotEmpty()) "Пароль (подсказка: ${s.hint})" else "Пароль",
                    keyboardType = KeyboardType.Password,
                    secret = true,
                    busy = ui.busy,
                    onSubmit = viewModel::submitPassword,
                )

                is AuthStep.Unsupported -> Text(
                    "Этот способ входа (${s.description}) пока не поддерживается.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            ui.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun InputStep(
    title: String,
    label: String,
    keyboardType: KeyboardType,
    busy: Boolean,
    onSubmit: (String) -> Unit,
    secret: Boolean = false,
) {
    // Ключ по заголовку: при смене шага поле очищается.
    var value by rememberSaveable(title) { mutableStateOf("") }

    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onSubmit(value) },
        enabled = value.isNotBlank() && !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) CircularProgressIndicator(Modifier.height(20.dp)) else Text("Далее")
    }
}
