package com.mahdi.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    val stalled: Boolean,
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
            stalled = json.optBoolean("stalled"),
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

/**
 * بطاقة التقدّم المصغّرة (6.1.0).
 *
 * قبل 6.1.0 كانت بطاقة بعرض الشاشة كامل تلتصق بأسفل الطرفية فوق شريط المفاتيح.
 * الآن صارت بطاقة صغيرة **مثبَّتة داخل الطرفية** في طرفها السفلي: الاسم والنسبة
 * وشريط رقيق في مساحة لا تتجاوز ١٩٦dp، فلا تحجب نصًّا ولا تغطّي الأزرار.
 */
@Composable
fun DownloadProgressDock(progress: DownloadProgress?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf<DownloadProgress?>(null) }
    LaunchedEffect(progress) { if (progress != null) last = progress }

    AnimatedVisibility(
        visible = progress != null,
        enter =
            slideInHorizontally(animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 2 } +
                fadeIn(animationSpec = tween(160)),
        exit = slideOutHorizontally(animationSpec = tween(180)) { it / 2 } + fadeOut(animationSpec = tween(140)),
        modifier = modifier,
    ) {
        last?.let { DockCard(it) }
    }
}

@Composable
private fun DockCard(progress: DownloadProgress) {
    val scheme = MaterialTheme.colorScheme
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val done = progress.done
    val failed = progress.failed
    val stalled = progress.stalled && !done && !failed
    val stageText = if (isArabic || progress.stageEn.isBlank()) progress.stage else progress.stageEn
    val accentStart =
        when {
            failed -> Color(0xFFEF4444)
            done -> Color(0xFF10B981)
            stalled -> Color(0xFFF59E0B)
            else -> scheme.primary
        }
    val accentEnd =
        when {
            failed -> Color(0xFFF97316)
            done -> Color(0xFF34D399)
            stalled -> Color(0xFFFBBF24)
            else -> scheme.tertiary
        }

    // سطر واحد ثابت: الشريط يتلاحق بسلاسة ولا يقفز (كان يقفز بسبب تحديثات pip المتقطّعة)
    val animatedPercent by
        animateFloatAsState(
            targetValue = progress.percent.toFloat(),
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            label = "dockPercent",
        )

    // 8.0.0: لا نكرّر اسم الحزمة في سطرين (كان يظهر مرّتين: فوق وتحت الشريط،
    // والسطر السفلي كان يفيض خارج حدود البطاقة). السطر الثالث يظهر فقط إذا
    // أضاف معلومة جديدة فعلًا.
    val caption =
        when {
            progress.detail.isNotBlank() && progress.detail != progress.name -> progress.detail
            stageText.isNotBlank() && stageText != progress.name -> stageText
            else -> ""
        }

    val shape = RoundedCornerShape(16.dp)

    Surface(
        shape = shape,
        color = scheme.surfaceContainerHigh.copy(alpha = 0.97f),
        shadowElevation = 8.dp,
        // عرض ثابت: بطاقة بعرض متغيّر كانت تجعل النصّ يفيض ويتراكب مع الطرفية
        modifier =
            Modifier.width(212.dp)
                .border(
                    width = 1.dp,
                    brush =
                        Brush.linearGradient(
                            listOf(accentStart.copy(alpha = 0.55f), accentEnd.copy(alpha = 0.18f)),
                        ),
                    shape = shape,
                ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier.size(6.dp)
                            .clip(CircleShape)
                            .background(accentStart),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = progress.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (done && !failed) "100%" else "${animatedPercent.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = accentStart,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(6.dp))

            GradientProgress(
                progress = (animatedPercent / 100f).coerceIn(0f, 1f),
                start = accentStart,
                end = accentEnd,
                track = scheme.surfaceContainerHighest,
                animated = !done && !failed,
                height = 4.dp,
            )

            if (caption.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stalled) accentStart else scheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
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
    height: Dp = 10.dp,
) {
    val shimmer =
        rememberInfiniteTransition(label = "shimmer")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
                label = "shimmerOffset",
            )

    Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
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
