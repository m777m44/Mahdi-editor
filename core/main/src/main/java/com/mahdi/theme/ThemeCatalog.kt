package com.mahdi.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.google.gson.JsonArray
import com.mahdi.color.toHex
import java.util.Properties

/**
 * مكتبة ثيمات Mahdi-Editor 2.0.
 *
 * كل ثيم يُعرَّف بـ **11 خانة لون** قابلة للتعديل من شاشة «تخصيص الثيم»:
 * الخلفية، السطح، الأساسي، الثانوي، التمييز، النص، النص الثانوي، الحدود، النجاح، الخطأ، التحذير.
 * بقية خانات Material 3 (`surfaceContainer*`, `inverse*`, الحاويات…) تُشتق رياضيًا من هذه الألوان،
 * فيبقى التباين متسقًا في كل الثيمات ويستطيع المستخدم تعديل أي لون دون أن تنهار الواجهة.
 */
enum class ThemeStyle(val label: String) {
    /** لون واحد صلب. */
    SOLID("صلب"),
    /** أسطح شبه شفافة تُحاكي الزجاج. */
    GLASS("زجاجي"),
    /** تدرّج بين لونين أو أكثر. */
    GRADIENT("تدرّج"),
    /** لون متوهّج على خلفية معتمة. */
    NEON("نيون"),
    /** شبكة ألوان متداخلة. */
    MESH("شبكة"),
    /** ألوان شفق متدرّجة. */
    AURORA("شفق"),
    /** تدرّج يتحرّك ببطء. */
    ANIMATED("متحرّك"),
}

/** الخانات اللونية الإحدى عشرة التي يعرضها محرّر الثيم. */
data class MahdiColors(
    val background: Long,
    val surface: Long,
    val primary: Long,
    val secondary: Long,
    val accent: Long,
    val text: Long,
    val textSecondary: Long,
    val outline: Long,
    val success: Long,
    val error: Long,
    val warning: Long,
) {
    fun with(background: Long = this.background, surface: Long = this.surface, primary: Long = this.primary): MahdiColors =
        copy(background = background, surface = surface, primary = primary)
}

/** تعريف ثيم كامل: الهوية + النمط + الألوان. */
data class ThemeSpec(
    val id: String,
    val name: String,
    val style: ThemeStyle,
    val colors: MahdiColors,
    /** ألوان إضافية للتدرّجات والشبكات (اختيارية). */
    val extras: List<Long> = emptyList(),
    /** هل الثيم داكن بطبيعته؟ (يحدّد ما يُختار عند الوضع التلقائي) */
    val dark: Boolean = true,
)

// ——————————————————————————————————————————————————————————————
// أدوات حسابية صغيرة لبناء مخطط Material 3 من 11 لونًا
// ——————————————————————————————————————————————————————————————

private fun Color.onColor(): Color = if (luminance() > 0.45f) Color(0xFF11130F) else Color(0xFFF8F6F0)

private fun Color.mix(other: Color, f: Float): Color = lerp(this, other, f)

private fun Long.c(): Color = Color(this)

/** يحوّل اللون إلى قيمة ARGB بصيغة `0xAARRGGBB`. */
private fun Color.asArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** لون مقروء على [bg]. */
private fun readable(bg: Color, preferred: Color): Color =
    if (preferred.luminance() > bg.luminance() + 0.12f || bg.luminance() - preferred.luminance() > 0.12f) {
        preferred
    } else {
        bg.onColor()
    }

private fun MahdiColors.toDarkScheme(): ColorScheme {
    val bg = background.c()
    val surf = surface.c()
    val pri = primary.c()
    val sec = secondary.c()
    val acc = accent.c()
    val fg = text.c()
    val fg2 = textSecondary.c()
    val line = outline.c()

    return darkColorScheme(
        primary = pri,
        onPrimary = pri.onColor(),
        primaryContainer = surf.mix(pri, 0.22f),
        onPrimaryContainer = readable(surf.mix(pri, 0.22f), fg),
        inversePrimary = pri.mix(Color.White, 0.35f),
        secondary = sec,
        onSecondary = sec.onColor(),
        secondaryContainer = surf.mix(sec, 0.22f),
        onSecondaryContainer = readable(surf.mix(sec, 0.22f), fg),
        tertiary = acc,
        onTertiary = acc.onColor(),
        tertiaryContainer = surf.mix(acc, 0.22f),
        onTertiaryContainer = readable(surf.mix(acc, 0.22f), fg),
        background = bg,
        onBackground = fg,
        surface = surf,
        onSurface = fg,
        surfaceVariant = surf.mix(line, 0.55f),
        onSurfaceVariant = fg2,
        surfaceTint = pri,
        inverseSurface = fg,
        inverseOnSurface = surf,
        error = error.c(),
        onError = error.c().onColor(),
        errorContainer = surf.mix(error.c(), 0.28f),
        onErrorContainer = readable(surf.mix(error.c(), 0.28f), fg),
        outline = line,
        outlineVariant = line.mix(surf, 0.45f),
        scrim = Color.Black,
        surfaceBright = surf.mix(Color.White, 0.08f),
        surfaceDim = bg.mix(Color.Black, 0.18f),
        surfaceContainerLowest = bg.mix(Color.Black, 0.14f),
        surfaceContainerLow = surf.mix(Color.White, 0.02f),
        surfaceContainer = surf.mix(Color.White, 0.05f),
        surfaceContainerHigh = surf.mix(Color.White, 0.08f),
        surfaceContainerHighest = surf.mix(Color.White, 0.12f),
    )
}

/** يبني نظيرًا فاتحًا للثيم بالاعتماد على نفس الألوان الأساسية. */
private fun MahdiColors.toLightScheme(): ColorScheme {
    val bg = background.c().mix(Color.White, 0.93f)
    val surf = surface.c().mix(Color.White, 0.95f)
    val pri = primary.c().mix(Color.Black, 0.22f)
    val sec = secondary.c().mix(Color.Black, 0.25f)
    val acc = accent.c().mix(Color.Black, 0.35f)
    val fg = text.c().mix(Color.Black, 0.86f)
    val fg2 = textSecondary.c().mix(Color.Black, 0.62f)
    val line = outline.c().mix(Color.Black, 0.30f).mix(Color.White, 0.45f)

    return lightColorScheme(
        primary = pri,
        onPrimary = pri.onColor(),
        primaryContainer = bg.mix(pri, 0.16f),
        onPrimaryContainer = pri.mix(Color.Black, 0.60f),
        inversePrimary = pri.mix(Color.White, 0.35f),
        secondary = sec,
        onSecondary = sec.onColor(),
        secondaryContainer = bg.mix(sec, 0.16f),
        onSecondaryContainer = sec.mix(Color.Black, 0.60f),
        tertiary = acc,
        onTertiary = acc.onColor(),
        tertiaryContainer = bg.mix(acc, 0.16f),
        onTertiaryContainer = acc.mix(Color.Black, 0.60f),
        background = bg,
        onBackground = fg,
        surface = surf,
        onSurface = fg,
        surfaceVariant = bg.mix(line, 0.30f),
        onSurfaceVariant = fg2,
        surfaceTint = pri,
        inverseSurface = fg,
        inverseOnSurface = surf,
        error = error.c().mix(Color.Black, 0.15f),
        onError = error.c().mix(Color.Black, 0.15f).onColor(),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = line,
        outlineVariant = line.mix(bg, 0.55f),
        scrim = Color.Black,
        surfaceBright = surf,
        surfaceDim = bg.mix(Color.Black, 0.10f),
        surfaceContainerLowest = surf.mix(Color.White, 0.30f),
        surfaceContainerLow = surf,
        surfaceContainer = bg.mix(Color.Black, 0.03f),
        surfaceContainerHigh = bg.mix(Color.Black, 0.06f),
        surfaceContainerHighest = bg.mix(Color.Black, 0.09f),
    )
}

/** لوحة ANSI للطرفية: 16 لونًا واضحة على الخلفيات الداكنة والفاتحة. */
private val terminalAnsi =
    listOf(
        "#20242E",
        "#E06C75",
        "#98C379",
        "#E5C07B",
        "#61AFEF",
        "#C678DD",
        "#56B6C2",
        "#ABB2BF",
        "#5C6370",
        "#FF7B86",
        "#B5E890",
        "#FFD68A",
        "#7CC0FF",
        "#D99BEE",
        "#6FD3DE",
        "#F2F4F8",
    )

private fun terminalColors(scheme: ColorScheme, accentHex: String): Properties =
    Properties().apply {
        put("foreground", scheme.onBackground.toHex())
        put("background", scheme.background.toHex())
        put("cursor", accentHex)
        terminalAnsi.forEachIndexed { i, hex -> put("color$i", hex) }
        put("color16", Color(0xFFF96A38).toHex())
        put("color17", Color(0xFF3971ED).toHex())
        put("color18", Color(0xFF282A2E).toHex())
        put("color19", Color(0xFF373B41).toHex())
        put("color20", Color(0xFFB4B7B4).toHex())
        put("color21", Color(0xFFE0E0E0).toHex())
    }

/** يحوّل التعريف إلى [ThemeHolder] جاهز للاستخدام في الواجهة والمحرر. */
fun ThemeSpec.toThemeHolder(): ThemeHolder {
    val darkScheme = colors.toDarkScheme()
    val lightScheme = colors.toLightScheme()
    return ThemeHolder(
        id = id,
        name = name,
        inheritBase = false,
        lightScheme = lightScheme,
        darkScheme = darkScheme,
        lightTerminalColors = terminalColors(lightScheme, colors.accent.c().toHex()),
        darkTerminalColors = terminalColors(darkScheme, colors.accent.c().toHex()),
        lightEditorColors = emptyList(),
        darkEditorColors = emptyList(),
        lightTokenColors = JsonArray(),
        darkTokenColors = JsonArray(),
    )
}

// ——————————————————————————————————————————————————————————————
// الافتراضيات المشتركة
// ——————————————————————————————————————————————————————————————

private const val DEF_SUCCESS = 0xFF10B981
private const val DEF_ERROR = 0xFFEF4444
private const val DEF_WARNING = 0xFFF59E0B

/**
 * تعريف مختصر: تُشتق الخانات غير المذكورة من `background` و`primary`.
 * النص يصبح أبيضَ مائلًا للون الأساسي، والثانوي نصٌّ باهت، والحدود خليطًا مُعتِمًا.
 */
private fun spec(
    id: String,
    name: String,
    style: ThemeStyle,
    background: Long,
    surface: Long,
    primary: Long,
    secondary: Long,
    accent: Long,
    text: Long? = null,
    textSecondary: Long? = null,
    outline: Long? = null,
    success: Long = DEF_SUCCESS,
    error: Long = DEF_ERROR,
    warning: Long = DEF_WARNING,
    dark: Boolean = true,
    extras: List<Long> = emptyList(),
): ThemeSpec {
    val bg = background.c()
    val pri = primary.c()
    val textColor = if (dark) Color.White.mix(pri, 0.10f) else Color.Black.mix(pri, 0.15f)
    val text2 = if (dark) Color.White.mix(bg, 0.42f) else Color.Black.mix(bg, 0.55f)
    val line = if (dark) bg.mix(pri, 0.18f) else bg.mix(pri, 0.30f)
    return ThemeSpec(
        id = id,
        name = name,
        style = style,
        colors =
            MahdiColors(
                background = background,
                surface = surface,
                primary = primary,
                secondary = secondary,
                accent = accent,
                text = text ?: textColor.asArgbLong(),
                textSecondary = textSecondary ?: text2.asArgbLong(),
                outline = outline ?: line.asArgbLong(),
                success = success,
                error = error,
                warning = warning,
            ),
        extras = extras,
        dark = dark,
    )
}

// ——————————————————————————————————————————————————————————————
// الثيم الافتراضي
// ——————————————————————————————————————————————————————————————

/** معرّف الثيم الافتراضي عند أول تشغيل. */
const val DEFAULT_THEME_ID = "mahdi-dark"

// ——————————————————————————————————————————————————————————————
// مكتبة الثيمات — أربعة ثيمات فقط: الفاتح، الداكن، المظلم، السمائي
//
// اختصارًا وبساطةً ووضوحًا: لا عشرات الثيمات المتشابهة، بل أربعة ثيمات مصمّمة بعناية،
// لكلٍّ منها نسختاه الفاتحة والداكنة تلقائيًا مع وضع النظام.
// ——————————————————————————————————————————————————————————————

/** الثيم الفاتح: أسطح بيضاء هادئة وأزرق واضح عالي التباين. */
val MahdiLight =
    spec(
        "mahdi-light",
        "الفاتح · Light",
        ThemeStyle.SOLID,
        0xFFF6F7FB,
        0xFFFFFFFF,
        0xFF2563EB,
        0xFF1D4ED8,
        0xFF0EA5E9,
        dark = false,
    )

/** الثيم الداكن: خلفية ليلية عميقة (هو الافتراضي) بأزرق سماوي وبنفسجي هادئ. */
val MahdiDark =
    spec(
        "mahdi-dark",
        "الداكن · Dark",
        ThemeStyle.SOLID,
        0xFF0E1116,
        0xFF151A21,
        0xFF64B5F6,
        0xFF38BDF8,
        0xFFA78BFA,
    )

/** الثيم المظلم: رمادي فحمي منخفض التباين مريح للعين في الإضاءة الخافتة. */
val MahdiDim =
    spec(
        "mahdi-dim",
        "المظلم · Dim",
        ThemeStyle.SOLID,
        0xFF1B1D21,
        0xFF23262B,
        0xFF9DB2C6,
        0xFF7C8FA3,
        0xFFE0A458,
    )

/** الثيم السمائي: تدرّجات السماء الزرقاء مع لمسات فيروزية. */
val MahdiSky =
    spec(
        "mahdi-sky",
        "السمائي · Sky",
        ThemeStyle.GRADIENT,
        0xFF0A1B2E,
        0xFF0F2438,
        0xFF38BDF8,
        0xFF60A5FA,
        0xFFE0F2FE,
        extras = listOf(0xFF38BDF8, 0xFF0A1B2E, 0xFF7DD3FC),
    )

val themeCatalog: List<ThemeSpec> = listOf(MahdiLight, MahdiDark, MahdiDim, MahdiSky)

/** يقرأ ألوان ثيم قائم ليبني عليها شاشة التخصيص. */
fun ThemeHolder.toMahdiColors(): MahdiColors {
    fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL
    val d = darkScheme
    return MahdiColors(
        background = d.background.argb(),
        surface = d.surface.argb(),
        primary = d.primary.argb(),
        secondary = d.secondary.argb(),
        accent = d.tertiary.argb(),
        text = d.onBackground.argb(),
        textSecondary = d.onSurfaceVariant.argb(),
        outline = d.outline.argb(),
        success = DEF_SUCCESS.toLong(),
        error = d.error.argb(),
        warning = DEF_WARNING.toLong(),
    )
}

/** الثيم الافتراضي جاهزًا للاستخدام. */
val defaultTheme: ThemeHolder = MahdiDark.toThemeHolder()

/** نمط الثيم (صلب/زجاجي/نيون…) بحسب معرّفه في كتالوج 2.0، أو `null` للثيمات الموروثة والمخصّصة. */
fun themeStyleOf(id: String): ThemeStyle? = themeCatalog.firstOrNull { it.id == id }?.style

/** كل الثيمات جاهزة، بالترتيب المعروض في الشاشة. */
val catalogThemes: List<ThemeHolder> = themeCatalog.map { it.toThemeHolder() }
