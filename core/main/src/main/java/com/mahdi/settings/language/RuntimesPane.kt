package com.mahdi.settings.language

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.lang.LanguageInstallStore
import com.mahdi.runtime.InstallState
import com.mahdi.runtime.RuntimeCatalog
import com.mahdi.runtime.RuntimeInstaller
import com.mahdi.runtime.RuntimeSpec
import com.mahdi.runtime.RuntimeStatus
import com.mahdi.text.bidiNumbers
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.SkyBadge
import com.mahdi.utils.toast
import kotlinx.coroutines.delay

/**
 * «المشغلات» (5.5.0) — انتقلت من الإعدادات إلى مركز اللغات، تبويب ثالث.
 *
 * الحالة تُقرأ من القرص لا من الزرّ:
 * - **مثبَّت** تظهر فقط بعد التحقّق من وجود الثنائي وتشغيله فعليًا داخل الـ sandbox.
 * - أثناء التثبيت تظهر نسبة حقيقية من ملف الحالة الذي يكتبه مثبّت الطرفية كل مرحلة.
 * - عند الفشل يظهر السبب مع زرّ **إعادة المحاولة**.
 *
 * 6.2.0 — ثلاثة إصلاحات جوهرية:
 * 1. **الحذف يعمل ويُظهر نتيجته**: كان `apt purge` يزيل الحزم ثم يعود فحص الثنائي
 *    ليقول «مثبَّت» (لأن `perl-base` أساسية في أوبنتو وتوفّر `/usr/bin/perl`)، فيبقى
 *    زرّ الحذف الأحمر ظاهرًا إلى الأبد. الآن يُكتب أثر حذف صريح (`state=removed`
 *    و`survived`) وتُعرض البطاقة «مضمّن» أو «غير مثبَّت» بحسب الحقيقة.
 * 2. **الحذف لا يقتحم الشاشة**: يجري في الخلفية عبر `SandboxedProcessRegistry` بدل
 *    فتح الطرفية؛ البطاقة نفسها تنتقل إلى «جارٍ الحذف…» ثم إلى حالتها الأخيرة.
 * 3. **الشاشة تُحدِّث نفسها**: إعادة قراءة عند كل عودة إليها + أثناء أي عملية جارية.
 *
 * أُزيلت في 5.5.0: بطاقة الملخّص، فقرة الشرح، شريط الشرائح، والحاشية السفلية —
 * و6.2.0 أزالت أيضًا البطاقات المتداخلة: الصفوف الآن داخل مجموعة واحدة كبقية
 * شاشات الإعدادات، بلا مستطيل داخل مستطيل.
 */
@Composable
fun RuntimesContent() {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val activity = LocalActivity.current
    val haptics = LocalHapticFeedback.current
    val statuses by RuntimeInstaller.status.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()

    // كل عودة إلى الشاشة (بعد التثبيت أو الحذف) تعيد القراءة من القرص
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) RuntimeInstaller.refresh()
    }

    // أثناء أي عملية جارية: قراءة كل ثانية حتى تظهر النسبة الحقيقية أو تنتهي العملية
    val busy = statuses.values.any { it.isBusy }
    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        while (true) {
            delay(1000)
            RuntimeInstaller.refresh()
        }
    }

    PreferenceGroup {
        RuntimeCatalog.all.forEach { spec ->
            val status =
                statuses[spec.id]
                    ?: RuntimeStatus(
                        id = spec.id,
                        nameAr = spec.nameAr,
                        nameEn = spec.nameEn,
                        state = InstallState.NotInstalled,
                        version = null,
                        sizeMb = spec.sizeMb,
                        packages = spec.packages,
                    )
            RuntimeRow(
                status = status,
                spec = spec,
                isArabic = isArabic,
                onInstall = {
                    if (activity == null) {
                        toast(if (isArabic) "افتح الشاشة من داخل التطبيق" else "Open this screen inside the app")
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        if (!RuntimeInstaller.install(activity, spec)) {
                            toast(if (isArabic) "تعذّر بدء التثبيت" else "Could not start the install")
                        }
                    }
                },
                onCancel = {
                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    RuntimeInstaller.cancel(spec.id)
                },
                onDelete = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // لا مربّع تأكيد: الحذف يبدأ فورًا في الخلفية وتُظهر البطاقة النتيجة
                    if (!RuntimeInstaller.uninstall(activity, spec)) {
                        toast(if (isArabic) "تعذّر بدء الحذف" else "Could not start the removal")
                    }
                },
            )
        }
    }
}

/** الحرف/الرمز المختصر الذي يميّز البيئة في بداية الصف. */
private fun monogramOf(id: String): String =
    when (id) {
        "python" -> "Py"
        "node" -> "JS"
        "php" -> "PHP"
        "ruby" -> "Rb"
        "lua" -> "Lua"
        "git" -> "Git"
        "java" -> "Jv"
        "gcc" -> "C++"
        "go" -> "Go"
        "rust" -> "Rs"
        "perl" -> "Perl"
        "sqlite" -> "SQL"
        else -> id.take(2).replaceFirstChar { it.uppercase() }
    }

@Composable
private fun RuntimeRow(
    status: RuntimeStatus,
    spec: RuntimeSpec?,
    isArabic: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val installState = status.state
    // مسار الملف التنفيذي داخل جذر Ubuntu الذي تراه الطرفية
    val path = spec?.binaries?.firstOrNull()?.let { "usr/bin/$it" } ?: "—"
    val animatedProgress =
        when (installState) {
            is InstallState.Downloading -> installState.progress / 100f
            InstallState.Extracting -> 0.95f
            is InstallState.Installed, InstallState.Bundled -> 1f
            else -> 0f
        }
    val progress by animateFloatAsState(targetValue = animatedProgress, label = "runtime-progress")

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RuntimeMonogram(id = status.id)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = status.name(isArabic).bidiNumbers(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    when (installState) {
                        is InstallState.Installed -> SkyBadge(if (isArabic) "مثبت" else "Installed")
                        InstallState.Bundled ->
                            SkyBadge(
                                text = if (isArabic) "مضمّن" else "Bundled",
                                showDot = false,
                                accent = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        else -> Unit
                    }
                }
                Text(
                    text = path.bidiNumbers(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = SkyAccent,
                )
                Text(
                    text = stateLine(installState, status, isArabic).bidiNumbers(),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        when (installState) {
                            is InstallState.Failed -> MaterialTheme.colorScheme.error
                            InstallState.Removing -> SkyAccent
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }

            RuntimeAction(
                state = installState,
                isArabic = isArabic,
                onInstall = onInstall,
                onCancel = onCancel,
                onDelete = onDelete,
            )
        }

        if (installState is InstallState.Downloading ||
            installState is InstallState.Extracting ||
            installState is InstallState.Removing
        ) {
            Spacer(Modifier.height(9.dp))
            val bar = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50))
            if (installState is InstallState.Removing) {
                // حذف بلا نسبة معروفة: مؤشّر متحرّك بدل شريط يتظاهر بالدقّة
                LinearProgressIndicator(
                    modifier = bar,
                    color = SkyAccent,
                    trackColor = SkyAccent.copy(alpha = 0.14f),
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = bar,
                    color = SkyAccent,
                    trackColor = SkyAccent.copy(alpha = 0.14f),
                )
            }
        }
    }
}

/** مربّع الرمز الرمادي‑السماوي في بداية الصف. */
@Composable
private fun RuntimeMonogram(id: String) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(shape)
                .background(SkyAccent.copy(alpha = 0.12f))
                .border(1.dp, SkyAccent.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogramOf(id),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = SkyAccent,
            maxLines = 1,
        )
    }
}

/** الزرّ في طرف الصف: تثبيت / إعادة / إلغاء / حذف — بلا مربّع تأكيد. */
@Composable
private fun RuntimeAction(
    state: InstallState,
    isArabic: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    when (state) {
        InstallState.NotInstalled ->
            Button(
                onClick = onInstall,
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                Text(
                    text = if (isArabic) "تثبيت" else "Install",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

        is InstallState.Failed ->
            Button(
                onClick = onInstall,
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                Text(
                    text = if (isArabic) "إعادة" else "Retry",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

        is InstallState.Installed -> {
            val shape = RoundedCornerShape(50)
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = if (isArabic) "حذف" else "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        InstallState.Removing -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SkyAccent)

        is InstallState.Downloading,
        InstallState.Extracting,
        InstallState.Queued ->
            TextButton(onClick = onCancel) {
                Text(
                    text = if (isArabic) "إلغاء" else "Cancel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        // مضمّن في الصورة: لا تثبيت ولا حذف — لا شيء يُفعل به
        InstallState.Bundled -> Spacer(Modifier.width(0.dp))
    }
}

/** السطر الثالث: إصدار أو سبب فشل أو حجم — بلا عبارات شرح. */
private fun stateLine(state: InstallState, status: RuntimeStatus, isArabic: Boolean): String {
    val size = status.sizeMb.toInt()
    return when (state) {
        is InstallState.Installed ->
            state.version?.takeIf { it.isNotBlank() } ?: if (isArabic) "~$size م.ب" else "~$size MB"
        InstallState.Bundled ->
            if (isArabic) "مضمّن في الصورة · ~$size م.ب" else "Bundled with the image · ~$size MB"
        is InstallState.Failed -> state.error
        is InstallState.Downloading ->
            if (isArabic) "قيد التثبيت… ${state.progress}%" else "Installing… ${state.progress}%"
        InstallState.Extracting -> if (isArabic) "التحقّق وإعداد الأذونات…" else "Verifying permissions…"
        InstallState.Queued -> if (isArabic) "في الانتظار…" else "Queued…"
        InstallState.Removing -> if (isArabic) "جارٍ الحذف…" else "Removing…"
        InstallState.NotInstalled ->
            if (isArabic) "غير مثبَّت · ~$size م.ب" else "Not installed · ~$size MB"
    }
}

/** يزامن مخزون اللغات مع البيئات المتحقَّق منها (يُستدعى بعد أي تثبيت ناجح). */
fun syncInstalledLanguages() {
    RuntimeCatalog.all.forEach { spec ->
        val isReady = RuntimeInstaller.statusOf(spec.id)?.isReady == true
        if (isReady) LanguageInstallStore.markInstalled(spec.id) else Unit
    }
}
