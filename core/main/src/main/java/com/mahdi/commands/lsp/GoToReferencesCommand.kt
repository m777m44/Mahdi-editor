package com.mahdi.commands.lsp

import com.mahdi.DefaultScope
import com.mahdi.commands.LspActionContext
import com.mahdi.commands.LspCommand
import com.mahdi.commands.LspNonActionContext
import com.mahdi.icons.Icon
import com.mahdi.lsp.goToReferences
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class GoToReferencesCommand : LspCommand() {
    override val id: String = "lsp.go_to_references"

    override fun getLabel(): String = strings.go_to_references.getString()

    override fun execute(context: LspActionContext) {
        goToReferences(
            scope = DefaultScope,
            context = context.currentActivity,
            viewModel = commandContext.mainViewModel,
            editorTab = context.editorTab,
        )
    }

    override fun isSupported(context: LspNonActionContext): Boolean {
        return context.lspConnector.isGoToReferencesSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.manage_search)
}
