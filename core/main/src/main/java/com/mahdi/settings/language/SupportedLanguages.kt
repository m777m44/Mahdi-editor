package com.mahdi.settings.language

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.lang.CodeLanguage
import com.mahdi.lang.LanguageRegistry
import com.mahdi.resources.strings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.lang.LanguageInstallStore
import com.mahdi.theme.SkyBadge

/**
 * شاشة «اللغات المدعومة»: تعرض ما يدعمه المحرر فعليًا — التلوين النحوي لكل لغة،
 * وإمكانية التشغيل المباشر داخل طرفية Ubuntu.
 */
@Composable
fun SupportedLanguages(modifier: Modifier = Modifier) {
    val installed by LanguageInstallStore.installed.collectAsStateWithLifecycle()

    PreferenceLayout(label = stringResource(strings.languages_supported), modifier = modifier, backArrowVisible = true) {
        Text(
            text =
                stringResource(strings.languages_supported_desc) +
                    "\n\n" +
                    stringResource(
                        strings.languages_supported_stats,
                        LanguageRegistry.supportedCount,
                        LanguageRegistry.runnableCount,
                    ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // 2.5.0: كل اللغات صارت قابلة للتشغيل المباشر — لا توجد مجموعة «تلوين فقط».
        PreferenceGroup(heading = stringResource(strings.language_runnable)) {
            LanguageRegistry.all.forEach { lang ->
                LanguageRow(lang, runnable = lang.runnable, installed = lang.id in installed)
            }
        }

        Text(
            text = stringResource(strings.runner_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun LanguageRow(lang: CodeLanguage, runnable: Boolean, installed: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // شارة «تشغيل مباشر» — تُظهر بوضوح أنّ اللغة قابلة للتنفيذ بلمسة واحدة
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (runnable) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (runnable) "▶" else "Aa",
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (runnable) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (runnable) {
                Text(
                    text = stringResource(strings.run_direct),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        val isArabic = LocalConfiguration.current.locales[0].language == "ar"
        Text(
            text = if (isArabic) "${lang.nameAr} (${lang.nameEn})" else lang.nameEn,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = lang.extensions.joinToString(" ") { ".$it" },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // شارة «مثبت» سمائية — تظهر بعد تثبيت اللغة من متجر الإضافات (2.7.0)
        if (installed) {
            SkyBadge(text = stringResource(strings.installed_state))
        }
    }
}
