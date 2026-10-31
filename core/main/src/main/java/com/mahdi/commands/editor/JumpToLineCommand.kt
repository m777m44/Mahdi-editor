package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class JumpToLineCommand : EditorCommand() {
    override val id: String = "editor.jump_to_line"

    override fun getLabel(): String = strings.jump_to_line.getString()

    override fun execute(context: EditorActionContext) {
        context.editorTab.editorState.apply {
            showJumpToLineDialog = true
            val line = context.editor.cursor.leftLine + 1
            val column = context.editor.cursor.leftColumn + 1
            jumpToLineValue = "$line:$column"
        }
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.arrow_outward)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_G, ctrl = true)
}
