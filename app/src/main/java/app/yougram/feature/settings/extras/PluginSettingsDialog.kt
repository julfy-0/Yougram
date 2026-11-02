package app.yougram.feature.settings.extras

import app.yougram.core.i18n.tr
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.TopAppBarDefaults
import app.yougram.core.ui.component.Button
import androidx.compose.material3.Text as M3Text
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yougram.feature.settings.component.CheckSwitch
import app.yougram.feature.settings.component.ConnectedChoiceGroup
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.plugin.InstalledPlugin
import app.yougram.plugin.NativePluginManager
import app.yougram.plugin.PluginSetting
import app.yougram.plugin.PluginSettingType

/**
 * Настройки плагина на весь экран по Material 3 Expressive: большая сворачивающаяся шапка, сгруппированные
 * строки настроек (как на остальных страницах приложения), «Сохранить» — акцентная кнопка в шапке.
 * Поля строятся по схеме из manifest.json, сохранение отправляет плагину settings_changed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PluginSettingsDialog(plugin: InstalledPlugin, manager: NativePluginManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manifest = plugin.manifest
    val drafts = remember(manifest.id) {
        mutableStateMapOf<String, String>().apply {
            manifest.settings.forEach { put(it.key, manager.settings.raw(manifest, it.key)) }
        }
    }
    val save = {
        runCatching { manager.applySettings(manifest.id, drafts.toMap()) }
            .onSuccess { onDismiss() }
            .onFailure { Toast.makeText(context, it.message ?: tr("Не удалось сохранить"), Toast.LENGTH_LONG).show() }
        Unit
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.surface,
                // Клавиатуру учитываем сами (imePadding у списка ниже): так поле ввода всегда остаётся над ней.
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                topBar = {
                    LargeFlexibleTopAppBar(
                        title = { M3Text(manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        subtitle = { M3Text("v${manifest.version} · ${manifest.author}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад"))
                            }
                        },
                        actions = {
                            if (manifest.settings.isNotEmpty()) {
                                Button(onClick = save, modifier = Modifier.padding(end = 12.dp)) { Text("Сохранить") }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (manifest.description.isNotBlank() || manifest.permissions.isNotEmpty()) {
                            SettingGroup {
                                item {
                                    SettingRow(
                                        title = "О плагине",
                                        subtitle = manifest.description.ifBlank { null },
                                        icon = Icons.Filled.Extension,
                                        below = if (manifest.permissions.isEmpty()) null else ({
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                manifest.permissions.forEach { p ->
                                                    SuggestionChip(onClick = {}, label = { M3Text(p) })
                                                }
                                            }
                                        }),
                                        belowFullWidth = true,
                                    )
                                }
                            }
                        }
                        if (manifest.settings.isNotEmpty()) {
                            SectionLabel("Настройки")
                            SettingGroup {
                                manifest.settings.forEach { setting ->
                                    item {
                                        PluginSettingRow(setting, drafts[setting.key].orEmpty()) { drafts[setting.key] = it }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/** Одна настройка плагина как строка группы настроек; тип поля берётся из схемы manifest.json. */
@Composable
private fun PluginSettingRow(setting: PluginSetting, value: String, onChange: (String) -> Unit) {
    val subtitle = setting.description.ifBlank { null }
    when (setting.type) {
        PluginSettingType.SWITCH -> SettingRow(
            title = setting.title,
            subtitle = subtitle,
            icon = Icons.Filled.ToggleOn,
            onClick = { onChange((value != "true").toString()) },
            trailing = { CheckSwitch(value == "true") { onChange(it.toString()) } },
        )
        PluginSettingType.TEXT, PluginSettingType.NUMBER -> SettingRow(
            title = setting.title,
            subtitle = subtitle,
            icon = Icons.Filled.TextFields,
            belowFullWidth = true,
            below = {
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = setting.type == PluginSettingType.NUMBER,
                    keyboardOptions = if (setting.type == PluginSettingType.NUMBER) {
                        KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    } else {
                        KeyboardOptions.Default
                    },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            },
        )
        PluginSettingType.CHOICE -> SettingRow(
            title = setting.title,
            subtitle = subtitle,
            icon = Icons.Filled.Tune,
            belowFullWidth = true,
            below = {
                val options = setting.options
                Column(Modifier.padding(top = 8.dp)) {
                    if (options.size in 2..3) {
                        // Мало вариантов — «связанная» группа кнопок M3 Expressive.
                        ConnectedChoiceGroup(
                            options = options,
                            selected = options.firstOrNull { it.value == value } ?: options.first(),
                            label = { it.title },
                            onSelect = { onChange(it.value) },
                        )
                    } else {
                        options.forEach { option ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onChange(option.value) }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = value == option.value, onClick = { onChange(option.value) })
                                M3Text(option.title, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
        )
    }
}
