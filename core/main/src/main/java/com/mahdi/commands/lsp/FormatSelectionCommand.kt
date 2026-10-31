package com.mahdi.commands.lsp

import com.mahdi.DefaultScope
import com.mahdi.commands.LspActionContext
import com.mahdi.commands.LspCommand
import com.mahdi.commands.LspNonActionContext
import com.mahdi.icons.Icon
import com.mahdi.lsp.formatDocumentRange
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class FormatSelectionCommand : LspCommand() {
    override val id: String = "lsp.format_selection"

    override fun getLabel(): String = strings.format_selection.getString()

    override fun execute(context: LspActionContext) {
        formatDocumentRange(DefaultScope, context.editorTab)
    }

    override fun isEnabled(context: LspNonActionContext): Boolean {
        return context.editorTab.editorState.editable
    }

    override fun isSupported(context: LspNonActionContext): Boolean {
        return context.lspConnector.isRangeFormattingSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.auto_fix)
}
