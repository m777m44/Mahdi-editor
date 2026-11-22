package com.mahdi.filetree

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 10.0.0 — صفّ واحد في شجرة الملفات.
 *
 * الصفّ الآن **بلا حالة داخلية تخصّ الشجرة**: يستقبل [row] جاهزًا (مفتوح؟
 * محدَّد؟ خطوط دليله؟) فيُعاد تركيبه فقط حين يتغيّر ما يخصّه هو.
 *
 * ثلاثة أشياء تُرى هنا ولا تُرى في صفوف عادية:
 *
 *  1. **خطوط دليل الشجرة.** خطوط رأسية تصل الأب بأبنائه، تنقطع عند آخر إخوة.
 *     ليست زينة: هي التي تُخبر العين أيّ مجلد يحوي هذا الملفّ حين تختفي
 *     أسماء الآباء خارج الشاشة.
 *  2. **ارتفاع معلن.** [FileTreeMetrics.rowHeight] يُفرض بـ`height` لا
 *     `heightIn`: القائمة تحتاج ارتفاعًا معروفًا مسبقًا لتحريك الصفوف
 *     (`animateItem`) بلا قفز.
 *  3. **اهتزاز الشيفرون بفيزياء نابضة** عند الفتح، لا خطّ مستقيم — إحساس
 *     «شيءٌ انفتح» لا «علامة تحرّكت».
 */
@Composable
fun FileTreeNodeItem(
    modifier: Modifier,
    root: FileObject,
    row: FileTreeRow,
    onFileClick: (FileTreeNode) -> Unit,
    viewModel: FileTreeViewModel,
    metrics: FileTreeMetrics,
) {
    val node = row.node
    val file = node.file
    val context = LocalContext.current

    val scheme = MaterialTheme.colorScheme
    val isActive = row.selected || row.focused

    // لون الخلفية ينتقل بسلاسة عند الاختيار أو التركيز — لا قفزة لونية.
    val background by
        animateColorAsState(
            targetValue =
                when {
                    row.selected -> scheme.primaryContainer
                    row.focused -> scheme.secondaryContainer.copy(alpha = 0.55f)
                    else -> Color.Transparent
                },
            animationSpec = tween(160),
            label = "rowBackground",
        )
    val zipWash by
        animateColorAsState(
            targetValue = if (file is ZipFileObject) scheme.tertiaryContainer.copy(alpha = 0.14f) else Color.Transparent,
            animationSpec = tween(160),
            label = "zipWash",
        )

    val isHidden = node.name.startsWith(".")
    val underlineColor = getUnderlineColor(context, viewModel, file)
    val titleColor =
        when {
            row.cut -> scheme.onSurfaceVariant
            underlineColor != null || row.severity >= 0 -> scheme.onSurface
            else -> getFileColor(file) ?: scheme.onSurface
        }

    val nameStyle =
        remember(metrics.nameSize, metrics.lineBox) {
            TextStyle(
                fontSize = metrics.nameSize,
                lineHeight = metrics.lineBox.value.sp,
                fontWeight = if (row.focused) FontWeight.SemiBold else FontWeight.Normal,
                // الاتجاه يتبع المحتوى: العربي RTL والاسم اللاتيني كاملًا LTR.
                textDirection = TextDirection.ContentOrLtr,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            )
        }

    val indent = metrics.indent * row.depth
    val rowShape = RoundedCornerShape(metrics.corner)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(metrics.rowHeight)
                .addIf(row.cut) { alpha(0.55f) }
                .padding(horizontal = 4.dp)
                .clip(rowShape)
                .background(background)
                .background(zipWash)
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
                .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ——— خطوط الدليل + الإزاحة ———
        TreeGuides(
            guides = row.guides,
            indent = indent,
            stroke = metrics.guideStroke,
            color = scheme.outlineVariant.copy(alpha = 0.55f),
            selectedColor = scheme.primary.copy(alpha = 0.45f),
            active = isActive,
        )

        // ——— شيفرون الطيّ ———
        Box(modifier = Modifier.size(metrics.chevron + 12.dp), contentAlignment = Alignment.Center) {
            if (node.isExpandable) {
                if (row.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(metrics.chevron * 0.85f),
                        strokeWidth = 1.8.dp,
                        color = scheme.primary,
                    )
                } else {
                    // فيزياء نابضة: يتجاوز ٩٠° قليلًا ثم يستقرّ — الحركة
                    // تُوحي بأن المجلد «انفتح» لا بأن سهمًا «تغيّر اتجاهه».
                    val rotation by
                        animateFloatAsState(
                            targetValue = if (row.expanded) 90f else 0f,
                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
                            label = "chevron",
                        )
                    Icon(
                        painter = painterResource(drawables.chevron_right),
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant.copy(alpha = if (row.expanded) 0.95f else 0.6f),
                        modifier = Modifier.size(metrics.chevron).rotate(rotation),
                    )
                }
            }
        }

        // ——— الأيقونة ———
        Box(
            modifier = Modifier.addIf(isHidden) { alpha(0.55f) },
            contentAlignment = Alignment.Center,
        ) {
            FileIcon(file, isExpanded = row.expanded, size = metrics.iconSize)
        }

        Spacer(Modifier.width(metrics.iconGap))

        // ——— الاسم ———
        Text(
            // 9.0.0: عزل الاسم بين FSI…PDI — وإلا اندمج الامتداد مع نصّ لاتيني
            // مجاور وقُذف إلى أقصى يسار السطر في الواجهة العربية.
            text = row.displayName.bidiFileName(),
            style = nameStyle,
            // اللفّ داخل تخطيط النصّ نفسه: يقيس بالعرض الحقيقي المتبقّي فيكسر
            // عند `-` حيث تكسر عين القارئ — «ملف-قاعدة-بيانات-» ثم الباقي.
            maxLines = if (metrics.twoLines) 2 else 1,
            softWrap = metrics.twoLines,
            overflow = TextOverflow.Ellipsis,
            color = titleColor,
            modifier = Modifier.weight(1f).addIf(underlineColor != null) { underlineModifier(underlineColor) },
        )

        // ——— ذيل الصفّ: شارة الحجم / العدّاد / نقطة التشخيص ———
        RowTrailing(row = row, metrics = metrics)
    }
}

/**
 * خطوط دليل الشجرة + الإزاحة في عمود واحد مرسوم بـ`Canvas`.
 *
 * الرسم بـ`Canvas` لا بـ`Row` من `Box`ات: صفّ بعمق ١٠ كان يضيف عشرة عناصر
 * تخطيط لكل صفّ يُعاد تركيبها مع كل تحديث. هنا رسمة واحدة ثابتة التكلفة
 * مهما كان العمق، وتُزاح مع التكبير بلا إعادة قياس.
 */
@Composable
private fun TreeGuides(
    guides: List<Boolean>,
    indent: Dp,
    stroke: Dp,
    color: Color,
    selectedColor: Color,
    active: Boolean,
) {
    if (guides.isEmpty() && indent.value <= 0f) return
    val lineColor = if (active) selectedColor else color
    Canvas(modifier = Modifier.width(indent).fillMaxSize()) {
        if (indent.value <= 0f) return@Canvas
        val step = size.width / guides.size.coerceAtLeast(1)
        val strokePx = stroke.toPx()
        guides.forEachIndexed { index, continues ->
            // مركز العمود الأيمن لهذا المستوى (RTL: نبدأ من اليمين).
            val x = size.width - step * (index + 0.5f)
            // الخطّ ينزل من أعلى الصفّ؛ يستمرّ كاملًا إن كان السلف ليس آخر
            // إخوته، وينتهي عند منتصف الصفّ إن كان التفرّع الأخير.
            val bottom = if (continues) size.height else size.height * 0.5f
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, bottom),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * ما يظهر في طرف الصفّ: حجم الملفّ، أو عدد عناصر المجلد، أو نقطة خطأ.
 *
 * الحجم يُقرأ من القرص بشكل غير متزامن مرّة واحدة لكل ملفّ ثم يبقى؛ ولا
 * يُطلب إطلاقًا للمجلدات (تكلفة بلا فائدة). النقطة الحمراء تُغني عن فتح
 * الملفّ لمعرفة أن فيه خطأ.
 */
@Composable
private fun RowTrailing(row: FileTreeRow, metrics: FileTreeMetrics) {
    val scheme = MaterialTheme.colorScheme
    val showSize = Settings.file_tree_show_size && !row.node.isExpandable
    val showCount = Settings.file_tree_show_count && row.node.isExpandable

    // نقطة التشخيص: أوّلاً، فهي أهمّ من الحجم ولا تُزاحمه.
    if (row.severity >= 0) {
        val dotColor = if (row.severity == 0) scheme.error else scheme.tertiary
        Box(
            modifier = Modifier.size((metrics.metaSize.value * 0.55f).coerceIn(4f, 8f).dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(6.dp))
    }

    val label =
        when {
            showSize -> rememberFileSizeLabel(row.node.file)
            showCount -> row.childCount?.takeIf { it > 0 }?.toString()
            else -> null
        } ?: return

    Text(
        text = label.bidiNumbers(),
        style =
            TextStyle(
                fontSize = metrics.metaSize,
                fontWeight = FontWeight.Medium,
                textDirection = TextDirection.ContentOrLtr,
            ),
        color = scheme.onSurfaceVariant.copy(alpha = 0.55f),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(start = 6.dp),
    )
}

/**
 * حجم الملفّ كنصّ مقروء — يُقرأ مرّة واحدة ويُخزَّن في ذاكرة التكوين.
 *
 * `produceState` مفتاحه `hashCode + volume`، فلا نُعيد القراءة عند مجرّد
 * إعادة تركيب الصفّ (تمرير، تغيير تحديد، تكبير).
 */
@Composable
private fun rememberFileSizeLabel(file: FileObject): String? {
    val state by
        produceState<String?>(initialValue = null, key1 = file.hashCode()) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching { file.length().takeIf { it > 0 }?.let { formatBytes(it) } }.getOrNull()
                }
        }
    return state
}

/**
 * الخطّ المموّج لملفّ فيه خطأ — أو لا شيء.
 *
 * `addIf` يتوقّع `Modifier.() -> Modifier`، والاستدعاء المضمّن مباشرةً داخل
 * تركيبة `Text` كان يخفق في استنتاج النوع. الدالّة الصريحة تُثبّت النوع.
 */
private fun underlineModifier(color: Color?): Modifier = if (color == null) Modifier else Modifier.drawErrorUnderline(color)
