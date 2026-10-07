package app.yougram.feature.settings.privacy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yougram.feature.account.data.BlockedItem
import app.yougram.feature.account.data.WebsiteItem
import app.yougram.feature.settings.SettingsDetailsViewModel
import app.yougram.feature.settings.component.ConfirmDialog
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn

/** Чёрный список: нажатие на строку предлагает разблокировать. */
@Composable
fun BlockedScreen(viewModel: SettingsDetailsViewModel, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadBlocked() }
    var toUnblock by remember { mutableStateOf<BlockedItem?>(null) }
    val list = state.blocked

    SettingsPageColumn(contentPadding) {
        when {
            list == null -> LoadingBox()
            list.isEmpty() -> SettingsFootnote("Чёрный список пуст.")
            else -> {
                SettingGroup {
                    list.forEach { item ->
                        item { SettingRow(item.title.ifEmpty { "—" }, icon = Icons.Filled.Block, onClick = { toUnblock = item }) }
                    }
                }
                SettingsFootnote("Нажмите на пользователя, чтобы разблокировать.")
            }
        }
    }

    toUnblock?.let { item ->
        ConfirmDialog(
            title = "Разблокировать?",
            text = item.title,
            confirmLabel = "Разблокировать",
            onConfirm = {
                viewModel.unblock(item)
                toUnblock = null
            },
            onDismiss = { toUnblock = null },
        )
    }
}

/** Сайты, где вход выполнен через Telegram: можно отключить один или все. */
@Composable
fun WebsitesScreen(viewModel: SettingsDetailsViewModel, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadWebsites() }
    var toDisconnect by remember { mutableStateOf<WebsiteItem?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    val list = state.websites

    SettingsPageColumn(contentPadding) {
        when {
            list == null -> LoadingBox()
            list.isEmpty() -> SettingsFootnote("Вы не авторизовались ни на одном сайте через Telegram.")
            else -> {
                SettingGroup {
                    list.forEach { site ->
                        item {
                            SettingRow(
                                site.domain,
                                subtitle = site.subtitle.ifEmpty { null },
                                icon = Icons.Filled.Public,
                                onClick = { toDisconnect = site },
                            )
                        }
                    }
                }
                SettingGroup {
                    item { SettingRow("Отключить все сайты", icon = Icons.Filled.Delete, onClick = { confirmAll = true }) }
                }
                SettingsFootnote("Нажмите на сайт, чтобы отключить его.")
            }
        }
    }

    toDisconnect?.let { site ->
        ConfirmDialog(
            title = "Отключить сайт?",
            text = site.domain,
            confirmLabel = "Отключить",
            onConfirm = {
                viewModel.disconnectWebsite(site.id)
                toDisconnect = null
            },
            onDismiss = { toDisconnect = null },
        )
    }
    if (confirmAll) {
        ConfirmDialog(
            title = "Отключить все сайты?",
            text = "Вход через Telegram на этих сайтах будет отключён.",
            confirmLabel = "Отключить",
            onConfirm = {
                viewModel.disconnectAllWebsites()
                confirmAll = false
            },
            onDismiss = { confirmAll = false },
        )
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}