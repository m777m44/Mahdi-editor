package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class SelectWordCommand : EditorCommand() {
    override val id: String = "editor.select_word"

    override fun getLabel(): String = strings.select_word.getString()

    override fun execute(context: EditorActionContext) {
        context.editor.selectCurrentWord()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.select)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_W, ctrl = true)
}
