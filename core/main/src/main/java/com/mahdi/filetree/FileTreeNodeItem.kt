package com.mahdi.filetree

import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.produceState
import com.mahdi.text.bidiNumbers
import com.mahdi.ui.components.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.components.compose.utils.addIf
import com.mahdi.components.getDrawerWidth
import com.mahdi.file.FileObject
import com.mahdi.file.ZipFileObject
import com.mahdi.resources.drawables
import com.mahdi.settings.Settings
import com.mahdi.utils.drawErrorUnderline
import com.mahdi.text.bidiFileName
import com.mahdi.utils.getFileColor
import com.mahdi.utils.getUnderlineColor

@Composable
fun FileTreeNodeItem(
    modifier: Modifier,
    root: FileObject,
    node: FileTreeNode,
    depth: Int,
    onFileClick: (FileTreeNode) -> Unit,
    viewModel: FileTreeViewModel,
) {
    val file = node.file
    val isHidden = file.getName().startsWith(".")
    if (isHidden && !Settings.show_hidden_files_drawer) return

    val expandedNodes by viewModel.expandedNodes.collectAsStateWithLifecycle()
    val loadingStates by viewModel.loadingStates.collectAsStateWithLifecycle()
    val cutNodes by viewModel.cutNodes.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val focusedFile by viewModel.focusedFile.collectAsStateWithLifecycle()
    val fileListCache by viewModel.fileListCache.collectAsStateWithLifecycle()

    val isExpanded = expandedNodes[root]?.contains(file) ?: false
    val horizontalPadding = (depth * 16).dp

    val isLoading = loadingStates[file] == true
    val isCut = file in cutNodes

    val isFileSelected = selectedFiles[root]?.contains(file) == true
    val isFileFocused = focusedFile[root] == file

    val context = LocalContext.current
    val surfaceColor =
        if (file is ZipFileObject) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    val selectionColor = MaterialTheme.colorScheme.primaryContainer

    val nodeBackground = remember { Animatable(surfaceColor) }
    LaunchedEffect(isFileFocused, isFileSelected, surfaceColor, selectionColor) {
        if (isFileFocused || isFileSelected) {
            nodeBackground.animateTo(selectionColor, animationSpec = tween(300))
        } else {
            nodeBackground.animateTo(surfaceColor, animationSpec = tween(300))
        }
    }

    // Load children when expanded
    LaunchedEffect(file, isExpanded) {
        if (isExpanded && node.isExpandable) {
            viewModel.loadChildrenForNode(node)
        }
    }

    val children by
        remember(file, isExpanded) {
            derivedStateOf {
                if (node.isExpandable && isExpanded) {
                    fileListCache[node.file] ?: emptyList()
                } else {
                    emptyList()
                }
            }
        }

    var displayedChildren by remember { mutableStateOf(children) }
    var displayName by remember { mutableStateOf(node.name) }

    LaunchedEffect(children, Settings.compact_folders_drawer) {
        displayedChildren =
            if (Settings.compact_folders_drawer && children.size == 1 && children[0].isExpandable) {
                val collapsedNode = viewModel.collapseNode(root, node)
                viewModel.getNodeChildren(collapsedNode)
            } else children
        displayName =
            if (Settings.compact_folders_drawer) {
                viewModel.getCollapsedName(node)
            } else node.name
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.addIf(isCut) { alpha(0.5f) }
                    .background(nodeBackground.value)
                    .combinedClickable(
                        onClick = {
                            if (viewModel.isAnyFileSelected(root)) {
                                viewModel.toggleSelection(root, file)
                                return@combinedClickable
                            }

                            if (node.isExpandable) {
                                viewModel.toggleNodeExpansion(root, file)
                                return@combinedClickable
                            }

                            onFileClick(node)
                        },
                        onLongClick = { viewModel.toggleSelection(root, file) },
                    )
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(horizontalPadding))

            if (node.isExpandable) {
                val rotationDegree by
                    animateFloatAsState(targetValue = if (!isExpanded) 0f else 90f, label = "rotation")

                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(9.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            painter = painterResource(drawables.chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp).rotate(rotationDegree),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))
            } else {
                Spacer(modifier = Modifier.width(24.dp))
            }

            Box(modifier = Modifier.addIf(isHidden) { alpha(0.5f) }) { FileIcon(file, isExpanded = isExpanded) }

            Spacer(modifier = Modifier.width(8.dp))

            val underlineColor = getUnderlineColor(context, viewModel, file)
            Row(modifier = Modifier.width((getDrawerWidth() - 61.dp)), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // 9.0.0: عزل الاسم بـFSI…PDI — وإلا اندمج امتداد `.py` مع سلسلة
                    // لاتينية مجاورة وقُذف إلى أقصى يسار السطر في الواجهة العربية.
                    text = displayName.bidiFileName(),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            textDirection = TextDirection.ContentOrLtr,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.weight(1f)
                            .addIf(underlineColor != null) { drawErrorUnderline(underlineColor!!) },
                    color = getFileColor(file) ?: MaterialTheme.colorScheme.onSurface,
                )

                // 9.6.0: بادئة صغيرة بحجم الملف — كما في مستكشف ملفات احترافي.
                // لا تُحسب إلا للملفات (لا المجلدات)، وفي الخلفية، وبعد أول عرض.
                if (!node.isExpandable) {
                    FileSizeHint(file)
                }
            }
        }

        AnimatedVisibility(
            modifier = Modifier.width(getDrawerWidth()),
            visible = isExpanded && node.isExpandable && children.isNotEmpty(),
            enter = expandVertically(animationSpec = tween(120)),
            exit = shrinkVertically(animationSpec = tween(90)),
        ) {
            Column {
                displayedChildren.forEach { childNode ->
                    key(childNode.file.hashCode(), childNode.name) {
                        FileTreeNodeItem(
                            modifier = Modifier.fillMaxWidth(),
                            root = root,
                            node = childNode,
                            depth = depth + 1,
                            onFileClick = onFileClick,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 9.6.0 — حجم الملف بجانب اسمه، بحساب غير حاجز.
 *
 * `FileObject.length()` دالة `suspend`، وقد تكون على قرص بطيء أو شبكة، فلا
 * تُستدعى داخل التكوين مباشرة. تُقرأ مرة واحدة في `produceState` على خيط IO،
 * وتُعرض بحجم صغير باهت لا يزاحم الاسم. المجلدات لا تمرّ من هنا أصلًا.
 */
@Composable
private fun FileSizeHint(file: FileObject) {
    val size by
        produceState<Long?>(initialValue = null, key1 = file.hashCode()) {
            value = withContext(Dispatchers.IO) { runCatching { file.length() }.getOrNull() }
        }
    val readable = size?.takeIf { it > 0 } ?: return
    val label = remember(readable) { formatBytes(readable) }
    Text(
        text = label.bidiNumbers(),
        style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.ContentOrLtr),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
        maxLines = 1,
        modifier = Modifier.padding(start = 6.dp, end = 2.dp),
    )
}
