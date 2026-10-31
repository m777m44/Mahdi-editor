package com.mahdi.terminal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahdi.file.child
import com.mahdi.file.sandboxHomeDir
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * بطاقة تقدّم التنزيل/التثبيت.
 *
 * تقرأ ملف `~/.mahdi/progress.json` الذي تكتبه سكربتات الطرفية (utils.sh و mahdi_pip.py)
 * كل ربع ثانية، وتعرض اسم المكتبة والطور والنسبة المئوية الحقيقية من 0 إلى 100.
 */
data class DownloadProgress(
    val id: String,
    val name: String,
    val stage: String,
    val stageEn: String,
    val detail: String,
    val percent: Int,
    val done: Boolean,
    val failed: Boolean,
    val timestamp: Long,
)

private val progressFile: File
    get() = sandboxHomeDir().child(".mahdi").child("progress.json")

/** يقرأ حالة التقدّم الحالية (null إن لم تكن هناك عملية جارية). */
fun readDownloadProgress(): DownloadProgress? {
    val file = progressFile
    if (!file.exists()) return null
    val text = runCatching { file.readText() }.getOrNull() ?: return null
    if (text.isBlank()) return null
    return runCatching {
        val json = JSONObject(text)
        DownloadProgress(
            id = json.optString("id"),
            name = json.optString("name").ifBlank { "—" },
            stage = json.optString("stage"),
            stageEn = json.optString("stage_en"),
            detail = json.optString("detail"),
            percent = json.optInt("percent").coerceIn(0, 100),
            done = json.optBoolean("done"),
            failed =
                json.optString("stage").contains("تعذّر") ||
                    json.optString("stage").contains("فشل"),
            timestamp = json.optLong("ts"),
        )
    }
        .getOrNull()
}

/** حالة تقدّم حيّة تُحدَّث كل ٠٫٢–٠٫٣ ثانية، وتختفي تلقائيًا بعد الانتهاء. */
@Composable
fun rememberDownloadProgress(): DownloadProgress? {
    return produceState<DownloadProgress?>(initialValue = null) {
        var doneAt = 0L
        while (true) {
            val snapshot = withContext(Dispatchers.IO) { readDownloadProgress() }
            val nowSeconds = System.currentTimeMillis() / 1000L
            when {
                snapshot == null || nowSeconds - snapshot.timestamp > 25 -> {
                    value = null
                    doneAt = 0L
                }
                snapshot.done -> {
                    if (doneAt == 0L) doneAt = System.currentTimeMillis()
                    value = if (System.currentTimeMillis() - doneAt < 2400) snapshot else null
                }
                else -> {
                    value = snapshot
                    doneAt = 0L
                }
            }
            delay(if (snapshot?.done == true) 150 else 260)
        }
    }.value
}

/** الغلاف المتحرّك: يظهر بانزلاق ناعم من الأسفل ويختفي بتلاشٍ. */
@Composable
fun DownloadProgressOverlay(progress: DownloadProgress?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf<DownloadProgress?>(null) }
    LaunchedEffect(progress) { if (progress != null) last = progress }

    AnimatedVisibility(
        visible = progress != null,
        enter =
            slideInVertically(animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f)) { it / 2 } +
                fadeIn(animationSpec = tween(220)),
        exit = slideOutVertically(animationSpec = tween(260)) { it / 2 } + fadeOut(animationSpec = tween(200)),
        modifier = modifier,
    ) {
        last?.let { DownloadProgressCard(it) }
    }
}

@Composable
private fun DownloadProgressCard(progress: DownloadProgress) {
    val scheme = MaterialTheme.colorScheme
    // نص الطور بلغة التطبيق: العربية افتراضيًا، والإنجليزية عند اختيارها
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val stageText = if (isArabic || progress.stageEn.isBlank()) progress.stage else progress.stageEn
    val done = progress.done
    val failed = progress.failed
    val accentStart =
        when {
            failed -> Color(0xFFEF4444)
            done -> Color(0xFF10B981)
            else -> scheme.primary
        }
    val accentEnd =
        when {
            failed -> Color(0xFFF97316)
            done -> Color(0xFF34D399)
            else -> scheme.tertiary
        }

    val animatedPercent by
        animateFloatAsState(
            targetValue = progress.percent.toFloat(),
            animationSpec = tween(durationMillis = 650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "percent",
        )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = scheme.surfaceContainerHigh,
        shadowElevation = 14.dp,
        tonalElevation = 3.dp,
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .border(
                    width = 1.dp,
                    brush =
                        Brush.linearGradient(
                            listOf(accentStart.copy(alpha = 0.55f), accentEnd.copy(alpha = 0.20f)),
                        ),
                    shape = RoundedCornerShape(26.dp),
                ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier.size(44.dp)
                            .background(
                                brush = Brush.linearGradient(listOf(accentStart, accentEnd)),
                                shape = CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector =
                            when {
                                failed -> Icons.Filled.Warning
                                done -> Icons.Filled.CheckCircle
                                else -> Icons.Filled.KeyboardArrowDown
                            },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = progress.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AnimatedContent(
                        targetState = stageText,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                        label = "stage",
                    ) { stage ->
                        Text(
                            text = stage,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${animatedPercent.toInt()}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = if (done || failed) accentStart else scheme.onSurface,
                    )
                    Text(
                        text = "%",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            GradientProgress(
                progress = (animatedPercent / 100f).coerceIn(0f, 1f),
                start = accentStart,
                end = accentEnd,
                track = scheme.surfaceContainerHighest,
                animated = !done && !failed,
            )

            if (progress.detail.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = progress.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

/** شريط تقدّم متدرّج بحواف دائرية ولمعة متحركة. */
@Composable
private fun GradientProgress(
    progress: Float,
    start: Color,
    end: Color,
    track: Color,
    animated: Boolean,
) {
    val shimmer =
        rememberInfiniteTransition(label = "shimmer")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
                label = "shimmerOffset",
            )

    Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
        val h = size.height
        val w = size.width
        val radius = CornerRadius(h / 2f, h / 2f)

        drawRoundRect(color = track, size = Size(w, h), cornerRadius = radius)

        val filled = (w * progress).coerceIn(0f, w)
        if (filled <= 0f) return@Canvas

        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(start, end), endX = filled),
            size = Size(filled, h),
            cornerRadius = radius,
        )

        if (animated && filled > 1f) {
            val bandWidth = w * 0.35f
            val x = -bandWidth + (w + bandWidth * 2f) * shimmer.value
            clipRect(right = filled) {
                drawRoundRect(
                    brush =
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color.White.copy(alpha = 0f),
                                    Color.White.copy(alpha = 0.28f),
                                    Color.White.copy(alpha = 0f),
                                ),
                            startX = x,
                            endX = x + bandWidth,
                        ),
                    topLeft = Offset(0f, 0f),
                    size = Size(filled, h),
                    cornerRadius = radius,
                )
            }
        }
    }
}
