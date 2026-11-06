package com.mahdi.settings.language

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.components.ConfirmDialog
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.lang.CodeLanguage
import com.mahdi.lang.DefaultLanguages
import com.mahdi.lang.LanguageInstallStore
import com.mahdi.lang.LanguageInstaller
import com.mahdi.lang.LanguageRegistry
import com.mahdi.resources.strings
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.SkyBadge
import com.mahdi.utils.toast
import kotlinx.coroutines.delay

/**
 * شاشة «لغات البرمجة» المستقلة — غلاف رقيق حول [SupportedLanguagesContent]
 * (5.5.0). تُعرض عادةً داخل تبويب في [LanguagesHub].
 */
@Composable
fun SupportedLanguages(modifier: Modifier = Modifier) {
    PreferenceLayout(
        label = stringResource(strings.languages_supported),
        modifier = modifier,
        backArrowVisible = true,
    ) {
        SupportedLanguagesContent()
    }
}

/**
 * محتوى «لغات البرمجة» — ثلاث مجموعات فقط: مضمّنة، مثبّتة بواسطتك، متاحة.
 *
 * 5.5.0: أُزيلت كل الفقرات التمهيدية والإحصاءات وتلميح المشغّل — القائمة تبدأ مباشرة.
 */
@Composable
fun SupportedLanguagesContent() {
    val installed by LanguageInstallStore.installed.collectAsStateWithLifecycle()
    val pending by LanguageInstaller.pending.collectAsStateWithLifecycle()
    val errors by LanguageInstaller.errors.collectAsStateWithLifecycle()

    val activity = LocalActivity.current
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    var pendingDelete by remember { mutableStateOf<CodeLanguage?>(null) }

    // تحديث من القرص عند فتح الشاشة + كل ثانية أثناء وجود تثبيت جارٍ
    LaunchedEffect(Unit) {
        LanguageInstallStore.refresh()
        while (true) {
            delay(1000)
            if (LanguageInstaller.pending.value.isNotEmpty()) LanguageInstallStore.refresh()
        }
    }

    val bundled = LanguageRegistry.all.filter { DefaultLanguages.isBundled(it.id) }
    val userInstalled =
        LanguageRegistry.all.filter { lang -> !DefaultLanguages.isBundled(lang.id) && lang.id in installed }
    val available =
        LanguageRegistry.all.filter { lang -> !DefaultLanguages.isBundled(lang.id) && lang.id !in installed }

    // ————— 1) مثبتة مسبقًا: لا حذف —————
    PreferenceGroup(heading = stringResource(strings.bundled_state)) {
        bundled.forEach { lang ->
            LanguageRow(
                lang = lang,
                isArabic = isArabic,
                state = RowState.Bundled,
            )
        }
    }

    // ————— 2) مثبتة بواسطة المستخدم: X ← تأكيد ← حذف كامل —————
    PreferenceGroup(heading = "${stringResource(strings.installed_languages)} (${userInstalled.size})") {
        if (userInstalled.isEmpty()) {
            Text(
                text = stringResource(strings.no_extra_languages),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        userInstalled.forEach { lang ->
            LanguageRow(
                lang = lang,
                isArabic = isArabic,
                state = RowState.Installed(onRemove = { pendingDelete = lang }),
            )
        }
    }

    // ————— 3) متاحة: تثبيت —————
    PreferenceGroup(heading = "${stringResource(strings.available_languages)} (${available.size})") {
        available.forEach { lang ->
            val isPending = lang.id in pending
            val failure = errors[lang.id]
            LanguageRow(
                lang = lang,
                isArabic = isArabic,
                state =
                    when {
                        isPending -> RowState.Installing
                        !failure.isNullOrBlank() -> RowState.Failed(message = failure)
                        else ->
                            RowState.Available(
                                onInstall = {
                                    val started = LanguageInstaller.install(activity, lang)
                                    if (!started) {
                                        toast(if (isArabic) "الطرفية غير جاهزة — افتحها أولًا" else "Terminal not ready")
                                    }
                                }
                            )
                    },
            )
        }
    }

    // نافذة التأكيد — لا تُحذف لغة قبل «موافق»
    pendingDelete?.let { lang ->
        val isBundled = DefaultLanguages.isBundled(lang.id)
        // النصّ يُحسب داخل التركيب لأن stringResource غير مسموح داخل لامدا عادية
        val removalToast = stringResource(strings.language_removal_started, lang.nameEn)
        if (!isBundled) {
            ConfirmDialog(
                title = stringResource(strings.remove_language_title),
                message = stringResource(strings.remove_language_message, if (isArabic) lang.nameAr else lang.nameEn),
                onConfirm = {
                    val removed = LanguageInstaller.uninstall(activity, lang)
                    if (removed) toast(removalToast)
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null },
            )
        }
    }
}

/** حالة الصفّ — تحدّد ما يُعرض في الطرف الأيسر من الشريط. */
private sealed interface RowState {
    data object Bundled : RowState

    data class Installed(val onRemove: () -> Unit) : RowState

    data class Available(val onInstall: () -> Unit) : RowState

    data object Installing : RowState

    data class Failed(val message: String) : RowState
}

@Composable
private fun LanguageRow(lang: CodeLanguage, isArabic: Boolean, state: RowState) {
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
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "▶",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(strings.run_direct),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Text(
            text = if (isArabic) "${lang.nameAr} (${lang.nameEn})" else lang.nameEn,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = lang.extensions.take(2).joinToString(" ") { ".$it" },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (state) {
            // مدمجة: شارة مميّزة فقط، لا زرّ حذف مهما كان
            RowState.Bundled -> BundledBadge(isArabic = isArabic)

            // مثبتة: شارة + X
            is RowState.Installed -> {
                SkyBadge(text = stringResource(strings.installed_state))
                IconButton(onClick = state.onRemove) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(strings.uninstall_action),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // متاحة: زرّ تثبيت
            is RowState.Available ->
                TextButton(onClick = state.onInstall) {
                    Text(text = stringResource(strings.install), color = SkyAccent)
                }

            // قيد التثبيت: مؤشّر + نصّ قصير
            RowState.Installing -> {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(strings.installing_state),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // فشل: سبب + إعادة المحاولة بنفس زرّ التثبيت
            is RowState.Failed -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** شارة «مثبت مسبقًا» بلون سماوي مخضرّ — تميّزها عن شارة «مثبّت» العادية. */
@Composable
private fun BundledBadge(isArabic: Boolean) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = "✅", style = MaterialTheme.typography.labelSmall)
        Text(
            text = if (isArabic) "مثبت مسبقًا" else "Pre-installed",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
