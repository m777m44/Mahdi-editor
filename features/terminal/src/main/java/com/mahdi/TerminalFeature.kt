package com.mahdi

import android.app.Application
import android.content.Intent
import com.mahdi.activities.main.MainActivity
import com.mahdi.activities.settings.SettingsRoutes
import com.mahdi.activities.terminal.Terminal
import com.mahdi.commands.CommandProvider
import com.mahdi.commands.ToolbarConfiguration
import com.mahdi.commands.global.TerminalCommand
import com.mahdi.drawer.AddProjectCategory
import com.mahdi.drawer.AddProjectOption
import com.mahdi.drawer.AddProjectRegistry
import com.mahdi.exec.pendingCommand
import com.mahdi.exec.ubuntuProcess
import com.mahdi.extension.api.DynamicRoute
import com.mahdi.feature.Feature
import com.mahdi.feature.FeatureRegistry
import com.mahdi.feature.FeatureToggle
import com.mahdi.file.FileObject
import com.mahdi.file.FileWrapper
import com.mahdi.file.sandboxHomeDir
import com.mahdi.filetree.FileAction
import com.mahdi.filetree.FileActionContext
import com.mahdi.filetree.FileActionProvider
import com.mahdi.filetree.FileActionType
import com.mahdi.icons.Icon
import com.mahdi.lsp.LspRegistry
import com.mahdi.lsp.servers.Bash
import com.mahdi.lsp.servers.CSS
import com.mahdi.lsp.servers.Emmet
import com.mahdi.lsp.servers.HTML
import com.mahdi.lsp.servers.Python
import com.mahdi.lsp.servers.TypeScript
import com.mahdi.lsp.servers.XML
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.runner.RunnerManager
import com.mahdi.runner.runners.UniversalRunner
import com.mahdi.settings.Settings
import com.mahdi.settings.SettingsCategory
import com.mahdi.settings.SettingsRegistry
import com.mahdi.settings.editor.TerminalFontScreen
import com.mahdi.settings.terminal.SettingsTerminalScreen
import com.mahdi.settings.terminal.TerminalCheckScreen
import com.mahdi.settings.terminal.TerminalExtraKeys
import com.mahdi.utils.dialogRes
import com.mahdi.utils.toast

class TerminalFeature : Feature {
    override val toggle =
        FeatureToggle(
            name = strings.terminal_feature.getString(),
            key = "feature_terminal",
            default = true,
            icon = Icon.ResourceIcon(drawables.terminal),
        )

    private var settingsCategory: SettingsCategory? = null
    private var addProjectOption: AddProjectOption? = null
    private val routes = mutableListOf<DynamicRoute>()

    override fun init(application: Application) {

        // Register the file action
        FileActionProvider.registerAction(TerminalAction)

        // Register settings categories
        settingsCategory =
            SettingsCategory(
                    label = strings.terminal.getString(),
                    description = strings.terminal_desc.getString(),
                    icon = Icon.ResourceIcon(drawables.terminal),
                    route = SettingsRoutes.TerminalSettings.route,
                )
                .also { SettingsRegistry.registerCategory(it) }

        if (FeatureRegistry.isEnabled("feature_terminal")) {
            addProjectOption =
                AddProjectOption(
                        icon = Icon.ResourceIcon(drawables.terminal),
                        title = strings.terminal_home.getString(),
                        description = strings.terminal_home_desc.getString(),
                        category = AddProjectCategory.STORAGE,
                        onClick = { onDismiss ->
                            if (!Settings.has_shown_terminal_dir_warning) {
                                dialogRes(
                                    title = strings.attention.getString(),
                                    msg = strings.warning_private_dir.getString(),
                                    onOk = {
                                        Settings.has_shown_terminal_dir_warning = true
                                        MainActivity.instance
                                            ?.drawerViewModel
                                            ?.addFileTreeTab(FileWrapper(sandboxHomeDir()), true)
                                    },
                                )
                            } else {
                                MainActivity.instance
                                    ?.drawerViewModel
                                    ?.addFileTreeTab(FileWrapper(sandboxHomeDir()), true)
                            }
                            onDismiss()
                        },
                    )
                    .also { AddProjectRegistry.register(it) }
        }

        // Register settings routes
        routes.add(DynamicRoute(SettingsRoutes.TerminalSettings.route) { _, _ -> SettingsTerminalScreen() })
        routes.add(DynamicRoute(SettingsRoutes.TerminalExtraKeys.route) { _, _ -> TerminalExtraKeys() })
        routes.add(DynamicRoute(SettingsRoutes.TerminalCheck.route) { _, _ -> TerminalCheckScreen() })
        routes.add(DynamicRoute(SettingsRoutes.TerminalFontScreen.route) { _, _ -> TerminalFontScreen() })

        routes.forEach { SettingsRegistry.registerRoute(it) }

        // Register UniversalRunner dynamically
        RunnerManager.addBuiltInRunner(UniversalRunner)

        // Register TerminalLauncher handler
        TerminalLauncher.handler = { activity, sandbox, exe, args, id, terminatePreviousSession, workingDir, env ->
            pendingCommand =
                com.mahdi.exec.TerminalCommand(
                    sandbox = sandbox,
                    exe = exe,
                    args = args,
                    id = id,
                    terminatePreviousSession = terminatePreviousSession,
                    workingDir = workingDir,
                    env = env,
                )
            try {
                val intent = Intent(activity, Terminal::class.java)
                activity.startActivity(intent)
            } catch (_: Exception) {
                toast("Terminal feature is not available in this build")
            }
        }

        // Register SandboxedProcessRegistry provider
        SandboxedProcessRegistry.provider = { command, workingDir, excludeMounts ->
            ubuntuProcess(excludeMounts, workingDir = workingDir, command = command)
        }

        // Register global command
        CommandProvider.registerCommand(TerminalCommand)

        // Assuming there's at least one item already there
        ToolbarConfiguration.addGlobalToolbarCommand(TerminalCommand, index = 1)

        // Register built-in LSP servers
        LspRegistry.addBuiltInServers(HTML, Emmet, CSS, TypeScript, Bash, XML, Python)
    }

    override fun dispose(application: Application) {
        FileActionProvider.unregisterAction(TerminalAction)
        settingsCategory?.let { SettingsRegistry.unregisterCategory(it) }
        addProjectOption?.let { AddProjectRegistry.unregister(it) }
        routes.forEach { SettingsRegistry.unregisterRoute(it) }
        routes.clear()

        RunnerManager.removeBuiltInRunner(UniversalRunner)
        TerminalLauncher.handler = null
        SandboxedProcessRegistry.provider = null
        CommandProvider.unregisterCommand(TerminalCommand)
        ToolbarConfiguration.removeGlobalToolbarCommand(TerminalCommand)
        LspRegistry.removeBuiltInServers(HTML, Emmet, CSS, TypeScript, Bash, XML, Python)
    }
}

object TerminalAction : FileAction() {
    override val icon = Icon.ResourceIcon(drawables.terminal)
    override val title = strings.open_in_terminal.getString()

    override suspend fun execute(context: FileActionContext) {
        val file = context.file
        val ctx = context.context

        val intent = Intent(ctx, Terminal::class.java)
        intent.putExtra("cwd", file.getAbsolutePath())
        ctx.startActivity(intent)
    }

    override suspend fun isSupported(file: FileObject, root: FileObject?): Boolean {
        return file is FileWrapper && FeatureRegistry.isEnabled("feature_terminal")
    }

    override val type = FileActionType(file = false, folder = true, rootFolder = true)
}
