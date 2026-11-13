package com.mahdi.lsp

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.DefaultScope
import com.mahdi.activities.main.MainActivity
import com.mahdi.events.Events
import com.mahdi.events.LSPEvent
import com.mahdi.file.FileObject
import com.mahdi.icons.Error
import com.mahdi.icons.MahdiIcons
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import com.mahdi.tabs.editor.EditorTab
import com.mahdi.tabs.editor.applyHighlightingAndConnectLSP
import com.mahdi.theme.greenStatus
import com.mahdi.theme.yellowStatus
import com.mahdi.utils.logError
import com.mahdi.utils.logInfo
import io.github.rosemoe.sora.lsp.client.languageserver.wrapper.LanguageServerWrapper
import io.github.rosemoe.sora.lsp.editor.LspProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType

enum class LspConnectionStatus {
    NOT_RUNNING,
    RUNNING,
    STARTING,
    STOPPING,
    RESTARTING,
    CRASHED,
    TIMEOUT,
}

enum class MessageSource {
    RPC,
    LSP,
    Runtime,
    Client,
}

data class LspLogEntry(
    val source: MessageSource,
    val type: MessageType?,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
)

data class LspServerInstance(
    val server: LspServer,
    internal val lspProject: LspProject,
    val projectRoot: FileObject,
) {
    val id = "${server.id}_${projectRoot.getAbsolutePath().hashCode()}"

    private val _status = MutableStateFlow(LspConnectionStatus.NOT_RUNNING)
    val status: StateFlow<LspConnectionStatus> = _status.asStateFlow()

    private val _startupTime = MutableStateFlow(-1L)
    val startupTime: StateFlow<Long> = _startupTime.asStateFlow()

    private val _logs = MutableStateFlow<List<LspLogEntry>>(emptyList())
    val logs: StateFlow<List<LspLogEntry>> = _logs.asStateFlow()

    private val _errorCount = MutableStateFlow(0)
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    fun addLog(messageParams: MessageParams) {
        addLog(
            LspLogEntry(
                type = messageParams.type,
                message = messageParams.message,
                source = MessageSource.LSP,
            )
        )
    }

    fun addLog(logTraceParams: LogTraceParams) {
        addLog(
            LspLogEntry(
                type = MessageType.Debug,
                message = logTraceParams.message,
                source = MessageSource.LSP,
            )
        )
        logTraceParams.verbose?.let {
            addLog(
                LspLogEntry(
                    type = MessageType.Debug,
                    message = it,
                    source = MessageSource.LSP,
                )
            )
        }
    }

    fun addLog(lspLogEntry: LspLogEntry) {
        if (lspLogEntry.type == MessageType.Error && lspLogEntry.source != MessageSource.LSP) {
            _errorCount.value += 1
        }

        _logs.update { it.plus(lspLogEntry).takeLast(Settings.lsp_log_limit) }

        DefaultScope.launch { Events.publish(LSPEvent.LogEntryWritten(this@LspServerInstance, lspLogEntry)) }
    }

    fun getLspLogs() = _logs.value

    fun setStatus(value: LspConnectionStatus) {
        _status.value = value
    }

    fun resetErrorCount() {
        _errorCount.value = 0
    }

    fun setStartupTime(value: Long) {
        _startupTime.value = value
    }

    companion object {
        /** أقصى انتظار لنجاح الإحياء الصامت (٣ ثوانٍ — no UI ينتظرها). */
        private const val SILENT_START_TIMEOUT_MS = 3_000L

        /** دقّة متابعة الحالة أثناء الإحياء. */
        private const val SILENT_START_POLL_MS = 250L
    }

    fun getWrapper(): LanguageServerWrapper? {
        return server.supportedExtensions.firstOrNull()?.let {
            lspProject.getLanguageServerWrapper(it, server.serverName)
        }
            ?: run {
                _errorCount.value += 1
                addLog(
                    LspLogEntry(
                        MessageSource.Client,
                        MessageType.Error,
                        "Language server instance not found...",
                    )
                )
                return null
            }
    }

    /** Stops this language server instance */
    suspend fun stop() {
        logInfo("Stopping LSP instance: $id for server: ${server.serverName}")
        withContext(Dispatchers.IO) {
            addLog(
                LspLogEntry(
                    MessageSource.Client,
                    MessageType.Info,
                    "User stopped language server instance...",
                )
            )
            val wrapper = getWrapper() ?: return@withContext
            DefinitionPrevention.register(lspProject, server)
            try {
                wrapper.stop(true)
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    /**
     * إيقاف مؤقت لتوفير الذاكرة (9.1.0) — يُستدعى من [LspMemoryGuard] وحده.
     *
     * يختلف عن [stop] في نقطة واحدة حاسمة: **لا يُسجّل الخادم في
     * [DefinitionPrevention]**. التسجيل هناك يعني «المستخدم أوقفه يدويًا»، فيُحذف
     * تعريف الخادم من المشروع ولا يعود حتى يُعاد تسجيله يدويًا — وهو عكس المطلوب
     * تمامًا: نحن نريد إيقافًا مؤقتًا تعود بعده الخوادم وحدها.
     */
    suspend fun pauseForMemory() {
        withContext(Dispatchers.IO) {
            val wrapper = getWrapper() ?: return@withContext
            addLog(
                LspLogEntry(
                    MessageSource.Client,
                    MessageType.Info,
                    "Language server paused to free memory for a script run",
                )
            )
            runCatching { wrapper.stop(true) }
                .onFailure { logError(it, "pause ${server.serverName}") }
            setStatus(LspConnectionStatus.NOT_RUNNING)
        }
    }

    /** Restarts this language server instance */
    suspend fun restart() {
        logInfo("Restarting LSP instance: $id for server: ${server.serverName}")
        withContext(Dispatchers.IO) {
            addLog(
                LspLogEntry(
                    MessageSource.Client,
                    MessageType.Info,
                    "User restarted language server instance...",
                )
            )
            val wrapper = getWrapper() ?: return@withContext
            try {
                wrapper.restartAndReconnect()
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    /**
     * إحياء صامت (9.0.0) — يُستدعى من [LspManager] وحده عند انهيار الخادم.
     *
     * لا يكتب «المستخدم أعاد التشغيل» في السجل (المستخدم لم يفعل شيئًا)، ولا يمسح
     * عدّاد الأخطاء، ولا يُظهر أي واجهة. يعيد `true` إذا صار الخادم يعمل.
     */
    suspend fun startSilently(): Boolean =
        withContext(Dispatchers.IO) {
            val wrapper = getWrapper() ?: return@withContext false
            runCatching {
                    // `restartAndReconnect` آمنة في كل الحالات: تُعيد الإقلاع إن كان
                    // الخادم ميتًا، وتُعيد الربط إن كان حيًّا.
                    wrapper.restartAndReconnect()
                    DefinitionPrevention.unregister(lspProject, server)
                }
                .onFailure { logError(it, "silent start ${server.serverName}") }

            addLog(
                LspLogEntry(
                    MessageSource.Client,
                    MessageType.Info,
                    "Silent restart of language server instance",
                )
            )

            // إعادة ربط المحرّرات المفتوحة بهدوء، فلا يبقى ملف بلا إكمال
            val reconnected = mutableListOf<EditorTab>()
            runCatching { reconnect(reconnected) }

            // نجاح الإحياء = عودة الحالة إلى RUNNING. لا نكتفي بوجود المُغلِّف
            // (فهو موجود حتى وهو منهار)، وإلا صار العدّاد يُصفَّر كل مرة فيدور
            // التطبيق في إعادة تشغيل لا تنتهي بلا أن يبلغ حدّ المحاولات.
            var waited = 0L
            while (waited < SILENT_START_TIMEOUT_MS && status.value != LspConnectionStatus.RUNNING) {
                delay(SILENT_START_POLL_MS)
                waited += SILENT_START_POLL_MS
            }
            status.value == LspConnectionStatus.RUNNING
        }

    /**
     * Starts this language server instance
     *
     * @return List of editors that were reconnected
     */
    suspend fun start(quiet: Boolean = false): List<EditorTab> {
        logInfo("Starting LSP instance: $id for server: ${server.serverName}")
        return withContext(Dispatchers.IO) {
            addLog(
                LspLogEntry(
                    MessageSource.Client,
                    MessageType.Info,
                    // 9.1.0: الاستئناف بعد «حارس الذاكرة» ليس فعلًا من المستخدم،
                    // فلا يُكتب في السجلّ ما لم يفعله.
                    if (quiet) {
                        "Language server resumed after the memory guard"
                    } else {
                        "User started language server instance..."
                    },
                )
            )
            val wrapper = getWrapper() ?: return@withContext emptyList()
            _errorCount.value = 0
            DefinitionPrevention.unregister(lspProject, server)
            try {
                wrapper.start()
            } catch (e: Exception) {
                logError(e)
            }

            val reconnectedEditors = mutableListOf<EditorTab>()
            reconnect(reconnectedEditors)
            return@withContext reconnectedEditors
        }
    }

    private fun reconnect(reconnectedEditors: MutableList<EditorTab>) {
        val editors = lspProject.getEditors()
        val filteredEditors = editors.filter { server.supportedExtensions.contains(it.fileExt) }
        filteredEditors.forEach { editor ->
            val matchingEditorTab =
                MainActivity.instance?.viewModelOrNull()?.run {
                    tabs.filterIsInstance<EditorTab>().find { it.editorState.editor.get() == editor.editor }
                } ?: return@forEach
            matchingEditorTab.applyHighlightingAndConnectLSP()
            reconnectedEditors.add(matchingEditorTab)
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            val wrapper = getWrapper() ?: return@withContext
            server.supportedExtensions.forEach {
                lspProject.removeServerDefinition(
                    it,
                    server.serverName,
                )
            }
            lspProject.getEditors().forEach { wrapper.disconnect(it) }
        }
    }
}

@Composable
fun LspServerInstance.getStatusColor(): Color? {
    val status by this.status.collectAsStateWithLifecycle()
    return when (status) {
        LspConnectionStatus.CRASHED,
        LspConnectionStatus.TIMEOUT -> {
            MaterialTheme.colorScheme.error
        }

        LspConnectionStatus.STARTING,
        LspConnectionStatus.RESTARTING,
        LspConnectionStatus.STOPPING -> {
            MaterialTheme.colorScheme.yellowStatus
        }

        LspConnectionStatus.RUNNING -> {
            MaterialTheme.colorScheme.greenStatus
        }

        else -> null
    }
}

@Composable
fun LspServerInstance.getStatusText(): String {
    val status by this.status.collectAsStateWithLifecycle()
    return when {
        status == LspConnectionStatus.CRASHED -> stringResource(strings.status_crashed)
        status == LspConnectionStatus.TIMEOUT -> stringResource(strings.status_timeout)
        status == LspConnectionStatus.STARTING -> stringResource(strings.status_starting)
        status == LspConnectionStatus.RESTARTING -> stringResource(strings.status_restarting)
        status == LspConnectionStatus.RUNNING -> stringResource(strings.status_running)
        status == LspConnectionStatus.STOPPING -> stringResource(strings.status_stopping)
        DefinitionPrevention.isServerPrevented(
            lspProject,
            server,
        ) -> stringResource(strings.status_not_running_forced)

        else -> stringResource(strings.status_not_running)
    }
}

@Composable
fun LspServerInstance.StatusIcon() {
    val status by this.status.collectAsStateWithLifecycle()
    when (status) {
        LspConnectionStatus.CRASHED,
        LspConnectionStatus.TIMEOUT -> {
            Icon(
                imageVector = MahdiIcons.Error,
                contentDescription = stringResource(strings.error),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        }
        LspConnectionStatus.STARTING,
        LspConnectionStatus.RESTARTING,
        LspConnectionStatus.STOPPING -> {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        LspConnectionStatus.RUNNING -> {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(strings.status_running),
                tint = MaterialTheme.colorScheme.greenStatus,
                modifier = Modifier.size(32.dp),
            )
        }
        LspConnectionStatus.NOT_RUNNING -> {}
    }
}
