package app.yougram.ui

import app.yougram.data.YougramBanner
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.R

val LocalYougramUsers = compositionLocalOf { emptySet<Long>() }
val LocalYougramBanners = compositionLocalOf { emptyMap<Long, YougramBanner>() }
val LocalBadgeChecker = compositionLocalOf<(Long) -> Unit> { { } }

/** Значок приложения рядом с именем; рисуется только если у [userId] найдена метка Yougram. По нажатию показывает пояснение. */
@Composable
fun YougramBadge(userId: Long, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val users = LocalYougramUsers.current
    val check = LocalBadgeChecker.current
    var showInfo by remember { mutableStateOf(false) }
    LaunchedEffect(userId) { check(userId) }
    if (userId in users) {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF424242))
                .clickable { showInfo = true },
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Пользователь Yougram",
                modifier = Modifier.fillMaxSize().scale(1.7f),
            )
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            text = { Text("Это пользователь клиента Yougram ❤️") },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
        )
    }
}