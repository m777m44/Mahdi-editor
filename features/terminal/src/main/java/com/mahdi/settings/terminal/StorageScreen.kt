package com.mahdi.settings.terminal

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceTemplate
import com.mahdi.components.compose.preferences.base.RefreshablePreferenceLayout
import com.mahdi.resources.getFilledString
import com.mahdi.resources.strings
import com.mahdi.storage.AppStorage
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.SkyBadge
import com.mahdi.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «مساحة التخزين» (4.3.0).
 *
 * الجواب المباشر على سؤال «لماذا ٣ غيغابايت؟»: الرقم في «معلومات التطبيق» يجمع
 * البيانات لا التطبيق — صورة أوبنتو + ما ثبّته المستخدم داخلها + المخازن
 * والبقايا المؤقتة. الشاشة تعرض التفصيل بالحجم الحقيقي من القرص، وتوفّر تنظيفًا
 * آمنًا لا يمسّ ملفات المستخدم ولا الحزم المخزّنة للعمل بلا إنترنت.
 */
@Composable
fun StorageScreen() {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf<AppStorage.Report?>(null) }
    var loading by remember { mutableStateOf(true) }
    var cleaning by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            report = withContext(Dispatchers.IO) { runCatching { AppStorage.report() }.getOrNull() }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    RefreshablePreferenceLayout(
        label = stringResource(strings.storage_title),
        isRefreshing = loading,
        onRefresh = { refresh() },
    ) {
        // ————— البطاقة الرئيسية —————
        PreferenceGroup {
            val r = report
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = stringResource(strings.storage_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (r == null) "…" else AppStorage.format(r.total),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = SkyAccent,
                )

                Spacer(Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = {
                        if (r == null || r.deviceTotal == 0L) 0f else (r.total.toFloat() / r.deviceTotal).coerceIn(0f, 1f)
                    },
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
                        text = stringResource(strings.storage_free, if (r == null) "…" else AppStorage.format(r.deviceFree)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SkyBadge(text = stringResource(strings.storage_app_data), showDot = false)
                }
            }
        }

        Text(
            text = stringResource(strings.storage_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )

        // ————— التفصيل —————
        val r = report
        if (r != null) {
            val max = r.groups.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1

            PreferenceGroup(heading = stringResource(strings.storage_where)) {
                r.groups.forEach { entry ->
                    StorageRow(
                        label = entry.label(isArabic),
                        size = entry.size,
                        fraction = entry.size.toFloat() / max,
                    )
                }
            }

            if (r.sandboxTop.isNotEmpty()) {
                PreferenceGroup(heading = stringResource(strings.storage_inside)) {
                    val maxInner = r.sandboxTop.maxOf { it.size }.coerceAtLeast(1)
                    r.sandboxTop.forEach { entry ->
                        StorageRow(
                            label = "${entry.labelAr}/",
                            size = entry.size,
                            fraction = entry.size.toFloat() / maxInner,
                        )
                    }
                }
            }
        }

        // ————— التنظيف —————
        PreferenceGroup(heading = stringResource(strings.storage_clean_section)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(strings.storage_clean_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        if (cleaning) return@FilledTonalButton
                        cleaning = true
                        val before = report?.total ?: 0L
                        scope.launch {
                            val freed = withContext(Dispatchers.IO) { runCatching { AppStorage.clean() }.getOrDefault(0L) }
                            val fresh = withContext(Dispatchers.IO) { runCatching { AppStorage.report() }.getOrNull() }
                            report = fresh
                            cleaning = false
                            val actual = if (fresh != null && before > 0) (before - fresh.total).coerceAtLeast(0) else freed
                            if (actual > 0) {
                                toast(strings.storage_cleaned.getFilledString(AppStorage.format(actual)))
                            } else {
                                toast(strings.storage_nothing.getString())
                            }
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
