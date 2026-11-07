package app.yougram.feature.settings.general

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.telegram.getOrThrow
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import dev.g000sha256.tdl.dto.StickerSetInfo
import dev.g000sha256.tdl.dto.StickerTypeRegular
import kotlinx.coroutines.launch

private data class SetRow(val id: Long, val title: String, val count: Int, val official: Boolean)

private fun StickerSetInfo.row() = SetRow(id = id, title = title, count = size, official = isOfficial)

private enum class StickerTab(val title: String) { Mine("Мои"), Archived("Архив"), Trending("Популярные") }

private val QuickReactions = listOf("👍", "❤️", "🔥", "🥰", "👏", "😁", "🎉", "🤩", "😢", "🙏")

/** Управление наборами стикеров и быстрой реакцией (аналог StickersActivity из Telegram). */
@Composable
fun StickersScreen(client: TdlClient, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<SetRow>?>(null) }
    var reaction by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val current = StickerTab.entries[tab]

    fun fail(e: Throwable) = Toast.makeText(context, e.message ?: tr("Ошибка"), Toast.LENGTH_SHORT).show()

    LaunchedEffect(tab, reload) {
        rows = null
        runCatching {
            when (current) {
                StickerTab.Mine -> client.getInstalledStickerSets(stickerType = StickerTypeRegular()).getOrThrow().sets
                StickerTab.Archived -> client.getArchivedStickerSets(
                    stickerType = StickerTypeRegular(), offsetStickerSetId = 0L, limit = 100,
                ).getOrThrow().sets
                StickerTab.Trending -> client.getTrendingStickerSets(
                    stickerType = StickerTypeRegular(), offset = 0, limit = 30,
                ).getOrThrow().sets
            }.map { it.row() }
        }.onSuccess { rows = it }.onFailure { rows = emptyList(); fail(it) }
    }

    fun change(id: Long, installed: Boolean, archived: Boolean) {
        scope.launch {
            runCatching { client.changeStickerSet(setId = id, isInstalled = installed, isArchived = archived).getOrThrow() }
                .onFailure(::fail)
            reload++
        }
    }

    fun move(index: Int, delta: Int) {
        val list = rows?.toMutableList() ?: return
        val to = index + delta
        if (to !in list.indices) return
        list.add(to, list.removeAt(index))
        rows = list
        scope.launch {
            runCatching {
                client.reorderInstalledStickerSets(
                    stickerType = StickerTypeRegular(),
                    stickerSetIds = list.map { it.id }.toLongArray(),
                ).getOrThrow()
            }.onFailure { fail(it); reload++ }
        }
    }

    SettingsPageColumn(contentPadding) {
        SectionLabel("Быстрая реакция")
        SettingGroup {
            item {
                SettingRow(
                    "Реакция по двойному нажатию",
                    subtitle = "Ставится на сообщение быстрым действием",
                    belowFullWidth = true,
                    below = {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickReactions.forEach { e ->
                                FilterChip(
                                    selected = reaction == e,
                                    onClick = {
                                        reaction = e
                                        scope.launch {
                                            runCatching { client.setDefaultReactionType(reactionType = ReactionTypeEmoji(emoji = e)).getOrThrow() }
                                                .onFailure(::fail)
                                        }
                                    },
                                    label = { Text(e) },
                                )
                            }
                        }
                    },
                )
            }
        }

        SectionLabel("Наборы стикеров")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StickerTab.entries.forEachIndexed { i, t ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(t.title) })
            }
        }

        val list = rows
        if (list == null) {
            SettingsFootnote("Загрузка…")
        } else if (list.isEmpty()) {
            SettingsFootnote(
                when (current) {
                    StickerTab.Mine -> "Установленных наборов нет."
                    StickerTab.Archived -> "Архив пуст."
                    StickerTab.Trending -> "Популярные наборы недоступны."
                },
            )
        } else {
            SettingGroup {
                list.forEachIndexed { index, s ->
                    item {
                        SettingRow(
                            title = s.title,
                            subtitle = tr("{0} стикеров", s.count) + if (s.official) " · " + tr("официальный") else "",
                            trailing = {
                                Row {
                                    when (current) {
                                        StickerTab.Mine -> {
                                            IconButton(onClick = { move(index, -1) }, enabled = index > 0) { Icon(Icons.Filled.ArrowUpward, tr("Выше")) }
                                            IconButton(onClick = { move(index, 1) }, enabled = index < list.lastIndex) { Icon(Icons.Filled.ArrowDownward, tr("Ниже")) }
                                            IconButton(onClick = { change(s.id, installed = true, archived = true) }) { Icon(Icons.Filled.Archive, tr("В архив")) }
                                            IconButton(onClick = { change(s.id, installed = false, archived = false) }) {
                                                Icon(Icons.Filled.Delete, tr("Удалить"), tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        StickerTab.Archived -> {
                                            IconButton(onClick = { change(s.id, installed = true, archived = false) }) { Icon(Icons.Filled.Unarchive, tr("Вернуть")) }
                                            IconButton(onClick = { change(s.id, installed = false, archived = false) }) {
                                                Icon(Icons.Filled.Delete, tr("Удалить"), tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        StickerTab.Trending -> IconButton(onClick = { change(s.id, installed = true, archived = false) }) {
                                            Icon(Icons.Filled.Add, tr("Добавить"))
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
