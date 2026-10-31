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

class SaveAsCommand : EditorCommand() {
    override val id: String = "editor.save_as"

    override fun getLabel(): String = strings.save_as.getString()

    override fun execute(context: EditorActionContext) {
        context.editorTab.saveAs()
    }

    override fun isEnabled(context: EditorNonActionContext): Boolean {
        return !context.editorTab.isReadOnly
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.save)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_S, ctrl = true, shift = true)
}
