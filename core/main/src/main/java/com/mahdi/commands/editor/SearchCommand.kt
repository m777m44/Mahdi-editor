package com.mahdi.commands.editor

import android.view.KeyEvent
import androidx.compose.ui.text.TextRange
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class SearchCommand : EditorCommand() {
    override val id: String = "editor.search"

    override fun getLabel(): String = strings.search.getString()

    override fun execute(context: EditorActionContext) {
        context.editorTab.editorState.apply {
            context.editor.getSelectedText()?.let {
                searchKeyword = searchKeyword.copy(text = it, selection = TextRange(it.length))
            }
            isSearching = true
            isReplaceShown = false
        }
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.search)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F, ctrl = true)
}
