package com.mahdi.commands.editor

import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.EditorNonActionContext
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class SortLinesAscendingCommand : EditorCommand() {
    override val id = "editor.sort_lines_ascending"

    override fun getLabel() = strings.sort_lines_ascending.getString()

    override fun execute(context: EditorActionContext) {
        val editor = context.editor

        val cursor = editor.cursor

        var startLine: Int
        var endLine: Int
        if (!cursor.isSelected) {
            startLine = 0
            endLine = editor.text.lineCount - 1
        } else {
            startLine = minOf(cursor.leftLine, cursor.rightLine)
            endLine = maxOf(cursor.leftLine, cursor.rightLine)
        }
        val endLineColumn = editor.text.getColumnCount(endLine)

        val lines = editor.text.subContent(startLine, 0, endLine, endLineColumn).lines()
        val ascendingLines = lines.sorted().joinToString("\n")

        editor.text.replace(startLine, 0, endLine, endLineColumn, ascendingLines)
    }

    override fun isEnabled(context: EditorNonActionContext): Boolean {
        return context.editorTab.editorState.editable
    }

    override fun getIcon() = Icon.ResourceIcon(drawables.sort_by_alphabet)
}
