package com.mahdi.commands.global

import android.view.KeyEvent
import com.mahdi.commands.ActionContext
import com.mahdi.commands.GlobalCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class CommandPaletteCommand : GlobalCommand() {
    override val id: String = "global.command_palette"

    override fun getLabel(): String = strings.command_palette.getString()

    override fun execute(context: ActionContext) {
        commandContext.mainViewModel.showCommandPalette()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.command_palette)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_P, ctrl = true, shift = true)
}
