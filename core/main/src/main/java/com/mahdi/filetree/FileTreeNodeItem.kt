package com.mahdi.filetree

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.components.compose.utils.addIf
import com.mahdi.file.FileObject
import com.mahdi.file.ZipFileObject
import com.mahdi.resources.drawables
import com.mahdi.settings.Settings
import com.mahdi.text.bidiFileName
import com.mahdi.text.bidiNumbers
import com.mahdi.ui.components.formatBytes
import com.mahdi.utils.drawErrorUnderline
import com.mahdi.utils.getFileColor
import com.mahdi.utils.getUnderlineColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 9.9.0 — صفّ واحد في شجرة الملفات.
 *
 * أُعيد بناء الصفّ كاملًا لأن الشكل السابق كان **مكسورًا فعليًا** لا قبيحًا فقط:
 *
 *  - العرض كان `getDrawerWidth() - 61.dp` أي ٨٣٪ من **عرض الشاشة** بينما اللوحة
 *    أضيق، فامتدّ الصفّ خارجها وانزاح الاسم إلى اليمين، ومع
 *    `horizontalScroll` التفّت الشجرة كلها جانبيًا (لقطات المستخدم).
 *  - الاسم كان سطرًا واحدًا مقطوعًا بـ«…» فأسماء الملفات المهمّة تفقد نهايتها
 *    وامتدادها.
 *
 * الآن: العرض يُقاس من الأب مباشرةً (`fillMaxWidth`)، والاسم يعيد اللفّ على
 * سطرين حين يطول، والامتداد يبقى ظاهرًا، وكل عنصر نقر له مساحته المستقلّة
 * (لم يبقَ `combinedClickable` على الصفّ كله فتصطدم أهداف اللمس).
 */
@Composable
fun FileTreeNodeItem(
    modifier: Modifier,
    root: FileObject,
    node: FileTreeNode,
    depth: Int,
    onFileClick: (FileTreeNode) -> Unit,
    viewModel: FileTreeViewModel,
    metrics: FileTreeMetrics,
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
    val isLoading = loadingStates[file] == true
    val isCut = file in cutNodes
    val isFileSelected = selectedFiles[root]?.contains(file) == true
    val isFileFocused = focusedFile[root] == file
    val isActive = isFileFocused || isFileSelected

    val context = LocalContext.current
    val selectionColor = MaterialTheme.colorScheme.primaryContainer
    val zipTint = MaterialTheme.colorScheme.tertiaryContainer

    // لون الخلفية ينتقل بسلاسة عند الاختيار أو التركيز — لا قفزة لونية.
    val background by
        animateColorAsState(
            targetValue = if (isActive) selectionColor else Color.Transparent,
            animationSpec = tween(180),
            label = "rowBackground",
        )
    val zipWash by
        animateColorAsState(
            targetValue = if (file is ZipFileObject) zipTint.copy(alpha = 0.14f) else Color.Transparent,
            animationSpec = tween(180),
            label = "zipWash",
        )

    LaunchedEffect(file, isExpanded) {
        if (isExpanded && node.isExpandable) viewModel.loadChildrenForNode(node)
    }

    val children by
        remember(file, isExpanded) {
            derivedStateOf {
                if (node.isExpandable && isExpanded) fileListCache[node.file] ?: emptyList() else emptyList()
            }
        }

    // 9.9.0 — «دمج المجلدات المفردة» (كما في VS Code): مجلد يحتوي مجلدًا
    // واحدًا فقط يُعرض في سطر واحد `src/main/kotlin`. يُحسب فقط عند تفعيل
    // الخيار وعند وجود مجلد واحد قابل للتوسيع، وإلا فالتكلفة صفر.
    val compactPath = Settings.file_tree_compact_path
    var mergedName by remember { mutableStateOf(node.name) }
    var mergedChildren by remember { mutableStateOf(children) }

    LaunchedEffect(children, compactPath, isExpanded) {
        val only = children.singleOrNull()
        if (compactPath && isExpanded && only != null && only.isExpandable) {
            val collapsed = viewModel.collapseNode(root, node)
            mergedName = viewModel.getCollapsedName(collapsed)
            mergedChildren = viewModel.getNodeChildren(collapsed)
        } else {
            mergedName = node.name
            mergedChildren = children
        }
    }
    val displayName = mergedName

    // الإزاحة عنصر واحد رفيع بدل حشو ضخم: يحفظ شكل الشجرة ويترك مساحة للاسم.
    val indent = metrics.indent * depth

    val nameStyle =
        MaterialTheme.typography.bodyMedium.copy(
            fontSize = metrics.nameSize,
            textDirection = TextDirection.ContentOrLtr,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        )

    val underlineColor = getUnderlineColor(context, viewModel, file)
    val titleColor = getFileColor(file) ?: MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(10.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.addIf(isCut) { alpha(0.5f) }
                    .padding(horizontal = 4.dp)
                    .clip(shape)
                    .background(background)
                    .background(zipWash)
                    // ‏‎48dp حدًّا أدنى للمس، وارتفاع فعلي يتبع حجم الخطّ.
                    .heightIn(min = 44.dp)
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (node.isExpandable) {
                                viewModel.toggleNodeExpansion(root, file)
                            } else {
                                onFileClick(node)
                            }
                        },
                        // الضغط المطوّل = تحديد دائمًا، للملف والمجلد معًا.
                        onLongClick = { viewModel.toggleSelection(root, file) },
                    )
                    .padding(
                        start = 6.dp,
                        end = 8.dp,
                        top = metrics.rowPadding,
                        bottom = metrics.rowPadding,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ——— العمود الأيمن: إزاحة + شيفرون + أيقونة ———
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(indent))

                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    if (node.isExpandable) {
                        val rotation by
                            animateFloatAsState(
                                targetValue = if (isExpanded) 90f else 0f,
                                animationSpec = tween(160),
                                label = "chevron",
                            )
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                painter = painterResource(drawables.chevron_right),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.size(metrics.glyphSize.value.dp).rotate(rotation),
                            )
                        }
                    }
                }

                Box(modifier = Modifier.addIf(isHidden) { alpha(0.55f) }) {
                    FileIcon(file, isExpanded = isExpanded, size = metrics.iconSize)
                }
            }

            Spacer(Modifier.width(9.dp))

            // ——— الاسم: سطر أو سطران، بلا بتر الامتداد ———
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    // 9.0.0: عزل الاسم بين FSI…PDI — وإلا اندمج الامتداد مع نصّ
                    // لاتيني مجاور وقُذف إلى أقصى يسار السطر في الواجهة العربية.
                    text = displayName.bidiFileName(),
                    style = nameStyle,
                    // 9.9.0 — اللفّ يتمّ **داخل** تخطيط النصّ نفسه، لا بتقطيع
                    // يدوي: فواصل Compose تعرف `-` و`_` والمسافة، وتقيس بالعرض
                    // الحقيقي المتبقّي بعد الإزاحة والأيقونة وحجم الملف. النتيجة:
                    // `ملف-قاعدة-بيانات-` في السطر الأول و`طلاب-في-مدرسة.txt`
                    // في الثاني — بلا حساب عرض هشّ يخالف ما يُرسم فعلًا.
                    maxLines = if (metrics.twoLines) 2 else 1,
                    softWrap = metrics.twoLines,
                    overflow = TextOverflow.Ellipsis,
                    color = titleColor,
                    // الاتجاه يُضبط في `nameStyle` لا هنا: `Text` لا يقبل
                    // `textDirection` كمعامل، و`TextStyle` هو الحامل الصحيح له.
                    modifier = underlineModifier(underlineColor),
                )
            }

            // ——— الذيل: حجم الملف أو عدد العناصر ———
            RowMeta(node = node, file = file, metrics = metrics)
        }

        if (isExpanded && node.isExpandable && mergedChildren.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                mergedChildren.forEach { childNode ->
                    key(childNode.file.hashCode(), childNode.name) {
                        FileTreeNodeItem(
                            modifier = Modifier.fillMaxWidth(),
                            root = root,
                            node = childNode,
                            depth = depth + 1,
                            onFileClick = onFileClick,
                            viewModel = viewModel,
                            metrics = metrics,
                        )
                    }
                }
            }
        }
    }
}

/**
 * معلومة الطرف: حجم الملف للملفات، وعدد العناصر للمجلدات المطويّة.
 *
 * كلاهما يُقرأ في الخلفية عبر `produceState`، وكلاهما يبقى **صغيرًا وباهتًا**
 * فلا يزاحم الاسم. لا يُطلب أي شيء قبل معرفة النوع.
 */
@Composable
private fun RowMeta(node: FileTreeNode, file: FileObject, metrics: FileTreeMetrics) {
    val showSize = Settings.file_tree_show_size && !node.isExpandable
    val showCount = Settings.file_tree_show_count && node.isExpandable
    if (!showSize && !showCount) return

    val memo by
        produceState<String?>(initialValue = null, key1 = file.hashCode(), key2 = showSize, key3 = showCount) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            if (showSize) {
                                file.length().takeIf { it > 0 }?.let { formatBytes(it) }
                            } else {
                                val count = file.listFiles()?.size ?: 0
                                if (count > 0) count.toString() else null
                            }
                        }
                        .getOrNull()
                }
        }

    val label = memo ?: return

    Text(
        text = label.bidiNumbers(),
        style =
            MaterialTheme.typography.labelSmall.copy(
                fontSize = metrics.metaSize,
                textDirection = TextDirection.ContentOrLtr,
                fontWeight = FontWeight.Medium,
            ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(start = 6.dp),
    )
}

/**
 * الخطّ المموّج لملفّ فيه خطأ — أو لا شيء.
 *
 * `addIf` يتوقّع `Modifier.() -> Modifier`، والاستدعاء المضمّن مباشرةً داخل
 * تركيبة `Text` كان يخفق في استنتاج النوع (خطأ المترجم: «None of the
 * following candidates is applicable»). الدالّة الصريحة تُثبّت النوع مرّة
 * واحدة وتُقرأ أوضح من تعبير مضمّن.
 */
private fun underlineModifier(color: Color?): Modifier =
    if (color == null) Modifier else Modifier.drawErrorUnderline(color)
