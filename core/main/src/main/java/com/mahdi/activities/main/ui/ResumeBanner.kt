package com.mahdi.activities.main.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahdi.activities.main.MainViewModel
import com.mahdi.session.RecentFile
import com.mahdi.session.RecentFiles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** يضمن ظهور الشريط مرّة واحدة فقط في كل تشغيل للتطبيق. */
private object ResumeBannerState {
    var shownThisLaunch = false
}

/**
 * شريط «استئناف الملف الأخير» عند الإقلاع (2.5.0 — أُعيد تصميمه في 8.1.0).
 *
 * ما كان معطوبًا:
 * - كان يُرسم فوق **الشاشة الرئيسية** أيضًا، فتظهر بطاقة كبيرة بعنوان واسم ملف
 *   غير مرئيين (لون غير متباين) وزرّان فقط — وهو ما في لقطة المستخدم.
 * - كان يبقى بلا نهاية ويأخذ ارتفاعًا كبيرًا من أعلى الشاشة.
 *
 * الآن:
 * - لا يظهر إلا إذا كان هناك تبويب مفتوح فعلًا (لا معنى له على الشاشة الرئيسية).
 * - سطر واحد: أيقونة + **اسم الملف** (واضح، بخطّ عريض) + وقت آخر فتح.
 * - زرّان صغيران أسفله، ويختفي تلقائيًا بعد ١٥ ثانية أو بلمسة.
 * - ألوان صريحة (`surfaceContainerHigh` / `onSurface`) — لا اعتماد على ألوان
 *   الثيم التي قد تتطابق مع الخلفية.
 */
@Composable
fun ResumeBanner(mainViewModel: MainViewModel, modifier: Modifier = Modifier) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    var lastFile by remember { mutableStateOf<RecentFile?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (ResumeBannerState.shownThisLaunch) return@LaunchedEffect
        ResumeBannerState.shownThisLaunch = true

        // ننتظر استعادة الجلسة واستقرار الواجهة قبل الظهور
        delay(700)
        if (mainViewModel.visibleTabs.isEmpty()) return@LaunchedEffect

        lastFile = RecentFiles.last()
        visible = lastFile != null

        // لا يبقى عالقًا فوق المحتوى: يختفي وحده بعد ١٥ ثانية
        if (visible) {
            delay(15_000)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Surface(
            modifier =
                Modifier.fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = lastFile?.name ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    lastFile?.let { file ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.US).format(Date(file.lastOpened)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = if (isArabic) "آخر ملف فتحته" else "Last file you opened",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { visible = false },
                        contentPadding = ButtonDefaults.ContentPadding,
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isArabic) "متابعة" else "Continue",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    TextButton(
                        onClick = {
                            visible = false
                            // «بدء جديد»: إغلاق كل التبويبات والعودة للشاشة الرئيسية
                            mainViewModel.tabManager.removeAllTabs()
                        },
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text(
                            text = if (isArabic) "بدء جديد" else "Start fresh",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
