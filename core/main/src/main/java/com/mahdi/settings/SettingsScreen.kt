package com.mahdi.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mahdi.activities.settings.SettingsRoutes
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.components.compose.preferences.category.PreferenceCategory
import com.mahdi.feature.FeatureRegistry
import com.mahdi.icons.MahdiIcon
import com.mahdi.resources.drawables
import com.mahdi.resources.getFilledString
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.settings.about.MAHDI_COPYRIGHT

@Composable
fun SettingsScreen(navController: NavController) {
    PreferenceLayout(label = stringResource(id = strings.settings), backArrowVisible = true) {
        Categories(navController)
    }
}

@Composable
private fun Categories(navController: NavController) {
    PreferenceCategory(
        label = stringResource(id = strings.app),
        description = stringResource(id = strings.app_desc),
        iconResource = drawables.android,
        onNavigate = { navController.navigate(SettingsRoutes.AppSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(strings.themes),
        description = stringResource(strings.theme_settings),
        iconResource = drawables.palette,
        onNavigate = { navController.navigate(SettingsRoutes.Themes.route) },
    )

    PreferenceCategory(
        label = stringResource(id = strings.editor),
        description = stringResource(id = strings.editor_desc),
        iconResource = drawables.edit_note,
        onNavigate = { navController.navigate(SettingsRoutes.EditorSettings.route) },
    )

    PreferenceCategory(
        label = stringResource(strings.keybindings),
        description = stringResource(strings.keybindings_desc),
        iconResource = drawables.keyboard,
        onNavigate = { navController.navigate(SettingsRoutes.Keybindings.route) },
    )

    PreferenceCategory(
        label = stringResource(strings.guide_title),
        description = stringResource(strings.guide_desc),
        iconResource = drawables.info,
        onNavigate = { navController.navigate(SettingsRoutes.Guide.route) },
    )

    SettingsRegistry.categories.value.forEach { category ->
        PreferenceCategory(
            label = category.label,
            description = category.description,
            startWidget = {
                MahdiIcon(
                    icon = category.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            onNavigate = { navController.navigate(category.route) },
        )
    }

    if (FeatureRegistry.isEnabled("debug_mode")) {
        PreferenceCategory(
            label = stringResource(strings.debug_options),
            description = strings.debug_options_desc.getFilledString(strings.app_name.getString()),
            iconResource = drawables.build,
            onNavigate = { navController.navigate(SettingsRoutes.DeveloperOptions.route) },
        )
    }

    // سطر الحقوق فقط — أُزيلت صفحات «حول» و«الدعم» بطلب من صاحب التطبيق
    Text(
        text = MAHDI_COPYRIGHT,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
    )
}
