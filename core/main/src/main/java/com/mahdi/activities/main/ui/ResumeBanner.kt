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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * شريط «استئناف الملف الأخير» عند الإقلاع (2.5.0).
 *
 * يظهر أعلى المحرّر مباشرة بعد استعادة الجلسة، ويعرض اسم الملف وتاريخ آخر فتح،
 * مع زرّين: «متابعة» لإبقاء الجلسة كما هي، و«بدء جديد» لإغلاق كل التبويبات
 * والعودة إلى الشاشة الرئيسية.
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
        if (mainViewModel.tabs.isEmpty()) return@LaunchedEffect

        lastFile = RecentFiles.last()
        visible = lastFile != null
    }

    fun dismiss() {
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "📄", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "استئناف الملف الأخير" else "Resume last file",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Spacer(Modifier.height(6.dp))

                lastFile?.let { file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(file.lastOpened)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { dismiss() }) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isArabic) "متابعة" else "Continue")
                        }
                        TextButton(
                            onClick = {
                                dismiss()
                                // «بدء جديد»: إغلاق كل التبويبات والعودة للشاشة الرئيسية
                                mainViewModel.tabManager.removeAllTabs()
                            }
                        ) {
                            Text(if (isArabic) "بدء جديد" else "Start fresh")
                        }
                    }
                }
            }
        }
    }
}
