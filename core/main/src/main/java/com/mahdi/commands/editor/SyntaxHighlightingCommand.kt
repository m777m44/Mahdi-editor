package com.mahdi.commands.editor

import com.mahdi.commands.Command
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.file.FileTypeManager
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class SyntaxHighlightingCommand : EditorCommand() {
    override val id: String = "editor.syntax_highlighting"

    override fun getLabel(): String = strings.highlighting.getString()

    override fun execute(context: EditorActionContext) {}

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.edit_note)

    override val childCommands: List<Command> by lazy {
        FileTypeManager.allTypes()
            .filter { it.textmateScope != null }
            .map { fileType ->
                object : EditorCommand() {
                    override val id: String = "editor.syntax_highlighting.${fileType.name.lowercase()}"

                    override fun getLabel(): String = fileType.title

                    override fun execute(context: EditorActionContext) {
                        context.editorTab.editorState.textmateScope = fileType.textmateScope!!
                    }

                    override fun getIcon(): Icon = fileType.getResolvedIcon()
                }
            }
    }

    override fun getChildSearchPlaceholder(): String = strings.select_language.getString()
}
