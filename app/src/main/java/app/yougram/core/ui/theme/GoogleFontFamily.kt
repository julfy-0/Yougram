package app.yougram.core.ui.theme

import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import app.yougram.R

private val GoogleFontWeights = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
)

private val AllWeights = listOf(
    FontWeight.Thin, FontWeight.ExtraLight, FontWeight.Light, FontWeight.Normal, FontWeight.Medium,
    FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black,
)

private val BundledWeights = listOf(
    FontWeight.Thin, FontWeight.ExtraLight, FontWeight.Light, FontWeight.Normal, FontWeight.Medium,
    FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black,
)

/**
 * Google Sans Flex, вшитый в приложение (res/font, лицензия OFL): вариативный шрифт с осями жирности и скругления.
 * [roundness] (0…100) — ось ROND. Вес задаётся явно для каждого начертания, поэтому скругление не меняет жирность.
 * Работает без интернета и Google Play Services.
 */
fun googleSansFlexFamily(roundness: Int = 0): FontFamily {
    val r = roundness.coerceIn(0, 100).toFloat()
    return FontFamily(
        BundledWeights.map { weight ->
            Font(
                resId = R.font.google_sans_flex,
                weight = weight,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight.weight),
                    FontVariation.Setting("ROND", r),
                ),
            )
        },
    )
}

/**
 * Pixelify Sans, вшитый в приложение (res/font, лицензия OFL): основной шрифт приложения.
 * Вариативный, ось жирности 400…700. В исходном файле не было заглавных кириллических «О» и «П» —
 * в вшитую копию они добавлены (О = латинская O, П = греческая Π).
 */
val PixelifySansFamily: FontFamily by lazy {
    FontFamily(
        BundledWeights.map { weight ->
            Font(
                resId = R.font.pixelify_sans,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight.coerceIn(400, 700))),
            )
        },
    )
}

/** Google Sans Flex с полным скруглением (ROND = 100): для выбранных вкладок, фильтров и разделителей. */
val GoogleSansFlexRounded: FontFamily by lazy { googleSansFlexFamily(100) }

/**
 * Nunito (res/font, OFL) — скруглённый шрифт с кириллицей. В Google Sans Flex нет кириллических глифов
 * (ни в вшитом файле, ни в официальном релизе), поэтому для русского текста скругление берём отсюда.
 */
val NunitoRounded: FontFamily by lazy {
    FontFamily(
        BundledWeights.map { weight ->
            Font(
                resId = R.font.nunito,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )
}

/**
 * ArtegraSoft Bold, вшитый в приложение (res/font/artegrasoft_bold.ttf): основной шрифт приложения.
 * Файл статический, одно начертание (Bold) с латиницей и кириллицей. Все веса указывают на него,
 * поэтому Compose не рисует синтетический жирный и текст везде выглядит одинаково.
 */
val ArtegraSoftFamily: FontFamily by lazy {
    FontFamily(
        AllWeights.flatMap { weight ->
            listOf(
                Font(resId = R.font.artegrasoft_bold, weight = weight, style = FontStyle.Normal),
                Font(resId = R.font.artegrasoft_bold, weight = weight, style = FontStyle.Italic),
            )
        },
    )
}

/** Основной шрифт приложения — [ArtegraSoftFamily]. */
val AppSansFamily: FontFamily get() = ArtegraSoftFamily

/** Единый шрифт выбранных вкладок, папок, фильтров и букв. Параметр оставлен для совместимости вызовов. */
@Suppress("UNUSED_PARAMETER")
fun roundedFamilyFor(text: String): FontFamily = AppSansFamily

/**
 * Семейство из Google Fonts по точному названию (например "Montserrat", "Comfortaa").
 * Google Sans Flex берётся из вшитого файла (там работает скругление [roundness], 0…100);
 * у остальных шрифтов скругление не применяется.
 * Если шрифт не скачался или такого названия нет, текст рисуется системным sans-serif.
 */
fun googleFontFamily(name: String, roundness: Int = 0): FontFamily {
    if (name.trim().equals("Google Sans Flex", ignoreCase = true)) return googleSansFlexFamily(roundness)
    // Скачанные файлы (см. FontStore) работают без интернета; пока шрифта нет на устройстве — загрузка через Google Play Services.
    val local = FontStore.localFiles(name)
    if (local.isNotEmpty()) {
        return FontFamily(
            AllWeights.map { w ->
                val file = local.entries.minBy { kotlin.math.abs(it.key - w.weight) }.value
                Font(file, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
            },
        )
    }
    val googleFont = GoogleFont(name)
    val fonts: List<Font> = GoogleFontWeights.map { weight ->
        Font(googleFont = googleFont, fontProvider = GoogleFontProvider, weight = weight)
    } + Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Normal)
    return FontFamily(fonts)
}
