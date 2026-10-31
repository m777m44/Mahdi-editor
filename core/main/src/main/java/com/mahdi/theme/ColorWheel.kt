package com.mahdi.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mahdi.resources.strings
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import com.mahdi.utils.toast

// ——————————————————————————————————————————————————————————————
// تحويلات الألوان (HSL ⇄ ARGB) مكتوبة يدويًا لتجنّب أي اختلاف في واجهات Compose
// ——————————————————————————————————————————————————————————————

/** يولّد لونًا من تدرّج/تشبّع/سطوع بنسب [0,1] ودرجة لون بالدرجات [0,360). */
fun hslToColor(hue: Float, saturation: Float, lightness: Float, alpha: Float = 1f): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)

    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f

    val (r1, g1, b1) =
        when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

/** يفكّك لونًا إلى (درجة، تشبّع، سطوع). */
fun colorToHsl(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC

    val lightness = (maxC + minC) / 2f

    if (delta < 0.0001f) return Triple(0f, 0f, lightness)

    val saturation = delta / (1f - abs(2f * lightness - 1f))

    val hue =
        when (maxC) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }

    return Triple(((hue % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness)
}

/** HEX بصيغة `#RRGGBB`. */
fun Color.toHexString(): String {
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

/** RGB بصيغة `(r, g, b)`. */
fun Color.toRgbString(): String {
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return "($r, $g, $b)"
}

/** HSL بصيغة `(h° s% l%)`. */
fun Color.toHslString(): String {
    val (h, s, l) = colorToHsl(this)
    return "(${h.roundToInt()}° ${(s * 100).roundToInt()}% ${(l * 100).roundToInt()}%)"
}

/** السطوع النسبي وفق WCAG 2.1. */
fun Color.relativeLuminance(): Float {
    fun channel(v: Float): Float = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).let { it * it * it }
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

/** نسبة التباين بين لونين (1.0 — 21.0). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    val lighter = max(la, lb)
    val darker = min(la, lb)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** هل يحقق الزوج WCAG AA للنص العادي (≥ 4.5)؟ */
fun passesWcagAa(a: Color, b: Color): Boolean = contrastRatio(a, b) >= 4.5f

// ——————————————————————————————————————————————————————————————
// دائرة الألوان (دائرية تمامًا — 2.5.0)
// ——————————————————————————————————————————————————————————————

/** 13 عيّنة حول دائرة الدرجات، تُستخدم لتدرّج واحد سلس بدل 360 قوسًا. */
private fun hueSpectrum(): List<Color> = (0..12).map { hslToColor(it * 30f, 1f, 0.5f) }

/** يضبط تشبّع وسطوع أي لون مع الحفاظ على درجته. */
fun Color.withSaturationLightness(saturation: Float, lightness: Float): Color {
    val (h, _, _) = colorToHsl(this)
    return hslToColor(h, saturation, lightness)
}

/**
 * دائرة ألوان دائرية احترافية:
 *
 * - **حلقة خارجية** لدرجة اللون (Hue) مرسومة بتدرّج واحد (`sweepGradient`) بدل 360 قوسًا،
 *   فتبقى الحركة 60fps بلا جهد.
 * - **قرص داخلي دائري** للتشبّع (Saturation) والسطوع (Lightness): كلّما اقتربت من المركز قلّ التشبّع.
 * - **مؤشّران** يتحرّكان بالسحب، ومعاينة حيّة فورية.
 * - منزلق السطوع + قيم HEX/RGB/HSL بضغطة نسخ.
 */
@Composable
fun ColorWheel(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    wheelSize: Int = 280,
) {
    val (initialHue, initialSat, initialLight) = remember(initialColor) { colorToHsl(initialColor) }

    var hue by remember { mutableFloatStateOf(initialHue) }
    var saturation by remember { mutableFloatStateOf(initialSat) }
    var lightness by remember { mutableFloatStateOf(initialLight) }

    val current = hslToColor(hue, saturation, lightness)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    // تُقرأ داخل Canvas (lambda غير composable) — لذا نأخذها هنا
    val outlineColor = MaterialTheme.colorScheme.outline

    // نُبلّغ الأب عند كل تغيير فعلي فقط
    LaunchedEffect(hue, saturation, lightness) { onColorChange(current) }

    val spectrum = remember { hueSpectrum() }

    // أبعاد القرص محسوبة مسبقًا ليتطابق الرسم مع اللمس تمامًا
    fun updateFrom(position: Offset, sizePx: Float) {
        val center = Offset(sizePx / 2f, sizePx / 2f)
        val outer = sizePx / 2f
        val ringWidth = outer * 0.18f
        val ringRadius = outer - ringWidth / 2f
        val discRadius = outer - ringWidth - 8f

        val dx = position.x - center.x
        val dy = position.y - center.y
        val distance = sqrt(dx * dx + dy * dy)

        val angle = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f)

        when {
            distance >= discRadius + 4f -> {
                hue = angle
            } // الحلقة: درجة اللون فقط
            else -> {
                hue = angle
                saturation = (distance / discRadius).coerceIn(0f, 1f)
            }
        }
        if (distance < 2f) saturation = 0f
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier =
                Modifier
                    .size(wheelSize.dp)
                    .pointerInput(wheelSize) {
                        detectTapGestures { updateFrom(it, size.width.toFloat()) }
                    }
                    .pointerInput(wheelSize) {
                        detectDragGestures(
                            onDragStart = { updateFrom(it, size.width.toFloat()) },
                            onDrag = { change, _ ->
                                change.consume()
                                updateFrom(change.position, size.width.toFloat())
                            },
                        )
                    }
        ) {
            val sizePx = size.minDimension
            val outer = sizePx / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val ringWidth = outer * 0.18f
            val ringRadius = outer - ringWidth / 2f
            val discRadius = outer - ringWidth - 8f

            // ١) الحلقة: تدرّج واحد يدور 360° (سريع جدًا)
            rotate(degrees = -90f, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(spectrum, center),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = ringWidth),
                )
            }

            // لمعة خفيفة على الحلقة لتبدو زجاجية
            drawCircle(
                brush =
                    Brush.sweepGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.White.copy(alpha = 0.1f)),
                        center,
                    ),
                radius = ringRadius,
                center = center,
                style = Stroke(width = ringWidth * 0.45f),
            )

            // ٢) القرص الداخلي: درجة اللون + تشبّع بالمسافة + سطوع بالطبقات
            rotate(degrees = -90f, pivot = center) {
                drawCircle(brush = Brush.sweepGradient(spectrum, center), radius = discRadius, center = center)
            }
            // تشبّع: أبيض في المركز يتلاشى نحو الحافة
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                        center = center,
                        radius = discRadius,
                    ),
                radius = discRadius,
                center = center,
            )
            // سطوع: تعتيم أو تفتيح حسب القيمة
            if (lightness < 0.5f) {
                drawCircle(
                    color = Color.Black.copy(alpha = ((0.5f - lightness) * 1.7f).coerceIn(0f, 1f)),
                    radius = discRadius,
                    center = center,
                )
            } else if (lightness > 0.5f) {
                drawCircle(
                    color = Color.White.copy(alpha = ((lightness - 0.5f) * 1.7f).coerceIn(0f, 1f)),
                    radius = discRadius,
                    center = center,
                )
            }

            // حدود رقيقة تفصل القرص عن الحلقة
            drawCircle(
                color = outlineColor.copy(alpha = 0.35f),
                radius = discRadius,
                center = center,
                style = Stroke(width = 1.5f),
            )

            // ٣) مؤشّر الحلقة
            val hueRad = Math.toRadians((hue - 90f).toDouble())
            val ringThumb =
                Offset(
                    center.x + (ringRadius * cos(hueRad)).toFloat(),
                    center.y + (ringRadius * sin(hueRad)).toFloat(),
                )
            drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = ringWidth * 0.30f, center = ringThumb)
            drawCircle(color = Color.White, radius = ringWidth * 0.26f, center = ringThumb)
            drawCircle(color = hslToColor(hue, 1f, 0.5f), radius = ringWidth * 0.18f, center = ringThumb)

            // ٤) مؤشّر القرص
            val discThumb =
                Offset(
                    center.x + (discRadius * saturation * cos(hueRad)).toFloat(),
                    center.y + (discRadius * saturation * sin(hueRad)).toFloat(),
                )
            drawCircle(color = Color.Black.copy(alpha = 0.45f), radius = 11f, center = discThumb)
            drawCircle(color = Color.White, radius = 10f, center = discThumb)
            drawCircle(color = current, radius = 7f, center = discThumb)
        }

        Spacer(Modifier.height(10.dp))

        // السطوع
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(strings.lightness),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(64.dp),
            )
            Slider(
                value = lightness,
                onValueChange = { lightness = it },
                valueRange = 0.02f..1f,
                modifier = Modifier.weight(1f),
            )
            Text(text = "${(lightness * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(6.dp))

        // المعاينة + القيم القابلة للنسخ
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(current)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )

            listOf(
                "HEX" to current.toHexString(),
                "RGB" to current.toRgbString(),
                "HSL" to current.toHslString(),
            ).forEach { (label, value) ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier =
                        Modifier.clickable {
                            clipboard.setText(AnnotatedString(value))
                            toast(context.getString(strings.copied))
                        },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(text = label, style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = value,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ——————————————————————————————————————————————————————————————
// اقتراحات ثيمات مصغّرة (2.5.0)
// ——————————————————————————————————————————————————————————————

/** اقتراح ثيم: اسم + لون أساسي + خلفية + تمييز (للمعاينة المصغّرة). */
data class ThemeSuggestion(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val primary: Color,
    val background: Color,
    val accent: Color,
)

/** يولّد اقتراحات مشتقّة من اللون الحالي + اقتراحات ثابتة أنيقة. */
fun generateSuggestions(base: Color): List<ThemeSuggestion> {
    val (h, s, l) = colorToHsl(base)
    val fixedS = s.coerceAtLeast(0.55f)
    fun at(delta: Float, sat: Float = fixedS, light: Float = l) = hslToColor(h + delta, sat, light)

    return listOf(
        ThemeSuggestion("mono", "أحادي", "Mono", base, hslToColor(h, 0.25f, 0.10f), at(0f, fixedS, (l + 0.2f).coerceAtMost(0.9f))),
        ThemeSuggestion("complement", "متضاد", "Complement", base, hslToColor(h + 180f, 0.35f, 0.10f), at(180f)),
        ThemeSuggestion("analogous", "متجاور", "Analogous", base, hslToColor(h - 25f, 0.30f, 0.10f), at(35f)),
        ThemeSuggestion("triadic", "ثلاثي", "Triadic", base, hslToColor(h, 0.22f, 0.08f), at(120f)),
        ThemeSuggestion("split", "منفصل", "Split", base, hslToColor(h, 0.20f, 0.09f), at(150f)),
        ThemeSuggestion("tint", "فاتح", "Light", base, hslToColor(h, 0.16f, 0.94f), at(20f, fixedS, 0.42f)),
        ThemeSuggestion("kiyani", "كِياني", "Kiyani", Color(0xFF14B8A6), Color(0xFF0A1F1F), Color(0xFFF5D76E)),
        ThemeSuggestion("royal", "ملكي", "Royal", Color(0xFFF5D76E), Color(0xFF1A0B2E), Color(0xFF7C3AED)),
        ThemeSuggestion("sky", "سمائي", "Sky", Color(0xFF38BDF8), Color(0xFF0A1B2E), Color(0xFFE0F2FE)),
        ThemeSuggestion("dim", "مظلم", "Dim", Color(0xFF9DB2C6), Color(0xFF1B1D21), Color(0xFFE0A458)),
        ThemeSuggestion("neon", "نيون", "Neon", Color(0xFF00F0FF), Color(0xFF0A0A0A), Color(0xFFFF00E5)),
        ThemeSuggestion("sunset", "غروب", "Sunset", Color(0xFFF97316), Color(0xFF7C2D12), Color(0xFFFACC15)),
        ThemeSuggestion("forest", "غابة", "Forest", Color(0xFF16A34A), Color(0xFF052E16), Color(0xFFF59E0B)),
        ThemeSuggestion("ocean", "محيط", "Ocean", Color(0xFF0EA5E9), Color(0xFF082F49), Color(0xFF67E8F9)),
    )
}

/**
 * صفّ اقتراحات مصغّرة أسفل دائرة الألوان: كل بطاقة تعاين ثيمًا كاملًا
 * (خلفية + شريط أساسي + شريط تمييز) وتطبّق لونها الأساسي بضغطة.
 */
@Composable
fun ThemeSuggestions(
    baseColor: Color,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = remember(baseColor) { generateSuggestions(baseColor) }
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier) {
        Text(
            text = "🎨 " + stringResource(strings.theme_suggestions),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(suggestions, key = { it.id }) { suggestion ->
                val selected = suggestion.primary.value == baseColor.value
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .width(84.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelect(suggestion.primary)
                            }
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .padding(4.dp),
                ) {
                    // معاينة مصغّرة: خلفية الثيم + شريطان
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(74.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(suggestion.background)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.85f)
                                .height(9.dp)
                                .background(suggestion.primary, RoundedCornerShape(5.dp))
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(0.55f)
                                .height(6.dp)
                                .background(suggestion.accent, RoundedCornerShape(3.dp))
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .height(6.dp)
                                .background(suggestion.primary.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isArabic) suggestion.nameAr else suggestion.nameEn,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** بطاقة لون صغيرة مستديرة تُستخدم في قوائم الخانات. */
@Composable
fun ColorSwatch(color: Color, selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp),
                )
    )
}
