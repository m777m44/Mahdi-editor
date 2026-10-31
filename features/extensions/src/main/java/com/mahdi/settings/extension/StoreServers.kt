package com.mahdi.settings.extension

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mahdi.lang.LanguageRegistry
import com.mahdi.lang.LanguageToolchain
import com.mahdi.lsp.LspRegistry
import com.mahdi.resources.drawables
import com.mahdi.resources.strings
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton

/**
 * تبويب «خوادم ومشغّلات» داخل متجر الإضافات (2.6.0).
 *
 * يعرض — داخل التطبيق نفسه — كل خوادم اللغة المدمجة مع زرّ تثبيت/إزالة حقيقي
 * (يُشغّل سكربت التثبيت في الطرفية)، وكل لغات المحرّر مع زرّ تثبيت المفسّر.
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

    val builtIn by LspRegistry.builtInServers.collectAsStateWithLifecycle()
    val extensionServers by LspRegistry.extensionServers.collectAsStateWithLifecycle()
    val externalServers by LspRegistry.externalServers.collectAsStateWithLifecycle()

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

    val installed = remember { mutableStateMapOf<String, Boolean>() }
    val installing = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(servers) {
        servers.forEach { server ->
            installed[server.id] = runCatching { server.isInstalled(context) }.getOrDefault(false)
        }
    }

    Text(
        text = stringResource(strings.servers_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )

    // ————— خوادم اللغات —————
    Text(
        text = stringResource(strings.lsp_servers),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )

    if (servers.isEmpty()) {
        Text(
            text = if (isArabic) "لا توجد خوادم لغة مضافة بعد." else "No language servers yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    servers.forEach { server ->
        val isInstalled = installed[server.id] == true
        val isBusy = installing[server.id] == true

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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (isInstalled) {
                        Text(
                            text = stringResource(strings.installed_state),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(
                            onClick = {
                                activity?.let { act -> server.uninstall(act) }
                                installed[server.id] = false
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
                                installing[server.id] = true
                                scope.launch {
                                    runCatching { server.install(act) }
                                    runCatching { installed[server.id] = server.isInstalled(context) }
                                    installing[server.id] = false
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
    Text(
        text = stringResource(strings.language_runners),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )

    val languages =
        remember(query) {
            LanguageRegistry.all.filter { lang ->
                query.isBlank() ||
                    lang.nameAr.contains(query, ignoreCase = true) ||
                    lang.nameEn.contains(query, ignoreCase = true) ||
                    lang.extensions.any { it.contains(query, ignoreCase = true) }
            }
        }

    languages.forEach { lang ->
        val hasPackages = LanguageToolchain.packagesFor(lang.id).isNotBlank()
        SettingsItem(
            label = if (isArabic) lang.nameAr else lang.nameEn,
            description = lang.extensions.take(5).joinToString(" ") { ".$it" },
            showSwitch = false,
            startWidget = {
                Icon(
                    painter = painterResource(drawables.run),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp).size(20.dp),
                )
            },
            endWidget = {
                if (hasPackages) {
                    IconButton(
                        onClick = {
                            val act = activity ?: return@IconButton
                            val command = LanguageToolchain.installCommandFor(lang.id) ?: return@IconButton
                            TerminalLauncher.launch(
                                activity = act,
                                exe = "/bin/bash",
                                args = arrayOf("-lc", command),
                                id = "install-${lang.id}",
                            )
                        }
                    ) {
                        Icon(
                            painter = painterResource(drawables.download),
                            contentDescription = stringResource(strings.install_interpreter),
                            modifier = Modifier.padding(end = 4.dp).size(20.dp),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(strings.ready_state),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                }
            },
        )
    }
}

/**
 * بطاقة «مدمج في التطبيق» أعلى تبويب الإضافات — تُبرز ما يأتي مع Mahdi-Editor
 * وتنقل المستخدم إلى تبويب الخوادم والمشغّلات بلمسة.
 */
@Composable
fun BundledPackagesSection(onBrowseServers: () -> Unit, modifier: Modifier = Modifier) {
    val builtIn by LspRegistry.builtInServers.collectAsStateWithLifecycle()
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

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
                        "${builtIn.size} خادم لغة · ${LanguageRegistry.all.size} لغة قابلة للتشغيل · 4 ثيمات"
                    } else {
                        "${builtIn.size} language servers · ${LanguageRegistry.all.size} runnable languages · 4 themes"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            TextButton(onClick = onBrowseServers) {
                Text(if (isArabic) "استعراض الخوادم والمشغّلات" else "Browse servers & runners")
            }
        }
    }
}
