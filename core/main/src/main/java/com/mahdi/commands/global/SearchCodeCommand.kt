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

class SearchCodeCommand : GlobalCommand() {
    override val id: String = "global.search_code"

    override fun getLabel(): String = strings.search_code.getString()

    override fun execute(context: ActionContext) {
        GlobalDialogs.codeSearchDialog.value = true
    }

    override fun isEnabled(): Boolean {
        return commandContext.drawerViewModel.currentDrawerTab != null
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.search)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_F, ctrl = true, shift = true)
}
