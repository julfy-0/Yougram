@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.settings

import app.yougram.core.i18n.tr
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.component.FileAvatar
import app.yougram.feature.account.data.AccountEntry
import app.yougram.feature.account.data.AccountManager
import app.yougram.feature.chat.data.FileState
import kotlinx.coroutines.flow.Flow

/*
 * Менеджер аккаунтов в стиле Material 3 Expressive (m3.material.io).
 *
 * Контейнер (стеклянная панель с размытием) по-прежнему рисует MainScreen через GlassPickerPanel, блюр не тронут:
 * плитки и кнопки-«стекло» полупрозрачные, чтобы размытие было видно и под ними.
 * Expressive-элементы: сегментированный список (2 dp между плитками, крупные внешние углы), форма плитки и кнопок
 * «перетекает» при нажатии (ButtonDefaults.shapes / IconButtonDefaults.shapes), аватар активного аккаунта —
 * «печенье» MaterialShapes.Cookie9Sided, крупная кнопка Medium для добавления аккаунта, акцентные заголовки.
 */

/** Содержимое менеджера аккаунтов; контейнер (стеклянная панель) задаёт MainScreen. */
@Composable
fun AccountManagerContent(
    accountManager: AccountManager,
    fileState: (Int) -> Flow<FileState>,
    onDismiss: () -> Unit,
) {
    val accounts by accountManager.accounts.collectAsState()
    val activeId by accountManager.activeAccountId.collectAsState()
    var removeTarget by remember { mutableStateOf<AccountEntry?>(null) }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Заголовок: крупный акцентный текст слева, круглая «тональная» кнопка закрытия справа.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Аккаунты",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                Text(
                    text = "Переключайтесь между аккаунтами или добавьте новый",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalIconButton(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp),
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = scheme.onSurface.copy(alpha = 0.10f),
                    contentColor = scheme.onSurface,
                ),
            ) {
                Icon(Icons.Filled.Close, contentDescription = tr("Закрыть"))
            }
        }

        LazyColumn(
            // Расстояние между плитками сегментированного списка в Expressive — 2 dp.
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            itemsIndexed(accounts, key = { _, acc -> acc.id }) { index, acc ->
                val isActive = acc.id == activeId
                AccountTile(
                    account = acc,
                    isActive = isActive,
                    canRemove = !isActive && accounts.size > 1,
                    first = index == 0,
                    last = index == accounts.lastIndex,
                    fileState = fileState,
                    onClick = {
                        if (!isActive) {
                            onDismiss()
                            accountManager.switchAndRestart(acc.id)
                        }
                    },
                    onRemove = { removeTarget = acc },
                )
            }
        }

        // Крупная кнопка Expressive (Medium, 56 dp): форма перетекает при нажатии.
        Button(
            onClick = {
                onDismiss()
                accountManager.addAndRestart()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) {
            Icon(
                Icons.Filled.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)),
            )
            Spacer(Modifier.width(8.dp))
            Text("Добавить аккаунт", style = MaterialTheme.typography.titleMediumEmphasized)
        }

        Spacer(Modifier.size(8.dp))
    }

    removeTarget?.let { acc ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Убрать аккаунт?") },
            text = {
                Text(
                    tr("«{0}» будет убран из списка, локальные данные удалятся. ", acc.name.ifEmpty { "Аккаунт" }) +
                        tr("Сеанс в Telegram останется активным, его можно закрыть в разделе «Устройства»."),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        accountManager.removeAccount(acc.id)
                        removeTarget = null
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.error,
                        contentColor = scheme.onError,
                    ),
                ) { Text("Убрать") }
            },
            dismissButton = {
                TextButton(
                    onClick = { removeTarget = null },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Отмена") }
            },
        )
    }
}

/**
 * Плитка аккаунта. Активная — заливка primaryContainer, аватар-«печенье» и метка «Активен»;
 * остальные — круглый аватар и тональная кнопка удаления. Заливки полупрозрачные: блюр панели виден и под плиткой.
 */
@Composable
private fun AccountTile(
    account: AccountEntry,
    isActive: Boolean,
    canRemove: Boolean,
    first: Boolean,
    last: Boolean,
    fileState: (Int) -> Flow<FileState>,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressShape = rememberPressMorphShape(interaction, first, last)
    // Активный аккаунт — «таблетка»: углы скруглены до максимума (CircleShape даёт радиус в половину высоты).
    val shape: Shape = if (isActive) CircleShape else pressShape

    Surface(
        onClick = onClick,
        enabled = !isActive,
        shape = shape,
        interactionSource = interaction,
        color = if (isActive) scheme.primaryContainer.copy(alpha = 0.75f) else scheme.onSurface.copy(alpha = 0.10f),
        contentColor = if (isActive) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val avatarShape: Shape = if (isActive) MaterialShapes.Cookie9Sided.toShape() else CircleShape
            FileAvatar(
                title = account.name.ifEmpty { "Аккаунт" },
                // id файла принадлежит базе своего аккаунта: у неактивных аватарку показать нельзя.
                fileId = if (isActive) account.avatarFileId else null,
                fileState = fileState,
                size = 48.dp,
                shape = avatarShape,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = account.name.ifEmpty { "Аккаунт" },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val hideOwn = app.yougram.core.ui.rememberOwnContactsHidden()
                val sub = listOfNotNull(
                    account.phone.takeIf { it.isNotEmpty() }?.let { if (hideOwn) app.yougram.core.ui.OWN_PHONE_MASK else it },
                    account.username?.takeIf { it.isNotEmpty() }?.let { if (hideOwn) app.yougram.core.ui.OWN_USERNAME_MASK else "@$it" },
                ).joinToString(" • ")
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isActive) {
                Spacer(Modifier.width(8.dp))
                // Метка-«таблетка» с галочкой.
                Surface(shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary) {
                    Row(
                        Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Активен", style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                }
            } else if (canRemove) {
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(40.dp),
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = scheme.error.copy(alpha = 0.14f),
                        contentColor = scheme.error,
                    ),
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = tr("Удалить аккаунт"),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private val OuterCorner = 28.dp
private val InnerCorner = 8.dp

/**
 * Форма плитки, которая «перетекает» при нажатии: углы пружинно уменьшаются (как у кнопок Expressive)
 * и возвращаются обратно при отпускании. Внешние углы сегмента крупные, внутренние мелкие.
 */
@Composable
private fun rememberPressMorphShape(source: InteractionSource, first: Boolean, last: Boolean): Shape {
    val pressed by source.collectIsPressedAsState()
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val k = if (pressed) 0.45f else 1f
    val top by animateDpAsState((if (first) OuterCorner else InnerCorner) * k, spec, label = "topCorner")
    val bottom by animateDpAsState((if (last) OuterCorner else InnerCorner) * k, spec, label = "bottomCorner")
    // Пружина может «перелетать» ноль — отрицательный радиус недопустим.
    val t = top.coerceAtLeast(0.dp)
    val b = bottom.coerceAtLeast(0.dp)
    return RoundedCornerShape(topStart = t, topEnd = t, bottomStart = b, bottomEnd = b)
}
