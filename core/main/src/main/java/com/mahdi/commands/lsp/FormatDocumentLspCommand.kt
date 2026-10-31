package com.mahdi.commands.lsp

import com.mahdi.commands.LspActionContext
import com.mahdi.commands.LspCommand
import com.mahdi.commands.LspNonActionContext
import com.mahdi.icons.Icon
import com.mahdi.lsp.formatDocumentSuspend
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import kotlinx.coroutines.launch

class FormatDocumentLspCommand : LspCommand() {
    override val id: String = "lsp.format_document"

    override fun getLabel(): String = strings.format_document_lsp.getString()

    override fun execute(context: LspActionContext) {
        context.editorTab.scope.launch { formatDocumentSuspend(context.editorTab) }
    }

    override fun isEnabled(context: LspNonActionContext): Boolean {
        return context.editorTab.editorState.editable
    }

    override fun isSupported(context: LspNonActionContext): Boolean {
        return context.lspConnector.isFormattingSupported()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.auto_fix)
}
