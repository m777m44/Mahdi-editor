package com.mahdi.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay

/**
 * 9.4.0 — حزمة الحركة والجمال المشتركة.
 *
 * كل عناصر «الإحساس الاحترافي» التي كانت مبعثرة داخل الشاشات في مكان واحد:
 * - [AuroraBackground]: خلفية شفق متحركة بثلاث هالات لونية تنساب ببطء خلف المحتوى.
 * - [StaggeredAppear]: ظهور متتابع ناعم لعناصر الشاشة (fade + انزلاق لطيف).
 * - [Modifier.bounceClick]: ضغط مرن — البطاقة تنكمش قليلًا ثم ترتدّ، بلا وميض.
 *
 * كل الحركات هنا **خفيفة** (رسوم Canvas + طبقة graphicsLayer) فلا تُسخّن الجهاز
 * ولا تُسقط الإطارات على هواتف متوسطة مثل Redmi Note 8.
 */

/**
 * خلفية «الشفق» المتحركة.
 *
 * تعتمد لون السمة نفسه ([MaterialTheme.colorScheme.background]) كأساس، ثم تُضيف
 * ثلاث هالات شفافة (سماوي · بنفسجي · نعناعي) تتحرك ببطء في مسارات متقاطعة.
 * في السمة الفاتحة تخفّ شدّة الهالات تلقائيًا فلا تصير الخلفية فاقعة.
 *
 * تُوضع دائمًا مع `Modifier.matchParentSize()` داخل `Box` فلا تزاحم المحتوى.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    accentA: Color = Color(0xFF38BDF8),
    accentB: Color = Color(0xFFA78BFA),
    accentC: Color = Color(0xFF2DD4BF),
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val baseTop = scheme.background
    val baseBottom =
        if (dark) {
            scheme.surfaceContainerLowest
        } else {
            scheme.surfaceContainerLow
        }

    // شدّة الهالات: واضحة في الداكن، هادئة جدًا في الفاتح.
    val strength = if (dark) 0.30f else 0.16f

    val drift = rememberInfiniteTransition(label = "aurora")
    val phaseA by
        drift.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse),
            label = "phaseA",
        )
    val phaseB by
        drift.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(31000, easing = LinearEasing), RepeatMode.Reverse),
            label = "phaseB",
        )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        drawRect(Brush.verticalGradient(listOf(baseTop, baseBottom)))

        auroraGlow(accentA, w * (0.18f + 0.22f * phaseA), h * (0.16f + 0.10f * phaseB), w * 0.92f, strength)
        auroraGlow(accentB, w * (0.86f - 0.20f * phaseB), h * (0.30f + 0.08f * phaseA), w * 0.80f, strength)
        auroraGlow(accentC, w * (0.42f + 0.16f * phaseB), h * (0.88f - 0.10f * phaseA), w * 0.74f, strength)
    }
}

/**
 * ظهور متتابع ناعم: الشفافية من ٠ إلى ١ مع انزلاق ٢٦dp من الأسفل.
 *
 * @param delayMillis تأخير البداية — يُعطى لكل عنصر تأخيرًا أكبر من سابقه
 *   (٦٠–٩٠ ملّي ثانية) فيبدو المحتوى كأنه «يُنساب» إلى مكانه.
 */
@Composable
fun StaggeredAppear(
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        shown = true
    }

    val alpha by
        animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(durationMillis = 420, delayMillis = 0, easing = FastOutSlowInEasing),
            label = "appearAlpha",
        )
    val offsetY by
        animateFloatAsState(
            targetValue = if (shown) 0f else 26f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            label = "appearOffset",
        )

    Box(
        modifier =
            modifier.graphicsLayer {
                this.alpha = alpha
                translationY = offsetY
            }
    ) {
        content()
    }
}

/**
 * ضغط مرن عند اللمس: ٠٫٩٦ عند الضغط ثم ارتداد بنابض.
 *
 * يعطي إحساس «البطاقة الحقيقية» بدل التغيّر المفاجئ، ويعمل مع مؤشّر اللمس الأصلي
 * ([LocalIndication]) فيبقى الـripple متاحًا للأجهزة التي تدعمه.
 */
@Composable
fun Modifier.bounceClick(scaleDown: Float = 0.96f, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (pressed) scaleDown else 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "bounce",
        )

    return this.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick,
        )
}

/** هالة نصف شفافة واحدة — تُرسم داخل [AuroraBackground]. */
private fun DrawScope.auroraGlow(color: Color, cx: Float, cy: Float, r: Float, strength: Float) {
    val center = Offset(cx, cy)
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = strength), Color.Transparent), center = center, radius = r),
        radius = r,
        center = center,
    )
}
