package com.mahdi.settings.extension

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.lang.LanguageInstallStore
import com.mahdi.runtime.InstallState
import com.mahdi.runtime.RuntimeCatalog
import com.mahdi.runtime.RuntimeInstaller
import com.mahdi.runtime.RuntimeStatus
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.SkyBadge
import com.mahdi.text.bidiNumbers
import com.mahdi.utils.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «بيئات التشغيل» (3.0.0) — شاشة واحدة تعرض الحالة الحقيقية لكل مفسّر/مشغّل.
 *
 * الحالة تُقرأ من القرص لا من الزرّ:
 * - **مثبَّت** تظهر فقط بعد التحقّق من وجود الثنائي وتشغيله فعليًا داخل الـ sandbox.
 * - أثناء التثبيت تظهر نسبة حقيقية من ملف الحالة الذي يكتبه مثبّت الطرفية كل مرحلة.
 * - عند الفشل يظهر السبب (شبكة، حزمة مفقودة…) مع زرّ **إعادة المحاولة**.
 */
@Composable
fun RuntimesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val scope = rememberCoroutineScope()
    val statuses by RuntimeInstaller.status.collectAsState()

    // اقرأ الحالة الحقيقية عند فتح الشاشة، وأعد القراءة كل ثانية أثناء أي تثبيت جارٍ
    LaunchedEffect(Unit) {
        RuntimeInstaller.refresh()
        repeat(600) {
            val busy =
                statuses.values.any {
                    it.state is InstallState.Downloading || it.state is InstallState.Extracting || it.state is InstallState.Queued
                }
            if (!busy) return@LaunchedEffect
            delay(1000)
            RuntimeInstaller.refresh()
        }
    }

    val list = remember(statuses) {
        RuntimeCatalog.all.map { spec ->
            statuses[spec.id]
                ?: RuntimeStatus(spec.id, spec.nameAr, spec.nameEn, InstallState.NotInstalled, null, spec.sizeMb, spec.packages)
        }
    }

    val ready = list.count { it.isReady }

    PreferenceLayout(
        label = if (isArabic) "بيئات التشغيل" else "Runtimes",
        modifier = modifier,
        backArrowVisible = true,
    ) {
        // ————— بطاقة الملخّص —————
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isArabic) "بيئات التشغيل" else "Runtimes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.weight(1f))
                    SkyBadge(text = "$ready / ${list.size}".bidiNumbers(), showDot = false)
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (list.isEmpty()) 0f else ready.toFloat() / list.size },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    color = SkyAccent,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text =
                        if (isArabic) {
                            "«مثبَّت» لا تظهر إلا بعد التحقّق من تشغيل الثنائي فعليًا داخل طرفية Ubuntu. " +
                                "كل تثبيت يُنفَّذ مرة واحدة ويبقى في الصورة."
                        } else {
                            "\"Installed\" appears only after the binary is verified inside the Ubuntu terminal. " +
                                "Every install runs once and persists in the image."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
        }

        // ————— الجاهزة الآن —————
        val readyItems = list.filter { it.isReady }
        if (readyItems.isNotEmpty()) {
            PreferenceGroup(heading = if (isArabic) "جاهزة للاستخدام" else "Ready to use") {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(readyItems, key = { it.id }) { status ->
                        Row(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(SkyAccent.copy(alpha = 0.14f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(SkyAccent)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = status.name(isArabic).bidiNumbers(),
                                style = MaterialTheme.typography.labelMedium,
                                color = SkyAccent,
                            )
                        }
                    }
                }
            }
        }

        // ————— كل البيئات —————
        PreferenceGroup(heading = if (isArabic) "كل البيئات" else "All runtimes") {
            list.forEach { status ->
                RuntimeCard(
                    status = status,
                    isArabic = isArabic,
                    onInstall = {
                        val activity = context as? android.app.Activity
                        if (activity == null) {
                            toast(if (isArabic) "افتح الشاشة من داخل التطبيق" else "Open this screen inside the app")
                            return@RuntimeCard
                        }
                        val spec = RuntimeCatalog.byId(status.id) ?: return@RuntimeCard
                        val started = RuntimeInstaller.install(activity, spec)
                        if (!started) {
                            toast(if (isArabic) "تعذّر بدء التثبيت" else "Could not start the install")
                        }
                        scope.launch { delay(1500); RuntimeInstaller.refresh() }
                    },
                    onCancel = {
                        RuntimeInstaller.cancel(status.id)
                        scope.launch { RuntimeInstaller.refresh() }
                    },
                )
            }
        }

        Text(
            text =
                if (isArabic) {
                    "ملاحظة: التثبيت يجري داخل طرفية Ubuntu المدمجة، ويحتاج اتصالًا بالشبكة أول مرة فقط. " +
                        "بعدها تعمل البيئة بلا إنترنت."
                } else {
                    "Note: installs run inside the built-in Ubuntu terminal and need a network connection only " +
                        "the first time. After that the runtime works offline."
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun RuntimeCard(
    status: RuntimeStatus,
    isArabic: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    val installState = status.state
    val progressTarget =
        when (installState) {
            is InstallState.Downloading -> installState.progress / 100f
            InstallState.Extracting -> 0.95f
            is InstallState.Installed, InstallState.Bundled -> 1f
            else -> 0f
        }
    val progress by animateFloatAsState(targetValue = progressTarget, label = "runtime-progress")

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = status.name(isArabic).bidiNumbers(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            when (installState) {
                                is InstallState.Installed ->
                                    (installState.version ?: status.version ?: "—").bidiNumbers()
                                is InstallState.Failed -> installState.error
                                is InstallState.Downloading ->
                                    if (isArabic) {
                                        "قيد التثبيت… ${installState.progress}%".bidiNumbers()
                                    } else {
                                        "Installing… ${installState.progress}%".bidiNumbers()
                                    }
                                InstallState.Extracting ->
                                    if (isArabic) "التحقّق وإعداد الأذونات…" else "Verifying permissions…"
                                InstallState.Queued -> if (isArabic) "في الانتظار…" else "Queued…"
                                InstallState.Bundled -> if (isArabic) "مضمّن في الصورة" else "Bundled"
                                InstallState.NotInstalled ->
                                    if (isArabic) {
                                        "غير مثبَّت · ~${status.sizeMb.toInt()} م.ب".bidiNumbers()
                                    } else {
                                        "Not installed · ~${status.sizeMb.toInt()} MB".bidiNumbers()
                                    }
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (installState is InstallState.Failed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }

                Spacer(Modifier.width(8.dp))

                when (installState) {
                    is InstallState.Installed -> SkyBadge(if (isArabic) "مثبت" else "Installed")
                    InstallState.Bundled -> SkyBadge(if (isArabic) "مضمّن" else "Bundled", showDot = false)
                    is InstallState.Failed -> {
                        Button(onClick = onInstall) { Text(if (isArabic) "إعادة" else "Retry") }
                    }
                    InstallState.NotInstalled -> {
                        Button(onClick = onInstall) { Text(if (isArabic) "تثبيت" else "Install") }
                    }
                    else -> {
                        TextButton(onClick = onCancel) {
                            Text(if (isArabic) "إلغاء" else "Cancel", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (installState is InstallState.Downloading ||
                installState is InstallState.Extracting ||
                installState is InstallState.Queued
            ) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                    color = SkyAccent,
                )
            }
        }
    }
}

/** يزامن مخزون اللغات مع البيئات المتحقَّق منها (يُستدعى بعد أي تثبيت ناجح). */
fun syncInstalledLanguages() {
    RuntimeCatalog.all.forEach { spec ->
        val isReady = RuntimeInstaller.statusOf(spec.id)?.isReady == true
        if (isReady) LanguageInstallStore.markInstalled(spec.id) else Unit
    }
}
