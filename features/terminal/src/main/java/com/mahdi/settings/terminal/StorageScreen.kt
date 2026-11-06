package com.mahdi.settings.terminal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceTemplate
import com.mahdi.components.compose.preferences.base.RefreshablePreferenceLayout
import com.mahdi.resources.getFilledString
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.storage.AppStorage
import com.mahdi.storage.StorageManager
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.SkyBadge
import com.mahdi.utils.logError
import com.mahdi.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «مساحة التخزين» (5.0.2).
 *
 * ## ما كان معطوبًا
 * الشاشة كانت تفتح على `…` **إلى الأبد**: الفحص كان يمشي على شجرة بيانات التطبيق
 * (صورة أوبنتو: مئات آلاف الملفات) **سبع مرّات** بلا مهلة ولا إلغاء، وزرّ
 * «تنظيف آمن» ينتظر أوبنتو ٩٠ ثانية ثم يعيد المشي مرّتين — فيبدو الزرّ كأنه
 * لا يعمل، والشاشة كأنها معلّقة.
 *
 * ## ما صار الآن
 * - تُعرض **آخر نتيجة محفوظة فورًا**، ثم يُحدَّث الرقم في الخلفية بفحص **واحد**
 *   بمهلة ٤ ثوانٍ.
 * - شاشة تحميل واضحة («جارٍ حساب الحجم…») بدل `…` الصامتة، وحالة خطأ بزرّ
 *   إعادة محاولة بدل الفشل الصامت.
 * - إن لم يُكمل الفحص السريع كل المجلدات يظهر الرقم بعلامة «≈» مع زرّ «فحص كامل».
 * - زرّ التنظيف يعمل على خطوات محدودة المدة، ويعرض حالة «جارٍ التنظيف…»،
 *   وينتهي **دائمًا** برسالة: إمّا الحجم المحرَّر أو أن شيئًا لم يُنظَّف.
 */
@Composable
fun StorageScreen() {
    val context = LocalContext.current
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val scope = rememberCoroutineScope()

    // نبدأ من آخر نتيجة محفوظة: الشاشة لا تفتح على "…" أبدًا
    var report by remember { mutableStateOf(AppStorage.cachedReport(context)) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var cleaning by remember { mutableStateOf(false) }

    fun scan(full: Boolean = false) {
        if (loading || cleaning) return
        loading = true
        failed = false
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching { if (full) AppStorage.fullReport() else AppStorage.report() }
                }
            result
                .onSuccess { report = it }
                .onFailure {
                    failed = true
                    logError(it, "storage report")
                }
            loading = false
        }
    }

    LaunchedEffect(Unit) { scan() }

    RefreshablePreferenceLayout(
        label = stringResource(strings.storage_title),
        isRefreshing = loading,
        onRefresh = { scan() },
    ) {
        // ——————————————— البطاقة الرئيسية ———————————————
        PreferenceGroup {
            val r = report
            val ratio by
                animateFloatAsState(
                    targetValue =
                        if (r == null || r.deviceTotal <= 0L) 0f
                        else (r.total.toFloat() / r.deviceTotal).coerceIn(0f, 1f),
                    label = "storage-ratio",
                )

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = stringResource(strings.storage_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            r == null && loading -> stringResource(strings.storage_calculating)
                            r == null -> stringResource(strings.storage_scan_failed)
                            r.partial -> "≈ ${AppStorage.format(r.total)}"
                            else -> AppStorage.format(r.total)
                        },
                        style =
                            if (r == null && loading) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                            },
                        color = SkyAccent,
                    )

                    if (loading && r != null) {
                        Spacer(Modifier.size(10.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = SkyAccent,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                    color = SkyAccent,
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            stringResource(
                                strings.storage_free,
                                if (r == null) "…" else AppStorage.format(r.deviceFree),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SkyBadge(text = stringResource(strings.storage_app_data), showDot = false)
                }

                // حالة «بلا بيانات» أو «فشل الفحص»: زرّ إعادة بدل الانتظار الصامت
                if (r == null && !loading) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text =
                            if (failed) {
                                stringResource(strings.storage_scan_failed)
                            } else {
                                stringResource(strings.storage_calculating)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { scan() }) {
                        Text(stringResource(strings.storage_retry))
                    }
                }

                if (r != null && r.fromCache && !loading) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(strings.storage_cached),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            text = stringResource(strings.storage_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )

        // ——————————————— التفصيل ———————————————
        val r = report
        if (r != null && r.groups.isNotEmpty()) {
            // 5.5.0: التفصيل حسب النوع من StorageManager — نفس الفحص الواحد، بلا مشي إضافي
            val breakdown = StorageManager.fromReport(r, context)
            val rows = breakdown.items.filter { it.size > 0 }
            if (rows.isNotEmpty()) {
                val max = rows.maxOf { it.size }.coerceAtLeast(1)
                PreferenceGroup(heading = stringResource(strings.storage_where)) {
                    rows.forEach { item ->
                        StorageRow(
                            label = item.category.label(isArabic),
                            size = item.size,
                            fraction = item.size.toFloat() / max,
                        )
                    }
                }
            }

            if (r.sandboxTop.isNotEmpty()) {
                PreferenceGroup(heading = stringResource(strings.storage_inside)) {
                    val maxInner = r.sandboxTop.maxOf { it.size }.coerceAtLeast(1)
                    r.sandboxTop.forEach { entry ->
                        StorageRow(
                            label = if (isArabic) entry.labelAr else entry.labelEn,
                            size = entry.size,
                            fraction = entry.size.toFloat() / maxInner,
                        )
                    }
                }
            }

            // أرقام تقريبية ⇒ نُخبر المستخدم ونعطيه فحصًا كاملًا
            if (r.partial) {
                PreferenceGroup {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(
                            text = stringResource(strings.storage_partial),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { scan(full = true) }, enabled = !loading) {
                            Text(stringResource(strings.storage_full_scan))
                        }
                    }
                }
            }
        }

        // ——————————————— التنظيف ———————————————
        PreferenceGroup(heading = stringResource(strings.storage_clean_section)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(strings.storage_clean_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                // 5.5.0: تنظيف مخصّص — كل زرّ يمسّ منطقته فقط ولا يقترب من بياناتك
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (cleaning) return@OutlinedButton
                            cleaning = true
                            scope.launch {
                                val freed =
                                    withContext(Dispatchers.IO) { StorageManager.cleanCache() }
                                toast(
                                    if (freed > 0L) {
                                        strings.storage_cleaned.getFilledString(AppStorage.format(freed))
                                    } else {
                                        strings.storage_nothing.getString()
                                    },
                                )
                                withContext(Dispatchers.IO) {
                                    runCatching { AppStorage.report(budgetMs = 2_500L) }.getOrNull()
                                }?.let { report = it }
                                cleaning = false
                            }
                        },
                        enabled = !cleaning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(strings.storage_clean_cache), style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            if (cleaning) return@OutlinedButton
                            cleaning = true
                            scope.launch {
                                val freed =
                                    withContext(Dispatchers.IO) { StorageManager.cleanTempFiles() }
                                toast(
                                    if (freed > 0L) {
                                        strings.storage_cleaned.getFilledString(AppStorage.format(freed))
                                    } else {
                                        strings.storage_nothing.getString()
                                    },
                                )
                                withContext(Dispatchers.IO) {
                                    runCatching { AppStorage.report(budgetMs = 2_500L) }.getOrNull()
                                }?.let { report = it }
                                cleaning = false
                            }
                        },
                        enabled = !cleaning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(strings.storage_clean_temp), style = MaterialTheme.typography.labelMedium)
                    }
                }

                FilledTonalButton(
                    onClick = {
                        if (cleaning) return@FilledTonalButton
                        cleaning = true
                        val before = report?.total ?: 0L

                        scope.launch {
                            val result =
                                withContext(Dispatchers.IO) {
                                    runCatching { AppStorage.cleanDetailed() }
                                }

                            result
                                .onSuccess { outcome ->
                                    // فحص قصير جدًا للرقم الجديد — لا نُجمّد الشاشة بفحص كامل
                                    val fresh =
                                        withContext(Dispatchers.IO) {
                                            runCatching { AppStorage.report(budgetMs = 2_500L) }.getOrNull()
                                        }
                                    if (fresh != null) report = fresh

                                    val measured =
                                        if (fresh != null && before > 0L) {
                                            (before - fresh.total).coerceAtLeast(0L)
                                        } else {
                                            outcome.freed
                                        }

                                    when {
                                        measured > 0L && outcome.sandboxFailed ->
                                            toast(
                                                strings.storage_clean_partial
                                                    .getFilledString(AppStorage.format(measured)),
                                            )
                                        measured > 0L ->
                                            toast(
                                                strings.storage_cleaned
                                                    .getFilledString(AppStorage.format(measured)),
                                            )
                                        else -> toast(strings.storage_nothing.getString())
                                    }
                                }
                                .onFailure {
                                    logError(it, "storage clean")
                                    toast(strings.storage_nothing.getString())
                                }

                            cleaning = false
                        }
                    },
                    enabled = !cleaning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            if (cleaning) {
                                stringResource(strings.storage_cleaning)
                            } else {
                                stringResource(strings.storage_clean)
                            }
                    )
                }

                // مؤشّر تقدّم غير محدّد: المستخدم يرى أن العمل جارٍ، والزرّ معطّل
                if (cleaning) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                        color = SkyAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageRow(label: String, size: Long, fraction: Float) {
    PreferenceTemplate(
        title = { Text(text = label, style = MaterialTheme.typography.bodyLarge) },
        description = {
            Box(
                modifier =
                    Modifier.fillMaxWidth(0.9f)
                        .padding(top = 6.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                            .height(5.dp)
                            .background(SkyAccent, RoundedCornerShape(4.dp)),
                )
            }
        },
        endWidget = {
            Text(
                text = AppStorage.format(size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 20.dp),
            )
        },
    )
}
