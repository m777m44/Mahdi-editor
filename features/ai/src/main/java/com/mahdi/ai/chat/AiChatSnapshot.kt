package com.mahdi.ai.chat

import com.mahdi.ai.model.AiChatMessage
import com.mahdi.ai.model.AiTodo
import com.mahdi.ai.model.AiTurn
import com.mahdi.ai.model.ToolCallStatus

data class AiChatSnapshot(
    val messages: List<AiChatMessage> = emptyList(),
    val history: List<AiTurn> = emptyList(),
    val goal: String? = null,
    val todos: List<AiTodo> = emptyList(),
    val draft: String = "",
    val nextId: Long = 0L,
    val showReasoning: Boolean = false,
) {
    /** The snapshot with in-flight work settled: a run never survives the process. */
    fun settled(): AiChatSnapshot = copy(messages = messages.map { it.settled() })
}

private fun AiChatMessage.settled(): AiChatMessage =
    copy(
        isStreaming = false,
        toolStatus = if (toolStatus?.isInProgress == true) ToolCallStatus.Interrupted else toolStatus,
        children = children.map { it.settled() },
    )
