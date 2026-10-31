package com.mahdi.commands.lsp

import com.mahdi.DefaultScope
import com.mahdi.commands.LspActionContext
import com.mahdi.commands.LspCommand
import com.mahdi.commands.LspNonActionContext
import com.mahdi.icons.Icon
import com.mahdi.lsp.renameSymbol
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class RenameSymbolCommand : LspCommand() {
    override val id: String = "lsp.rename_symbol"

    override fun getLabel(): String = strings.rename_symbol.getString()

    override fun execute(context: LspActionContext) {
        renameSymbol(DefaultScope, context.editorTab)
    }

    override fun isSupported(context: LspNonActionContext): Boolean {
        return context.lspConnector.isRenameSymbolSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.manage_search)
}
