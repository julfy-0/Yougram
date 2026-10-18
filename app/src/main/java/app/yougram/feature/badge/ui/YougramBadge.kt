package app.yougram.feature.badge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import app.yougram.core.ui.component.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.R
import app.yougram.feature.badge.data.YougramBanner

val LocalYougramUsers = compositionLocalOf { emptySet<Long>() }
val LocalGoldUsers = compositionLocalOf { emptySet<Long>() }
val LocalCreatorUsers = compositionLocalOf { emptySet<Long>() }
val LocalYougramBanners = compositionLocalOf { emptyMap<Long, YougramBanner>() }
val LocalBadgeStyles = compositionLocalOf { emptyMap<Long, Int>() }
val LocalBadgeChecker = compositionLocalOf<(Long) -> Unit> { { } }

enum class BadgeKind { NORMAL, GOLD, CREATOR, CHANNEL }

/** Значок приложения рядом с именем; рисуется если у [userId] найдена метка Yougram, золотая или синяя метка. */
@Composable
fun YougramBadge(userId: Long, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val users = LocalYougramUsers.current
    val goldUsers = LocalGoldUsers.current
    val creatorUsers = LocalCreatorUsers.current
    val styles = LocalBadgeStyles.current
    val check = LocalBadgeChecker.current
    var showInfo by remember { mutableStateOf(false) }
    var badgeKind by remember { mutableStateOf(BadgeKind.NORMAL) }
    LaunchedEffect(userId) { check(userId) }

    val isChannel = userId in app.yougram.feature.badge.data.YougramBadge.CHANNEL_CHAT_IDS
    val isCreator = userId == app.yougram.feature.badge.data.YougramBadge.CREATOR_USER_ID || userId in creatorUsers
    val isGold = userId in app.yougram.feature.badge.data.YougramBadge.GOLD_USER_IDS || userId in goldUsers
    val isNormal = userId in users

    if (isChannel || isCreator || isGold || isNormal) {
        val currentKind = when {
            isChannel -> BadgeKind.CHANNEL
            isCreator -> BadgeKind.CREATOR
            isGold -> BadgeKind.GOLD
            else -> BadgeKind.NORMAL
        }

        val style = if (currentKind == BadgeKind.NORMAL) styles[userId] ?: 0 else 0

        // Значок рисуется своей PNG-картинкой для каждого вида метки, без подложки и обрезки.
        val badgeRes = when (currentKind) {
            BadgeKind.CHANNEL -> R.drawable.ic_badge_channel
            BadgeKind.CREATOR -> R.drawable.ic_badge_blue
            BadgeKind.GOLD -> R.drawable.ic_badge_gold
            BadgeKind.NORMAL -> R.drawable.ic_badge_normal
        }

        Box(
            modifier
                .size(size)
                .clickable {
                    badgeKind = currentKind
                    showInfo = true
                },
        ) {
            if (style != 0) {
                BadgeStyleIcon(style, Modifier.fillMaxSize())
            } else Image(
                painter = painterResource(id = badgeRes),
                contentDescription = when (currentKind) {
                    BadgeKind.CHANNEL -> "Официальный канал Yougram"
                    BadgeKind.CREATOR -> "Создатель Yougram"
                    BadgeKind.GOLD -> "Разработчик Yougram"
                    BadgeKind.NORMAL -> "Пользователь Yougram"
                },
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            text = {
                Text(
                    when (badgeKind) {
                        BadgeKind.CHANNEL -> "Официальный канал клиента Yougram ❤️"
                        BadgeKind.CREATOR -> "Создатель Yougram ❤️"
                        BadgeKind.GOLD -> "Разработчик клиента❤️"
                        BadgeKind.NORMAL -> "Пользователь Yougram ❤️"
                    },
                )
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
        )
    }
}