package com.mahdi.runner

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.activities.settings.SettingsRoutes
import com.mahdi.commands.CommandProvider
import com.mahdi.commands.ToolbarConfiguration
import com.mahdi.commands.editor.RunCommand
import com.mahdi.commands.global.OpenLinkCommand
import com.mahdi.components.DialogProvider
import com.mahdi.components.DialogRegistry
import com.mahdi.extension.api.DynamicRoute
import com.mahdi.feature.Feature
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.settings.SettingsCategory
import com.mahdi.settings.SettingsRegistry
import com.mahdi.settings.runners.HtmlRunnerSettings
import com.mahdi.settings.runners.RunnerSettings

class RunnerFeature : Feature {
    private var dialogProvider: DialogProvider? = null
    private var settingsCategory: SettingsCategory? = null
    private var runnersRoute: DynamicRoute? = null
    private var htmlRunnersRoute: DynamicRoute? = null

    override fun init(application: Application) {
        // Register RunnerSheet overlay
        dialogProvider =
            DialogProvider {
                val showRunnerDialog by RunnerUI.showRunnerDialog.collectAsStateWithLifecycle()
                if (showRunnerDialog) {
                    RunnerSheet()
                }

                // 9.2.0 — «معاينة رابط»: نافذة إدخال العنوان للمتصفّح الداخلي
                val showLinkDialog by RunnerUI.showLinkDialog.collectAsStateWithLifecycle()
                if (showLinkDialog) {
                    LinkPreviewSheet()
                }
            }
                .also { DialogRegistry.register(it) }

        // Register settings category
        settingsCategory =
            SettingsCategory(
                    label = strings.runners.getString(),
                    description = strings.runners_desc.getString(),
                    icon = Icon.ResourceIcon(drawables.run),
                    route = SettingsRoutes.Runners.route,
                )
                .also { SettingsRegistry.registerCategory(it) }

        // Register settings routes
        runnersRoute =
            DynamicRoute(SettingsRoutes.Runners.route) { navController, _ ->
                    RunnerSettings(navController = navController)
                }
                .also { SettingsRegistry.registerRoute(it) }

        htmlRunnersRoute =
            DynamicRoute(SettingsRoutes.HtmlRunner.route) { _, _ -> HtmlRunnerSettings() }
                .also { SettingsRegistry.registerRoute(it) }

        // Register Run command
        CommandProvider.registerCommand(RunCommand)
        ToolbarConfiguration.addGlobalToolbarCommand(RunCommand, 0)

        // 9.2.0: «فتح رابط» — متصفّح داخلي كما في المتصفحات (لا متصفّح ثانٍ:
        // يُستخدم PreviewBrowserActivity نفسه الذي يعرض ملفات HTML).
        CommandProvider.registerCommand(OpenLinkCommand)
    }

    override fun dispose(application: Application) {
        dialogProvider?.let { DialogRegistry.unregister(it) }
        settingsCategory?.let { SettingsRegistry.unregisterCategory(it) }
        runnersRoute?.let { SettingsRegistry.unregisterRoute(it) }
        htmlRunnersRoute?.let { SettingsRegistry.unregisterRoute(it) }

        CommandProvider.unregisterCommand(OpenLinkCommand)
        CommandProvider.unregisterCommand(RunCommand)
        ToolbarConfiguration.removeGlobalToolbarCommand(RunCommand)
    }
}
