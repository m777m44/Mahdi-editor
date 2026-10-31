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

class CutCommand : EditorCommand() {
    override val id: String = "editor.cut"

    override fun getLabel(): String = strings.cut.getString()

    override fun execute(context: EditorActionContext) {
        context.editor.cutText()
    }

    override fun isEnabled(context: EditorNonActionContext): Boolean {
        return context.editorTab.editorState.editable
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.cut)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_X, ctrl = true)
}
