package com.mahdi.ai.command

import com.mahdi.activities.main.MainActivity
import com.mahdi.ai.icons.SparklesTiny
import com.mahdi.ai.tab.AiTab
import com.mahdi.commands.ActionContext
import com.mahdi.commands.GlobalCommand
import com.mahdi.icons.Icon
import com.mahdi.resources.getString
import com.mahdi.resources.strings

class AIChatCommand : GlobalCommand() {
    override val id: String = "global.ai_chat"

    override fun getLabel(): String = strings.ai_chat_command_label.getString()

    override fun execute(context: ActionContext) {
        MainActivity.instance?.viewModel?.tabManager?.addTab(AiTab(), switchToTab = true)
    }

    override fun getIcon(): Icon = Icon.VectorIcon(SparklesTiny)

    override fun isSupported(): Boolean {
        return MainActivity.instance?.viewModel?.currentVisibleTab !is AiTab
    }
}
