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

class UndoCommand : EditorCommand() {
    override val id: String = "editor.undo"

    override val repeatOnHold: Boolean = true

    override fun getLabel(): String = strings.undo.getString()

    override fun execute(context: EditorActionContext) {
        val editor = context.editor
        if (editor.canUndo()) editor.undo()
        context.editorTab.editorState.updateUndoRedo()
    }

    override fun isEnabled(context: EditorNonActionContext): Boolean {
        val editorState = context.editorTab.editorState
        return editorState.editable && editorState.canUndo
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.undo)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_Z, ctrl = true)
}
