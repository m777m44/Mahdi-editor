package com.mahdi.ai.chat

import com.mahdi.ai.model.AiChatMessage
import com.mahdi.ai.model.ToolCallStatus

internal fun AiChatMessage.stopped(): AiChatMessage =
    copy(isStreaming = false, children = children.map { it.stopped() })

internal val ToolCallStatus.isInProgress: Boolean
    get() =
        this == ToolCallStatus.Running ||
            this == ToolCallStatus.AwaitingApproval ||
            this == ToolCallStatus.AwaitingInput
