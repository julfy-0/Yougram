package app.yougram.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun LanguageScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val language = Locale.getDefault().let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.uppercase() } }

    fun openLanguageSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
        } else {
            Intent(Settings.ACTION_LOCALE_SETTINGS)
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show()
        }
    }

    SettingsPageColumn(contentPadding) {
        SettingGroup {
            item { SettingRow("Язык системы", icon = Icons.Filled.Language, value = language) }
            item {
                SettingRow(
                    "Изменить язык",
                    subtitle = "Откроются системные настройки",
                    icon = Icons.Filled.Translate,
                    onClick = ::openLanguageSettings,
                )
            }
        }
        SettingsFootnote("Интерфейс Yougram пока только на русском, перевод появится позже.")
    }
}