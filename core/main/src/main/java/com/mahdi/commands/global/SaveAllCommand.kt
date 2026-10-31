package com.mahdi.commands.global

import android.view.KeyEvent
import com.mahdi.DefaultScope
import com.mahdi.commands.ActionContext
import com.mahdi.commands.GlobalCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SaveAllCommand : GlobalCommand() {
    override val id: String = "global.save_all"

    override fun getLabel(): String = strings.save_all.getString()

    override fun execute(context: ActionContext) {
        commandContext.mainViewModel.editorTabs.forEach { DefaultScope.launch(Dispatchers.IO) { it.save() } }
    }

    override fun isEnabled(): Boolean {
        return commandContext.mainViewModel.editorTabs.any { it.editorState.isDirty } || Settings.auto_save
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.save)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_S, ctrl = true, alt = true)
}
