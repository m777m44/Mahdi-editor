package com.mahdi.motivation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * بطاقة احتفال + قصاصات ملوّنة عند فتح إنجاز جديد.
 *
 * كل شيء مرسوم في Compose بلا أي مكتبات إضافية، والحركة تعمل بإطارات حقيقية
 * (`withFrameNanos`) لتظل 60fps حتى على الأجهزة المتوسطة.
 */
@Composable
fun MotivationOverlay(modifier: Modifier = Modifier) {
    val celebration by Motivation.celebration.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(celebration) {
        if (celebration != null) {
            visible = true
            kotlinx.coroutines.delay(3600)
            visible = false
            kotlinx.coroutines.delay(350)
            Motivation.consumeCelebration()
        }
    }

    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // قصاصات ملوّنة تسقط من الأعلى
        if (visible) Confetti()

        AnimatedVisibility(
            visible = visible,
            enter =
                slideInVertically(animationSpec = tween(420)) { -it } + fadeIn(tween(240)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(320)),
            exit = slideOutVertically(animationSpec = tween(300)) { -it } + fadeOut(tween(200)),
        ) {
            celebration?.let { event ->
                val achievement = event.achievement
                Card(
                    modifier = Modifier.padding(top = 76.dp, start = 16.dp, end = 16.dp).widthIn(max = 420.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            modifier = Modifier.size(46.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = achievement.emoji, style = MaterialTheme.typography.titleLarge)
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isArabic) "إنجاز جديد" else "Achievement unlocked",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (isArabic) achievement.titleAr else achievement.titleEn,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (isArabic) achievement.descriptionAr else achievement.descriptionEn,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Particle(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val sway: Float,
    val spin: Float,
    val size: Float,
    val color: Color,
)

private val confettiColors =
    listOf(
        Color(0xFFF5D76E),
        Color(0xFF14B8A6),
        Color(0xFF38BDF8),
        Color(0xFFF97316),
        Color(0xFFEC4899),
        Color(0xFFA78BFA),
        Color(0xFF34D399),
    )

@Composable
private fun Confetti() {
    val particles = remember {
        val random = Random(System.currentTimeMillis())
        List(70) {
            Particle(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.45f,
                speed = 0.55f + random.nextFloat() * 0.75f,
                sway = 0.4f + random.nextFloat() * 1.4f,
                spin = random.nextFloat() * 6.28f,
                size = 5f + random.nextFloat() * 7f,
                color = confettiColors[random.nextInt(confettiColors.size)],
            )
        }
    }

    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                time = (now - start) / 1_000_000_000f
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            val t = (time - p.delay).coerceAtLeast(0f) * p.speed
            if (t <= 0f) return@forEach
            val progress = (t % 1.7f) / 1.7f
            val y = -20f + progress * (h + 40f)
            val sway = sin((progress * 2 * PI + p.sway).toFloat()) * 34f
            val x = p.x * w + sway

            rotate(degrees = (p.spin * 60f + progress * 540f), pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = (1f - progress * 0.65f).coerceIn(0f, 1f)),
                    topLeft = Offset(x - p.size / 2f, y - p.size / 4f),
                    size = Size(p.size, p.size / 2f),
                )
            }
        }
    }
}
