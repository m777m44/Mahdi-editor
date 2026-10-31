package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.DefaultScope
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.events.EditorTabEvent
import com.mahdi.events.Events
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.tabs.editor.EditorTab
import com.mahdi.utils.dialogRes
import kotlinx.coroutines.launch

class RefreshCommand : EditorCommand() {
    override val id: String = "editor.refresh"

    override fun getLabel(): String = strings.refresh.getString()

    override fun execute(context: EditorActionContext) {
        val currentTab = context.editorTab
        if (currentTab.editorState.isDirty) {
            dialogRes(
                activity = context.currentActivity,
                title = strings.attention.getString(),
                msg = strings.ask_refresh.getString(),
                okRes = strings.refresh,
                onCancel = {},
                onOk = {
                    currentTab.refresh()
                    publishEvent(currentTab)
                },
            )
        } else {
            currentTab.refresh()
            publishEvent(currentTab)
        }
    }

    private fun publishEvent(currentTab: EditorTab) {
        DefaultScope.launch { Events.publish(EditorTabEvent.Refreshed(currentTab)) }
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.refresh)

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_R, ctrl = true, shift = true)
}
