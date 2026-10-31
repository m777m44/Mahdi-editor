package com.mahdi.activities.main.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.activities.main.MainViewModel
import com.mahdi.components.GlobalToolbarActions
import com.mahdi.components.isPermanentDrawer
import com.mahdi.drawer.DrawerViewModel
import com.mahdi.resources.strings
import com.mahdi.utils.toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MahdiTopBar(
    drawerState: DrawerState,
    viewModel: MainViewModel,
    drawerViewModel: DrawerViewModel,
    fullScreen: Boolean,
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val showTopBar by viewModel.showTopBar.collectAsStateWithLifecycle()

    AnimatedVisibility(visible = showTopBar, enter = expandVertically(), exit = shrinkVertically()) {
        TopAppBar(
            windowInsets = if (fullScreen) WindowInsets() else TopAppBarDefaults.windowInsets,
            modifier =
                Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    )
                },
            title = {},
            navigationIcon = {
                if (!isPermanentDrawer) {
                    IconButton(
                        onClick = {
                            scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() }
                        }
                    ) {
                        Icon(Icons.Outlined.Menu, null)
                    }
                }
            },
            actions = {
                if (viewModel.visibleTabs.isNotEmpty()) {
                    val tab = viewModel.visibleTabs.getOrNull(viewModel.visibleCurrentTabIndex)

                    if (tab != null) {
                        tab.apply { Actions() }
                    } else {
                        toast(strings.unknown_error)
                    }
                }

                GlobalToolbarActions(viewModel)
            },
        )
    }
}
