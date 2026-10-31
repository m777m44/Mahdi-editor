package com.mahdi.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
// دائرة الألوان
// ——————————————————————————————————————————————————————————————

/**
 * دائرة ألوان HSL كاملة:
 * - الحلقة الخارجية: درجة اللون (360°).
 * - المربع الداخلي: التشبع (أفقيًا) والسطوع (عموديًا).
 * أي تغيير يُبلَّغ فورًا عبر [onColorChange] فتُحدَّث المعاينة الحية.
 */
@Composable
fun ColorWheel(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    wheelSize: Int = 260,
) {
    val (initialHue, initialSat, initialLight) = remember(initialColor) { colorToHsl(initialColor) }

    var hue by remember { mutableFloatStateOf(initialHue) }
    var saturation by remember { mutableFloatStateOf(initialSat) }
    var lightness by remember { mutableFloatStateOf(initialLight) }

    val current = hslToColor(hue, saturation, lightness)

    // نُبلّغ الأب عند كل تغيير فعلي فقط
    LaunchedEffect(hue, saturation, lightness) { onColorChange(current) }

    fun updateFrom(position: Offset, sizePx: Float) {
        val center = Offset(sizePx / 2f, sizePx / 2f)
        val dx = position.x - center.x
        val dy = position.y - center.y
        val radius = sqrt(dx * dx + dy * dy)

        val outer = sizePx / 2f
        val ringOuter = outer
        val ringInner = outer * 0.76f
        val padOuter = outer * 0.70f

        val angle = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f)

        when {
            // منطقة الحلقة: تغيير درجة اللون
            radius in ringInner..(ringOuter + 8f) -> hue = angle
            // منطقة المربع: تشبّع + سطوع
            radius <= padOuter -> {
                val padSize = padOuter * 2f
                val left = center.x - padOuter
                val top = center.y - padOuter
                saturation = ((position.x - left) / padSize).coerceIn(0f, 1f)
                lightness = (1f - ((position.y - top) / padSize)).coerceIn(0f, 1f)
            }
            else -> hue = angle
        }
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
            val ringWidth = outer * 0.24f
            val radius = outer - ringWidth / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // حلقة درجة اللون: 90 قوسًا لتبدو ناعمة
            val segments = 90
            val sweep = 360f / segments
            for (i in 0 until segments) {
                drawArc(
                    color = hslToColor(i * sweep, 0.95f, 0.5f),
                    startAngle = i * sweep - 90f,
                    sweepAngle = sweep + 0.6f,
                    useCenter = false,
                    style = Stroke(width = ringWidth, cap = StrokeCap.Butt),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                )
            }

            // مربع التشبع × السطوع
            val pad = outer * 0.70f
            for (y in 0..24) {
                for (x in 0..24) {
                    val sat = x / 24f
                    val light = 1f - y / 24f
                    val cellW = (pad * 2f) / 25f + 1.5f
                    val cellH = (pad * 2f) / 25f + 1.5f
                    drawRect(
                        color = hslToColor(hue, sat, light),
                        topLeft = Offset(center.x - pad + x * (pad * 2f / 25f), center.y - pad + y * (pad * 2f / 25f)),
                        size = Size(cellW, cellH),
                    )
                }
            }

            // مؤشر الحلقة
            val angleRad = Math.toRadians(hue.toDouble())
            val thumb = Offset(center.x + (radius * cos(angleRad)).toFloat(), center.y + (radius * sin(angleRad)).toFloat())
            drawCircle(color = Color.White, radius = ringWidth * 0.34f, center = thumb)
            drawCircle(color = hslToColor(hue, 1f, 0.5f), radius = ringWidth * 0.24f, center = thumb)

            // مؤشر المربع
            val padPoint =
                Offset(
                    center.x - pad + saturation * pad * 2f,
                    center.y - pad + (1f - lightness) * pad * 2f,
                )
            drawCircle(color = Color.Black, radius = 9f, center = padPoint)
            drawCircle(color = Color.White, radius = 6f, center = padPoint)
        }

        val (h, s, l) = Triple(hue, saturation, lightness)

        Text(
            text = stringResource(strings.hue) + ": ${h.roundToInt()}°",
            style = MaterialTheme.typography.labelLarge,
        )

        Slider(
            value = s,
            onValueChange = { saturation = it },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Text(
            text = stringResource(strings.saturation) + ": ${(s * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
        )

        Slider(
            value = l,
            onValueChange = { lightness = it },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Text(
            text = stringResource(strings.lightness) + ": ${(l * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(current)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Text(
                text = "  HEX ${current.toHexString()}   •   RGB ${current.toRgbString()}   •   HSL ${current.toHslString()}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp),
            )
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
