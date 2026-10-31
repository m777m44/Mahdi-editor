package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class CopyCommand : EditorCommand() {
    override val id: String = "editor.copy"

    override fun getLabel(): String = strings.copy.getString()

    override fun execute(context: EditorActionContext) {
        context.editor.copyText()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.copy)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_C, ctrl = true)
}
