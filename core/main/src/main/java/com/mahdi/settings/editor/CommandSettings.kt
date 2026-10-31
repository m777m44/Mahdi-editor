package com.mahdi.settings.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.mahdi.commands.ActionContext
import com.mahdi.commands.Command
import com.mahdi.commands.CommandPalette
import com.mahdi.commands.CommandProvider
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

@Composable
fun CommandSelectionDialog(
    commandIds: SnapshotStateList<String>,
    saveOrder: (SnapshotStateList<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogCommands =
        CommandProvider.commandList.value.map { command ->
            val existingCommands = command.childCommands
            val patchedChildCommands =
                if (existingCommands.isEmpty()) {
                    emptyList()
                } else {
                    buildAddActions(command, commandIds, existingCommands, saveOrder)
                }

            val hasChildCommands = patchedChildCommands.isNotEmpty()
            command.copy(
                childCommands = patchedChildCommands,
                execute = {
                    commandIds.add(command.id)
                    saveOrder(commandIds)
                },
                isSupported = { true },
                isEnabled = { !commandIds.contains(command.id) || hasChildCommands },
            )
        }

    CommandPalette(progress = 1f, commands = dialogCommands, lastUsedCommand = null) { onDismiss() }
}

private fun buildAddActions(
    command: Command,
    commandIds: SnapshotStateList<String>,
    existingCommands: List<Command>,
    saveOrder: (SnapshotStateList<String>) -> Unit,
): List<Command> = buildList {
    add(
        object : Command() {
            override val id: String = command.id

            override fun getLabel(): String = strings.add_parent_command.getString()

            override fun execute(context: ActionContext) {
                commandIds.add(command.id)
                saveOrder(commandIds)
            }

            override val sectionId: Int = 0

            override fun isEnabled(): Boolean = !commandIds.contains(command.id)

            override fun getIcon(): Icon = Icon.ResourceIcon(drawables.arrow_outward)
        }
    )
    addAll(
        existingCommands.map { command ->
            command.copy(
                execute = {
                    commandIds.add(command.id)
                    saveOrder(commandIds)
                },
                isEnabled = { !commandIds.contains(command.id) },
                isSupported = { true },
                sectionId = command.sectionId + 1,
            )
        }
    )
}
