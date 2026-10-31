package com.mahdi.commands.global

import android.content.Intent
import android.view.KeyEvent
import com.mahdi.activities.terminal.Terminal
import com.mahdi.commands.ActionContext
import com.mahdi.commands.GlobalCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.feature.FeatureRegistry
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

object TerminalCommand : GlobalCommand() {
    override val id: String = "global.terminal"

    override fun getLabel(): String = strings.terminal.getString()

    override fun execute(context: ActionContext) {
        val activity = context.currentActivity
        val intent = Intent(activity, Terminal::class.java)
        activity.startActivity(intent)
    }

    override fun isSupported(): Boolean = FeatureRegistry.isEnabled("feature_terminal")

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.terminal)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_J, ctrl = true)
}
