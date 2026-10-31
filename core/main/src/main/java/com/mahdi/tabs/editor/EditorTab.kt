package com.mahdi.tabs.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.activities.main.MainActivity
import com.mahdi.activities.main.MainViewModel
import com.mahdi.activities.main.session.DocumentState
import com.mahdi.activities.main.session.DocumentStateDatabase
import com.mahdi.activities.main.session.EditorTabState
import com.mahdi.activities.main.session.TabState
import com.mahdi.activities.main.ui.searchViewModel
import com.mahdi.color.ColorPicker
import com.mahdi.components.SingleInputDialog
import com.mahdi.editor.intelligent.IntelligentFeatureRegistry
import com.mahdi.events.EditorTabEvent
import com.mahdi.events.Events
import com.mahdi.extension.api.MahdiExtensionPoint
import com.mahdi.encoding.EncodingManager
import com.mahdi.file.FileObject
import com.mahdi.file.FileTypeManager
import com.mahdi.lsp.LspConnector
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.search.EditorSearchPanel
import com.mahdi.search.FindingsDialog
import com.mahdi.motivation.Motivation
import com.mahdi.settings.Settings
import com.mahdi.settings.editor.refreshEditorSettings
import com.mahdi.tabs.base.Tab
import com.mahdi.utils.errorDialog
import com.mahdi.utils.hasBinaryChars
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.ec4j.core.Cache.Caches
import org.ec4j.core.EditorConfigLoader
import org.ec4j.core.Resource
import org.ec4j.core.ResourcePath
import org.ec4j.core.ResourceProperties
import org.ec4j.core.ResourcePropertiesService
import org.ec4j.core.model.PropertyType

@OptIn(DelicateCoroutinesApi::class)
open class EditorTab(
    override var file: FileObject?,
    override var projectRoot: FileObject?,
    override var scopeRoot: FileObject? = null,
    val viewModel: MainViewModel,
    isReadOnly: Boolean = false,
    private val customTitle: String? = null,
    val fallbackExtension: String = "txt",
    initialContent: Content? = null,
) : Tab() {

    var isReadOnly: Boolean = isReadOnly
        private set

    val isTemp: Boolean
        get() = file == null && !isReadOnly

    private var autoSaveJob: Job? = null

    /** مهمّة حفظ موضع المؤشر/التمرير المؤجّل (2.5.0 — debounce 500ms). */
    private var documentStateJob: Job? = null
    private var activeTasks = mutableStateSetOf<String>()

    private var charset = Charset.forName(Settings.encoding)
    var lspConnector: LspConnector? = null

    /**
     * يقرأ محتوى الملف بالترميز الصحيح:
     * - إن كان «كشف الترميز تلقائيًا» مفعّلًا، يُخمَّن الترميز من بصمة الملف ثم يُعتمد للملف كله.
     * - وإلا يُستخدم الترميز الافتراضي من الإعدادات، ويظل `.editorconfig` قادرًا على تجاوزه.
     */
    private suspend fun readContent(file: FileObject): Content {
        if (!Settings.encoding_auto_detect) {
            return file.getInputStream().use { ContentIO.createFrom(it, charset) }
        }
        val bytes = file.getInputStream().use { it.readBytes() }
        charset = EncodingManager.detect(bytes)
        return ContentIO.createFrom(ByteArrayInputStream(bytes), charset)
    }

    override val icon: ImageVector
        get() = Icons.Outlined.Edit

    override val name: String
        get() = strings.editor.getString()

    override val floatPadding: Dp
        get() {
            val extraKeysPadding = if (Settings.split_extra_keys) 88.dp else 48.dp
            return if (Settings.show_extra_keys) extraKeysPadding else 0.dp
        }

    val scope = CoroutineScope(Dispatchers.Default)

    override var title by mutableStateOf(customTitle ?: file?.getName() ?: strings.temp_file.getString())

    val editorState by mutableStateOf(CodeEditorState(initialContent))

    override fun onTabAdded() {
        scope.launch(Dispatchers.Main) { restoreDocumentState() }
    }

    override fun onTabRemoved() {
        saveDocumentState()
        autoSaveJob?.cancel()
        scope.cancel()
        editorState.content = null
        editorState.editor.get()?.apply {
            setText("")
            release()
        }
        GlobalScope.launch(Dispatchers.IO) { lspConnector?.disconnect() }
    }

    override fun onTabSelected() {
        editorState.editor.get()?.apply {
            requestFocus()
            requestFocusFromTouch()
        }
    }

    override fun onTabUnselected() {
        editorState.editor.get()?.clearFocus()
    }

    override fun onDuplicate(tab: Tab) {
        if (tab is EditorTab) {
            scope.launch(Dispatchers.Main) {
                tab.editorState.contentLoaded.await()

                val newContent = tab.editorState.content
                if (newContent != null && !editorState.isDirty && newContent != editorState.content) {
                    editorState.updateLock.withLock {
                        editorState.content = newContent
                        editorState.editor.get()?.setText(newContent)
                        editorState.updateUndoRedo()
                        editorState.isDirty = false
                    }
                }
            }
        }
    }

    init {
        scope.launch {
            val file = file
            if (file == null) {
                editorState.contentLoaded.complete(Unit)
                editorState.editable = !isReadOnly
                editorState.textmateScope = FileTypeManager.fromExtension(fallbackExtension).textmateScope
                return@launch
            }

            scope.launch(Dispatchers.IO) {
                val parent = file.getParentFile()
                val titleAlreadyExists = viewModel.tabs.any { it.title == title && it != this@EditorTab }

                if (titleAlreadyExists && parent != null) {
                    withContext(Dispatchers.Main) { title = "${parent.getName()}/$title" }
                }
            }

            if (!file.exists() || !file.canRead()) return@launch
            if (!file.canWrite()) {
                this@EditorTab.isReadOnly = true
            }

            projectRoot = projectRoot ?: file.getParentFile()

            editorState.editable = !isReadOnly && !Settings.read_only_default
            if (editorState.textmateScope == null) {
                editorState.textmateScope = FileTypeManager.fromFileName(file.getName()).textmateScope
            }

            loadEditorConfig()

            if (editorState.content == null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        withTask {
                            editorState.content = readContent(file)

                            if (Settings.detect_bin_files && hasBinaryChars(editorState.content.toString())) {
                                editorState.editable = false
                                showNotice(BINARY_NOTICE_KEY) { id -> BinaryNotice(id) }
                            }
                        }
                    }
                        .onFailure { errorDialog(throwable = it) }
                }
            }
            editorState.contentLoaded.complete(Unit)
        }
    }

    companion object {
        private const val BINARY_NOTICE_KEY = "binary_file"
        private const val EDITORCONFIG_NOTICE_KEY = "editorconfig_changed"

        const val FORMAT_DOCUMENT_TASK_ID = "format_document"
        const val LAYOUT_BUSY_TASK_ID = "layout_busy"
        const val LSP_CONNECTING_TASK_ID = "connecting_lsp"
    }

    @Composable
    private fun BinaryNotice(id: String) {
        EditorNotice(
            stringResource(strings.binary_file_notice),
            actionButton = {
                IconButton(onClick = { removeNotice(id) }) {
                    Icon(
                        painter = painterResource(drawables.close),
                        contentDescription = stringResource(strings.close),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        )
    }

    @Composable
    private fun EditorConfigNotice(id: String) {
        EditorNotice(
            text = stringResource(strings.editorconfig_changed),
            actionButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            save()
                            refreshEditorSettings()
                            removeNotice(id)
                        }
                    }
                ) {
                    Text(stringResource(strings.apply))
                }
            },
        )
    }

    @MahdiExtensionPoint
    fun showNotice(id: String, notice: @Composable (String) -> Unit) {
        if (editorState.notices.value.containsKey(id)) return
        editorState.addNotice(id, notice)
    }

    @MahdiExtensionPoint
    fun removeNotice(id: String) {
        editorState.removeNotice(id)
    }

    /** Refresh all normal editor settings and EditorConfig settings and apply them to the editor */
    suspend fun reapplyEditorSettings() {
        val editor = editorState.editor.get()
        editor?.apply {
            applySettings()

            if (file != null) {
                loadEditorConfig()
                editorState.editorConfigLoaded?.await()?.let { applySettings(it) }
            }

            val isTxtFile = file?.getName()?.endsWith(".txt") ?: (fallbackExtension == "txt")
            if (Settings.word_wrap_text && isTxtFile) {
                setWordwrap(true, true, true)
            }
        }
    }

    suspend fun loadEditorConfig() {
        val file = file
        if (!Settings.enable_editorconfig || file == null) {
            editorState.editorConfigLoaded = null
            return
        }

        val deferred = CompletableDeferred<ResourceProperties>()
        editorState.editorConfigLoaded = deferred

        withContext(Dispatchers.IO) {
            val cache = Caches.permanent()
            val loader = EditorConfigLoader.default_()
            val propService =
                ResourcePropertiesService.builder()
                    .apply {
                        cache(cache)
                        loader(loader)
                        file.getParentFile()?.getAbsolutePath()?.let {
                            rootDirectory(ResourcePath.ResourcePaths.ofPath(Paths.get(it), charset))
                        }
                    }
                    .build()
            val props =
                propService.queryProperties(Resource.Resources.ofPath(Paths.get(file.getAbsolutePath()), charset))
            deferred.complete(props)

            val editorConfigCharset = props.getValue(PropertyType.charset, null, false)
            editorConfigCharset?.let { charset = Charset.forName(it) }
        }
    }

    fun refresh() {
        val file = file ?: return
        scope.launch(Dispatchers.IO) {
            if (!file.exists() || !file.canRead()) return@launch

            val newContent = readContent(file)

            withContext(Dispatchers.Main) {
                editorState.updateLock.withLock {
                    editorState.content = newContent
                    editorState.editor.get()?.setText(newContent)
                    editorState.updateUndoRedo()
                    editorState.isDirty = false
                }
            }
        }
    }

    private val saveMutex = Mutex()

    private suspend fun write() {
        val file = file ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                if (!file.canWrite()) {
                    errorDialog(strings.cant_write)
                    return@withContext
                }

                val editor = editorState.editor.get() ?: return@runCatching
                val content = editor.text.toString()
                val normalizedContent = editor.lineEnding.applyOn(content)
                file.writeText(normalizedContent, charset)

                editorState.isDirty = false
                lspConnector?.notifySave()
            }
                .onFailure { errorDialog(throwable = it) }
        }
    }

    suspend fun quickSave() = saveMutex.withLock {
        if (isReadOnly) return@withLock
        if (isTemp) return@withLock
        val file = file ?: return@withLock

        write()

        searchViewModel.get()?.syncIndex(file)
        Events.publish(EditorTabEvent.Saved(this, file, true))
    }

    fun saveAs() {
        val file = file
        val extension = FileTypeManager.fromScope(editorState.textmateScope).extensions.firstOrNull() ?: "txt"
        val defaultName = file?.getName() ?: "$title.$extension"

        MainActivity.instance?.apply {
            fileManager.createNewFile(mimeType = "*/*", title = defaultName) {
                if (it != null) {
                    this@EditorTab.file = it
                    title = it.getName()
                    editorState.textmateScope = FileTypeManager.fromFileName(it.getName()).textmateScope

                    scope.launch {
                        write()
                        searchViewModel.get()?.syncIndex(it)
                        Events.publish(EditorTabEvent.Saved(this@EditorTab, it, false))
                    }
                }
            }
        }
    }

    suspend fun save() = saveMutex.withLock {
        if (isReadOnly) return@withLock
        val editor = editorState.editor.get()
        if (Settings.format_on_save && editor != null && !editor.isFormatting && editor.hasFormatter()) {
            val deferred = CompletableDeferred<Boolean>()
            editorState.formatDeferred = deferred

            registerTask(FORMAT_DOCUMENT_TASK_ID)
            editor.formatCodeAsync()
            withTimeoutOrNull(10.seconds) { deferred.await() }
            unregisterTask(FORMAT_DOCUMENT_TASK_ID)
        }

        if (isTemp) {
            withContext(Dispatchers.Main) { saveAs() }
            return@withLock
        }
        val file = file ?: return@withLock

        write()

        searchViewModel.get()?.syncIndex(file)
        Events.publish(EditorTabEvent.Saved(this, file, false))

        Settings.saves += 1
        // تحفيز: سلسلة الأيام + الهدف اليومي + الإنجازات (2.5.0)
        runCatching { Motivation.onSave(editorState.editor.get()?.text?.lineCount ?: 0) }
    }

    @MahdiExtensionPoint
    suspend fun withTask(block: suspend () -> Unit) {
        val uniqueId = UUID.randomUUID().toString()

        registerTask(uniqueId)
        try {
            block()
        } finally {
            unregisterTask(uniqueId)
        }
    }

    @MahdiExtensionPoint
    fun registerTask(id: String) {
        activeTasks.add(id)
    }

    @MahdiExtensionPoint
    fun unregisterTask(id: String) {
        activeTasks.remove(id)
    }

    fun isTaskInProcess() = activeTasks.isNotEmpty()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        key(refreshKey) {
            LaunchedEffect(editorState.editable) { editorState.editor.get()?.editable = editorState.editable }

            Column {
                if (editorState.showFindingsDialog) {
                    FindingsDialog(
                        title = editorState.findingsTitle,
                        codeItems = editorState.findingsItems,
                        description = editorState.findingsDescription,
                        onFinish = { editorState.showFindingsDialog = false },
                    )
                }

                if (editorState.showRenameDialog) {
                    SingleInputDialog(
                        title = stringResource(strings.rename_symbol),
                        inputLabel = stringResource(strings.new_name),
                        inputValue = editorState.renameValue,
                        errorMessage = editorState.renameError,
                        confirmEnabled = editorState.renameValue.isNotBlank(),
                        onInputValueChange = {
                            editorState.renameValue = it
                            editorState.renameError = null
                            if (editorState.renameValue.isBlank()) {
                                editorState.renameError = strings.name_empty_err.getString()
                            }
                        },
                        onConfirm = { editorState.renameConfirm?.let { it(editorState.renameValue) } },
                        onFinish = {
                            editorState.renameValue = ""
                            editorState.renameError = null
                            editorState.renameConfirm = null
                            editorState.showRenameDialog = false
                        },
                    )
                }

                if (editorState.showJumpToLineDialog) {
                    SingleInputDialog(
                        title = stringResource(strings.jump_to_line),
                        inputLabel = stringResource(strings.line_number),
                        inputValue = editorState.jumpToLineValue,
                        errorMessage = editorState.jumpToLineError,
                        confirmEnabled = editorState.jumpToLineValue.isNotBlank(),
                        onInputValueChange = {
                            val editor = editorState.editor.get()
                            val lastLine = editor?.lineCount ?: 0

                            editorState.jumpToLineValue = it
                            editorState.jumpToLineError = null

                            val parts = it.split(":")

                            val line = parts.getOrNull(0)?.toIntOrNull()
                            val column = parts.getOrNull(1)?.toIntOrNull()

                            when {
                                line == null -> {
                                    editorState.jumpToLineError = strings.value_invalid.getString()
                                }

                                column == null && parts.size > 1 -> {
                                    editorState.jumpToLineError = strings.value_invalid.getString()
                                }

                                parts.size > 2 -> {
                                    editorState.jumpToLineError = strings.value_invalid.getString()
                                }

                                line < 0 -> {
                                    editorState.jumpToLineError = strings.value_invalid.getString()
                                }

                                column != null && column < 0 -> {
                                    editorState.jumpToLineError = strings.value_invalid.getString()
                                }
                            }
                        },
                        onConfirm = {
                            val editor = editorState.editor.get() ?: return@SingleInputDialog
                            val parts = editorState.jumpToLineValue.split(":")

                            val line =
                                parts.getOrNull(0)?.toIntOrNull()?.minus(1)?.coerceIn(0, editor.text.lineCount - 1) ?: 0
                            val maxColumn = editor.text.getLine(line).length
                            val column = parts.getOrNull(1)?.toIntOrNull()?.minus(1)?.coerceIn(0, maxColumn) ?: 0

                            editor.setSelection(line, column)
                        },
                        onFinish = {
                            editorState.jumpToLineValue = ""
                            editorState.jumpToLineError = null
                            editorState.showJumpToLineDialog = false
                        },
                    )
                }

                Column(modifier = Modifier.animateContentSize()) {
                    EditorSearchPanel(editorState = editorState)
                    if (editorState.isSearching) {
                        HorizontalDivider()
                    }

                    AnimatedVisibility(visible = isTaskInProcess()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    editorState.notices.collectAsStateWithLifecycle().value.forEach { (id, notice) -> notice(id) }
                }

                val fileExtension = file?.getExtension() ?: fallbackExtension
                val intelligentFeatures =
                    IntelligentFeatureRegistry.allFeatures.filter { feature ->
                        feature.supportedExtensions.contains(fileExtension) && feature.isEnabled()
                    }

                CodeEditor(
                    modifier = Modifier.weight(1f),
                    intelligentFeatures = intelligentFeatures,
                    onTextChange = {
                        if (Settings.auto_save && !isTemp && file != null) {
                            autoSaveJob?.cancel()
                            autoSaveJob =
                                scope.launch(Dispatchers.IO) {
                                    delay(Settings.auto_save_delay.milliseconds)
                                    quickSave()
                                }
                        } else {
                            editorState.isDirty = true
                        }

                        // «آخر ملف مفتوح»: تحديث مؤجّل بعد 500ms (2.5.0)
                        scheduleDocumentStateSave()

                        if (file?.getName() == ".editorconfig" && Settings.enable_editorconfig) {
                            showNotice(EDITORCONFIG_NOTICE_KEY) { id -> EditorConfigNotice(id) }
                        }
                    },
                )

                val showColorPicker = editorState.showColorPicker
                if (showColorPicker != null) {
                    ColorPicker(
                        initialColor = showColorPicker.first,
                        initialFormat = showColorPicker.second,
                        onApply = {
                            val textRange = editorState.colorPickerRange ?: return@ColorPicker
                            val editor = editorState.editor.get() ?: return@ColorPicker
                            editor.text.replace(
                                textRange.start.line,
                                textRange.start.column,
                                textRange.end.line,
                                textRange.end.column,
                                it,
                            )
                        },
                    ) {
                        editorState.showColorPicker = null
                    }
                }

                if (Settings.show_extra_keys) {
                    ExtraKeys(editorTab = this@EditorTab)
                }

                LaunchedEffect(
                    editorState.textmateScope,
                    refreshKey,
                    LocalConfiguration.current,
                    LocalContext.current,
                    MaterialTheme.colorScheme,
                ) {
                    applyHighlightingAndConnectLSP()
                }
            }
        }
    }

    override fun getState(): TabState? {
        saveDocumentState()
        val editor = editorState.editor.get() ?: return null
        return EditorTabState(
            fileObject = file,
            projectRoot = projectRoot,
            scopeRoot = scopeRoot,
            content =
                when {
                    file == null -> editor.text.toString()
                    editorState.isDirty -> editor.text.toString()
                    else -> null
                },
            isDirty = editorState.isDirty,
            isReadOnly = isReadOnly,
            customTitle = customTitle,
            fallbackExtension = fallbackExtension,
        )
    }

    private suspend fun restoreDocumentState() {
        val file = file ?: return
        editorState.contentRendered.await()
        val editor = editorState.editor.get() ?: return

        val db = DocumentStateDatabase.getDatabase(MainActivity.instance!!)
        val state = db.documentStateDao().getState(file.getAbsolutePath())
        if (state != null) {
            val maxLine = editor.text.lineCount - 1
            val lineLeft = state.cursorLineLeft.coerceAtMost(maxLine)
            val lineRight = state.cursorLineRight.coerceAtMost(maxLine)

            val maxColumnLeft = editor.text.getColumnCount(lineLeft)
            val maxColumnRight = editor.text.getColumnCount(lineRight)
            val columnLeft = state.cursorColumnLeft.coerceAtMost(maxColumnLeft)
            val columnRight = state.cursorColumnRight.coerceAtMost(maxColumnRight)

            editor.setSelectionRegion(lineLeft, columnLeft, lineRight, columnRight)
            editor.scroller.startScroll(state.scrollX, state.scrollY, 0, 0)
        }
    }

    /** يأخذ لقطة متزامنة من محرّر الملف الحالي (يجب أن يُنادى من خيط الواجهة). */
    private fun snapshotDocumentState(): DocumentState? {
        val file = file ?: return null
        val editor = editorState.editor.get() ?: return null
        val cursor = editor.cursor
        return DocumentState(
            path = file.getAbsolutePath(),
            cursorLineLeft = cursor.leftLine,
            cursorColumnLeft = cursor.leftColumn,
            cursorLineRight = cursor.rightLine,
            cursorColumnRight = cursor.rightColumn,
            scrollX = editor.scrollX,
            scrollY = editor.scrollY,
            lastOpened = System.currentTimeMillis(),
        )
    }

    private suspend fun persistDocumentState(state: DocumentState) {
        val activity = MainActivity.instance ?: return
        runCatching {
            DocumentStateDatabase.getDatabase(activity).documentStateDao().insertState(state)
        }
    }

    /**
     * «آخر ملف مفتوح» — يحفظ موضع المؤشر والتمرير بعد **500 مللي ثانية** من آخر تغيير
     * (كتابة أو تحريك المؤشر)، فلا يُثقل قاعدة البيانات أثناء الكتابة السريعة.
     */
    fun scheduleDocumentStateSave() {
        if (file == null) return
        val snapshot = snapshotDocumentState() ?: return
        documentStateJob?.cancel()
        documentStateJob =
            GlobalScope.launch(Dispatchers.IO) {
                delay(500)
                persistDocumentState(snapshot)
            }
    }

    /** حفظ فوري (عند الإيقاف المؤقت أو إغلاق التبويب). */
    private fun saveDocumentState() {
        if (file == null) return
        documentStateJob?.cancel()
        val snapshot = snapshotDocumentState() ?: return
        GlobalScope.launch(Dispatchers.IO) { persistDocumentState(snapshot) }
    }

    @Composable
    override fun RowScope.Actions() {
        EditorToolbarActions(viewModel = viewModel)
    }

    override val showGlobalActions: Boolean = false

    override fun hashCode(): Int {
        return file?.hashCode() ?: customTitle?.hashCode() ?: super.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is EditorTab) {
            return false
        }

        return if (file != null && other.file != null) {
            other.file == file
        } else if (file == null && other.file == null) {
            customTitle == other.customTitle
        } else {
            false
        }
    }

    override fun toString(): String {
        return "[EditorTab] ${file?.getAbsolutePath() ?: customTitle}"
    }
}
