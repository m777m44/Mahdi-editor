package com.mahdi.commands.global

import android.view.KeyEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.mahdi.DefaultScope
import com.mahdi.activities.main.ui.drawerStateRef
import com.mahdi.commands.ActionContext
import com.mahdi.commands.GlobalCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import kotlinx.coroutines.launch

class BrowseProjectCommand : GlobalCommand() {
    override val id: String = "global.browse_project"

    override fun getLabel(): String = strings.browse_project.getString()

    override fun getIcon(): Icon = Icon.VectorIcon(Icons.Rounded.Menu)

    override fun execute(context: ActionContext) {
        DefaultScope.launch(AndroidUiDispatcher.Main) { drawerStateRef.get()?.open() }
    }

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_B, ctrl = true)
}
