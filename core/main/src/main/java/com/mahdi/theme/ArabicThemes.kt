package com.mahdi.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.google.gson.JsonArray
import com.mahdi.color.toHex
import java.util.Properties

/**
 * الثيمات العربية السبعة المضافة إلى Mahdi-Editor.
 *
 * كل ثيم يُعرَّف بالبيانات فقط: ألوان الخلفية والسطح في الوضعين الفاتح والداكن، وثلاثة ألوان أساسية مستوحاة من
 * التراث العربي (ذهب عباسي، نحاس، فيروز، رمال الصحراء، مياه دجلة والفرات…). أما بقية ألوان [ColorScheme] فتُشتق
 * رياضيًا من هذه البذور، فيبقى التباين مقروءًا في كل ثيم دون كتابة عشرات الألوان يدويًا.
 */
private data class MahdiPalette(
    /** معرّف ثابت يُخزَّن في تفضيلات المستخدم؛ لا يُغيَّر بعد الإصدار. */
    val id: String,
    /** الاسم المعروض في شاشة الثيمات. */
    val name: String,
    /** اللون الأساسي (الذهب، النحاس، الماء…). */
    val primary: Long,
    val secondary: Long,
    val tertiary: Long,
    val darkBackground: Long,
    val darkSurface: Long,
    val darkSurfaceVariant: Long,
    val lightBackground: Long,
    val lightSurface: Long,
    val lightSurfaceVariant: Long,
)

/** لون نص/أيقونة مقروء فوق اللون الحالي. */
private fun Color.onColor(): Color = if (luminance() > 0.45f) Color(0xFF14110B) else Color(0xFFF8F4EA)

private fun Color.mix(other: Color, fraction: Float): Color = lerp(this, other, fraction)

/** يبني مخطط ألوان Material 3 كاملًا من بذور قليلة. */
private fun buildScheme(
    dark: Boolean,
    background: Long,
    surface: Long,
    surfaceVariant: Long,
    primary: Long,
    secondary: Long,
    tertiary: Long,
): ColorScheme {
    val bg = Color(background)
    val surf = Color(surface)
    val surfVar = Color(surfaceVariant)
    val onBg = bg.onColor()
    val onSurf = surf.onColor()

    // في الوضع الداكن نرفع إضاءة الألوان قليلًا، وفي الفاتح نُخفضها، لضمان التباين.
    val primaryColor = if (dark) Color(primary).mix(Color.White, 0.22f) else Color(primary).mix(Color.Black, 0.06f)
    val secondaryColor = if (dark) Color(secondary).mix(Color.White, 0.22f) else Color(secondary).mix(Color.Black, 0.06f)
    val tertiaryColor = if (dark) Color(tertiary).mix(Color.White, 0.22f) else Color(tertiary).mix(Color.Black, 0.06f)

    val primaryContainer = surf.mix(primaryColor, if (dark) 0.28f else 0.18f)
    val secondaryContainer = surf.mix(secondaryColor, if (dark) 0.28f else 0.18f)
    val tertiaryContainer = surf.mix(tertiaryColor, if (dark) 0.28f else 0.18f)

    val onPrimaryContainer = if (dark) primaryColor.mix(Color.White, 0.55f) else primaryColor.mix(Color.Black, 0.55f)
    val onSecondaryContainer = if (dark) secondaryColor.mix(Color.White, 0.55f) else secondaryColor.mix(Color.Black, 0.55f)
    val onTertiaryContainer = if (dark) tertiaryColor.mix(Color.White, 0.55f) else tertiaryColor.mix(Color.Black, 0.55f)

    val error = if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
    val errorContainer = if (dark) Color(0xFF8C1D18) else Color(0xFFFFDAD6)
    val onErrorContainer = if (dark) Color(0xFFFFDAD6) else Color(0xFF410002)

    return if (dark) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = primaryColor.onColor(),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = primaryContainer,
            secondary = secondaryColor,
            onSecondary = secondaryColor.onColor(),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiaryColor,
            onTertiary = tertiaryColor.onColor(),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = bg,
            onBackground = onBg,
            surface = surf,
            onSurface = onSurf,
            surfaceVariant = surfVar,
            onSurfaceVariant = onSurf.mix(surfVar, 0.25f),
            surfaceTint = primaryColor,
            inverseSurface = onSurf,
            inverseOnSurface = surf,
            error = error,
            onError = error.onColor(),
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = onSurf.mix(surf, 0.45f),
            outlineVariant = onSurf.mix(surf, 0.70f),
            scrim = Color.Black,
            surfaceBright = surf.mix(Color.White, 0.08f),
            surfaceDim = bg.mix(Color.Black, 0.10f),
            surfaceContainerLowest = bg.mix(Color.Black, 0.12f),
            surfaceContainerLow = surf.mix(Color.White, 0.02f),
            surfaceContainer = surf.mix(Color.White, 0.05f),
            surfaceContainerHigh = surf.mix(Color.White, 0.08f),
            surfaceContainerHighest = surf.mix(Color.White, 0.11f),
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = primaryColor.onColor(),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = primaryColor.mix(Color.White, 0.35f),
            secondary = secondaryColor,
            onSecondary = secondaryColor.onColor(),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiaryColor,
            onTertiary = tertiaryColor.onColor(),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = bg,
            onBackground = onBg,
            surface = surf,
            onSurface = onSurf,
            surfaceVariant = surfVar,
            onSurfaceVariant = onSurf.mix(surfVar, 0.25f),
            surfaceTint = primaryColor,
            inverseSurface = onSurf,
            inverseOnSurface = surf,
            error = error,
            onError = error.onColor(),
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = onSurf.mix(surf, 0.45f),
            outlineVariant = onSurf.mix(surf, 0.70f),
            scrim = Color.Black,
            surfaceBright = surf,
            surfaceDim = bg.mix(Color.Black, 0.06f),
            surfaceContainerLowest = surf,
            surfaceContainerLow = bg.mix(Color.Black, 0.02f),
            surfaceContainer = bg.mix(Color.Black, 0.04f),
            surfaceContainerHigh = bg.mix(Color.Black, 0.07f),
            surfaceContainerHighest = bg.mix(Color.Black, 0.10f),
        )
    }
}

/**
 * الألوان الستة عشر الأولى من لوحة ANSI، واضحة على الخلفيات الداكنة والفاتحة على السواء.
 * الألوان 16–21 تُشتق منها لأن الطرفية تتوقعها أيضًا.
 */
private val terminalAnsi =
    listOf(
        "#20242E", // 0  أسود
        "#E06C75", // 1  أحمر
        "#98C379", // 2  أخضر
        "#E5C07B", // 3  أصفر
        "#61AFEF", // 4  أزرق
        "#C678DD", // 5  أرجواني
        "#56B6C2", // 6  سماوي
        "#ABB2BF", // 7  أبيض
        "#5C6370", // 8  أسود فاتح
        "#FF7B86", // 9  أحمر فاتح
        "#B5E890", // 10 أخضر فاتح
        "#FFD68A", // 11 أصفر فاتح
        "#7CC0FF", // 12 أزرق فاتح
        "#D99BEE", // 13 أرجواني فاتح
        "#6FD3DE", // 14 سماوي فاتح
        "#F2F4F8", // 15 أبيض فاتح
    )

/** ألوان الطرفية: الخلفية والمقدمة والمؤشر من مخطط الثيم، والستة عشر لونًا من [terminalAnsi]. */
private fun terminalColors(scheme: ColorScheme): Properties =
    Properties().apply {
        put("foreground", scheme.onBackground.toHex())
        put("background", scheme.background.toHex())
        put("cursor", scheme.primary.toHex())

        terminalAnsi.forEachIndexed { index, hex -> put("color$index", hex) }

        // الألوان الإضافية التي تقرأها المحاكية (منحنيات الألوان 16–21 في بعض السمات).
        put("color16", Color(0xFFF96A38).toHex())
        put("color17", Color(0xFF3971ED).toHex())
        put("color18", Color(0xFF282A2E).toHex())
        put("color19", Color(0xFF373B41).toHex())
        put("color20", Color(0xFFB4B7B4).toHex())
        put("color21", Color(0xFFE0E0E0).toHex())
    }

private fun MahdiPalette.toThemeHolder(): ThemeHolder {
    val darkScheme =
        buildScheme(
            dark = true,
            background = darkBackground,
            surface = darkSurface,
            surfaceVariant = darkSurfaceVariant,
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
        )
    val lightScheme =
        buildScheme(
            dark = false,
            background = lightBackground,
            surface = lightSurface,
            surfaceVariant = lightSurfaceVariant,
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
        )

    return ThemeHolder(
        id = id,
        name = name,
        inheritBase = false,
        lightScheme = lightScheme,
        darkScheme = darkScheme,
        lightTerminalColors = terminalColors(lightScheme),
        darkTerminalColors = terminalColors(darkScheme),
        lightEditorColors = emptyList(),
        darkEditorColors = emptyList(),
        lightTokenColors = JsonArray(),
        darkTokenColors = JsonArray(),
    )
}

private val mahdiDark =
    MahdiPalette(
        id = "mahdi-dark",
        name = "مهدي الداكن",
        primary = 0xFFE3B341,
        secondary = 0xFFC08A2E,
        tertiary = 0xFF9C7A46,
        darkBackground = 0xFF0F0D09,
        darkSurface = 0xFF17130C,
        darkSurfaceVariant = 0xFF241E14,
        lightBackground = 0xFFFAF5EA,
        lightSurface = 0xFFFFFCF5,
        lightSurfaceVariant = 0xFFEDE3D0,
    )

private val mahdiLight =
    MahdiPalette(
        id = "mahdi-light",
        name = "مهدي الفاتح",
        primary = 0xFF8A5A00,
        secondary = 0xFF6B4E2E,
        tertiary = 0xFF3F6C5B,
        darkBackground = 0xFF14110B,
        darkSurface = 0xFF1C1810,
        darkSurfaceVariant = 0xFF2A2418,
        lightBackground = 0xFFFFFBF2,
        lightSurface = 0xFFFFFFFF,
        lightSurfaceVariant = 0xFFF0E7D8,
    )

private val arabicNight =
    MahdiPalette(
        id = "arabic-night",
        name = "ليل عربي",
        primary = 0xFFD4AF37,
        secondary = 0xFF2AA198,
        tertiary = 0xFFB084CC,
        darkBackground = 0xFF080B16,
        darkSurface = 0xFF0F1424,
        darkSurfaceVariant = 0xFF1B2238,
        lightBackground = 0xFFF7F5EE,
        lightSurface = 0xFFFFFDF6,
        lightSurfaceVariant = 0xFFE7E2D2,
    )

private val baghdad =
    MahdiPalette(
        id = "baghdad",
        name = "بغداد",
        primary = 0xFFC9A227,
        secondary = 0xFFB3543F,
        tertiary = 0xFF2E8B7A,
        darkBackground = 0xFF140F10,
        darkSurface = 0xFF1D1517,
        darkSurfaceVariant = 0xFF2C2023,
        lightBackground = 0xFFFBF3E6,
        lightSurface = 0xFFFFFAF0,
        lightSurfaceVariant = 0xFFEFE1C9,
    )

private val desert =
    MahdiPalette(
        id = "desert",
        name = "صحراء",
        primary = 0xFFB0702F,
        secondary = 0xFF8C6239,
        tertiary = 0xFF6E7F52,
        darkBackground = 0xFF17130D,
        darkSurface = 0xFF211A12,
        darkSurfaceVariant = 0xFF2E2519,
        lightBackground = 0xFFFDF4E3,
        lightSurface = 0xFFFFFBF1,
        lightSurfaceVariant = 0xFFF0E2C8,
    )

private val tigris =
    MahdiPalette(
        id = "tigris",
        name = "دجلة",
        primary = 0xFF4FA3D1,
        secondary = 0xFF2E7DA6,
        tertiary = 0xFF7FD1C1,
        darkBackground = 0xFF071420,
        darkSurface = 0xFF0C1E2E,
        darkSurfaceVariant = 0xFF16304A,
        lightBackground = 0xFFF2F8FC,
        lightSurface = 0xFFFBFDFF,
        lightSurfaceVariant = 0xFFDDEBF5,
    )

private val euphrates =
    MahdiPalette(
        id = "euphrates",
        name = "الفرات",
        primary = 0xFF4FB477,
        secondary = 0xFF2F8F5B,
        tertiary = 0xFF8FD3A8,
        darkBackground = 0xFF08140F,
        darkSurface = 0xFF0D1F17,
        darkSurfaceVariant = 0xFF16352A,
        lightBackground = 0xFFF2FAF5,
        lightSurface = 0xFFFAFFFC,
        lightSurfaceVariant = 0xFFDCEEE3,
    )

/** الثيمات العربية السبعة جاهزة للإضافة إلى قائمة الثيمات المدمجة. */
internal val arabicThemes: List<ThemeHolder> =
    listOf(mahdiDark, mahdiLight, arabicNight, baghdad, desert, tigris, euphrates).map { it.toThemeHolder() }
