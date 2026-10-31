package com.mahdi.commands.editor

import android.view.KeyEvent
import androidx.compose.ui.text.TextRange
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.EditorNonActionContext
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class ReplaceCommand : EditorCommand() {
    override val id: String = "editor.replace"

    override fun getLabel(): String = strings.replace.getString()

    override fun execute(context: EditorActionContext) {
        context.editorTab.editorState.apply {
            context.editor.getSelectedText()?.let {
                searchKeyword = searchKeyword.copy(text = it, selection = TextRange(it.length))
            }
            isSearching = true
            isReplaceShown = true
        }
    }

    override fun isEnabled(context: EditorNonActionContext): Boolean {
        return context.editorTab.editorState.editable
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.find_replace)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_H, ctrl = true)
}
