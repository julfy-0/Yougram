package app.yougram.ui.contacts

import app.yougram.ui.glass.plateColor
import app.yougram.data.PlateArea
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.data.ContactItem
import app.yougram.ui.FileAvatar
import app.yougram.ui.settings.segmentShape

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    contentPadding: PaddingValues,
    onOpenChat: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.contacts.isEmpty() -> Text(
                state.error ?: "Контактов нет",
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
                itemsIndexed(state.contacts, key = { _, c -> c.id }) { index, contact ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = segmentShape(index, state.contacts.size),
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