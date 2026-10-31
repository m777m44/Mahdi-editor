package com.mahdi.git

import com.mahdi.commands.ActionContext
import com.mahdi.commands.GlobalCommand
import com.mahdi.filetree.FileTreeTab
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import java.io.File

object GitInitCommand : GlobalCommand() {
    override val id: String = "git.init"

    override fun getLabel(): String = strings.git_init.getString()

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.git)

    override fun isSupported(): Boolean {
        val drawerViewModel = commandContext.drawerViewModel
        val currentTab = drawerViewModel.currentDrawerTab
        return currentTab is FileTreeTab
    }

    override fun execute(context: ActionContext) {
        val drawerViewModel = commandContext.drawerViewModel
        val currentTab = drawerViewModel.currentDrawerTab as? FileTreeTab ?: return
        val rootPath = currentTab.root.getAbsolutePath()

        val gitViewModel = gitViewModel
        gitViewModel?.initRepository(File(rootPath)) { gitViewModel.loadRepository(rootPath) }
    }
}
