package app.yougram.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.yougram.data.MessageFilters
import app.yougram.data.SettingsRepository
import app.yougram.data.SpyPrefs
import app.yougram.data.SpyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Хаб с категориями: призрак, шпион, фильтры. */
@Composable
fun ExtrasScreen(settings: SettingsRepository, contentPadding: PaddingValues, onNavigate: (SettingsPage) -> Unit) {
    val ghost by settings.ghost.collectAsState()
    val filters by settings.filterPrefs.collectAsState()
    val badge by settings.badge.collectAsState()
    SettingsPageColumn(contentPadding) {
        SectionLabel("Категории")
        SettingGroup {
            item {
                SettingRow(
                    "Режим призрака",
                    subtitle = if (ghost.enabled) "Включён" else "Выключен",
                    icon = Icons.Filled.VisibilityOff,
                    onClick = { onNavigate(SettingsPage.Ghost) },
                )
            }
            item {
                SettingRow(
                    "Шпион",
                    subtitle = "Удалённые сообщения, правки, вложения",
                    icon = Icons.Filled.Visibility,
                    onClick = { onNavigate(SettingsPage.Spy) },
                )
            }
            item {
                SettingRow(
                    "Фильтры",
                    subtitle = if (filters.enabled) "Включены" else "Выключены",
                    icon = Icons.Filled.FilterList,
                    onClick = { onNavigate(SettingsPage.MessageFilters) },
                )
            }
            item {
                SwitchRow(
                    "Значок Yougram",
                    badge,
                    { v -> settings.setBadge(v) },
                    subtitle = "Ставит метку в «О себе»: другие пользователи Yougram увидят значок у вашего имени",
                    icon = Icons.Filled.VerifiedUser,
                )
            }
        }
    }
}

@Composable
fun GhostModeScreen(settings: SettingsRepository, contentPadding: PaddingValues) {
    val ghost by settings.ghost.collectAsState()
    SettingsPageColumn(contentPadding) {
        SectionLabel("Режим призрака")
        SettingGroup {
            item {
                SwitchRow(
                    "Режим призрака",
                    ghost.enabled,
                    { v -> settings.updateGhost { g -> g.copy(enabled = v) } },
                    subtitle = "Не отправлять отметку «прочитано»",
                    icon = Icons.Filled.VisibilityOff,
                )
            }
            item {
                SwitchRow(
                    "Читать при действиях",
                    ghost.readOnAction,
                    { v -> settings.updateGhost { g -> g.copy(readOnAction = v) } },
                )
            }
        }
        SettingsFootnote(
            "Пока режим включён, открытие чата не помечает сообщения прочитанными. " +
                "С опцией «Читать при действиях» чат читается автоматически, когда вы сами отправляете сообщение."
        )
    }
}

@Composable
fun SpyModeScreen(settings: SettingsRepository, spy: SpyStore, contentPadding: PaddingValues) {
    val p by settings.spyPrefs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editFolder by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launchToast(context, "База данных экспортирована") {
            context.contentResolver.openOutputStream(uri)?.use { spy.exportTo(it) } ?: error("Нет доступа к файлу")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launchToast(context, "База данных импортирована") {
            context.contentResolver.openInputStream(uri)?.use { spy.importFrom(it) } ?: error("Нет доступа к файлу")
        }
    }

    SettingsPageColumn(contentPadding) {
        SectionLabel("Режим шпиона")
        SettingGroup {
            item { SwitchRow("Сохранять удалённые сообщения", p.saveDeleted, { v -> settings.updateSpy { s -> s.copy(saveDeleted = v) } }) }
            item { SwitchRow("Сохранять историю правок", p.saveEdits, { v -> settings.updateSpy { s -> s.copy(saveEdits = v) } }) }
            item { SwitchRow("Сохранять в чатах с ботами", p.saveInBots, { v -> settings.updateSpy { s -> s.copy(saveInBots = v) } }) }
            item { SwitchRow("Сохранять дату чтения", p.saveReadDate, { v -> settings.updateSpy { s -> s.copy(saveReadDate = v) } }) }
        }
        SettingsFootnote("Локально сохраняет данные о чтении сообщений. Будет использоваться, если Telegram не предоставит дату чтения.")
        SettingGroup {
            item { SwitchRow("Сохранять последний онлайн", p.saveLastOnline, { v -> settings.updateSpy { s -> s.copy(saveLastOnline = v) } }) }
        }
        SettingsFootnote(
            "Запоминает последний известный онлайн людей со скрытым последним посещением (по их сообщениям). " +
                "Вы увидите очень приблизительно, когда они были в сети в последний раз."
        )
        SettingGroup {
            item {
                SwitchRow(
                    "Сохранять вложения",
                    p.saveAttachments,
                    { v -> settings.updateSpy { s -> s.copy(saveAttachments = v) } },
                    subtitle = "Входящие файлы до 100 МБ",
                )
            }
            item { SwitchRow("Вложения из каналов", p.attachmentsChannels, { v -> settings.updateSpy { s -> s.copy(attachmentsChannels = v) } }) }
            item { SettingRow("Папка вложений", value = p.folderName, onClick = { editFolder = true }) }
        }
        SectionLabel("Максимальный размер папки")
        SettingGroup {
            item {
                SettingRow(
                    "Лимит",
                    subtitle = SpyPrefs.MAX_FOLDER_LABELS[p.maxFolderIndex],
                    below = {
                        DotSlider(
                            value = p.maxFolderIndex.toFloat(),
                            onValueChange = { v ->
                                val index = v.roundToInt().coerceIn(0, SpyPrefs.MAX_FOLDER_LABELS.lastIndex)
                                if (index != p.maxFolderIndex) settings.updateSpy { s -> s.copy(maxFolderIndex = index) }
                            },
                            valueRange = 0f..SpyPrefs.MAX_FOLDER_LABELS.lastIndex.toFloat(),
                            dots = SpyPrefs.MAX_FOLDER_LABELS.size - 2,
                        )
                    },
                )
            }
        }
        SettingsFootnote(
            "Если папка превысит лимит, самые старые вложения будут удалены с устройства. " +
                "Файлы лежат в Android/data/app.yougram/files/${p.folderName}."
        )
        SettingGroup {
            item { SettingRow("Экспорт базы данных", icon = Icons.Filled.Upload, onClick = { exportLauncher.launch("yougram-spy.db") }) }
            item { SettingRow("Импорт базы данных", icon = Icons.Filled.Download, onClick = { importLauncher.launch(arrayOf("*/*")) }) }
        }
        SettingGroup {
            item { SettingRow("Очистить", subtitle = "Удалить архив сообщений, правок и онлайна", icon = Icons.Filled.DeleteSweep, onClick = { confirmClear = true }) }
        }
    }

    if (editFolder) {
        EditDialog(
            title = "Папка вложений",
            labels = listOf("Название папки"),
            initial = listOf(p.folderName),
            onConfirm = { values ->
                settings.updateSpy { s -> s.copy(folderName = values[0].trim().ifEmpty { SpyPrefs.DEFAULT_FOLDER }) }
                editFolder = false
            },
            onDismiss = { editFolder = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Очистить архив?",
            text = "Сохранённые удалённые сообщения, история правок и время прочтения будут удалены. Скачанные вложения останутся.",
            confirmLabel = "Очистить",
            onConfirm = {
                confirmClear = false
                scope.launchToast(context, "Архив очищен") { spy.clear() }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/** Выполняет [block] в IO и показывает тост об успехе или тексте ошибки. */
private fun CoroutineScope.launchToast(context: Context, success: String, block: () -> Unit) {
    launch {
        val error = withContext(Dispatchers.IO) { runCatching { block() }.exceptionOrNull() }
        Toast.makeText(context, error?.message?.let { "Ошибка: $it" } ?: success, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MessageFiltersScreen(settings: SettingsRepository, contentPadding: PaddingValues, onNavigate: (SettingsPage) -> Unit) {
    val f by settings.filterPrefs.collectAsState()
    SettingsPageColumn(contentPadding) {
        SectionLabel("Основные")
        SettingGroup {
            item { SwitchRow("Включить фильтры", f.enabled, { v -> settings.updateFilters { x -> x.copy(enabled = v) } }) }
            item { SwitchRow("Включить общие фильтры в чатах", f.sharedInChats, { v -> settings.updateFilters { x -> x.copy(sharedInChats = v) } }) }
            item { SwitchRow("Скрывать от пользователей в ЧС", f.hideBlocked, { v -> settings.updateFilters { x -> x.copy(hideBlocked = v) } }) }
        }
        SettingGroup {
            item {
                SettingRow(
                    "Общие фильтры",
                    subtitle = "${f.patterns.size} фильтров",
                    onClick = { onNavigate(SettingsPage.SharedFilters) },
                )
            }
            item {
                SettingRow(
                    "Теневой бан",
                    subtitle = "${f.shadowBanned.size} пользователей",
                    onClick = { onNavigate(SettingsPage.ShadowBan) },
                )
            }
        }
        SettingsFootnote("Сообщения скрываются только на этом устройстве: собеседник ничего не узнает, а в Telegram они остаются.")
    }
}

@Composable
fun SharedFiltersScreen(settings: SettingsRepository, contentPadding: PaddingValues) {
    val f by settings.filterPrefs.collectAsState()
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }

    SettingsPageColumn(contentPadding) {
        SettingGroup {
            f.patterns.forEach { pattern ->
                item { SettingRow(pattern, subtitle = "Нажмите, чтобы удалить", onClick = { deleting = pattern }) }
            }
            item { SettingRow("Добавить фильтр", icon = Icons.Filled.Add, onClick = { adding = true }) }
        }
        SettingsFootnote(
            "Сообщения, содержащие слово или фразу (без учёта регистра), скрываются. " +
                "Регулярное выражение указывается в слэшах, например /https?:\\/\\/\\S+/. " +
                "Работает, если включены «Фильтры» и «Общие фильтры в чатах»."
        )
    }

    if (adding) {
        EditDialog(
            title = "Новый фильтр",
            labels = listOf("Слово, фраза или /regex/"),
            initial = listOf(""),
            onConfirm = { values ->
                val pattern = values[0].trim()
                if (MessageFilters.isValid(pattern)) {
                    settings.addPattern(pattern)
                    adding = false
                } else {
                    Toast.makeText(context, "Некорректное выражение", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { adding = false },
        )
    }
    deleting?.let { pattern ->
        ConfirmDialog(
            title = "Удалить фильтр?",
            text = pattern,
            confirmLabel = "Удалить",
            onConfirm = {
                settings.removePattern(pattern)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
fun ShadowBanScreen(settings: SettingsRepository, contentPadding: PaddingValues) {
    val f by settings.filterPrefs.collectAsState()
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Long?>(null) }

    SettingsPageColumn(contentPadding) {
        SettingGroup {
            f.shadowBanned.forEach { user ->
                item {
                    SettingRow(
                        user.name.ifEmpty { "ID ${user.userId}" },
                        subtitle = "ID ${user.userId} · нажмите, чтобы убрать",
                        onClick = { removing = user.userId },
                    )
                }
            }
            item { SettingRow("Добавить по ID", icon = Icons.Filled.Add, onClick = { adding = true }) }
        }
        SettingsFootnote(
            "Сообщения этих пользователей не показываются в чатах. " +
                "Быстрый способ: долгое нажатие на сообщение в чате → «Теневой бан»."
        )
    }

    if (adding) {
        EditDialog(
            title = "Теневой бан",
            labels = listOf("ID пользователя", "Имя (необязательно)"),
            initial = listOf("", ""),
            onConfirm = { values ->
                val id = values[0].trim().toLongOrNull()
                if (id != null && id > 0) {
                    settings.addShadowBan(id, values[1].trim())
                    adding = false
                } else {
                    Toast.makeText(context, "Введите числовой ID", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { adding = false },
        )
    }
    removing?.let { id ->
        ConfirmDialog(
            title = "Убрать из теневого бана?",
            text = "Сообщения пользователя снова будут видны.",
            confirmLabel = "Убрать",
            onConfirm = {
                settings.removeShadowBan(id)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}
