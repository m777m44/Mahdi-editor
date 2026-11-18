package com.mahdi.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 9.5.0 — إحصاءات المشروع المفتوح.
 *
 * بطاقة صغيرة في الرئيسية تُظهر: عدد الملفات · عدد المجلدات · الحجم على القرص.
 * الحساب يجري على خيط IO بحدود صارمة (عدد مدخلات أقصى) حتى لا يتجمّد التطبيق ولا
 * يستهلك البطارية في مشروع ضخم، والمجلدات الثقيلة غير المفيدة (`.git` ·
 * `node_modules` · `build` · `__pycache__` · `venv`) تُتجاوز تمامًا.
 */
data class ProjectStats(
    val files: Int,
    val folders: Int,
    val bytes: Long,
    /** `true` إن توقّف العدّ عند الحد الأقصى (المشروع أكبر من أن يُحصى كاملًا). */
    val truncated: Boolean,
)

private val SKIPPED_DIRS =
    setOf(".git", "node_modules", "build", "__pycache__", "venv", ".gradle", ".idea", "dist", ".venv", "target")

/**
 * يقرأ إحصاءات [root] دون تجميد الواجهة.
 *
 * @param maxEntries سقف عدد المدخلات المفحوصة (افتراضيًا ٢٥٠٠) — يكفي لتقدير دقيق
 *   ويحافظ على سرعة الاستجابة على أجهزة متوسطة.
 */
suspend fun readProjectStats(root: File?, maxEntries: Int = 2500): ProjectStats? =
    withContext(Dispatchers.IO) {
        if (root == null || !root.exists()) return@withContext null

        var files = 0
        var folders = 0
        var bytes = 0L
        var visited = 0
        var truncated = false

        val queue = ArrayDeque<File>()
        queue.addLast(root)

        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (visited >= maxEntries) {
                    truncated = true
                    break
                }
                visited++
                val name = child.name
                if (child.isDirectory) {
                    if (name in SKIPPED_DIRS || name.startsWith(".cache")) continue
                    folders++
                    // عمق محدود: لا نغوص أكثر من ٦ مستويات
                    if (dir.absolutePath.count { it == File.separatorChar } - root.absolutePath.count { it == File.separatorChar } < 6) {
                        queue.addLast(child)
                    }
                } else {
                    files++
                    bytes += runCatching { child.length() }.getOrDefault(0L)
                }
            }
            if (truncated) break
        }

        ProjectStats(files = files, folders = folders, bytes = bytes, truncated = truncated)
    }

/** صياغة الحجم بصيغة مقروءة (بايت · ك.ب · م.ب · ج.ب). */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

/**
 * شريط الإحصاءات: ثلاث خلايا (ملفات · مجلدات · حجم) بأرقام تتصاعد بنعومة عند
 * ظهورها (`animateIntAsState`) — إحساس «عدّاد حقيقي» بدل رقم جامد.
 */
@Composable
fun ProjectStatsStrip(stats: ProjectStats?, modifier: Modifier = Modifier) {
    if (stats == null) return

    val files by animateIntAsState(targetValue = stats.files, animationSpec = tween(900), label = "files")
    val folders by animateIntAsState(targetValue = stats.folders, animationSpec = tween(900), label = "folders")

    val cells =
        listOf(
            Triple(strings.project_stats_files, "$files", Color(0xFF38BDF8)),
            Triple(strings.project_stats_folders, "$folders", Color(0xFFA78BFA)),
            Triple(strings.project_stats_size, formatBytes(stats.bytes), Color(0xFF2DD4BF)),
        )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cells.forEach { (labelRes, value, accent) ->
            Column(
                modifier =
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.10f))
                        .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                        .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                )
                Text(
                    text = labelRes.getString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}
