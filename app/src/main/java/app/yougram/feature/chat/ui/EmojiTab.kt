package app.yougram.feature.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yougram.core.i18n.Text
import kotlinx.coroutines.delay

/** Вкладки нижней панели чата. */
enum class PickerTab(val title: String) { Emoji("Эмодзи"), Stickers("Стикеры"), Gif("GIF") }

/** Переключатель вкладок панели эмодзи / стикеров / GIF. */
@Composable
fun PickerTabs(selected: PickerTab, onSelect: (PickerTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PickerTab.entries.forEach { tab ->
            Surface(
                onClick = { onSelect(tab) },
                shape = CircleShape,
                color = pickerChipColor(tab == selected),
            ) {
                Text(
                    tab.title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Категории: значок вкладки и список эмодзи через пробел. */
private val EmojiCategories: List<Pair<String, List<String>>> = listOf(
    "😀" to ("😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 ☹️ 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖").split(" "),
    "👍" to ("👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐 🖖 👋 🤝 🙏 ✍️ 💪 👏 🙌 👐 🤲 🤜 🤛 ✊ 👊 💅 🤳 👀 👁 👂 👃 👄 🦷 👅 🧠").split(" "),
    "❤️" to ("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ✨ ⭐ 🌟 💫 🔥 💥 💢 💦 💤 🎉 🎊 🎈 🎁 🏆 🥇 💯 ✅ ❌ ⚡ ☀️ 🌙 🌈 ☁️ ❄️").split(" "),
    "🐶" to ("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦀 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐘 🦒 🐑 🐐 🐕 🐈 🌵 🌲 🌳 🌴 🌸 🌹 🌻 🍀").split(" "),
    "🍕" to ("🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🥦 🥕 🌽 🌶 🥔 🍞 🧀 🥚 🍳 🥞 🥓 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🍝 🍜 🍲 🍣 🍱 🍤 🍙 🍚 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 🍿 ☕ 🍵 🍺 🍻 🥂 🍷 🥃 🍸 🍹").split(" "),
    "⚽" to ("⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🥋 🎯 ⛳ 🎣 🎮 🎲 🧩 🎭 🎨 🎬 🎤 🎧 🎸 🎹 🥁 🎺 🎻 📷 📱 💻 ⌚ 💡 🔋 🔑 🔒 💰 💎 🚗 🚕 🚌 🚓 🚑 🚒 ✈️ 🚀 🚁 🚲 🏍 ⛵ 🏠 🏝 🗽 🗼 🌍").split(" "),
)

/** Сетка эмодзи с категориями; выбранный эмодзи уходит в [onPick] (вставляется в поле ввода). */
@Composable
fun EmojiTab(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var category by remember { mutableIntStateOf(0) }
    var previous by remember { mutableIntStateOf(0) }
    val items = EmojiCategories[category].second
    // Вся сетка выезжает из-за края экрана одним целым: справа при открытии и при переходе вперёд, слева при переходе назад.
    val slide = remember(category) { Animatable(1f) }
    val dir = if (category >= previous) 1f else -1f
    LaunchedEffect(category) {
        slide.snapTo(1f)
        slide.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 260f))
    }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    Column(modifier.fillMaxWidth()) {
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(EmojiCategories) { index, (icon, _) ->
                Surface(
                    onClick = { previous = category; category = index },
                    shape = CircleShape,
                    color = pickerChipColor(index == category),
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Text(icon, fontSize = 20.sp)
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(44.dp),
            modifier = Modifier.fillMaxWidth().weight(1f).graphicsLayer {
                translationX = slide.value * dir * screenWidthDp.dp.toPx()
            },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            gridItemsIndexed(items, key = { _, e -> "$category$e" }) { _, emoji ->
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { onPick(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 26.sp)
                }
            }
        }
    }
}