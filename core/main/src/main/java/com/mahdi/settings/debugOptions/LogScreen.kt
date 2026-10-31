package com.mahdi.settings.debugOptions

import android.content.Intent
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.mahdi.activities.settings.SettingsActivity
import com.mahdi.editor.Editor
import com.mahdi.file.BuiltinFileType
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.search.EditorSearchPanel
import com.mahdi.tabs.editor.CodeEditorState
import com.mahdi.theme.GitColorScheme
import com.mahdi.theme.MahdiTheme
import com.mahdi.utils.copyToClipboard
import com.mahdi.utils.dialogRes
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    logText: String,
    copyLabel: String,
    flow: Flow<String>? = null,
    toolbarButtons: @Composable RowScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    val editorState = remember { CodeEditorState() }

    LaunchedEffect(flow) {
        flow?.collect { newLine ->
            editorState.editor.get()?.let { editor ->
                val text = editor.text
                val lastLine = text.lineCount - 1
                val lastColumn = text.getColumnCount(lastLine)
                val isAtBottom = editor.offsetY >= editor.scrollMaxY - 5
                text.insert(lastLine, lastColumn, "\n" + newLine)

                if (isAtBottom) {
                    editor.post {
                        editor.scroller.forceFinished(true)
                        editor.scroller.startScroll(editor.offsetX, editor.scrollMaxY, 0, 0, 0)
                        editor.scroller.abortAnimation()
                    }
                }
            }
        }
    }

    MahdiTheme {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        title = { Text(stringResource(strings.logs)) },
                        actions = {
                            TextButton(
                                onClick = {
                                    val currentText = editorState.editor.get()?.text
                                    copyToClipboard(copyLabel, currentText?.toString() ?: logText, true)
                                }
                            ) {
                                Text(stringResource(strings.copy))
                            }

                            // «الإبلاغ عن مشكلة» أُزيل في 2.7.1: لا روابط دعم خارجية داخل التطبيق.
                        },
                    )
                    HorizontalDivider()
                }
            }
        ) { paddingValues ->
            val selectionColors = LocalTextSelectionColors.current
            val isDarkMode = isSystemInDarkTheme()
            val colorScheme = MaterialTheme.colorScheme
            val gitColorScheme = GitColorScheme.create()

            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Surface {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            IconButton(onClick = { editorState.isSearching = !editorState.isSearching }) {
                                Icon(
                                    painter = painterResource(drawables.search),
                                    contentDescription = stringResource(strings.search),
                                )
                            }

                            toolbarButtons()
                        }

                        HorizontalDivider()
                    }
                }

                Column(modifier = Modifier.animateContentSize()) {
                    EditorSearchPanel(editorState)
                    if (editorState.isSearching) {
                        HorizontalDivider()
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    factory = { context ->
                        Editor(context).apply {
                            editorState.editor = WeakReference(this)

                            setTextSize(10f)
                            setText(logText)

                            editorState.editable = false
                            editable = editorState.editable

                            isWordwrap = false
                            setThemeColors(
                                isDarkMode = isDarkMode,
                                selectionColors = selectionColors,
                                colorScheme = colorScheme,
                                gitColorScheme = gitColorScheme,
                            )

                            scope.launch { configureLanguage(BuiltinFileType.LOG.textmateScope!!) }
                        }
                    },
                    update = { editor ->
                        val currentText = editor.text.toString()
                        if (currentText != logText) {
                            editor.setText(logText)
                            editor.post {
                                editor.scroller.forceFinished(true)
                                editor.scroller.startScroll(editor.offsetX, editor.scrollMaxY, 0, 0, 0)
                                editor.scroller.abortAnimation()
                            }
                        }
                    },
                )
            }
        }
    }
}

