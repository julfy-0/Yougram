package app.yougram.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun FoldersScreen(viewModel: SettingsDetailsViewModel, contentPadding: PaddingValues) {
    val folders by viewModel.folders.collectAsState()

    SettingsPageColumn(contentPadding) {
        if (folders.isEmpty()) {
            SettingsFootnote("Папок пока нет. Создайте их в официальном приложении Telegram, и они появятся здесь.")
        } else {
            SectionLabel("Ваши папки")
            SettingGroup {
                folders.forEach { folder -> item { SettingRow(folder.title, icon = Icons.Filled.Folder) } }
            }
            SettingsFootnote("Папки отображаются вкладками над списком чатов. Создание и редактирование появятся позже.")
        }
    }
}