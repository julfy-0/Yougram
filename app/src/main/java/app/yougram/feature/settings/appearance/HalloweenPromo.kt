package app.yougram.feature.settings.appearance

import app.yougram.core.i18n.tr
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yougram.feature.badge.ui.BadgeStyleIcon
import app.yougram.feature.splash.ui.HalloweenEvent
import app.yougram.feature.splash.ui.drawCobweb
import app.yougram.feature.splash.ui.drawPumpkin
import app.yougram.feature.splash.ui.halloweenFall
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

private val PromoOrange = Color(0xFFFF8A1F)
private val PromoDark = Color(0xFF2B1200)

private const val PromoKey = "halloween_2026"

/** Показывать ли экран ивента: пока ивент идёт и пользователь его ещё не закрывал. */
fun shouldShowHalloweenPromo(context: Context): Boolean {
    if (!HalloweenEvent.isActive()) return false
    val prefs = context.getSharedPreferences("yougram_promo", Context.MODE_PRIVATE)
    return !prefs.getBoolean(PromoKey, false)
}

/** Запоминает, что экран показан: второй раз он не появится. */
fun markHalloweenPromoSeen(context: Context) {
    context.getSharedPreferences("yougram_promo", Context.MODE_PRIVATE)
        .edit().putBoolean(PromoKey, true).apply()
}

private class PromoPage(val title: String, val text: String)

private val PromoPages = listOf(
    PromoPage(
        "Хеллоуинский ивент",
        "Yougram переоделся к Хэллоуину. Событие идёт до 23:59 31 октября.",
    ),
    PromoPage(
        "Новая заставка",
        "При запуске падают тыквы и паутинка, а под иконкой бежит волнистая полоска загрузки.",
    ),
    PromoPage(
        "Тыквы в шапке",
        "В верхней панели приложения тихонько падают тыквы и паутинки.",
    ),
    PromoPage(
        "Хеллоуинский значок",
        "Выберите его в «Настройки → Внешний вид и чаты → Баннер и значок»: его увидят все пользователи Yougram.",
    ),
    PromoPage(
        "Успейте до 31 октября",
        "Ивент закончится в 23:59 31 октября. Потом тыквы улетят до следующего года.",
    ),
)

/**
 * Полноэкранный рассказ про хеллоуинский ивент: несколько экранов со свайпом и кнопкой «Далее» / «Начать».
 * Закрытие (кнопка на последнем экране или «назад») вызывает [onClose].
 */
@Composable
fun HalloweenPromo(onClose: () -> Unit) {
    val pager = rememberPagerState { PromoPages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PromoPages.lastIndex
    val pulse by rememberInfiniteTransition(label = "pumpkin").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pumpkinScale",
    )

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF1A0B2E), Color(0xFF2B0F3F), Color(0xFF120A06))))
                .halloweenFall(speedScale = 1.5f, darkBackground = true),
        ) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
                    PromoPageContent(index, pulse)
                }

                Row(
                    Modifier.padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(PromoPages.size) { i ->
                        val selected = pager.currentPage == i
                        val w by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dotW")
                        val c by animateColorAsState(
                            if (selected) PromoOrange else Color.White.copy(alpha = 0.3f),
                            label = "dotC",
                        )
                        Box(Modifier.width(w).height(8.dp).clip(CircleShape).background(c))
                    }
                }

                Button(
                    onClick = {
                        if (last) onClose()
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PromoOrange, contentColor = PromoDark),
                ) {
                    Text(
                        if (last) "Начать" else "Далее",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PromoPageContent(index: Int, pulse: Float) {
    val page = PromoPages[index]
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (index) {
            0 -> GlowBox { BadgeStyleIcon(3, Modifier.size(170.dp).scale(pulse)) }
            1 -> GlowBox {
                Canvas(Modifier.size(220.dp)) {
                    translate(size.width * 0.5f, size.height * 0.58f) { drawPumpkin(size.width * 0.62f) }
                    translate(size.width * 0.17f, size.height * 0.3f) { drawCobweb(size.width * 0.34f) }
                    translate(size.width * 0.84f, size.height * 0.2f) { drawCobweb(size.width * 0.22f) }
                }
            }
            2 -> {
                // Макет верхней панели с теми же падающими частичками.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .halloweenFall(speedScale = 3f, darkBackground = true),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "Yougram",
                        modifier = Modifier.padding(start = 24.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            3 -> GlowBox { BadgeStyleIcon(3, Modifier.size(150.dp).scale(pulse)) }
            else -> {
                val left = Duration.between(LocalDateTime.now(), HalloweenEvent.End).let { if (it.isNegative) Duration.ZERO else it }
                GlowBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏳", fontSize = 64.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Осталось",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        Text(
                            tr("{0} дн. {1} ч.", left.toDays(), left.toHours() % 24),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = PromoOrange,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            page.text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
    }
}

/** Оранжевое свечение за иллюстрацией экрана. */
@Composable
private fun GlowBox(content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(300.dp)
                .background(
                    Brush.radialGradient(listOf(PromoOrange.copy(alpha = 0.35f), Color.Transparent)),
                    CircleShape,
                ),
        )
        content()
    }
}