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
import androidx.compose.material3.TextButton
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
val LocalBadgeChecker = compositionLocalOf<(Long) -> Unit> { { } }

enum class BadgeKind { NORMAL, GOLD, CREATOR }

/** Значок приложения рядом с именем; рисуется если у [userId] найдена метка Yougram, золотая или синяя метка. */
@Composable
fun YougramBadge(userId: Long, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val users = LocalYougramUsers.current
    val goldUsers = LocalGoldUsers.current
    val creatorUsers = LocalCreatorUsers.current
    val check = LocalBadgeChecker.current
    var showInfo by remember { mutableStateOf(false) }
    var badgeKind by remember { mutableStateOf(BadgeKind.NORMAL) }
    LaunchedEffect(userId) { check(userId) }

    val isCreator = userId == app.yougram.feature.badge.data.YougramBadge.CREATOR_USER_ID || userId in creatorUsers
    val isGold = userId in app.yougram.feature.badge.data.YougramBadge.GOLD_USER_IDS || userId in goldUsers
    val isNormal = userId in users

    if (isCreator || isGold || isNormal) {
        val currentKind = when {
            isCreator -> BadgeKind.CREATOR
            isGold -> BadgeKind.GOLD
            else -> BadgeKind.NORMAL
        }

        val bgModifier = when (currentKind) {
            BadgeKind.CREATOR -> Modifier.background(Brush.linearGradient(listOf(Color(0xFF00B0FF), Color(0xFF0055FF))))
            BadgeKind.GOLD -> Modifier.background(Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA000))))
            BadgeKind.NORMAL -> Modifier.background(Color(0xFF424242))
        }

        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .then(bgModifier)
                .clickable {
                    badgeKind = currentKind
                    showInfo = true
                },
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = when (currentKind) {
                    BadgeKind.CREATOR -> "Создатель Yougram"
                    BadgeKind.GOLD -> "Помощник Yougram"
                    BadgeKind.NORMAL -> "Пользователь Yougram"
                },
                modifier = Modifier.fillMaxSize().scale(1.7f),
            )
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            text = {
                Text(
                    when (badgeKind) {
                        BadgeKind.CREATOR -> "Этот человек создал клиент Yougram ❤️"
                        BadgeKind.GOLD -> "Это человек помогал в развитии клиента❤️"
                        BadgeKind.NORMAL -> "Это пользователь клиента Yougram ❤️"
                    },
                )
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
        )
    }
}