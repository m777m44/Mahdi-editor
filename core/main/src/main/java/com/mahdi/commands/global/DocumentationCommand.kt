package com.mahdi.commands.global

import android.view.KeyEvent
import com.mahdi.commands.ActionContext
import com.mahdi.commands.GlobalCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.icons.Menu_book
import com.mahdi.icons.MahdiIcons
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.utils.openUrl

class DocumentationCommand : GlobalCommand() {
    override val id: String = "global.documentation"

    override fun getLabel(): String = strings.docs.getString()

    override fun getIcon(): Icon = Icon.VectorIcon(MahdiIcons.Menu_book)

    override fun execute(context: ActionContext) {
        val url = "https://github.com/m777m44/Mahdi-editor/tree/main/docs"
        context.currentActivity.openUrl(url)
    }

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F1)
}
