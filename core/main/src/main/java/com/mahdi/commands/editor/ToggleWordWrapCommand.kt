package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class ToggleWordWrapCommand : EditorCommand() {
    override val id: String = "editor.toggle_word_wrap"

    override fun getLabel(): String = strings.toggle_word_wrap.getString()

    override fun execute(context: EditorActionContext) {
        val editor = context.editor
        editor.setWordwrap(!editor.isWordwrap, true, true)
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.edit_note)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_Z, alt = true)
}
