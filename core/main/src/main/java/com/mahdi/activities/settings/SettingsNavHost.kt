package com.mahdi.activities.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mahdi.animations.NavigationAnimationTransitions
import com.mahdi.lsp.LspRegistry
import com.mahdi.settings.SettingsRegistry
import com.mahdi.settings.SettingsScreen
import com.mahdi.settings.about.AboutScreen
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
import com.mahdi.settings.keybinds.KeybindingsScreen
import com.mahdi.settings.language.LanguageScreen
import com.mahdi.settings.language.SupportedLanguages
import com.mahdi.settings.lsp.LspInitializationOptions
import com.mahdi.settings.lsp.LspServerDetail
import com.mahdi.settings.lsp.LspServerLogs
import com.mahdi.settings.lsp.LspSettings
import com.mahdi.settings.support.Support
import com.mahdi.settings.theme.ThemeScreen
import com.mahdi.theme.ThemeCustomizer

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

        composable(SettingsRoutes.About.route) { AboutScreen() }
        composable(SettingsRoutes.EditorFontScreen.route) { EditorFontScreen() }
        composable(SettingsRoutes.AppFontScreen.route) { AppFontScreen() }

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
        composable(SettingsRoutes.Support.route) { Support() }
        composable(SettingsRoutes.LanguageScreen.route) { LanguageScreen() }
        composable(SettingsRoutes.Formatters.route) { FormatterSettings(navController) }
        composable(SettingsRoutes.LspSettings.route) { LspSettings(navController) }
        composable(
            "${SettingsRoutes.LspServerDetail.route}/{serverId}",
            arguments = listOf(navArgument("serverId", builder = { type = NavType.StringType })),
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")!!
            val server = LspRegistry.getForId(serverId)!!
            LspServerDetail(navController, server)
        }
        composable(
            "${SettingsRoutes.LspInitializationOptions.route}/{serverId}",
            arguments = listOf(navArgument("serverId", builder = { type = NavType.StringType })),
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")!!
            val server = LspRegistry.getForId(serverId)!!
            LspInitializationOptions(server)
        }
        composable(
            "${SettingsRoutes.LspServerLogs.route}/{serverId}/{instanceId}",
            arguments =
                listOf(
                    navArgument("serverId", builder = { type = NavType.StringType }),
                    navArgument("instanceId", builder = { type = NavType.StringType }),
                ),
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId")!!
            val server = LspRegistry.getForId(serverId)!!
            val instanceId = backStackEntry.arguments?.getString("instanceId")!!
            LspServerLogs(server, instanceId)
        }
        composable(SettingsRoutes.Themes.route) { ThemeScreen(navController) }
        composable(SettingsRoutes.ThemeCustomizer.route) { ThemeCustomizer(navController) }
        composable(SettingsRoutes.SupportedLanguages.route) { SupportedLanguages() }

        SettingsRegistry.routes.value.forEach { customRoute ->
            composable(customRoute.route, arguments = customRoute.arguments) { backStackEntry ->
                customRoute.content(navController, backStackEntry)
            }
        }
    }
}
