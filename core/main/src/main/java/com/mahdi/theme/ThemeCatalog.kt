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
                text = textColor.asArgbLong(),
                textSecondary = text2.asArgbLong(),
                outline = line.asArgbLong(),
                success = DEF_SUCCESS,
                error = DEF_ERROR,
                warning = DEF_WARNING,
            ),
        extras = extras,
        dark = dark,
    )
}

// ——————————————————————————————————————————————————————————————
// الثيم الافتراضي
// ——————————————————————————————————————————————————————————————

/** معرّف الثيم الافتراضي عند أول تشغيل. */
const val DEFAULT_THEME_ID = "mahdi-kiyani"

/**
 * **Mahdi Kiyani** — الثيم الافتراضي: أسود تركوازي عميق، أساسي تركوازي، تمييز ذهبي ملكي.
 */
val MahdiKiyani =
    ThemeSpec(
        id = DEFAULT_THEME_ID,
        name = "Mahdi Kiyani",
        style = ThemeStyle.SOLID,
        colors =
            MahdiColors(
                background = 0xFF0A1F1F,
                surface = 0xFF112E2E,
                primary = 0xFF14B8A6,
                secondary = 0xFF0D9488,
                accent = 0xFFF5D76E,
                text = 0xFFE6F4F1,
                textSecondary = 0xFF8EA9A9,
                outline = 0xFF1F3D3D,
                success = 0xFF10B981,
                error = 0xFFEF4444,
                warning = 0xFFF59E0B,
            ),
        extras = listOf(0xFF0A1F1F, 0xFF112E2E, 0xFF0D9488),
    )

// ——————————————————————————————————————————————————————————————
// مكتبة الثيمات (25 + 3 متوافقة مع الإصدار 1)
// ——————————————————————————————————————————————————————————————

val themeCatalog: List<ThemeSpec> =
    listOf(
        MahdiKiyani,
        // 2 — الملكي
        spec("royal", "الملكي (Royal)", ThemeStyle.GRADIENT, 0xFF1A0B2E, 0xFF241040, 0xFF7C3AED, 0xFF6D28D9, 0xFFFFD700, extras = listOf(0xFF7C3AED, 0xFFFFD700)),
        // 3 — الزجاجي
        spec("glass", "الزجاجي (Glass)", ThemeStyle.GLASS, 0xFF0B1418, 0xFF16242B, 0xFF7DD3FC, 0xFF38BDF8, 0xFFF0ABFC, extras = listOf(0x44FFFFFF)),
        // 4 — المستقبلي
        spec("futuristic", "المستقبلي (Futuristic)", ThemeStyle.NEON, 0xFF0A0A0A, 0xFF121218, 0xFF00F0FF, 0xFFFF00E5, 0xFF00F0FF, extras = listOf(0xFF00F0FF, 0xFFFF00E5)),
        // 5 — سايبربانك
        spec("cyberpunk", "سايبربانك (Cyberpunk)", ThemeStyle.NEON, 0xFF0A0A0A, 0xFF141410, 0xFFFACC15, 0xFFEF4444, 0xFF22D3EE, extras = listOf(0xFFFACC15, 0xFF22D3EE)),
        // 6 — AMOLED
        spec("amoled", "AMOLED أسود", ThemeStyle.SOLID, 0xFF000000, 0xFF0A0A0A, 0xFFFFFFFF, 0xFF9CA3AF, 0xFF38BDF8),
        // 7 — Ocean
        spec("ocean", "المحيط (Ocean)", ThemeStyle.GRADIENT, 0xFF082F49, 0xFF0C3B58, 0xFF0EA5E9, 0xFF0284C7, 0xFF67E8F9, extras = listOf(0xFF0EA5E9, 0xFF082F49)),
        // 8 — Forest
        spec("forest", "الغابة (Forest)", ThemeStyle.MESH, 0xFF052E16, 0xFF0A3D1E, 0xFF16A34A, 0xFF15803D, 0xFF86EFAC),
        // 9 — Sunset
        spec("sunset", "الغروب (Sunset)", ThemeStyle.AURORA, 0xFF7C2D12, 0xFF8A3418, 0xFFF97316, 0xFFEA580C, 0xFFFDE68A, extras = listOf(0xFFF97316, 0xFF7C2D12, 0xFFFDE68A)),
        // 10 — Rose Gold
        spec("rosegold", "الذهبي الوردي (Rose Gold)", ThemeStyle.GRADIENT, 0xFF2A0F14, 0xFF3A1620, 0xFFF43F5E, 0xFFE11D48, 0xFFFFD700, extras = listOf(0xFFF43F5E, 0xFFFFD700)),
        // 11 — Midnight
        spec("midnight", "منتصف الليل (Midnight)", ThemeStyle.SOLID, 0xFF0A0E27, 0xFF111634, 0xFF1E40AF, 0xFF3B82F6, 0xFF60A5FA),
        // 12 — Dracula
        spec("dracula", "دراكولا (Dracula)", ThemeStyle.SOLID, 0xFF282A36, 0xFF333544, 0xFFBD93F9, 0xFFFF79C6, 0xFF8BE9FD, 0xFFF8F8F2, 0xFF6272A4, 0xFF44475A),
        // 13 — Nord
        spec("nord", "نورد (Nord)", ThemeStyle.SOLID, 0xFF2E3440, 0xFF3B4252, 0xFF88C0D0, 0xFF81A1C1, 0xFFA3BE8C, 0xFFECEFF4, 0xFF8FBCBB, 0xFF434C5E),
        // 14 — Tokyo Night
        spec("tokyo-night", "طوكيو الليلية (Tokyo Night)", ThemeStyle.NEON, 0xFF1A1B26, 0xFF24283B, 0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFC0CAF5, 0xFF565F89, 0xFF3B4261),
        // 15 — Catppuccin
        spec("catppuccin", "كاتبوتشينو (Catppuccin)", ThemeStyle.SOLID, 0xFF1E1E2E, 0xFF2A2A3C, 0xFFCBA6F7, 0xFF89B4FA, 0xFFF5C2E7, 0xFFCDD6F4, 0xFF7F849C, 0xFF45475A),
        // 16 — Gruvbox
        spec("gruvbox", "غروفبوكس (Gruvbox)", ThemeStyle.SOLID, 0xFF282828, 0xFF3C3836, 0xFFFABD2F, 0xFF83A598, 0xFFFE8019, 0xFFEBDBB2, 0xFFA89984, 0xFF504945),
        // 17 — Monokai
        spec("monokai", "مونوكاي (Monokai)", ThemeStyle.SOLID, 0xFF272822, 0xFF33342C, 0xFFA6E22E, 0xFF66D9EF, 0xFFF92672, 0xFFF8F8F2, 0xFF9E9D8C, 0xFF49483E),
        // 18 — Solarized
        spec("solarized", "سولارايزد (Solarized)", ThemeStyle.SOLID, 0xFF002B36, 0xFF073642, 0xFF268BD2, 0xFF2AA198, 0xFFB58900, 0xFFEEE8D5, 0xFF93A1A1, 0xFF586E75),
        // 19 — One Dark
        spec("one-dark", "ون دارك (One Dark)", ThemeStyle.SOLID, 0xFF282C34, 0xFF31353F, 0xFF61AFEF, 0xFF98C379, 0xFFE5C07B, 0xFFABB2BF, 0xFF5C6370, 0xFF3E4451),
        // 20 — Material You
        spec("material-you", "ماتيريال يو (Material You)", ThemeStyle.MESH, 0xFF141218, 0xFF1D1B20, 0xFFD0BCFF, 0xFFCCC2DC, 0xFFEFB8C8, 0xFFE6E1E5, 0xFF938F99, 0xFF49454F, extras = listOf(0xFFD0BCFF, 0xFFEFB8C8)),
        // 21 — Baghdad
        spec("baghdad", "بغداد", ThemeStyle.MESH, 0xFF1B3A2F, 0xFF224A3C, 0xFFD4A574, 0xFFC08A2E, 0xFFE9C46A, 0xFFF2E8CF, 0xFFB8C4B8, 0xFF2E5545, extras = listOf(0xFFD4A574, 0xFF1B3A2F, 0xFFE9C46A)),
        // 22 — Desert
        spec("desert", "صحراء", ThemeStyle.GRADIENT, 0xFF3D2817, 0xFF4C3420, 0xFFE8B86D, 0xFFD9A05B, 0xFFF0D9A8, 0xFFF7EDE2, 0xFFC4A484, 0xFF5E4229, extras = listOf(0xFFE8B86D, 0xFF3D2817)),
        // 23 — Tigris
        spec("tigris", "دجلة", ThemeStyle.GRADIENT, 0xFF0C4A6E, 0xFF0F5A82, 0xFF0D9488, 0xFF14B8A6, 0xFF7DD3FC, 0xFFE0F2FE, 0xFF8FB8CC, 0xFF155E75, extras = listOf(0xFF0D9488, 0xFF0C4A6E)),
        // 24 — Euphrates
        spec("euphrates", "الفرات", ThemeStyle.AURORA, 0xFF064E3B, 0xFF0A5C46, 0xFF059669, 0xFF10B981, 0xFF6EE7B7, 0xFFECFDF5, 0xFF8CB8A8, 0xFF0F5F4A, extras = listOf(0xFF059669, 0xFF064E3B, 0xFF6EE7B7)),
        // 25 — Paper Light
        spec(
            "paper-light",
            "ورقي فاتح (Paper Light)",
            ThemeStyle.SOLID,
            0xFFF5F5DC,
            0xFFFFFDF2,
            0xFF1A1A1A,
            0xFF4A4A4A,
            0xFF8A6D3B,
            dark = false,
        ),
        // — ثيمات الإصدار 1 المحفوظة (للتوافق مع تفضيلات قائمة) —
        spec("mahdi-dark", "مهدي الداكن", ThemeStyle.SOLID, 0xFF0F0D09, 0xFF17130C, 0xFFE3B341, 0xFFC08A2E, 0xFF9C7A46),
        spec("mahdi-light", "مهدي الفاتح", ThemeStyle.SOLID, 0xFFFAF5EA, 0xFFFFFCF5, 0xFF8A5A00, 0xFF6B4E2E, 0xFF3F6C5B, dark = false),
        spec("arabic-night", "ليل عربي", ThemeStyle.NEON, 0xFF080B16, 0xFF0F1424, 0xFFD4AF37, 0xFF2AA198, 0xFFB084CC, extras = listOf(0xFFD4AF37, 0xFF2AA198)),
    )

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
val defaultTheme: ThemeHolder = MahdiKiyani.toThemeHolder()

/** كل الثيمات جاهزة، بالترتيب المعروض في الشاشة. */
val catalogThemes: List<ThemeHolder> = themeCatalog.map { it.toThemeHolder() }
