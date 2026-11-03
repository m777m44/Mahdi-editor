package com.mahdi.activities.main.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mahdi.activities.main.MainActivity
import com.mahdi.components.DialogRegistry
import com.mahdi.components.GlobalDialogHost
import com.mahdi.components.ResponsiveDrawer
import com.mahdi.drawer.DrawerContent
import com.mahdi.drawer.DrawerPersistence
import com.mahdi.filetree.FileTreeViewModel
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.search.SearchViewModel
import com.mahdi.settings.Settings
import com.mahdi.tabs.editor.EditorTab
import com.mahdi.theme.MahdiTheme
import com.mahdi.utils.dialogRes
import java.lang.ref.WeakReference
import kotlinx.coroutines.launch

var fileTreeViewModel = WeakReference<FileTreeViewModel?>(null)
var searchViewModel = WeakReference<SearchViewModel?>(null)

var snackbarHostStateRef: WeakReference<SnackbarHostState?> = WeakReference(null)
var drawerStateRef: WeakReference<DrawerState?> = WeakReference(null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainActivity.MainContentHost(
    fileTreeViewModel: FileTreeViewModel = viewModel(),
    searchViewModel: SearchViewModel = viewModel(),
) {
    com.mahdi.activities.main.ui.fileTreeViewModel = WeakReference(fileTreeViewModel)
    com.mahdi.activities.main.ui.searchViewModel = WeakReference(searchViewModel)

    MahdiTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(drawerState) { drawerStateRef = WeakReference(drawerState) }
            LaunchedEffect(snackbarHostState) { snackbarHostStateRef = WeakReference(snackbarHostState) }

            LaunchedEffect(drawerState.isOpen) {
                val viewModel = MainActivity.instance?.viewModel ?: return@LaunchedEffect
                if (drawerState.isOpen) {
                    viewModel.tabManager.currentTab?.let {
                        if (it is EditorTab) {
                            it.editorState.editor.get()?.clearFocus()
                        }
                    }
                } else if (drawerState.isClosed) {
                    viewModel.tabManager.currentTab?.let {
                        if (it is EditorTab) {
                            it.editorState.editor.get()?.apply {
                                requestFocus()
                                requestFocusFromTouch()
                            }
                        }
                    }
                }
            }

            LaunchedEffect(Settings.fullscreen) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                val statusBarType = WindowInsetsCompat.Type.statusBars()
                if (Settings.fullscreen) {
                    controller.hide(statusBarType)
                } else {
                    controller.show(statusBarType)
                }
            }

            val keyboardShown = WindowInsets.isImeVisible
            LaunchedEffect(keyboardShown, Settings.smart_toolbar) {
                viewModel.setShowTopBar(!Settings.smart_toolbar || !keyboardShown)
            }

            val scope = rememberCoroutineScope()

            BackHandler {
                if (drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                } else if (viewModel.tabs.isNotEmpty() && Settings.confirm_exit) {
                    dialogRes(
                        title = strings.attention.getString(),
                        msg = strings.confirm_exit.getString(),
                        onCancel = {},
                        onOk = { finish() },
                        okRes = strings.exit,
                    )
                } else {
                    finish()
                }
            }

            val density = LocalDensity.current
            var accumulator = 0f
            val softThreshold = with(density) { 50.dp.toPx() }
            val hardThreshold = with(density) { 100.dp.toPx() }

            val floatPadding = viewModel.currentVisibleTab?.floatPadding ?: 0.dp
            val taskOutputPadding = if (TaskOutputState.isActive) 72.dp else 0.dp
            val snackbarBottomPadding = floatPadding + taskOutputPadding

            val mainContent: @Composable () -> Unit = {
                Scaffold(
                    contentWindowInsets =
                        if (Settings.fullscreen) WindowInsets() else ScaffoldDefaults.contentWindowInsets,
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(bottom = snackbarBottomPadding),
                        ) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                actionColor = MaterialTheme.colorScheme.primary,
                                dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
                    topBar = {
                        MahdiTopBar(
                            drawerState = drawerState,
                            viewModel = viewModel,
                            drawerViewModel = drawerViewModel,
                            fullScreen = Settings.fullscreen,
                            onDrag = { dragAmount ->
                                accumulator += dragAmount

                                viewModel.setDraggingPalette(true)

                                scope.launch {
                                    val newProgress = (accumulator / hardThreshold).coerceIn(0f, 1f)
                                    viewModel.draggingPaletteProgress.snapTo(newProgress)
                                }
                            },
                            onDragEnd = {
                                val shouldOpen = accumulator >= softThreshold
                                if (shouldOpen) {
                                    Settings.command_palette_swipe_used = true
                                }
                                scope.launch {
                                    viewModel.setDraggingPalette(shouldOpen)
                                    viewModel.draggingPaletteProgress.animateTo(
                                        if (shouldOpen) 1f else 0f,
                                        animationSpec = spring(stiffness = 800f),
                                    )
                                }
                                accumulator = 0f
                            },
                        )
                    },
                ) { innerPadding ->
                    MainContent(
                        innerPadding = innerPadding,
                        mainViewModel = viewModel,
                        drawerViewModel = drawerViewModel,
                        fileTreeViewModel = fileTreeViewModel,
                    )
                }
            }

            val sheetContent: @Composable ColumnScope.() -> Unit = {
                LaunchedEffect(Unit) {
                    drawerViewModel.setLoading(true)
                    DrawerPersistence.restoreState(drawerViewModel)
                    drawerViewModel.setLoading(false)
                }
                DrawerContent(Settings.fullscreen)
            }

            ResponsiveDrawer(
                drawerState = drawerState,
                fullscreen = Settings.fullscreen,
                mainContent = mainContent,
                sheetContent = sheetContent,
            )

            GlobalDialogHost(viewModel, drawerViewModel)

            DialogRegistry.getDialogs().forEach { dialog -> dialog() }
        }
    }
}
