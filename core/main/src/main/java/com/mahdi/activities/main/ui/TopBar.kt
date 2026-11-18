package com.mahdi.activities.main.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.activities.main.MainViewModel
import com.mahdi.components.GlobalToolbarActions
import com.mahdi.components.isPermanentDrawer
import com.mahdi.drawer.DrawerViewModel
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.text.FileNameText
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
        // 9.5.0: خطّ لوني رفيع أسفل الشريط (سماوي ⟶ نعناعي ⟶ بنفسجي ⟶ ذهبي) —
        // نفس هوية شريط الطرفية، فتصبح كل شاشات التطبيق موقّعة باللون نفسه.
        Column {
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
                // 8.1.0: اسم الملف الحالي ظاهر دائمًا في الشريط العلوي.
                // كان `title = {}` فارغًا، فلا يعرف المستخدم أي ملف مفتوح (شكوى اللقطة).
                title = {
                    val tab = viewModel.visibleTabs.getOrNull(viewModel.visibleCurrentTabIndex)
                    val title = tab?.title ?: strings.app_name.getString()
                    // 9.0.0: اسم الملف معزول بـFSI…PDI فلا ينفصل الامتداد `.py` عن الاسم
                    // العربي، وبخط أحادي العرض بلون سماوي كما في بيئات التطوير الاحترافية.
                    // 9.4.0: تبديل التبويبات ينتقل بحركة انزلاق + تلاشٍ بدل قفزة نصّية،
                    // فيبدو الانتقال بين الملفات سلسًا كما في المحرّرات الاحترافية.
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                                (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 3 })
                        },
                        label = "topBarTitle",
                    ) { animatedTitle ->
                        FileNameText(
                            name = animatedTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
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

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF38BDF8),
                                Color(0xFF2DD4BF),
                                Color(0xFFA78BFA),
                                Color(0xFFF5D76E),
                            )
                        )
                    )
        )
        }
    }
}
