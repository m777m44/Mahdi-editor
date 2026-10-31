package com.mahdi.commands.global

import android.view.KeyEvent
import com.mahdi.commands.ActionContext
import com.mahdi.commands.GlobalCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.components.GlobalDialogs
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class NewFileCommand : GlobalCommand() {
    override val id: String = "global.new_file"

    override fun getLabel(): String = strings.new_file.getString()

    override fun execute(context: ActionContext) {
        GlobalDialogs.addDialog.value = true
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.add)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_N, ctrl = true)
}
