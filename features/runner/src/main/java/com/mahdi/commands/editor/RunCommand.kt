package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.commands.ActionContext
import com.mahdi.commands.Command
import com.mahdi.commands.CommandProvider
import com.mahdi.commands.KeyCombination
import com.mahdi.filetree.FileTreeTab
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.runner.RunnerManager
import com.mahdi.runner.RunnerUI
import com.mahdi.tabs.editor.EditorTab
import kotlinx.coroutines.DelicateCoroutinesApi

@OptIn(DelicateCoroutinesApi::class)
object RunCommand : Command() {
    override val id: String = "editor.run"

    override fun getLabel(): String = strings.run.getString()

    override fun execute(context: ActionContext) {
        launchRunner(context, forceSelection = false)
    }

    override fun onLongClick(context: ActionContext): Boolean {
        launchRunner(context, forceSelection = true)
        return true
    }

    private fun launchRunner(context: ActionContext, forceSelection: Boolean) {
        val mainViewModel = commandContext.mainViewModel
        val drawerViewModel = commandContext.drawerViewModel

        val currentTab = mainViewModel.currentTab as? EditorTab
        val currentDrawerTab = drawerViewModel.currentDrawerTab as? FileTreeTab
        val projectRoot = currentTab?.projectRoot ?: currentDrawerTab?.root
        val fileObject = currentTab?.file

        RunnerManager.run(
            activity = context.currentActivity,
            fileObject = fileObject,
            projectRoot = projectRoot,
            forceSelection = forceSelection,
            beforeRun = {
                if (currentTab != null) {
                    CommandProvider.SaveCommand.execute(context)
                }
            },
            onMultipleRunners = {
                RunnerUI.runnersToShow.value = it
                RunnerUI.showRunnerDialog.value = true
            },
        )
    }

    override fun isSupported(): Boolean {
        val mainViewModel = commandContext.mainViewModel
        val drawerViewModel = commandContext.drawerViewModel

        val currentTab = mainViewModel.currentTab as? EditorTab
        val currentDrawerTab = drawerViewModel.currentDrawerTab as? FileTreeTab
        val projectRoot = currentTab?.projectRoot ?: currentDrawerTab?.root
        val fileObject = currentTab?.file

        return RunnerManager.isRunnable(fileObject, projectRoot)
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.run)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F5)
}
