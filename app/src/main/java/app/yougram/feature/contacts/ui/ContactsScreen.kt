package app.yougram.feature.contacts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.PlateArea
import app.yougram.core.ui.component.FileAvatar
import app.yougram.core.ui.glass.plateColor
import app.yougram.feature.chat.data.ContactItem
import app.yougram.feature.settings.component.segmentShape

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    contentPadding: PaddingValues,
    onOpenChat: (Long) -> Unit,
    /** Строка поиска из нижней/верхней панели; пусто — показываем всех. */
    query: String = "",
) {
    val state by viewModel.state.collectAsState()
    val contacts = remember(state.contacts, query) {
        val q = query.trim()
        if (q.isEmpty()) state.contacts else state.contacts.filter { it.name.contains(q, ignoreCase = true) }
    }
    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingIndicator(Modifier.align(Alignment.Center))
            contacts.isEmpty() -> Text(
                state.error ?: if (state.contacts.isEmpty()) "Контактов нет" else "Ничего не найдено",
                Modifier.align(Alignment.Center).padding(contentPadding),
                color = if (state.error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(contacts, key = { _, c -> c.id }) { index, contact ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = segmentShape(index, contacts.size),
                        color = plateColor(PlateArea.Contacts),
                    ) {
                        ContactRow(contact, viewModel) { viewModel.openChat(contact.id, onOpenChat) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactItem, viewModel: ContactsViewModel, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileAvatar(title = contact.name, fileId = contact.avatarFileId, fileState = viewModel::fileState)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                contact.statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (contact.online) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}