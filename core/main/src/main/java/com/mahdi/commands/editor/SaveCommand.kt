package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.DefaultScope
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.EditorNonActionContext
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SaveCommand : EditorCommand() {
    override val id: String = "editor.save"

    override fun getLabel(): String = strings.save.getString()

    override fun execute(context: EditorActionContext) {
        DefaultScope.launch(Dispatchers.IO) { context.editorTab.save() }
    }

    override fun isEnabled(context: EditorNonActionContext): Boolean {
        return !context.editorTab.isReadOnly && (context.editorTab.editorState.isDirty || Settings.auto_save)
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.save)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_S, ctrl = true)
}
