package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.EditorNonActionContext
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class RedoCommand : EditorCommand() {
    override val id: String = "editor.redo"

    override val repeatOnHold: Boolean = true

    override fun getLabel(): String = strings.redo.getString()

    override fun execute(context: EditorActionContext) {
        val editor = context.editor
        if (editor.canRedo()) editor.redo()
        context.editorTab.editorState.updateUndoRedo()
    }

    override fun isEnabled(context: EditorNonActionContext): Boolean {
        val editorState = context.editorTab.editorState
        return editorState.editable && editorState.canRedo
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.redo)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_Y, ctrl = true)
}
