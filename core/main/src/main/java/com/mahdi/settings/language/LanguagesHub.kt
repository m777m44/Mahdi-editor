package com.mahdi.settings.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.activities.settings.SettingsRoutes
import com.mahdi.components.NextScreenCard
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.lang.DefaultLanguages
import com.mahdi.lang.LanguageInstallStore
import com.mahdi.lang.LanguageInstaller
import com.mahdi.lang.LanguageRegistry
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.theme.SkyBadge

/**
 * «اللغات» — بوابة واحدة لكل ما يخصّ اللغة (4.2.0).
 *
 * قبل هذا الإصدار كانت إعدادات اللغة موزّعة على مكانين في «إعدادات التطبيق»
 * («لغة التطبيق» و«اللغات المدعومة»)، فكان المستخدم يبحث عن تثبيت لغة أو حذفها
 * في مكانين. الآن كل ما يخصّ اللغة يخرج من **شاشة واحدة** داخل ⚙️ الإعدادات:
 *
 * 1. **لغة الواجهة** — العربية/الإنجليزية…
 * 2. **لغات البرمجة** — تثبيت، حالة حقيقية من القرص، و**✖️** للحذف بعد التثبيت.
 *
 * ولا يوجد مدخل ثانٍ للغات في أي شاشة إعدادات أخرى.
 */
@Composable
fun LanguagesHub(modifier: Modifier = Modifier) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val installed by LanguageInstallStore.installed.collectAsStateWithLifecycle()
    val pending by LanguageInstaller.pending.collectAsStateWithLifecycle()

    val total by remember { mutableIntStateOf(LanguageRegistry.all.size) }
    val bundled by remember { mutableIntStateOf(LanguageRegistry.all.count { DefaultLanguages.isBundled(it.id) }) }
    val userInstalled = installed.count { !DefaultLanguages.isBundled(it) }

    PreferenceLayout(label = stringResource(strings.languages_hub), modifier = modifier, backArrowVisible = true) {
        Text(
            text = stringResource(strings.languages_hub_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkyBadge(text = "${stringResource(strings.bundled_state)} · $bundled", showDot = true)
            SkyBadge(text = "${stringResource(strings.installed_languages)} · $userInstalled", showDot = false)
            if (pending.isNotEmpty()) {
                SkyBadge(
                    text = if (isArabic) "جارٍ التثبيت · ${pending.size}" else "Installing · ${pending.size}",
                    showDot = true,
                )
            }
        }

        // ——— 1) لغة الواجهة ———
        PreferenceGroup(heading = stringResource(strings.app_language_section)) {
            NextScreenCard(
                label = stringResource(strings.lang),
                description = stringResource(strings.lang_desc),
                route = SettingsRoutes.LanguageScreen,
                iconRes = drawables.edit_note,
            )
        }

        // ——— 2) لغات البرمجة ———
        PreferenceGroup(
            heading = stringResource(strings.programming_languages),
            description = stringResource(strings.languages_hub_stats, total, bundled, userInstalled),
        ) {
            NextScreenCard(
                label = stringResource(strings.languages_supported),
                description =
                    if (pending.isEmpty()) {
                        stringResource(strings.languages_supported_desc)
                    } else {
                        if (isArabic) {
                            "جارٍ تثبيت ${pending.size} لغة…"
                        } else {
                            "Installing ${pending.size} language(s)…"
                        }
                    },
                route = SettingsRoutes.SupportedLanguages,
                iconRes = drawables.text,
            )
        }
    }
}
