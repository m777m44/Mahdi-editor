package com.mahdi.settings.extension

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.TerminalLauncher
import com.mahdi.components.SettingsItem
import com.mahdi.icons.MahdiIcon
import com.mahdi.lang.LanguageInstallStore
import com.mahdi.runtime.InstallState
import com.mahdi.runtime.RuntimeCatalog
import com.mahdi.runtime.RuntimeInstaller
import com.mahdi.lang.LanguageRegistry
import com.mahdi.lang.LanguageToolchain
import com.mahdi.lsp.LspRegistry
import com.mahdi.resources.drawables
import com.mahdi.resources.strings
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.SkyBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * تبويب «خوادم ومشغّلات» داخل متجر الإضافات (2.6.x / 2.7.0).
 *
 * - خوادم اللغة المدمجة مع تثبيت/إزالة حقيقي.
 * - كل لغات المحرّر مع زرّ تثبيت المفسّر داخل طرفية Ubuntu.
 * - **حالة التثبيت محفوظة**: بعد نجاح التثبيت تظهر شارة «مثبت» سمائية وتبقى بعد إعادة التشغيل،
 *   لأن السكربت يكتب ملفّ علامة داخل `/home/.mahdi/installed` ويقرأه التطبيق مباشرة.
 */
@Composable
fun ServersAndRunnersSection(
    query: String,
    activity: android.app.Activity?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    val installedLanguages by LanguageInstallStore.installed.collectAsStateWithLifecycle()
    val pendingInstalls = remember { mutableStateMapOf<String, Boolean>() }

    val builtIn by LspRegistry.builtInServers.collectAsStateWithLifecycle()
    val extensionServers by LspRegistry.extensionServers.collectAsStateWithLifecycle()
    val externalServers by LspRegistry.externalServers.collectAsStateWithLifecycle()

    // فحص المخزون عند فتح الشاشة (يقرأ ملفّات العلامة من القرص)
    LaunchedEffect(Unit) { LanguageInstallStore.refresh() }

    val servers =
        remember(builtIn, extensionServers, externalServers, query) {
            (builtIn + extensionServers + externalServers)
                .distinctBy { it.id }
                .filter { server ->
                    query.isBlank() ||
                        server.serverName.contains(query, ignoreCase = true) ||
                        server.languageName.contains(query, ignoreCase = true) ||
                        server.supportedExtensions.any { it.contains(query, ignoreCase = true) }
                }
        }

    val installedServers = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(servers) {
        servers.forEach { server ->
            installedServers[server.id] = runCatching { server.isInstalled(context) }.getOrDefault(false)
        }
    }

    // ————— بطاقة المخزون —————
    InventoryCard(
        installedCount = installedLanguages.size,
        totalLanguages = LanguageRegistry.all.size,
        serverCount = servers.size,
        modifier = modifier,
        onRefresh = { LanguageInstallStore.refresh() },
    )

    Text(
        text = stringResource(strings.servers_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )

    // ————— خوادم اللغات —————
    SectionHeader(stringResource(strings.lsp_servers))

    if (servers.isEmpty()) {
        EmptyLine(if (isArabic) "لا توجد خوادم لغة مضافة بعد." else "No language servers yet.")
    }

    servers.forEach { server ->
        val isInstalled = installedServers[server.id] == true

        SettingsItem(
            label = server.serverName,
            description =
                buildString {
                    append(server.languageName)
                    if (server.supportedExtensions.isNotEmpty()) {
                        append(" · ")
                        append(server.supportedExtensions.take(6).joinToString(" ") { ".$it" })
                    }
                },
            showSwitch = false,
            startWidget = {
                Box(modifier = Modifier.padding(start = 16.dp, end = 4.dp), contentAlignment = Alignment.Center) {
                    val icon = server.icon
                    if (icon != null) {
                        MahdiIcon(
                            icon = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            painter = painterResource(drawables.server),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            },
            endWidget = {
                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isInstalled) {
                        SkyBadge(stringResource(strings.installed_state))
                        IconButton(
                            onClick = {
                                activity?.let { act -> runCatching { server.uninstall(act) } }
                                installedServers[server.id] = false
                            }
                        ) {
                            Icon(
                                painter = painterResource(drawables.close),
                                contentDescription = stringResource(strings.uninstall_action),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val act = activity ?: return@IconButton
                                scope.launch {
                                    runCatching { server.install(act) }
                                    runCatching { installedServers[server.id] = server.isInstalled(context) }
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(drawables.download),
                                contentDescription = stringResource(strings.install_action),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
        )
    }

    // ————— المشغّلات واللغات —————
    SectionHeader(stringResource(strings.language_runners))

    // حالة بيئات التشغيل الحقيقية (من القرص) — JavaScript وTypeScript تتبعان Node،
    // وJava وKotlin تتبعان JDK، وC/C++ تتبعان GCC… فلا يدور مؤشّر بلا نهاية.
    val runtimeStates by RuntimeInstaller.status.collectAsStateWithLifecycle()

    // قراءة أولية من القرص عند فتح الشاشة
    LaunchedEffect(Unit) {
        runCatching { RuntimeInstaller.refresh() }
    }

    // تحديث دوري هادئ ما دام تثبيت جارٍ (يعمل أيضًا بعد إعادة فتح التطبيق وسط التثبيت)
    LaunchedEffect(Unit) {
        while (true) {
            val busy =
                RuntimeInstaller.status.value.values.any { status ->
                    val state = status.state
                    state is InstallState.Downloading || state is InstallState.Extracting || state is InstallState.Queued
                }
            if (busy) runCatching { RuntimeInstaller.refresh() }
            delay(1500)
        }
    }

    val languages =
        remember(query) {
            LanguageRegistry.all.filter { lang ->
                query.isBlank() ||
                    lang.nameAr.contains(query, ignoreCase = true) ||
                    lang.nameEn.contains(query, ignoreCase = true) ||
                    lang.extensions.any { it.contains(query, ignoreCase = true) }
            }
        }

    // لغة بلا بيئة تشغيل (HTML/CSS…) تُثبَّت بعلامة على القرص — لها مهلة صريحة بدل الدوران الأبدي
    val pendingSince = remember { mutableStateMapOf<String, Long>() }
    val failedInstalls = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(pendingInstalls.keys.toList()) {
        if (pendingInstalls.isEmpty()) return@LaunchedEffect
        // 3.1.0: مهلة 120 ثانية — بعدها تظهر «فشل» مع زرّ إعادة بدل مؤشّر لا يتوقّف أبدًا
        while (pendingInstalls.isNotEmpty()) {
            LanguageInstallStore.refresh()
            runCatching { RuntimeInstaller.refresh() }
            val now = System.currentTimeMillis()
            pendingInstalls.keys.toList().forEach { id ->
                if (pendingSince[id] == null) pendingSince[id] = now
                val started = pendingSince[id] ?: now
                when {
                    LanguageInstallStore.isInstalled(id) -> {
                        pendingSince.remove(id)
                        failedInstalls.remove(id)
                        pendingInstalls.remove(id)
                    }

                    now - started > 120_000L -> {
                        pendingSince.remove(id)
                        pendingInstalls.remove(id)
                        failedInstalls[id] =
                            if (isArabic) "انتهت المهلة" else "Timed out"
                    }
                }
            }
            delay(1000)
        }
    }

    languages.forEach { lang ->
        val spec = RuntimeCatalog.forLanguage(lang.id)
        val runtimeStatus = spec?.let { runtimeStates[it.id] }
        val runtimeState = runtimeStatus?.state
        val runtimeReady = runtimeState is InstallState.Installed || runtimeState is InstallState.Bundled
        val runtimeBusy = runtimeState is InstallState.Downloading || runtimeState is InstallState.Extracting || runtimeState is InstallState.Queued
        val runtimeProgress = (runtimeState as? InstallState.Downloading)?.progress
        val runtimeError = (runtimeState as? InstallState.Failed)?.error

        val installed = lang.id in installedLanguages || runtimeReady
        val pending = pendingInstalls[lang.id] == true || runtimeBusy
        val failure = runtimeError ?: failedInstalls[lang.id]
        val hasPackages = LanguageToolchain.packagesFor(lang.id).isNotBlank() || spec != null

        fun startInstall(activity: android.app.Activity) {
            failedInstalls.remove(lang.id)
            if (spec != null) {
                RuntimeInstaller.install(activity, spec)
            } else {
                pendingSince[lang.id] = System.currentTimeMillis()
                pendingInstalls[lang.id] = true
                TerminalLauncher.launch(
                    activity = activity,
                    exe = "/bin/bash",
                    args = arrayOf("-lc", LanguageInstallStore.installCommand(lang.id)),
                    id = "install-${lang.id}",
                )
            }
        }

        SettingsItem(
            label = if (isArabic) lang.nameAr else lang.nameEn,
            // 3.1.0: الامتدادات في سطر واحد — كانت تلتفّ حرفًا حرفًا فتتراكب مع الصفوف المجاورة.
            // ونُظهر بيئة التشغيل المطلوبة (Node للجافاسكربت، JDK لجافا…) ليعرف المستخدم ما يُثبَّت.
            description =
                buildString {
                    append(lang.extensions.take(3).joinToString(" ") { ".$it" })
                    if (spec != null) {
                        append("  ·  ")
                        append(if (isArabic) spec.nameAr else spec.nameEn)
                    }
                },
            singleLineDescription = true,
            showSwitch = false,
            startWidget = {
                Icon(
                    painter = painterResource(drawables.run),
                    contentDescription = null,
                    tint = if (installed) SkyAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp).size(20.dp),
                )
            },
            endWidget = {
                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when {
                        // ✅ مثبت — شارة سمائية تبقى بعد إعادة التشغيل
                        installed -> {
                            SkyBadge(stringResource(strings.installed_state))
                            if (spec == null) {
                                IconButton(onClick = { LanguageInstallStore.unmark(lang.id) }) {
                                    Icon(
                                        painter = painterResource(drawables.close),
                                        contentDescription = stringResource(strings.uninstall_action),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        // ⏳ قيد التثبيت — نسبة حقيقية إن توفّرت، بلا نصّ طويل يزاحم العنوان
                        pending -> {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text =
                                    if (runtimeProgress != null) {
                                        "${runtimeProgress}%"
                                    } else {
                                        stringResource(strings.installing_state)
                                    },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // ✖ فشل — سبب مختصر + إعادة المحاولة
                        !failure.isNullOrBlank() -> {
                            Text(
                                text = if (isArabic) "فشل" else "Failed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            IconButton(
                                enabled = activity != null,
                                onClick = { activity?.let { act -> startInstall(act) } },
                            ) {
                                Icon(
                                    painter = painterResource(drawables.download),
                                    contentDescription = if (isArabic) "إعادة المحاولة" else "Retry",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        // ⬇️ يمكن تثبيته
                        hasPackages -> {
                            IconButton(
                                enabled = activity != null,
                                onClick = { activity?.let { act -> startInstall(act) } },
                            ) {
                                Icon(
                                    painter = painterResource(drawables.download),
                                    contentDescription = stringResource(strings.install_interpreter),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        // ⚡ جاهزة بلا تثبيت
                        else -> SkyBadge(stringResource(strings.ready_state))
                    }
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** بطاقة «المخزون»: كم لغة مثبَّتة من الإجمالي، وشريط تقدّم أنيق. */
@Composable
fun InventoryCard(
    installedCount: Int,
    totalLanguages: Int,
    serverCount: Int,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val progress = if (totalLanguages == 0) 0f else installedCount.toFloat() / totalLanguages

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SkyAccent.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "📦", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(strings.install_inventory),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                SkyBadge(
                    text = "$installedCount / $totalLanguages",
                    showDot = false,
                )
                TextButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(
                        text = stringResource(strings.refresh),
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyAccent,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SkyAccent.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(SkyAccent)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text =
                    if (isArabic) {
                        "$serverCount خادم لغة مدمج · كل اللغات تُشغَّل مباشرة بعد التثبيت"
                    } else {
                        "$serverCount bundled language servers · every language runs right after install"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * بطاقة «مدمج في التطبيق» أعلى تبويب الإضافات — تُبرز ما يأتي مع Mahdi-Editor
 * وتنقل المستخدم إلى تبويب الخوادم والمشغّلات بلمسة.
 */
@Composable
fun BundledPackagesSection(onBrowseServers: () -> Unit, modifier: Modifier = Modifier) {
    val builtIn by LspRegistry.builtInServers.collectAsStateWithLifecycle()
    val installedLanguages by LanguageInstallStore.installed.collectAsStateWithLifecycle()
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isArabic) "مدمج في التطبيق" else "Bundled with the app",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text =
                    if (isArabic) {
                        "${builtIn.size} خادم لغة · ${LanguageRegistry.all.size} لغة قابلة للتشغيل · 4 ثيمات أساسية"
                    } else {
                        "${builtIn.size} language servers · ${LanguageRegistry.all.size} runnable languages · 4 base themes"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text =
                        if (isArabic) {
                            "المخزون: ${installedLanguages.size} لغة مثبَّتة داخل التطبيق. " +
                                "التثبيت يتم داخل طرفية Ubuntu ويبقى محفوظًا بعد إغلاق التطبيق."
                        } else {
                            "Inventory: ${installedLanguages.size} languages installed inside the app. " +
                                "Installs run inside the Ubuntu terminal and persist across restarts."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                )
            }

            Row {
                TextButton(onClick = onBrowseServers) {
                    Text(if (isArabic) "استعراض الخوادم والمشغّلات" else "Browse servers & runners")
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) {
                            if (isArabic) "إخفاء التفاصيل" else "Hide details"
                        } else {
                            if (isArabic) "التفاصيل" else "Details"
                        }
                    )
                }
            }
        }
    }
}
