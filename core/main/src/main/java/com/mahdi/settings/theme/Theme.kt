package com.mahdi.settings.theme

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mahdi.App
import com.mahdi.App.Companion.themeManager
import com.mahdi.DefaultScope
import com.mahdi.components.ConfirmDialog
import com.mahdi.components.SettingsItem
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.events.AppEvent
import com.mahdi.events.Events
import com.mahdi.icons.pack.VsCodeIconPack
import com.mahdi.icons.pack.currentIconPack
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import com.mahdi.settings.editor.refreshEditors
import com.mahdi.theme.CustomThemes
import com.mahdi.theme.FeaturedTheme
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.StoreThemeCatalog
import com.mahdi.theme.ThemeHolder
import com.mahdi.theme.ThemeInstaller
import com.mahdi.theme.VsCodeThemeInstaller
import com.mahdi.theme.VsCodeThemes
import com.mahdi.theme.isBundledTheme
import com.mahdi.theme.themeStyleOf
import com.mahdi.utils.errorDialog
import com.mahdi.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ThemeScreen(navController: NavController, modifier: Modifier = Modifier) {
    val amoledState = remember { mutableStateOf(Settings.amoled) }
    // 9.8.0: أيقونات أنواع الملفات — حالة مستقلة لأن الحزمة قد تكون غير جاهزة
    // (نسخ الأصول فشل مثلًا)، وحينها يظهر الصفّ معطّلًا بدل أن يكذب على المستخدم.
    val vscodeIcons =
        remember { App.iconPackManager.localIconPacks.value[VsCodeIconPack.ID] }
    val iconsState = remember { mutableStateOf(Settings.icon_pack == VsCodeIconPack.ID) }
    val loadedThemes = themeManager.loadedThemesState.value
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    // 4.0.0: «تثبيت» و«إزالة» بنفس النافذة المخصّصة لكل عملية حذف
    var pendingDelete by remember { mutableStateOf<ThemeHolder?>(null) }

    // 9.8.0: استيراد ثيم VS Code من ملف يختاره المستخدم
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themePicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                // القراءة والتحليل على IO (قد يكون الملف على بطاقة/سحابة)،
                // والحفظ والتطبيق على Main لأنهما يكتبان حالة Compose.
                val parsed = withContext(Dispatchers.IO) { VsCodeThemeInstaller.installFromUri(context, uri) }
                when (parsed) {
                    is VsCodeThemeInstaller.Result.Ready -> {
                        when (val saved = VsCodeThemeInstaller.commit(context, parsed)) {
                            is VsCodeThemeInstaller.Result.Success -> toast("أُضيف الثيم: ${saved.name}")
                            is VsCodeThemeInstaller.Result.Failure -> errorDialog(msg = saved.reason)
                            else -> Unit
                        }
                    }
                    is VsCodeThemeInstaller.Result.Failure -> errorDialog(msg = parsed.reason)
                    else -> Unit
                }
            }
        }

    // الأساسية = الأربعة المعروفة في الكتالوج؛ كل ما سواها = ثيمات مثبّتة قابلة للحذف
    val baseThemes =
        loadedThemes.filter { theme -> isBundledTheme(theme.id) && !VsCodeThemes.isVsCodeTheme(theme.id) }
    val installedThemes =
        (loadedThemes + CustomThemes.themes)
            .distinctBy { it.id }
            .filterNot { theme -> isBundledTheme(theme.id) }
    // يُعاد حسابها مع كل إعادة رسم (تحدث بعد التثبيت/الحذف لأن القائمة حالة قابلة للمراقبة)
    val availableThemes = StoreThemeCatalog.themes.filterNot { ThemeInstaller.isInstalled(it.id) }

    PreferenceLayout(label = stringResource(strings.themes), modifier = modifier) {
        PreferenceGroup(heading = stringResource(strings.theme_settings)) {
            SettingsItem(
                label = stringResource(id = strings.oled),
                description = stringResource(id = strings.oled_desc),
                default = Settings.amoled,
                state = amoledState,
                sideEffect = {
                    Settings.amoled = it
                    refreshEditors()
                },
            )
        }

        // ————— أيقونات أنواع الملفات (9.8.0) —————
        // تُعرض هنا لا في شاشة منفصلة: من يفتح «المظهر» ليتغيّر شكل التطبيق
        // يجدها في نفس المكان. التبديل فوري — لا حوار «سيُعاد التشغيل».
        PreferenceGroup(heading = stringResource(strings.theme_settings)) {
            SettingsItem(
                label = stringResource(strings.vscode_icons_name),
                description =
                    if (vscodeIcons == null) {
                        stringResource(strings.vscode_icons_unavailable)
                    } else {
                        stringResource(strings.vscode_icons_desc)
                    },
                default = Settings.icon_pack == VsCodeIconPack.ID,
                state = iconsState,
                isEnabled = vscodeIcons != null,
                sideEffect = { enabled ->
                    Settings.icon_pack_chosen_once = true
                    val previous = currentIconPack.value
                    currentIconPack.value = if (enabled) vscodeIcons else null
                    Settings.icon_pack = if (enabled) VsCodeIconPack.ID else ""
                    // المستعرضات والأدراج ترسم الأيقونات من هذه الحالة، فتُحدَّث
                    // كلّها بنفس الحدث — لا حاجة لإعادة تشغيل التطبيق.
                    DefaultScope.launch {
                        Events.publish(AppEvent.IconPackChanged(currentIconPack.value, previous))
                    }
                },
            )
        }

        // ————— 0) ثيمات VS Code: مدمجة، تُختار كأي ثيم، ولا تُحذف —————
        val vscodeThemes = loadedThemes.filter { VsCodeThemes.isVsCodeTheme(it.id) }
        if (vscodeThemes.isNotEmpty()) {
            PreferenceGroup(
                heading = stringResource(strings.vscode_themes_group),
                description = stringResource(strings.vscode_themes_desc),
            ) {
                vscodeThemes.forEach { theme -> ThemeRow(theme, deletable = false) }
            }
        }

        // ————— استيراد أي ثيم VS Code آخر من ملف —————
        PreferenceGroup(heading = stringResource(strings.import_vscode_theme)) {
            SettingsItem(
                label = stringResource(strings.import_vscode_theme),
                description = stringResource(strings.import_vscode_theme_desc),
                showSwitch = false,
                default = false,
                // النقر على الصف يُفتح المنتقي (‏`sideEffect` مرتبط بالتبديل لا بالنقر)
                onClick = { themePicker.launch("*/*") },
            )
        }

        // ————— 1) الثيمات الأساسية: أربعة فقط، لا تُحذف —————
        PreferenceGroup(heading = stringResource(strings.base_themes)) {
            baseThemes.forEach { theme -> ThemeRow(theme, deletable = false) }
        }

        // ————— 2) الثيمات المثبّتة: لها X ← تأكيد ← حذف كامل —————
        PreferenceGroup(
            heading = "${stringResource(strings.installed_themes)} (${installedThemes.size})",
            description = if (installedThemes.isEmpty()) stringResource(strings.added_themes_desc) else null,
        ) {
            installedThemes.forEach { theme ->
                ThemeRow(
                    theme = theme,
                    deletable = true,
                    onDelete = { pendingDelete = theme },
                )
            }
        }

        // ————— 3) الثيمات المتاحة: زرّ تثبيت واحد لكل ثيم —————
        PreferenceGroup(
            heading = "${stringResource(strings.available_themes)} (${availableThemes.size})",
            description = stringResource(strings.available_themes_desc),
        ) {
            availableThemes.forEach { theme ->
                AvailableThemeRow(
                    theme = theme,
                    isArabic = isArabic,
                    onInstall = { ThemeInstaller.install(theme) },
                )
            }
        }
    }

    // حوار التأكيد — لا يُحذف أي ثيم قبل ضغط «موافق»
    pendingDelete?.let { theme ->
        ConfirmDialog(
            title = stringResource(strings.remove_theme_title),
            message = stringResource(strings.remove_theme_message, theme.name),
            onConfirm = {
                ThemeInstaller.remove(theme.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * صفّ ثيم **متاح** (غير مثبَّت) في شاشة الثيمات (4.0.0).
 *
 * يعرض ألوان الثيم الحقيقية (خلفية + أساسي + تمييز) قبل التثبيت،
 * وزرّ «تثبيت» واحد. التثبيت لا يطبّق الثيم — التطبيق بضغطة على الصف نفسه.
 */
@Composable
private fun AvailableThemeRow(theme: FeaturedTheme, isArabic: Boolean, onInstall: () -> Unit) {
    SettingsItem(
        label = theme.displayName(isArabic),
        description = theme.source,
        showSwitch = false,
        default = false,
        startWidget = {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(theme.dark.background, theme.dark.primary, theme.dark.accent).forEach { color ->
                    Box(
                        modifier =
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(color)),
                    )
                }
            }
        },
        endWidget = {
            TextButton(onClick = onInstall) {
                Text(text = stringResource(strings.install), color = SkyAccent)
            }
        },
        sideEffect = { onInstall() },
    )
}

/**
 * صفّ ثيم واحد في شاشة الثيمات (4.0.0).
 *
 * - الثيمات الأساسية: اختيار فقط، بلا زرّ حذف.
 * - الثيمات المثبّتة: اختيار + **X** يفتح نافذة تأكيد قبل الحذف الكامل.
 */
@Composable
private fun ThemeRow(
    theme: ThemeHolder,
    deletable: Boolean,
    onDelete: (() -> Unit)? = null,
) {
    SettingsItem(
        isEnabled = true,
        label = theme.name,
        description = themeStyleOf(theme.id)?.label ?: if (VsCodeThemes.isVsCodeTheme(theme.id)) "VS Code" else null,
        showSwitch = false,
        default = false,
        startWidget = {
            RadioButton(
                modifier = Modifier.padding(start = 16.dp),
                enabled = true,
                selected = themeManager.currentTheme.id == theme.id,
                onClick = null,
            )
        },
        endWidget =
            if (deletable && onDelete != null) {
                {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(strings.delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                null
            },
        sideEffect = {
            // 3.1.0: setTheme يجعل تغيير الثيم حالة قابلة للمراقبة فتتحدّث الواجهة فورًا
            themeManager.setTheme(theme.id, theme)
        },
    )
}
