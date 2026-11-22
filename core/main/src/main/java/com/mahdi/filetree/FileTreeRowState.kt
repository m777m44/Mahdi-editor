package com.mahdi.filetree

import com.mahdi.file.FileObject
import com.mahdi.settings.Settings

/**
 * 10.0.0 — لقطة واحدة من حالة الشجرة.
 *
 * قبل 10.0.0 كان كل صفّ يجمع ستّة تدفّقات (`expandedNodes` · `selectedFiles` ·
 * `focusedFile` · `loadingStates` · `cutNodes` · `fileListCache`) بنفسه، أي
 * ‏٦ اشتراكات × عدد الصفوف. مع شجرة مفتوحة تحتوي ألف صفّ = ستّة آلاف اشتراك،
 * وكل تغيير واحد (اختيار ملفّ) يُعيد تركيب الجميع. صار الجمع **مرّة واحدة**
 * في أعلى الشجرة، والصفوف تُستقبل حالة جاهزة للعرض فقط.
 */
data class FileTreeFlags(
    val expanded: Set<FileObject> = emptySet(),
    val selected: Set<FileObject> = emptySet(),
    val cut: Set<FileObject> = emptySet(),
    val loading: Map<FileObject, Boolean> = emptyMap(),
    val severity: Map<FileObject, Int> = emptyMap(),
    val focused: FileObject? = null,
)

/**
 * 10.0.0 — صفّ واحد جاهز للرسم.
 *
 * لا يحمل [FileObject] فقط بل كل ما يحتاج القرار البصري: هل هو مفتوح؟
 * هل تحته خطوط دليل متّصلة؟ هل هو آخر إخوته؟ فلا يحتاج الصفّ أي حساب.
 *
 * @property guides خطوط الدليل لطبقات الأسلاف: `true` يعني أن الخطّ يستمرّ
 *   (لأن السلف ليس آخر إخوته)، و`false` يعني أنه ينتهي عند هذا الصفّ.
 *   `guides.size == depth` دائمًا.
 * @property displayName الاسم المعروض — قد يكون مسارًا مدموجًا لمجلد مفرد.
 */
data class FileTreeRow(
    val node: FileTreeNode,
    val depth: Int,
    val expanded: Boolean,
    val loading: Boolean,
    val selected: Boolean,
    val focused: Boolean,
    val cut: Boolean,
    val severity: Int,
    val displayName: String,
    val guides: List<Boolean>,
    /** عدد العناصر المعروف من الذاكرة (للمجلدات) — `null` يعني مجهولًا. */
    val childCount: Int?,
) {
    /** مفتاح ثابت لهذه اللوحة في القائمة: المسار داخل الشجرة. */
    val key: String
        get() = node.file.getAbsolutePath()
}

/** أقصى عمق نتتبّعه. أعمق من ذلك في نظام ملفات حقيقي مؤشّر على حلقة ارتباط. */
private const val MAX_TREE_DEPTH = 64

/**
 * 10.0.0 — يحوّل الشجرة المتداخلة إلى قائمة صفوف مسطّحة مرئية.
 *
 * **لماذا التسوية هي المفتاح:** `LazyColumn` لا يعيد التركيب إلا للصفوف
 * الظاهرة، لكن الشجرة المتداخلة تجبره على تركيب **كل** ما فُتح لأن الأبناء
 * داخل أبيهم. بتسوية الشجرة صار الفتح والطيّ يعنيان ببساطة «أضف/احذف صفوفًا
 * من القائمة»، فتُركَّب ١٥ صفًّا بدل آلاف، ويعمل `animateItem()` فيُنساب
 * الفتح والطيّ بدل القفز.
 *
 * الشجرة تُبنى في مرور واحد بالعمق (DFS) وبمكدّس صريح لا باستدعاء ذاتي،
 * فلا خطر تجاوز المكدّس على مسار عميق.
 *
 * @param filter نصّ تصفية: يُبقي الصفّ إن طابق اسمه، أو إن كان سلفًا لصفّ
 *   مطابقًا في الجزء المحمّل من الذاكرة. فارغ = كل الصفوف.
 */
fun buildFileTreeRows(
    root: FileTreeNode,
    flags: FileTreeFlags,
    cache: Map<FileObject, List<FileTreeNode>>,
    showHidden: Boolean,
    filter: String = "",
): List<FileTreeRow> {
    val query = filter.trim()
    // عند التصفية نعتبر كل شيء مفتوحًا: الهدف «أرِني ما يطابق» لا «تنقّل».
    val revealAll = query.isNotEmpty()
    val rows = ArrayList<FileTreeRow>(64)

    // مكدّس صريح: (العقدة، العمق، خطوط الأسلاف، هل هي آخر إخوتها)
    val stack = ArrayDeque<Frame>()
    stack.addLast(Frame(root, 0, emptyList()))

    while (stack.isNotEmpty()) {
        val frame = stack.removeLast()
        val node = frame.node
        val depth = frame.depth
        if (depth > MAX_TREE_DEPTH) continue

        val file = node.file
        val isHidden = node.name.startsWith(".")
        if (isHidden && !showHidden) continue

        // دمج المجلدات المفردة: ننزل ما دام في الذاكرة مجلدٌ وحيدٌ قابل للتوسيع.
        // قرار **قراءة فقط** — لا تحميل ولا انتظار، فلا يتوقّف الرسم على القرص.
        val merge = resolveMergedPath(node, flags.expanded, cache, revealAll)
        val shown = merge.node
        val shownName = merge.name

        val expanded = revealAll || shown.file in flags.expanded
        val children = if (expanded) cache[shown.file].orEmpty() else emptyList()

        if (revealAll && !subtreeMatches(shown, cache, query, showHidden)) continue

        val row =
            FileTreeRow(
                node = node,
                depth = depth,
                expanded = expanded,
                loading = flags.loading[shown.file] == true,
                selected = file in flags.selected,
                focused = flags.focused == file,
                cut = file in flags.cut,
                severity = flags.severity[file] ?: -1,
                displayName = shownName,
                guides = frame.guides,
                childCount = cache[shown.file]?.size,
            )
        rows.add(row)

        if (expanded && children.isNotEmpty()) {
            // الإزاحة للأبناء تأخذ خطّ هذا الصفّ: يستمرّ إن لم يكن آخر إخوته.
            val childGuides = frame.guides + (frame.hasFollowingSibling)
            // نُدخل بترتيب معاكس كي يُخرَج الأوّل أوّلًا من المكدّس (LIFO).
            for (index in children.indices.reversed()) {
                val child = children[index]
                stack.addLast(
                    Frame(
                        node = child,
                        depth = depth + 1,
                        guides = childGuides,
                        hasFollowingSibling = index < children.lastIndex,
                    )
                )
            }
        }
    }
    return rows
}

private data class Frame(
    val node: FileTreeNode,
    val depth: Int,
    val guides: List<Boolean>,
    val hasFollowingSibling: Boolean = false,
)

private data class MergedPath(val node: FileTreeNode, val name: String)

/**
 * ينزل في سلسلة مجلدات مفردة ويجمع أسماءها «اسم/اسم» كما في VS Code.
 *
 * لا يلمس القرص إطلاقًا: يعتمد فقط على ما حمّلته الجلسة. مجلد لم يُفتح بعد
 * يظهر باسمه المفرد، وبعد أول فتح يظهر مساره المدموج — سلوك متدرّج بلا
 * انتظار ولا مؤشّر تحميل.
 */
private fun resolveMergedPath(
    node: FileTreeNode,
    expanded: Set<FileObject>,
    cache: Map<FileObject, List<FileTreeNode>>,
    revealAll: Boolean,
): MergedPath {
    if (!Settings.file_tree_compact_path) return MergedPath(node, node.name)
    if (!revealAll && node.file !in expanded) return MergedPath(node, node.name)

    var current = node
    var name = node.name
    var hops = 0
    while (hops < MAX_TREE_DEPTH) {
        val kids = cache[current.file] ?: break
        val only = kids.singleOrNull() ?: break
        if (!only.isExpandable) break
        if (!revealAll && only.file !in expanded) break
        name = "$name/${only.name}"
        current = only
        hops++
    }
    return MergedPath(current, name)
}

/**
 * هل تحتوي شجرة العقدة صفًّا يطابق [query]؟
 *
 * تُستدعى فقط أثناء التصفية، وتقرأ الذاكرة المحمّلة وحدها، بعمق محدود — فلا
 * يمكنها تعليق الواجهة على مجلد ضخم غير محمّل.
 */
private fun subtreeMatches(
    node: FileTreeNode,
    cache: Map<FileObject, List<FileTreeNode>>,
    query: String,
    showHidden: Boolean,
): Boolean {
    if (node.name.contains(query, ignoreCase = true)) return true
    if (node.name.startsWith(".") && !showHidden) return false
    val stack = ArrayDeque<Pair<FileTreeNode, Int>>()
    stack.addLast(node to 0)
    while (stack.isNotEmpty()) {
        val (current, depth) = stack.removeLast()
        if (depth > MAX_TREE_DEPTH) continue
        for (child in cache[current.file].orEmpty()) {
            if (child.name.startsWith(".") && !showHidden) continue
            if (child.name.contains(query, ignoreCase = true)) return true
            if (child.isExpandable) stack.addLast(child to depth + 1)
        }
    }
    return false
}
