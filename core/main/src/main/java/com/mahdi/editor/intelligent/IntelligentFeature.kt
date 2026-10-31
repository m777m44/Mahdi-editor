package com.mahdi.editor.intelligent

import com.mahdi.editor.Editor
import com.mahdi.extension.api.MahdiExtensionPoint
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.event.KeyBindingEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object IntelligentFeatureRegistry {
    val builtInFeatures = listOf(AutoCloseTag, BulletContinuation)

    private val _mutableFeatures = MutableStateFlow<List<IntelligentFeature>>(emptyList())
    val extensionFeatures: List<IntelligentFeature>
        get() = _mutableFeatures.value

    val allFeatures: List<IntelligentFeature>
        get() = builtInFeatures + _mutableFeatures.value

    @MahdiExtensionPoint
    fun registerFeature(feature: IntelligentFeature) {
        _mutableFeatures.update { list -> if (list.contains(feature)) list else list + feature }
    }

    @MahdiExtensionPoint
    fun unregisterFeature(feature: IntelligentFeature) {
        _mutableFeatures.update { it - feature }
    }
}

abstract class IntelligentFeature {
    abstract val id: String
    abstract val supportedExtensions: List<String>
    open val triggerCharacters: List<Char> = emptyList()

    open fun handleInsertChar(triggerCharacter: Char, editor: Editor) {}

    open fun handleDeleteChar(triggerCharacter: Char, editor: Editor) {}

    open fun handleInsert(editor: Editor) {}

    open fun handleDelete(editor: Editor) {}

    open fun handleKeyEvent(event: EditorKeyEvent, editor: Editor) {}

    open fun handleKeyBindingEvent(event: KeyBindingEvent, editor: Editor) {}

    open fun isEnabled(): Boolean = true
}
