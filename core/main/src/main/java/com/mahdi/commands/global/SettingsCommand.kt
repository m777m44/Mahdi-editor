package com.mahdi.commands.global

import android.content.Intent
import android.view.KeyEvent
import com.mahdi.activities.settings.SettingsActivity
import com.mahdi.commands.ActionContext
import com.mahdi.commands.GlobalCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class SettingsCommand : GlobalCommand() {
    override val id: String = "global.settings"

    override fun getLabel(): String = strings.settings.getString()

    override fun execute(context: ActionContext) {
        val activity = context.currentActivity
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.settings)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_COMMA, ctrl = true)
}
