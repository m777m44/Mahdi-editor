package com.mahdi.extension.api

import com.mahdi.file.FileObject

/** Interface for handling intents in Mahdi-Editor. */
@MahdiExtensionPoint
fun interface IntentHandler {
    /**
     * Handles the given file if it matches the handler's criteria.
     *
     * @return true if the file was handled, false otherwise.
     */
    suspend fun handle(file: FileObject): Boolean
}

object IntentHandleRegistry {
    private val handlers = mutableListOf<IntentHandler>()

    @MahdiExtensionPoint
    fun register(handler: IntentHandler) {
        handlers.add(handler)
    }

    @MahdiExtensionPoint
    fun unregister(handler: IntentHandler) {
        handlers.remove(handler)
    }

    suspend fun handleIntent(file: FileObject): Boolean {
        for (handler in handlers) {
            if (handler.handle(file)) {
                return true
            }
        }
        return false
    }
}
