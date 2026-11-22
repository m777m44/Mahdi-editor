package com.mahdi.filetree

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mahdi.activities.main.MainActivity
import com.mahdi.activities.main.ui.searchViewModel
import com.mahdi.components.MahdiDropdownMenuItem
import com.mahdi.drawer.DrawerViewModel
import com.mahdi.file.FileObject
import com.mahdi.icons.MahdiIcon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import com.mahdi.text.bidiNumbers
import com.mahdi.utils.application
import kotlin.math.min
import kotlinx.coroutines.launch

enum class SortMode(val stringRes: Int) {
    SORT_BY_NAME(strings.sort_by_name),
    SORT_BY_SIZE(strings.sort_by_size),
    SORT_BY_DATE(strings.sort_by_date),
}

@Composable
fun FileTree(
    rootNode: FileTreeNode,
    modifier: Modifier = Modifier,
    onFileClick: FileTreeNode.(FileTreeNode) -> Unit,
    onSearchClick: () -> Unit = {},
    viewModel: FileTreeViewModel,
    drawerViewModel: DrawerViewModel,
) {
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val fileOperationsCount by viewModel.fileOperationsCount.collectAsStateWithLifecycle()
    val searchVM = searchViewModel.get()
    val isIndexingMap =
        searchVM?.isIndexing?.collectAsStateWithLifecycle(initialValue = emptyMap())?.value ?: emptyMap()
    val isIndexingRoot = isIndexingMap[rootNode.file] == true
    val isAnyFileSelected = selectedFiles[rootNode.file]?.isNotEmpty() == true
    val selectionCount = selectedFiles[rootNode.file]?.size ?: 0
    val isFileOperationInProgress = fileOperationsCount > 0

    // 10.0.0 — الحالة كلّها تُجمع **هنا** مرّة واحدة، لا في كل صفّ.
    // ستّة اشتراكات × ألف صفّ كانت تعني ستّة آلاف اشتراك وستّة آلاف إعادة
    // تركيب لكل نقرة واحدة على ملفّ. الآن: صفّ واحد يُعاد تركيبه.
    val expandedNodes by viewModel.expandedNodes.collectAsStateWithLifecycle()
    val selectedMap by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val focusedMap by viewModel.focusedFile.collectAsStateWithLifecycle()
    val loadingStates by viewModel.loadingStates.collectAsStateWithLifecycle()
    val cutNodes by viewModel.cutNodes.collectAsStateWithLifecycle()
    val fileListCache by viewModel.fileListCache.collectAsStateWithLifecycle()
    val diagnosedNodes by viewModel.diagnosedNodes.collectAsStateWithLifecycle()
    val showHidden = Settings.show_hidden_files_drawer

    var filterText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val flags =
        remember(expandedNodes, selectedMap, focusedMap, loadingStates, cutNodes, diagnosedNodes, rootNode.file) {
            FileTreeFlags(
                expanded = expandedNodes[rootNode.file] ?: emptySet(),
                selected = selectedMap[rootNode.file]?.toSet() ?: emptySet(),
                cut = cutNodes.toSet(),
                loading = loadingStates,
                severity = diagnosedNodes,
                focused = focusedMap[rootNode.file],
            )
        }

    // الشجرة تُسوَّى إلى صفوف مرئية: الفتح والطيّ = إضافة/حذف صفوف من القائمة،
    // فتُركَّب الصفوف الظاهرة وحدها (١٥ بدل آلاف) ويعمل `animateItem`.
    val rows =
        remember(rootNode, flags, fileListCache, showHidden, filterText) {
            buildFileTreeRows(
                root = rootNode,
                flags = flags,
                cache = fileListCache,
                showHidden = showHidden,
                filter = filterText,
            )
        }

    // Auto-expand root node on first composition
    LaunchedEffect(rootNode.file) {
        if (!viewModel.isNodeExpanded(rootNode.file, rootNode.file)) {
            viewModel.toggleNodeExpansion(rootNode.file, rootNode.file)
            viewModel.loadChildrenForNode(rootNode)
        }
    }

    LaunchedEffect(MainActivity.instance) {
        MainActivity.instance?.foregroundListener["fileTreeRefresh"] = { resumed ->
            if (resumed) {
                viewModel.refreshEverything()
            }
        }
    }

    LaunchedEffect(sortMode) { viewModel.refreshEverything() }

    // 9.9.0 — التكبير: قيمة محدودة تُثبَّت في التخزين عند انتهاء الإيماءة.
    // تُقرأ من `Settings` في `remember` حتى يبدأ الشريط على آخر حجم اختاره
    // المستخدم، ولا يُكتب في التفضيلات في كل إطار من إيماءة القرص.
    var liveScale by
        remember {
            mutableStateOf(Settings.file_tree_scale.coerceIn(FILE_TREE_MIN_SCALE, FILE_TREE_MAX_SCALE))
        }
    val metrics = rememberFileTreeMetrics(liveScale)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isAnyFileSelected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.unselectAllFiles(rootNode.file) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(strings.go_prev))
                    }

                    Text(
                        text = selectionCount.toString().bidiNumbers(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f),
            ) {
                if (isAnyFileSelected) {
                    SelectionActions(viewModel, drawerViewModel, rootNode, selectedFiles[rootNode.file] ?: emptyList())
                } else {
                    // ذرّا التكبير بجانب الأدوات: لا يحتاج المستخدم معرفة إيماءة
                    // القرص، ويكفيه نقرة واحدة. يُضغطان محدودين عند الحدّين.
                    ZoomButtons(
                        scale = liveScale,
                        onScale = { next ->
                            liveScale = next
                            Settings.file_tree_scale = next
                        },
                    )
                    FileTreeActions(viewModel, rootNode.file, onSearchClick, liveScale) { next ->
                        liveScale = next
                        Settings.file_tree_scale = next
                    }
                }
            }
        }

        // 10.0.0 — تصفية فورية داخل الشجرة.
        // البحث القديم كان نافذة منفصلة ثم قائمة نتائج: تنتقل ذهابًا وإيابًا.
        // هنا تكتب فيُضمر ما لا يطابق في مكانه، فترى الملفّ **داخل سياقه**
        // (أيّ مجلد يحويه) لا في قائمة مبنيّة من أسماء.
        TreeFilterBar(
            value = filterText,
            onValueChange = { filterText = it },
            matchCount = if (filterText.isBlank()) null else rows.count { !it.node.isExpandable || it.expanded },
        )

        Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            if (isFileOperationInProgress || isIndexingRoot) {
                LinearProgressIndicator(modifier = Modifier.fillMaxSize())
            } else {
                HorizontalDivider()
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.viewModelScope.launch { viewModel.refreshEverything(wasPulled = true) } },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            // التكبير بإيماءة القرص يلفّ القائمة **فقط**، فلا تُكبَّر شريط
            // الأدوات معها. الإيماءة لا تستهلك اللمس المفرد، فيبقى التمرير
            // والنقر كما كانا.
            FileTreePinchZoom(
                onZoom = { next ->
                    liveScale = next
                    Settings.file_tree_scale = next
                },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) {
                        itemsIndexed(
                            items = rows,
                            key = { _, row -> row.key },
                            contentType = { _, row -> if (row.node.isExpandable) "dir" else "file" },
                        ) { _, row ->
                            FileTreeNodeItem(
                                // `animateItem` يجعل الفتح والطيّ انسيابًا: الصفوف
                                // تنزلق إلى مواضعها بدل أن تقفز قفزة واحدة.
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(160),
                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    fadeOutSpec = tween(110),
                                ),
                                root = rootNode.file,
                                row = row,
                                onFileClick = { rootNode.onFileClick(it) },
                                viewModel = viewModel,
                                metrics = metrics,
                            )
                        }
                    }

                    BackToTopButton(
                        visible = listState.firstVisibleItemIndex > 6,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    )
                }
            }
        }
    }
}

/**
 * 9.9.0 — زرّا التكبير («+» و«−») في شريط الشجرة.
 *
 * التكبير بنقرة ضرورة لا رفاهية: إيماءة القرص غير معروفة لكثير من المستخدمين،
 * ومن يحتاج خطًّا أكبر (ضعف نظر، شمس ساطعة) يجب أن يجده في نقرة. الزرّان
 * يُعطَّلان عند الحدّ الأقصى/الأدنى حتى لا يبدو الضغط بلا أثر.
 */
@Composable
private fun ZoomButtons(scale: Float, onScale: (Float) -> Unit) {
    val step = 0.1f
    val canShrink = scale > FILE_TREE_MIN_SCALE + 0.001f
    val canGrow = scale < FILE_TREE_MAX_SCALE - 0.001f

    IconButton(
        enabled = canShrink,
        onClick = { onScale((scale - step).coerceIn(FILE_TREE_MIN_SCALE, FILE_TREE_MAX_SCALE)) },
    ) {
        ZoomGlyph(
            plus = false,
            tint =
                if (canShrink) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }

    IconButton(
        enabled = canGrow,
        onClick = { onScale((scale + step).coerceIn(FILE_TREE_MIN_SCALE, FILE_TREE_MAX_SCALE)) },
    ) {
        ZoomGlyph(
            plus = true,
            tint =
                if (canGrow) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
}

/**
 * ‏«+» و«−» مرسومان بـ`Canvas` لا كأصل جديد.
 *
 * السبب: حزمة الأيقونات في المشروع `material-icons-core` فقط، ولا تحتوي
 * `Remove`. رسم عشرين نبضة أوفر من إضافة ملفّ drawable جديد ومن استيراد
 * حزمة كاملة لأجل خطّين.
 */
@Composable
private fun ZoomGlyph(plus: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        val c = size.width / 2f
        val r = size.width * 0.36f
        drawLine(tint, Offset(c - r, c), Offset(c + r, c), strokeWidth = stroke, cap = StrokeCap.Round)
        if (plus) {
            drawLine(tint, Offset(c, c - r), Offset(c, c + r), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun SelectionActions(
    viewModel: FileTreeViewModel,
    drawerViewModel: DrawerViewModel,
    rootNode: FileTreeNode,
    selectedFiles: List<FileObject>,
) {
    val scope = rememberCoroutineScope()
    var actions by remember { mutableStateOf<List<BaseFileAction>>(emptyList()) }
    var enabledActions by remember { mutableStateOf<Set<BaseFileAction>>(emptySet()) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedFiles, rootNode.file) {
        val newActions = FileActionProvider.getActions(selectedFiles, rootNode.file)
        val newEnabled =
            newActions
                .filter { action ->
                    when (action) {
                        is FileAction -> action.isEnabled(selectedFiles.first(), rootNode.file)
                        is MultiFileAction -> action.isEnabled(selectedFiles, rootNode.file)
                        else -> true
                    }
                }
                .toSet()

        actions = newActions
        enabledActions = newEnabled
    }

    val context = LocalContext.current

    BoxWithConstraints {
        val itemWidth = 64.dp
        val availableWidth = maxWidth - 48.dp
        val maxVisibleCount = (availableWidth / itemWidth).toInt().coerceAtLeast(0)

        // Calculate actual number of actions to show in toolbar
        var actualVisibleCount = min(actions.size, maxVisibleCount)

        // Make sure that the dropdown menu never contains only one entry
        if (actions.size - actualVisibleCount == 1) {
            actualVisibleCount += 1
        }

        val toolbarActions = actions.sortedByDescending { it.importance }.take(actualVisibleCount)
        val dropdownActions = actions - toolbarActions.toSet()

        Row(verticalAlignment = Alignment.CenterVertically) {
            toolbarActions.forEach { action ->
                when (action) {
                    is FileAction -> {
                        val file = selectedFiles.first() // Is safe because of the check in getActions()
                        IconButton(
                            enabled = action in enabledActions,
                            onClick = {
                                val context =
                                    FileActionContext(file, rootNode.file, viewModel, drawerViewModel, context)
                                scope.launch { action.execute(context) }

                                viewModel.unselectAllFiles(rootNode.file)
                            },
                        ) {
                            MahdiIcon(action.icon, contentDescription = action.title)
                        }
                    }
                    is MultiFileAction -> {
                        IconButton(
                            enabled = action in enabledActions,
                            onClick = {
                                val context =
                                    MultiFileActionContext(
                                        selectedFiles,
                                        rootNode.file,
                                        viewModel,
                                        drawerViewModel,
                                        context,
                                    )
                                scope.launch { action.execute(context) }

                                viewModel.unselectAllFiles(rootNode.file)
                            },
                        ) {
                            MahdiIcon(action.icon, contentDescription = action.title)
                        }
                    }
                }
            }

            if (dropdownActions.isNotEmpty()) {
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.MoreVert, stringResource(strings.more))
                    }

                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        dropdownActions.forEach { action ->
                            when (action) {
                                is FileAction -> {
                                    val file = selectedFiles.first() // Is safe because of the check in getActions()
                                    MahdiDropdownMenuItem(
                                        text = { Text(action.title) },
                                        leadingIcon = { MahdiIcon(action.icon, contentDescription = action.title) },
                                        enabled = action in enabledActions,
                                        onClick = {
                                            val context =
                                                FileActionContext(
                                                    file,
                                                    rootNode.file,
                                                    viewModel,
                                                    drawerViewModel,
                                                    context,
                                                )
                                            scope.launch { action.execute(context) }

                                            viewModel.unselectAllFiles(rootNode.file)
                                            expanded = false
                                        },
                                    )
                                }
                                is MultiFileAction -> {
                                    MahdiDropdownMenuItem(
                                        text = { Text(action.title) },
                                        leadingIcon = { MahdiIcon(action.icon, contentDescription = action.title) },
                                        enabled = action in enabledActions,
                                        onClick = {
                                            val context =
                                                MultiFileActionContext(
                                                    selectedFiles,
                                                    rootNode.file,
                                                    viewModel,
                                                    drawerViewModel,
                                                    context,
                                                )
                                            scope.launch { action.execute(context) }

                                            viewModel.unselectAllFiles(rootNode.file)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileTreeActions(
    viewModel: FileTreeViewModel,
    viewModelRoot: FileObject,
    onSearchClick: () -> Unit,
    scale: Float,
    onScale: (Float) -> Unit,
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()

    IconButton(onClick = { viewModel.viewModelScope.launch { viewModel.refreshEverything() } }) {
        Icon(Icons.Outlined.Refresh, stringResource(strings.refresh))
    }

    IconButton(onClick = onSearchClick) { Icon(Icons.Outlined.Search, stringResource(strings.search)) }

    Box {
        IconButton(onClick = { showOptionsMenu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(strings.more)) }

        DropdownMenu(expanded = showOptionsMenu, onDismissRequest = { showOptionsMenu = false }) {
            MahdiDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = Settings.show_hidden_files_drawer, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.show_hidden_files))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    Settings.show_hidden_files_drawer = !Settings.show_hidden_files_drawer
                    showOptionsMenu = false
                },
            )

            MahdiDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = Settings.compact_folders_drawer, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.compact_folders))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    Settings.compact_folders_drawer = !Settings.compact_folders_drawer
                    showOptionsMenu = false
                },
            )

            MenuSectionHeader(stringResource(strings.file_tree_section_appearance))

            MahdiDropdownMenuItem(
                text = { Text(stringResource(strings.file_tree_expand_all)) },
                onClick = {
                    viewModel.expandAllLoaded(viewModelRoot)
                    showOptionsMenu = false
                },
            )

            MahdiDropdownMenuItem(
                text = { Text(stringResource(strings.file_tree_collapse_all)) },
                onClick = {
                    viewModel.collapseAll(viewModelRoot)
                    showOptionsMenu = false
                },
            )

            // ————— 9.9.0: شكل الأسماء والأحجام (داخل نفس القائمة، لا شاشة ثانية) —————
            MahdiDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = Settings.file_tree_two_lines, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.file_tree_two_lines))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    Settings.file_tree_two_lines = !Settings.file_tree_two_lines
                    showOptionsMenu = false
                },
            )

            MahdiDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = Settings.file_tree_show_size, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.file_tree_show_size))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    Settings.file_tree_show_size = !Settings.file_tree_show_size
                    showOptionsMenu = false
                },
            )

            MahdiDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = Settings.file_tree_compact_path, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.file_tree_compact_path))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    Settings.file_tree_compact_path = !Settings.file_tree_compact_path
                    showOptionsMenu = false
                },
            )

            // ثلاث كثافات بدل مفتاحين: «مضغوط» و«مريح» حالتان متطرّفتان،
            // والوسط هو ما يريده الأكثر. النقر يبدّل والحفظ فوري.
            MahdiDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(strings.file_tree_density),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        // نُظهر الحالة الحالية صريحةً بدل تكرار النقر عشوائيًا.
                        Text(
                            text =
                                stringResource(
                                    when (Settings.file_tree_density) {
                                        0 -> strings.file_tree_density_compact
                                        2 -> strings.file_tree_density_roomy
                                        else -> strings.file_tree_density_normal
                                    }
                                ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    Settings.file_tree_density = (Settings.file_tree_density + 1) % 3
                    showOptionsMenu = false
                },
            )

            MahdiDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(strings.file_tree_reset_zoom))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    Settings.file_tree_scale = FILE_TREE_DEFAULT_SCALE
                    onScale(FILE_TREE_DEFAULT_SCALE)
                    showOptionsMenu = false
                },
            )

            MenuSectionHeader(stringResource(strings.file_tree_section_sort))

            MahdiDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sortMode == SortMode.SORT_BY_NAME, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.sort_by_name))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    viewModel.setSortMode(SortMode.SORT_BY_NAME)
                    Settings.sort_mode = SortMode.SORT_BY_NAME.ordinal
                    showOptionsMenu = false
                },
            )

            MahdiDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sortMode == SortMode.SORT_BY_SIZE, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.sort_by_size))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    viewModel.setSortMode(SortMode.SORT_BY_SIZE)
                    Settings.sort_mode = SortMode.SORT_BY_SIZE.ordinal
                    showOptionsMenu = false
                },
            )

            MahdiDropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sortMode == SortMode.SORT_BY_DATE, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(strings.sort_by_date))
                        Spacer(Modifier.width(8.dp))
                    }
                },
                onClick = {
                    viewModel.setSortMode(SortMode.SORT_BY_DATE)
                    Settings.sort_mode = SortMode.SORT_BY_DATE.ordinal
                    showOptionsMenu = false
                },
            )
        }
    }
}

fun FileObject.getAppropriateName(): String {
    return if (getAbsolutePath() == Environment.getExternalStorageDirectory().absolutePath) {
        strings.storage.getString()
    } else if (getAbsolutePath() == application?.filesDir?.parentFile?.absolutePath) {
        strings.private_files.getString()
    } else {
        getName()
    }
}

/**
 * عنوان قسم داخل قائمة الخيارات.
 *
 * القائمة صارت عشرة بنود؛ بلا فواصل تصير جدارًا نصّيًا لا يُقرأ. العنوان
 * صغير وباهت ومُعطَّل (لا يُنقر)، فلا يُشتبه بأنه خيار.
 */
@Composable
private fun MenuSectionHeader(title: String) {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
    )
}

/**
 * 10.0.0 — شريط تصفية رفيع أعلى الشجرة.
 *
 * يظهر فقط عند الحاجة: حين تكتب، أو حين تضغط عدسة البحث. وجوده الدائم كان
 * يأكل صفًّا كاملًا من الشجرة القصيرة، وغيابه الدائم يجعل الميزة غير مكتشفة.
 * ويُظهر عدد المطابقات صريحًا (`٣ من ١٢`) — رقم بلا كلمة «من» يبدو خطأً.
 */
@Composable
private fun TreeFilterBar(value: String, onValueChange: (String) -> Unit, matchCount: Int?) {
    val focusManager = LocalFocusManager.current
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(140)) + expandVertically(tween(160)),
        exit = fadeOut(tween(110)) + shrinkVertically(tween(140)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
                placeholder = {
                    Text(
                        text = stringResource(strings.file_tree_filter_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(drawables.search),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                painter = painterResource(drawables.close),
                                contentDescription = stringResource(strings.file_tree_filter_clear),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(10.dp),
            )

            if (matchCount != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = matchCount.toString().bidiNumbers(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * زرّ «العودة إلى الأعلى».
 *
 * مُستخرَج في دالّة مستقلّة لسبب تقني لا تجميلي: داخل `Column` أعلاه يكون
 * `ColumnScope` حاضرًا ضمنيًا، فتلتقط Kotlin نسخة `ColumnScope.AnimatedVisibility`
 * وترفض الاستدعاء داخل `Box` («cannot be called in this context with an
 * implicit receiver»). هنا لا يوجد أي مُستقبِل ضمني فتُحلّ النسخة العامّة.
 */
@Composable
private fun BackToTopButton(visible: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(150)) + scaleIn(tween(180), initialScale = 0.8f),
        exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.8f),
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            // شيفرون قائم بدل أصل جديد: نفس عائلة الأيقونات، ولا ملفّ يُصان لأجل سهم.
            Icon(
                painter = painterResource(drawables.chevron_up),
                contentDescription = stringResource(strings.file_tree_back_to_top),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
