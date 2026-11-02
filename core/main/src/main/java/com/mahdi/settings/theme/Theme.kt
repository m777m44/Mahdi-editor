package com.mahdi.settings.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.mahdi.theme.ThemeHolder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mahdi.App.Companion.iconPackManager
import com.mahdi.App.Companion.themeManager
import com.mahdi.DefaultScope
import com.mahdi.activities.settings.SettingsRoutes
import com.mahdi.components.BottomSheetContent
import com.mahdi.components.SettingsItem
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.components.compose.preferences.base.PreferenceTemplate
import com.mahdi.events.AppEvent
import com.mahdi.events.Events
import com.mahdi.resources.drawables
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import com.mahdi.settings.editor.refreshEditors
import com.mahdi.theme.CustomThemes
import com.mahdi.theme.DEFAULT_THEME_ID
import com.mahdi.theme.THEME_MODE_AUTO_TIME
import com.mahdi.theme.supportsDynamicTheming
import com.mahdi.theme.themeCatalog
import com.mahdi.theme.themeStyleOf
import kotlinx.coroutines.launch

@Composable
fun ThemeScreen(navController: NavController, modifier: Modifier = Modifier) {
    val showDayNightBottomSheet = remember { mutableStateOf(false) }
    val amoledState = remember { mutableStateOf(Settings.amoled) }
    val loadedThemes = themeManager.loadedThemesState.value

    PreferenceLayout(label = stringResource(strings.themes)) {
        PreferenceGroup(heading = stringResource(strings.theme_settings)) {
            SettingsItem(
                label = stringResource(id = strings.theme_mode),
                description = stringResource(id = strings.theme_mode_desc),
                showSwitch = false,
                default = false,
                sideEffect = { showDayNightBottomSheet.value = true },
            )

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

        // 3.3.0: فصل واضح — الثيمات الأساسية في مجموعة، وكل ثيم يُثبَّت من المتجر
        // ينتقل تلقائيًا إلى «ثيمات مضافة» فلا يختلط بالأساسية.
        // الأساسية = الأربعة المعروفة في الكتالوج؛ كل ما سواها (متجر/مستورد/مخصّص) = مضافة
        val baseThemes = loadedThemes.filter { theme -> themeCatalog.any { it.id == theme.id } }
        val addedThemes = loadedThemes.filterNot { theme -> themeCatalog.any { it.id == theme.id } }

        PreferenceGroup(heading = stringResource(strings.base_themes)) {
            baseThemes.forEach { theme -> ThemeRow(theme) }
        }

        PreferenceGroup(
            heading = stringResource(strings.added_themes),
            description =
                if (addedThemes.isEmpty()) {
                    if (LocalConfiguration.current.locales[0].language == "ar") {
                        "لم تُضف ثيمات بعد — ثبّت أي ثيم من «تصفح الإضافات ‹ الثيمات» وسيظهر هنا فورًا."
                    } else {
                        "No added themes yet — install one from Browse add-ons › Themes and it lands here."
                    }
                } else {
                    stringResource(strings.added_themes_desc)
                },
        ) {
            addedThemes.forEach { theme -> ThemeRow(theme) }
        }

        if (showDayNightBottomSheet.value) {
            DayNightDialog(showBottomSheet = showDayNightBottomSheet, context = LocalContext.current)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayNightDialog(showBottomSheet: MutableState<Boolean>, context: Context) {
    val bottomSheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    var selectedMode by remember { mutableIntStateOf(Settings.theme_mode) }

    val modes =
        listOf(
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            THEME_MODE_AUTO_TIME,
        )
    val modeLabels =
        listOf(
            context.getString(strings.light_mode),
            context.getString(strings.dark_mode),
            context.getString(strings.auto_mode),
            context.getString(strings.auto_time_mode),
        )

    val theme = themeManager.currentTheme
    val isLightSupported = theme.isLightSupported
    val isDarkSupported = theme.isDarkSupported

    if (showBottomSheet.value) {
        ModalBottomSheet(onDismissRequest = { showBottomSheet.value = false }, sheetState = bottomSheetState) {
            BottomSheetContent(
                title = { Text(text = stringResource(id = strings.theme_mode)) },
                buttons = {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                bottomSheetState.hide()
                                showBottomSheet.value = false
                            }
                        }
                    ) {
                        Text(text = stringResource(id = strings.cancel))
                    }
                },
            ) {
                LazyColumn {
                    itemsIndexed(modes) { index, mode ->
                        val isEnabled =
                            when {
                                mode == AppCompatDelegate.MODE_NIGHT_NO -> isLightSupported
                                mode == AppCompatDelegate.MODE_NIGHT_YES -> isDarkSupported
                                else -> true
                            }

                        PreferenceTemplate(
                            title = { Text(text = modeLabels[index]) },
                            modifier =
                                Modifier.clickable(enabled = isEnabled) {
                                    selectedMode = mode
                                    Settings.theme_mode = selectedMode
                                    themeManager.ensureThemeMode(theme)
                                    coroutineScope.launch {
                                        bottomSheetState.hide()
                                        showBottomSheet.value = false
                                    }
                                },
                            enabled = isEnabled,
                            startWidget = {
                                RadioButton(selected = selectedMode == mode, onClick = null, enabled = isEnabled)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * صفّ ثيم واحد في شاشة الثيمات (3.3.0) — مستخدم في المجموعتين الأساسية والمضافة.
 * الثيمات المضافة وحدها تظهر لها زرّ حذف.
 */
@Composable
private fun ThemeRow(theme: ThemeHolder) {
    // الثيمات المضافة (متجر/مستوردة/مخصّصة) وحدها قابلة للحذف؛ الأربعة الأساسية لا تُحذف
    val deleteAction: (@Composable () -> Unit)? =
        if (themeCatalog.none { it.id == theme.id }) {
            {
                IconButton(
                    onClick = {
                        CustomThemes.delete(theme.id)
                        themeManager.removeCustomTheme(theme.id)
                        if (Settings.theme == theme.id) {
                            themeManager.setTheme(DEFAULT_THEME_ID)
                        }
                    }
                ) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = stringResource(strings.delete))
                }
            }
        } else {
            null
        }

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
        endWidget = deleteAction,
        sideEffect = {
            // 3.1.0: setTheme يجعل تغيير الثيم حالة قابلة للمراقبة فتتحدّث الواجهة فورًا
            themeManager.setTheme(theme.id, theme)
        },
    )
}
