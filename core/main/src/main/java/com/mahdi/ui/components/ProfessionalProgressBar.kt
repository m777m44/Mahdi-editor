package com.mahdi.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * شريط تقدّم احترافي (5.0.0).
 *
 * - يتحرّك بسلاسة (`animateFloatAsState`) فلا يقفز بين النسب.
 * - تدرّج ثلاثي الألوان: سمائي ← فيروزي ← ذهبي.
 * - نسبة رقمية بخطّ ثابت العرض تحته، فلا ترتجف الأرقام أثناء التحميل.
 */
@Composable
fun ProfessionalProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String = "",
    height: androidx.compose.ui.unit.Dp = 12.dp,
) {
    val animated by
        animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "professional-progress",
        )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(RoundedCornerShape(height / 2))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animated)
                        .clip(RoundedCornerShape(height / 2))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF38BDF8),
                                    Color(0xFF14B8A6),
                                    Color(0xFFF5D76E),
                                ),
                            ),
                        ),
            )
        }

        if (label.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
