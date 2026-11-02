package app.yougram.feature.settings.appearance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import app.yougram.core.ui.component.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import app.yougram.core.ui.theme.FontStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.ui.theme.GoogleFontsCatalog
import app.yougram.core.ui.theme.googleFontFamily
import app.yougram.core.ui.theme.loadGoogleFontsList

/** Выбор шрифта из Google Fonts: поиск по каталогу, превью в самом шрифте и ввод любого точного названия. */
@Composable
fun GoogleFontPickerDialog(
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val fonts by produceState(GoogleFontsCatalog) { value = loadGoogleFontsList(context) }
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val filtered = remember(q, fonts) {
        if (q.isEmpty()) fonts else fonts.filter { it.contains(q, ignoreCase = true) }
    }
    val showCustom = q.isNotEmpty() && fonts.none { it.equals(q, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Fonts") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск или точное название") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (showCustom) {
                        item(key = "custom:$q") {
                            GoogleFontRow(
                                name = q,
                                caption = tr("Попробовать шрифт с таким названием"),
                                selected = selected.equals(q, ignoreCase = true),
                                debounceMs = 800,
                                onClick = { onSelect(q) },
                            )
                        }
                    }
                    items(filtered, key = { it }) { name ->
                        GoogleFontRow(
                            name = name,
                            caption = "Привет, мир! Hello 123",
                            selected = name == selected,
                            onClick = { onSelect(name) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun GoogleFontRow(
    name: String,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
    debounceMs: Long = 250,
) {
    val context = LocalContext.current
    // Шрифт скачивается, только когда строка попала на экран (LazyColumn создаёт только видимые);
    // задержка отсекает строки, которые пролистали на лету.
    LaunchedEffect(name) {
        delay(debounceMs)
        FontStore.ensure(context, name)
    }
    val revision = FontStore.revision
    val family = remember(name, revision) { googleFontFamily(name) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = family,
                color = MaterialTheme.colorScheme.onSurface,
            )
            androidx.compose.material3.Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = family,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
