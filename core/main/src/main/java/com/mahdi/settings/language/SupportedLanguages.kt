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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.lang.CodeLanguage
import com.mahdi.lang.LanguageRegistry
import com.mahdi.resources.strings

/**
 * شاشة «اللغات المدعومة»: تعرض ما يدعمه المحرر فعليًا — التلوين النحوي لكل لغة،
 * وإمكانية التشغيل المباشر داخل طرفية Ubuntu.
 */
@Composable
fun SupportedLanguages(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.languages_supported), modifier = modifier, backArrowVisible = true) {
        Text(
            text =
                stringResource(strings.languages_supported_desc) +
                    "\n\n" +
                    "التلوين النحوي: ${LanguageRegistry.supportedCount} لغة — التشغيل المباشر: ${LanguageRegistry.runnableCount} لغة",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        PreferenceGroup(heading = stringResource(strings.language_runnable)) {
            LanguageRegistry.runnable.forEach { lang -> LanguageRow(lang, runnable = true) }
        }

        PreferenceGroup(heading = stringResource(strings.language_highlight)) {
            LanguageRegistry.all.filterNot { it.runnable }.forEach { lang -> LanguageRow(lang, runnable = false) }
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
private fun LanguageRow(lang: CodeLanguage, runnable: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (runnable) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (runnable) "▶" else "Aa",
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (runnable) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = lang.nameAr,
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
    }
}
