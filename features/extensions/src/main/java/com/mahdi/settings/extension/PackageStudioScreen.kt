package com.mahdi.settings.extension

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mahdi.App.Companion.themeManager
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.packstudio.PackageStudio
import com.mahdi.packstudio.PackageStudio.PackReport
import com.mahdi.settings.Settings
import com.mahdi.settings.editor.refreshEditors
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.SkyBadge
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «استوديو الحزم» (2.8.0) — قلب منظومة الإضافات في Mahdi-Editor.
 *
 * شاشة واحدة تجمع ما كان موزّعًا على أدوات خارجية:
 * - توليد قوالب ثيم/أيقونات/إضافات جاهزة بحقوق `Mahdi-Editor-Code`.
 * - فحص أي حزمة وفق المخططات قبل تثبيتها أو نشرها.
 * - استيراد أي `theme.json` قياسي وتحويله إلى ثيم داخلي بلوحة فاتحة وداكنة.
 * - دليل الصيغة كامل داخل التطبيق (بلا إنترنت).
 */
@Composable
fun PackageStudioScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    var selectedTheme by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var lastPack by remember { mutableStateOf<File?>(null) }
    var report by remember { mutableStateOf<PackReport?>(null) }
    var importedName by remember { mutableStateOf<String?>(null) }

    val themes = remember { PackageStudio.themeSources() }
    val outDir = remember { PackageStudio.workspace(context.getExternalFilesDir(null) ?: context.filesDir) }

    fun generate(action: () -> File) {
        if (busy) return
        busy = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { action() }.getOrNull() }
            busy = false
            if (file != null) {
                lastPack = file
                report = null
            }
        }
    }

    val validateLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            busy = true
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        val tmp = context.copyUriToCache(uri) ?: return@withContext null
                        PackageStudio.validate(tmp)
                    }
                busy = false
                report = result
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            busy = true
            scope.launch {
                val holder =
                    withContext(Dispatchers.IO) {
                        val tmp = context.copyUriToCache(uri) ?: return@withContext null
                        PackageStudio.importThemeIntoApp(tmp)
                    }
                busy = false
                if (holder != null) {
                    themeManager.addCustomTheme(holder)
                    Settings.theme = holder.id
                    themeManager.ensureThemeMode(holder)
                    refreshEditors()
                    importedName = holder.name
                }
            }
        }

    PreferenceLayout(
        label = if (isArabic) "استوديو الحزم" else "Package studio",
        modifier = modifier,
        backArrowVisible = true,
    ) {
        StudioHeader(isArabic = isArabic)

        // ————— 1. قالب ثيم —————
        PreferenceGroup(
            heading = if (isArabic) "١ · قالب ثيم مزدوج الوضع" else "1 · Dual-mode theme template",
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text =
                        if (isArabic) {
                            "يُنتج حزمة كاملة (manifest.json + theme.json) بلوحة فاتحة ولوحة داكنة " +
                                "مأخوذة من ثيمات المتجر الأربعين — جاهزة للتثبيت أو للمشاركة."
                        } else {
                            "Produces a full pack (manifest.json + theme.json) with real light and dark " +
                                "palettes taken from the 40 store themes — ready to install or share."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(themes) { index, theme ->
                        val colors = theme.dark
                        FilterChip(
                            selected = index == selectedTheme,
                            onClick = { selectedTheme = index },
                            label = {
                                Text(
                                    text = theme.displayName(isArabic),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(colors.primary))
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            themes.getOrNull(selectedTheme)?.let { theme ->
                                generate { PackageStudio.createThemePack(outDir, theme) }
                            }
                        },
                    ) {
                        Text(if (isArabic) "توليد حزمة الثيم" else "Generate theme pack")
                    }
                    TextButton(enabled = !busy, onClick = { validateLauncher.launch("*/*") }) {
                        Text(if (isArabic) "فحص حزمة…" else "Validate pack…")
                    }
                }
            }
        }

        // ————— 2. قالب أيقونات —————
        PreferenceGroup(
            heading = if (isArabic) "٢ · قالب حزمة أيقونات" else "2 · Icon pack template",
        ) {
            StudioActionRow(
                title = if (isArabic) "حزمة أيقونات جاهزة" else "Ready icon pack",
                description =
                    if (isArabic) {
                        "manifest.json + icons/file.svg + folder.svg + folder-open.svg بترخيص MIT."
                    } else {
                        "manifest.json + icons/file.svg + folder.svg + folder-open.svg, MIT licensed."
                    },
                actionLabel = if (isArabic) "توليد" else "Generate",
                enabled = !busy,
                onClick = { generate { PackageStudio.createIconPack(outDir) } },
            )
        }

        // ————— 3. قالب إضافة —————
        PreferenceGroup(
            heading = if (isArabic) "٣ · قالب إضافة" else "3 · Extension template",
        ) {
            StudioActionRow(
                title = if (isArabic) "مشروع إضافة كامل" else "Full extension project",
                description =
                    if (isArabic) {
                        "manifest.json + نقطة بداية Kotlin (ExtensionAPI) + build.gradle.kts + README."
                    } else {
                        "manifest.json + Kotlin entry point (ExtensionAPI) + build.gradle.kts + README."
                    },
                actionLabel = if (isArabic) "توليد" else "Generate",
                enabled = !busy,
                onClick = { generate { PackageStudio.createExtensionPack(outDir) } },
            )
        }

        // ————— 4. استيراد ثيم قياسي —————
        PreferenceGroup(
            heading = if (isArabic) "٤ · استيراد ثيم قياسي" else "4 · Import standard theme",
        ) {
            StudioActionRow(
                title = if (isArabic) "من theme.json أو حزمة" else "From theme.json or a pack",
                description =
                    if (isArabic) {
                        "يقرأ ألوان الوضعين ويضيف الثيم إلى المخزون ويطبّقه فورًا."
                    } else {
                        "Reads both palettes, adds the theme to inventory and applies it instantly."
                    },
                actionLabel = if (isArabic) "اختيار ملف…" else "Pick file…",
                enabled = !busy,
                onClick = { importLauncher.launch("*/*") },
            )
            importedName?.let { name ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkyBadge(text = if (isArabic) "مثبَّت ومطبَّق" else "Installed & applied")
                    Spacer(Modifier.width(8.dp))
                    Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ————— 5. نتيجة توليد / فحص —————
        if (busy) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = if (isArabic) "جارٍ العمل…" else "Working…",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        lastPack?.let { file ->
            PreferenceGroup(heading = if (isArabic) "الحزمة المولَّدة" else "Generated pack") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${file.parent ?: ""} · ${file.length() / 1024} KB",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { context.shareFile(file) }) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isArabic) "مشاركة" else "Share")
                        }
                        TextButton(onClick = { report = PackageStudio.validate(file) }) {
                            Text(if (isArabic) "فحص الحزمة" else "Validate")
                        }
                    }
                }
            }
        }

        report?.let { result -> StudioReportCard(result, isArabic) }

        // ————— 6. الدليل —————
        PreferenceGroup(heading = if (isArabic) "دليل الصيغة" else "Format guide") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = PackageStudio.packageGuide(isArabic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = SkyAccent.copy(alpha = 0.18f))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Mahdi-Editor-Code © 2026",
                    style = MaterialTheme.typography.labelSmall,
                    color = SkyAccent,
                )
            }
        }
    }
}

@Composable
private fun StudioHeader(isArabic: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isArabic) "استوديو الحزم" else "Package studio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.weight(1f))
                SkyBadge(text = "2.8.0", showDot = false)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text =
                    if (isArabic) {
                        "أنشئ ثيمات وحزم أيقونات وإضافات، افحص أي حزمة وفق المخططات، واستورد " +
                            "ثيمات المحرّرات الأخرى — كل ذلك داخل التطبيق وبهوية Mahdi-Editor-Code."
                    } else {
                        "Build themes, icon packs and extensions, validate any pack against the schema, " +
                            "and import themes from other editors — all inside the app under Mahdi-Editor-Code."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StudioActionRow(
    title: String,
    description: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Button(enabled = enabled, onClick = onClick) { Text(actionLabel) }
    }
}

@Composable
private fun StudioReportCard(report: PackReport, isArabic: Boolean) {
    val ok = report.ok
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (ok) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer
            ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text =
                    when {
                        ok && report.issues.isEmpty() -> if (isArabic) "الحزمة سليمة تمامًا ✔" else "Pack is fully valid ✔"
                        ok -> if (isArabic) "سليمة مع ملاحظات" else "Valid with notes"
                        else -> if (isArabic) "${report.errors.size} خطأ يمنع التثبيت" else "${report.errors.size} blocking errors"
                    },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            report.issues.take(12).forEach { issue ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = (if (isArabic) "• " + issue.titleAr else "• " + issue.titleEn),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun Context.copyUriToCache(uri: Uri): File? =
    runCatching {
            val tmp = File(cacheDir, "studio_${System.currentTimeMillis()}.bin")
            contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.takeIf { it.exists() && it.length() > 0 }
        }
        .getOrNull()

private fun Context.shareFile(file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        startActivity(Intent.createChooser(intent, file.name))
    }
}
