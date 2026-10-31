package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.EditorNonActionContext
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.tabs.editor.EditorTab

class ToggleReadOnlyCommand : EditorCommand() {
    override val id: String = "editor.editable"

    override fun getLabel(): String {
        val editorTab = commandContext.mainViewModel.currentTab as? EditorTab
        return if (editorTab?.editorState?.editable == true) {
            strings.read_mode.getString()
        } else {
            strings.edit_mode.getString()
        }
    }

    override fun execute(context: EditorActionContext) {
        val editorState = context.editorTab.editorState
        context.editorTab.removeNotice("binary_file")
        editorState.editable = !editorState.editable
    }

    override fun isEnabled(context: EditorNonActionContext): Boolean {
        return !context.editorTab.isReadOnly
    }

    override fun getIcon(): Icon {
        val editorTab = commandContext.mainViewModel.currentTab as? EditorTab
        return if (editorTab?.editorState?.editable == true) {
            Icon.ResourceIcon(drawables.lock)
        } else {
            Icon.ResourceIcon(drawables.edit)
        }
    }

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_E, ctrl = true)
}
