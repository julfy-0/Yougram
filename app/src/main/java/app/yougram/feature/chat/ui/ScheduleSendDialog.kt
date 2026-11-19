package app.yougram.feature.chat.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.feature.chat.data.SendSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private const val MinLeadSeconds = 30L
private const val MaxDaysAhead = 366L

/**
 * Выбор времени отправки: сначала дата, затем время.
 * [canWhenOnline] — показать «Когда будет в сети» (только личные чаты).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSendDialog(
    canWhenOnline: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (SendSchedule) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val lastDay = remember { today.plusDays(MaxDaysAhead) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    if (pickedDate == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !d.isBefore(today) && !d.isAfter(lastDay)
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = {
                        dateState.selectedDateMillis?.let {
                            pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                    },
                ) { Text("Далее") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (canWhenOnline) {
                        TextButton(onClick = { onConfirm(SendSchedule.WhenOnline) }) { Text("Когда будет в сети") }
                    }
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                }
            },
        ) {
            DatePicker(state = dateState, showModeToggle = false)
        }
        return
    }

    val date = pickedDate!!
    val context = LocalContext.current
    val now = LocalDateTime.now(zone).plusMinutes(5)
    val timeState = rememberTimePickerState(
        initialHour = now.hour,
        initialMinute = now.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    val target = LocalDateTime.of(date, LocalTime.of(timeState.hour, timeState.minute)).atZone(zone)
    val tooSoon = target.toEpochSecond() < System.currentTimeMillis() / 1000 + MinLeadSeconds

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Время отправки") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = timeState)
                if (tooSoon) {
                    Text(
                        "Выберите время в будущем",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !tooSoon,
                onClick = { onConfirm(SendSchedule.At(target.toEpochSecond().toInt())) },
            ) { Text("Запланировать") }
        },
        dismissButton = {
            TextButton(onClick = { pickedDate = null }) { Text("Назад") }
        },
    )
}
