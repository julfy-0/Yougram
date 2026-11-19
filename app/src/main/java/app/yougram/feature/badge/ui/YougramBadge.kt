package app.yougram.feature.badge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.R
import app.yougram.feature.badge.data.YougramBanner
import app.yougram.feature.badge.data.YougramBadge as BadgeData

/** Пользователи с меткой Yougram в «О себе». */
val LocalYougramUsers = staticCompositionLocalOf<Set<Long>> { emptySet() }

/** Пользователи с золотой меткой. */
val LocalGoldUsers = staticCompositionLocalOf<Set<Long>> { emptySet() }

/** Создатель клиента. */
val LocalCreatorUsers = staticCompositionLocalOf<Set<Long>> { emptySet() }

/** Баннеры из «О себе»: id пользователя → баннер. */
val LocalYougramBanners = staticCompositionLocalOf<Map<Long, YougramBanner>> { emptyMap() }

/** Вид значка, выбранный пользователем: id пользователя → стиль (0 — классический, в карте не хранится). */
val LocalBadgeStyles = staticCompositionLocalOf<Map<Long, Int>> { emptyMap() }

/** Лениво запускает проверку «О себе» пользователя на метку. */
val LocalBadgeChecker = staticCompositionLocalOf<(Long) -> Unit> { {} }

/** Значок рядом с именем: канал, создатель, золотой, либо обычный/выбранного вида. Если метки нет, ничего не рисуется. */
@Composable
fun YougramBadge(chatId: Long, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val check = LocalBadgeChecker.current
    LaunchedEffect(chatId) { if (chatId > 0L) check(chatId) }

    val users = LocalYougramUsers.current
    val gold = LocalGoldUsers.current
    val creators = LocalCreatorUsers.current
    val styles = LocalBadgeStyles.current

    val res = when {
        chatId in BadgeData.CHANNEL_CHAT_IDS -> R.drawable.ic_badge_channel
        chatId in creators -> R.drawable.ic_badge_blue
        chatId in gold -> R.drawable.ic_badge_gold
        chatId in users -> {
            val style = styles[chatId] ?: 0
            if (style != 0) {
                BadgeStyleIcon(style, modifier.size(size))
                return
            }
            R.drawable.ic_badge_normal
        }
        else -> return
    }
    Image(
        painter = painterResource(id = res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}
