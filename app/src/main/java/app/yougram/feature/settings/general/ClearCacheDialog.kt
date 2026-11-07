package app.yougram.feature.settings.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.feature.chat.data.CacheCategory

/**
 * Диалог очистки кэша: можно отметить всё сразу («Выбрать все») или только нужные категории.
 * [sizes] — размер кэша по категориям, null пока считается.
 */
@Composable
internal fun ClearCacheDialog(
    sizes: Map<CacheCategory, Long>?,
    formatSize: (Long) -> String,
    onConfirm: (Set<CacheCategory>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(CacheCategory.entries.toSet()) }
    val all = selected.size == CacheCategory.entries.size
    val allState = when {
        all -> ToggleableState.On
        selected.isEmpty() -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
    fun toggleAll() { selected = if (all) emptySet() else CacheCategory.entries.toSet() }
    fun toggle(c: CacheCategory) { selected = if (c in selected) selected - c else selected + c }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Что очистить?") },
        text = {
            Column {
                Text(
                    "Файлы можно скачать заново",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OptionRow(
                    label = "Выбрать все",
                    value = null,
                    checkbox = { TriStateCheckbox(state = allState, onClick = null) },
                    modifier = Modifier.toggleable(
                        value = all,
                        role = Role.Checkbox,
                        onValueChange = { toggleAll() },
                    ),
                    bold = true,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                CacheCategory.entries.forEach { c ->
                    val checked = c in selected
                    OptionRow(
                        label = c.title,
                        value = sizes?.get(c)?.let(formatSize) ?: if (sizes == null) "…" else "—",
                        checkbox = { Checkbox(checked = checked, onCheckedChange = null) },
                        modifier = Modifier.toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { toggle(c) },
                        ),
                    )
                }
                if (!sizes.isNullOrEmpty() && selected.isNotEmpty()) {
                    val total = selected.sumOf { sizes[it] ?: 0L }
                    // При полной очистке удаляются ещё и голосовые, стикеры, временные файлы — поэтому «не меньше».
                    val shown = formatSize(total).let { if (all) "≥ $it" else it }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("Будет освобождено: {0}", shown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected) },
                enabled = selected.isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Очистить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun OptionRow(
    label: String,
    value: String?,
    checkbox: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        checkbox()
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Medium else null,
        )
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
