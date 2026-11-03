package com.mahdi.settings.theme

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mahdi.App.Companion.themeManager
import com.mahdi.components.ConfirmDialog
import com.mahdi.components.SettingsItem
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import com.mahdi.settings.editor.refreshEditors
import com.mahdi.theme.CustomThemes
import com.mahdi.theme.FeaturedTheme
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.StoreThemeCatalog
import com.mahdi.theme.ThemeHolder
import com.mahdi.theme.ThemeInstaller
import com.mahdi.theme.themeCatalog
import com.mahdi.theme.themeStyleOf

@Composable
fun ThemeScreen(navController: NavController, modifier: Modifier = Modifier) {
    val amoledState = remember { mutableStateOf(Settings.amoled) }
    val loadedThemes = themeManager.loadedThemesState.value
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    // 4.0.0: «تثبيت» و«إزالة» بنفس النافذة المخصّصة لكل عملية حذف
    var pendingDelete by remember { mutableStateOf<ThemeHolder?>(null) }

    // الأساسية = الأربعة المعروفة في الكتالوج؛ كل ما سواها = ثيمات مثبّتة قابلة للحذف
    val baseThemes = loadedThemes.filter { theme -> themeCatalog.any { it.id == theme.id } }
    val installedThemes =
        (loadedThemes + CustomThemes.themes)
            .distinctBy { it.id }
            .filterNot { theme -> themeCatalog.any { it.id == theme.id } }
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
        description = themeStyleOf(theme.id)?.label,
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
