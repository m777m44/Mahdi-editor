package com.mahdi.ai

import android.app.Application
import com.mahdi.ai.api.AiExtensions
import com.mahdi.ai.command.AIChatCommand
import com.mahdi.ai.icons.SparklesBig
import com.mahdi.ai.icons.SparklesTiny
import com.mahdi.ai.settings.AiMemoryScreen
import com.mahdi.ai.settings.AiSettingsScreen
import com.mahdi.ai.tab.AiTab
import com.mahdi.commands.CommandProvider
import com.mahdi.commands.ToolbarConfiguration
import com.mahdi.extension.api.DynamicRoute
import com.mahdi.feature.Feature
import com.mahdi.feature.FeatureToggle
import com.mahdi.icons.Icon
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.settings.SettingsCategory
import com.mahdi.settings.SettingsRegistry

class AiFeature : Feature {
    override val toggle =
        FeatureToggle(
            name = strings.ai_feature_label.getString(),
            key = "feature_ai",
            default = true,
            icon = Icon.VectorIcon(SparklesTiny),
        )

    private var settingsCategory: SettingsCategory? = null
    private val settingsRoutes = mutableListOf<DynamicRoute>()
    private val aiChatCommand = AIChatCommand()

    override fun init(application: Application) {
        AiExtensions.installBuiltins()
        AiTab.register()
        registerSettings()
        registerCommands()
    }

    private fun registerCommands() {
        CommandProvider.registerCommand(aiChatCommand)
        ToolbarConfiguration.addGlobalToolbarCommand(aiChatCommand, 0)

        // Editor tabs hide the global toolbar (see EditorTab.showGlobalActions), so the AI command
        // must also be part of the editor toolbar to stay reachable while editing a file. This is a
        // no-op when the command is already present, so a user's custom ordering is preserved.
        ToolbarConfiguration.addEditorToolbarCommand(aiChatCommand)
    }

    private fun registerSettings() {
        settingsCategory =
            SettingsCategory(
                    label = strings.ai_feature_label.getString(),
                    description = strings.ai_settings_description.getString(),
                    icon = Icon.VectorIcon(SparklesBig),
                    route = AI_SETTINGS_ROUTE,
                )
                .also { SettingsRegistry.registerCategory(it) }

        settingsRoutes +=
            DynamicRoute(AI_SETTINGS_ROUTE) { navController, _ ->
                AiSettingsScreen(onOpenMemory = { navController.navigate(AI_MEMORY_ROUTE) })
            }
        settingsRoutes += DynamicRoute(AI_MEMORY_ROUTE) { _, _ -> AiMemoryScreen() }
        settingsRoutes.forEach { SettingsRegistry.registerRoute(it) }
    }

    override fun dispose(application: Application) {
        AiTab.unregister()
        ToolbarConfiguration.removeGlobalToolbarCommand(aiChatCommand)
        ToolbarConfiguration.removeEditorToolbarCommand(aiChatCommand)
        CommandProvider.unregisterCommand(aiChatCommand)

        settingsCategory?.let { SettingsRegistry.unregisterCategory(it) }
        settingsCategory = null
        settingsRoutes.forEach { SettingsRegistry.unregisterRoute(it) }
        settingsRoutes.clear()
    }

    private companion object {
        private const val AI_SETTINGS_ROUTE = "ai_settings"
        private const val AI_MEMORY_ROUTE = "ai_memory"
    }
}
