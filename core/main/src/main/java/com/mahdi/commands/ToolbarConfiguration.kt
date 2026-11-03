package com.mahdi.commands

import com.mahdi.extension.api.MahdiExtensionPoint
import com.mahdi.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ToolbarConfiguration {
    /**
     * الشريط الافتراضي قبل 4.1.0 — يبقى محفوظًا للترحيل فقط، حتى لا نلمس شريطًا
     * عدّله المستخدم بنفسه.
     */
    const val LEGACY_DEFAULT_EDITOR_TOOLBAR_COMMANDS =
        "editor.undo|editor.redo|editor.save|editor.run|global.new_file|editor.editable|editor.search|editor.refresh|global.terminal|global.settings"

    /**
     * 4.1.0: أُضيف `editor.encoding` — قائمة الترميز صارت في متناول اليد من الشريط
     * (أو من قائمة «المزيد» حين يضيق المكان) بدل أن تبقى مدفونة في الإعدادات العامة.
     */
    const val DEFAULT_EDITOR_TOOLBAR_COMMANDS =
        "editor.undo|editor.redo|editor.save|editor.run|global.new_file|editor.editable|editor.search|editor.encoding|editor.refresh|global.terminal|global.settings"

    /**
     * ترحيل لمرّة واحدة: من كان على الشريط الافتراضي القديم بلا تعديل يحصل على زرّ
     * «الترميز»، ومن عدّل شريطه يبقى كما هو تمامًا.
     */
    fun migrateEncodingCommand() {
        val current = Settings.action_items
        if (current.contains("editor.encoding")) return
        if (current != LEGACY_DEFAULT_EDITOR_TOOLBAR_COMMANDS) return
        Settings.action_items =
            current.split("|").toMutableList().apply {
                val index = indexOf("editor.refresh")
                if (index >= 0) add(index, "editor.encoding") else add("editor.encoding")
            }.joinToString("|")
    }

    val editorCommands: List<Command>
        get() = Settings.action_items.split("|").mapNotNull { CommandProvider.getForId(it) }

    @MahdiExtensionPoint
    fun addEditorToolbarCommand(commandId: String, index: Int? = null) {
        val items = Settings.action_items.split("|").toMutableList()

        if (commandId in items) {
            if (index == null) return

            items.remove(commandId)
            items.add(index.coerceIn(0, items.size), commandId)
            Settings.action_items = items.joinToString("|")
            return
        }

        if (index != null) {
            items.add(index.coerceIn(0, items.size), commandId)
        } else {
            items.add(commandId)
        }

        Settings.action_items = items.joinToString("|")
    }

    @MahdiExtensionPoint
    fun addEditorToolbarCommand(command: Command, index: Int? = null) {
        addEditorToolbarCommand(command.id, index)
    }

    @MahdiExtensionPoint
    fun removeEditorToolbarCommand(commandId: String) {
        val items = Settings.action_items.split("|").toMutableList()

        if (!items.remove(commandId)) {
            return
        }

        Settings.action_items = items.joinToString("|")
    }

    @MahdiExtensionPoint
    fun removeEditorToolbarCommand(command: Command) {
        removeEditorToolbarCommand(command.id)
    }

    private val _globalCommands =
        MutableStateFlow<List<Command>>(
            listOf(
                CommandProvider.NewFileCommand,
                CommandProvider.SettingsCommand,
            )
        )

    val globalCommands: StateFlow<List<Command>> = _globalCommands.asStateFlow()

    @MahdiExtensionPoint
    fun addGlobalToolbarCommand(command: Command, index: Int? = null) {
        _globalCommands.update { current ->
            val existingIndex = current.indexOf(command)

            val list =
                if (existingIndex != -1) {
                    current.toMutableList().apply { removeAt(existingIndex) }
                } else {
                    current.toMutableList()
                }

            val insertIndex =
                when {
                    index != null -> index.coerceIn(0, list.size)
                    existingIndex != -1 -> existingIndex.coerceIn(0, list.size)
                    else -> list.size
                }

            list.add(insertIndex, command)
            list
        }
    }

    @MahdiExtensionPoint
    fun addGlobalToolbarCommand(commandId: String, index: Int? = null) {
        val command = CommandProvider.getForId(commandId) ?: return
        addGlobalToolbarCommand(command, index)
    }

    @MahdiExtensionPoint
    fun removeGlobalToolbarCommand(command: Command) {
        _globalCommands.update { it - command }
    }

    @MahdiExtensionPoint
    fun removeGlobalToolbarCommand(commandId: String) {
        val command = CommandProvider.getForId(commandId) ?: return
        removeGlobalToolbarCommand(command)
    }
}
