package com.mahdi.commands.lsp

import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.tabs.editor.EditorTab

class FormatDocumentCommand : EditorCommand() {
    override val id: String = "editor.format_document"

    override fun getLabel(): String = strings.format_document.getString()

    override fun execute(context: EditorActionContext) {
        context.editorTab.registerTask(EditorTab.FORMAT_DOCUMENT_TASK_ID)
        context.editor.formatCodeAsync()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.auto_fix)
}
