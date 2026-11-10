package com.mahdi.activities.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mahdi.animations.NavigationAnimationTransitions
import com.mahdi.resources.strings
import com.mahdi.lsp.LspRegistry
import com.mahdi.settings.SettingsRegistry
import com.mahdi.settings.SettingsScreen
import com.mahdi.settings.app.SettingsAppScreen
import com.mahdi.settings.debugOptions.AppLogs
import com.mahdi.settings.debugOptions.DeveloperOptions
import com.mahdi.settings.editor.AppFontScreen
import com.mahdi.settings.editor.DefaultEncoding
import com.mahdi.settings.editor.DefaultLineEnding
import com.mahdi.settings.editor.EditExtraKeys
import com.mahdi.settings.editor.EditToolbarActions
import com.mahdi.settings.editor.EditorFontScreen
import com.mahdi.settings.editor.ExcludeFiles
import com.mahdi.settings.editor.FormatterSettings
import com.mahdi.settings.editor.SettingsEditorScreen
import com.mahdi.settings.guide.GuideScreen
import com.mahdi.settings.keybinds.KeybindingsScreen
import com.mahdi.settings.language.LanguageScreen
import com.mahdi.settings.language.LanguagesHub
import com.mahdi.settings.language.SupportedLanguages
import com.mahdi.settings.lsp.LspInitializationOptions
import com.mahdi.settings.lsp.LspServerDetail
import com.mahdi.settings.lsp.LspServerLogs
import com.mahdi.settings.lsp.LspSettings
import com.mahdi.settings.theme.ThemeScreen
import com.mahdi.settings.update.UpdateScreen

@Composable
fun SettingsNavHost(navController: NavHostController, activity: SettingsActivity) {
    NavHost(
        navController = navController,
        startDestination = SettingsRoutes.Settings.route,
        enterTransition = { NavigationAnimationTransitions.enterTransition },
        exitTransition = { NavigationAnimationTransitions.exitTransition },
        popEnterTransition = { NavigationAnimationTransitions.popEnterTransition },
        popExitTransition = { NavigationAnimationTransitions.popExitTransition },
    ) {
        composable(SettingsRoutes.Settings.route) { SettingsScreen(navController) }
        composable(SettingsRoutes.AppSettings.route) { SettingsAppScreen(activity, navController) }
        composable(SettingsRoutes.EditorSettings.route) { SettingsEditorScreen(navController) }
        composable(SettingsRoutes.Keybindings.route) { KeybindingsScreen() }

        composable(SettingsRoutes.EditorFontScreen.route) { EditorFontScreen() }
        composable(SettingsRoutes.AppFontScreen.route) { AppFontScreen() }
        composable(SettingsRoutes.AppIcon.route) { com.mahdi.settings.app.AppIconScreen() }

        composable(SettingsRoutes.DefaultEncoding.route) { DefaultEncoding() }
        composable(SettingsRoutes.DefaultLineEnding.route) { DefaultLineEnding() }
        composable(SettingsRoutes.ToolbarActions.route) { EditToolbarActions() }
        composable(SettingsRoutes.ExtraKeys.route) { EditExtraKeys() }
        composable(
            "${SettingsRoutes.ExcludeFiles.route}/{isDrawer}",
            arguments = listOf(navArgument("isDrawer", builder = { type = NavType.BoolType })),
        ) {
            val isDrawer = it.arguments?.getBoolean("isDrawer")!!
            ExcludeFiles(isDrawer)
        }
        composable(SettingsRoutes.DeveloperOptions.route) { DeveloperOptions(navController = navController) }
        composable(SettingsRoutes.AppLogs.route) { AppLogs() }
        composable(SettingsRoutes.LanguageScreen.route) { LanguageScreen() }
        composable(SettingsRoutes.Languages.route) { LanguagesHub(navController = navController) }
        composable(SettingsRoutes.Formatters.route) { FormatterSettings(navController) }
        composable(SettingsRoutes.LspSettings.route) { LspSettings(navController) }
        composable(
            "${SettingsRoutes.LspServerDetail.route}/{serverId}",
            arguments = listOf(navArgument("serverId", builder = { type = NavType.StringType })),
        ) { backStackEntry ->
            val server = backStackEntry.arguments?.getString("serverId")?.let { LspRegistry.getForId(it) }
            if (server == null) {
                ServerUnavailableScreen(onBack = { navController.popBackStack() })
            } else {
                LspServerDetail(navController, server)
            }
        }
        composable(
            "${SettingsRoutes.LspInitializationOptions.route}/{serverId}",
            arguments = listOf(navArgument("serverId", builder = { type = NavType.StringType })),
        ) { backStackEntry ->
            val server = backStackEntry.arguments?.getString("serverId")?.let { LspRegistry.getForId(it) }
            if (server == null) {
                ServerUnavailableScreen(onBack = { navController.popBackStack() })
            } else {
                LspInitializationOptions(server)
            }
        }
        composable(
            "${SettingsRoutes.LspServerLogs.route}/{serverId}/{instanceId}",
            arguments =
                listOf(
                    navArgument("serverId", builder = { type = NavType.StringType }),
                    navArgument("instanceId", builder = { type = NavType.StringType }),
                ),
        ) { backStackEntry ->
            val server = backStackEntry.arguments?.getString("serverId")?.let { LspRegistry.getForId(it) }
            val instanceId = backStackEntry.arguments?.getString("instanceId")
            if (server == null || instanceId == null) {
                ServerUnavailableScreen(onBack = { navController.popBackStack() })
            } else {
                LspServerLogs(server, instanceId)
            }
        }
        composable(SettingsRoutes.Updates.route) { UpdateScreen() }
        composable(SettingsRoutes.Themes.route) { ThemeScreen(navController) }
        composable(SettingsRoutes.Guide.route) { GuideScreen() }
        composable(SettingsRoutes.SupportedLanguages.route) { SupportedLanguages() }

        SettingsRegistry.routes.value.forEach { customRoute ->
            composable(customRoute.route, arguments = customRoute.arguments) { backStackEntry ->
                customRoute.content(navController, backStackEntry)
            }
        }
    }
}

/**
 * 8.2.0: «إدارة» تنبيه الاتصال قد تفتح تفاصيل خادم حُذف للتو أو أُزيلت إضافته.
 * قبل ذلك كان `LspRegistry.getForId(id)!!` يرمي استثناءً ويُغلق التطبيق؛ الآن
 * شاشة خطأ أنيقة بنصّ عربي كامل وزرّ رجوع واحد، بلا انهيار وبلا شاشة فارغة.
 */
@Composable
private fun ServerUnavailableScreen(onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(strings.lsp_server_missing),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(strings.lsp_server_missing_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onBack) { Text(stringResource(strings.back)) }
        }
    }
}
