package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class SelectAllCommand : EditorCommand() {
    override val id: String = "editor.select_all"

    override fun getLabel(): String = strings.select_all.getString()

    override fun execute(context: EditorActionContext) {
        context.editor.selectAll()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.select_all)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_A, ctrl = true)
}
