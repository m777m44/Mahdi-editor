package com.mahdi.filetree

import com.mahdi.file.FileObject
import com.mahdi.file.FileWrapper
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 10.0.1 — اختبارات تسوية الشجرة إلى صفوف.
 *
 * **لماذا هذه الاختبارات موجودة:** في 10.0.0 تحوّلت الشجرة من عناصر متداخلة
 * إلى قائمة صفوف مسطّحة (`buildFileTreeRows`). التحويل بدا سليمًا، لكنه نقل
 * مسؤولية تحميل الأبناء من الصفّ إلى لا مكان — فصار ضغط المجلد لا يُظهر شيئًا.
 * لم يكشف ذلك أي اختبار، لأن المنطق كان «يُختبر» بالنظر إلى الشاشة.
 *
 * هذه الاختبارات تُثبّت العقد الحقيقي للتسوية:
 *
 *  1. مجلد مطويّ يُنتج صفًّا واحدًا فقط.
 *  2. مجلد مفتوح **ومحمّل** يُظهر أبناءه فورًا (وإلا فالمستخدم لا يرى ملفات).
 *  3. مجلد مفتوح **غير محمّل** لا يُسقط الصفّ نفسه — يظهر فارغًا لحظة،
 *     ثم يمتلئ حين تصل البيانات. سقوطه كان سيُخفي المجلد من الشجرة كليًّا.
 *  4. الملفات المخفيّة تُحترم، والجذر لا يُحجب بحال.
 *  5. الدمج والتصفية لا يبتلعان صفوفًا.
 */
class FileTreeRowsTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun tree(): Pair<FileObject, File> {
        val root = tmp.newFolder("root")
        File(root, "a.txt").writeText("a")
        File(root, "b.txt").writeText("b")
        val sub = File(root, "sub").apply { mkdirs() }
        File(sub, "deep.txt").writeText("deep")
        File(root, ".hidden").writeText("h")
        return FileWrapper(root) to root
    }

    private fun nodeOf(file: FileObject, name: String, expandable: Boolean) =
        FileTreeNode(file = file, isFile = !expandable, isExpandable = expandable, name = name)

    private fun rowsFor(
        fileListCache: Map<FileObject, List<FileTreeNode>>,
        expanded: Set<FileObject>,
        showHidden: Boolean = true,
    ): List<FileTreeRow> {
        val (rootObj, rootDir) = tree()
        val rootNode = nodeOf(rootObj, rootDir.name, expandable = true)
        return buildFileTreeRows(
            root = rootNode,
            flags = FileTreeFlags(expanded = expanded),
            cache = fileListCache,
            showHidden = showHidden,
        )
    }

    @Test
    fun collapsedRootYieldsSingleRow() {
        val rows = rowsFor(fileListCache = emptyMap(), expanded = emptySet())
        assertEquals("شجرة مطويّة يجب أن تكون صفًّا واحدًا", 1, rows.size)
        assertEquals(0, rows.first().depth)
        assertFalse(rows.first().expanded)
    }

    @Test
    fun openAndLoadedFolderRevealsChildren() {
        val (rootObj, rootDir) = tree()
        val subObj = FileWrapper(File(rootDir, "sub"))
        val rootNode = nodeOf(rootObj, rootDir.name, expandable = true)

        val children =
            listOf(
                nodeOf(FileWrapper(File(rootDir, "a.txt")), "a.txt", expandable = false),
                nodeOf(subObj, "sub", expandable = true),
            )
        val cache = mapOf(rootObj to children)

        val rows =
            buildFileTreeRows(
                root = rootNode,
                flags = FileTreeFlags(expanded = setOf(rootObj)),
                cache = cache,
                showHidden = false,
            )

        // الجذر + ابنان — هذا بالضبط ما كان يعجز عن الظهور في 10.0.0.
        assertEquals(3, rows.size)
        assertTrue("الأبناء يجب أن يكونوا في العمق ١", rows.drop(1).all { it.depth == 1 })
        assertTrue(rows.any { it.node.name == "a.txt" })
        assertTrue(rows.any { it.node.name == "sub" })
    }

    @Test
    fun openButUnloadedFolderStillShows() {
        // الحالة الحرجة: المستخدم ضغط المجلد، والقرص لم يجب بعد. لو سقط الصفّ
        // هنا لاختفى المجلد من الشجرة وكأنه حُذف.
        val rows = rowsFor(fileListCache = emptyMap(), expanded = setOf())
        assertEquals(1, rows.size)
        assertTrue("الجذر يجب أن يبقى ظاهرًا", rows.first().node.isExpandable)
    }

    @Test
    fun nestedOpenFoldersExpandRecursively() {
        val (rootObj, rootDir) = tree()
        val subObj = FileWrapper(File(rootDir, "sub"))
        val deepObj = FileWrapper(File(File(rootDir, "sub"), "deep.txt"))

        val rootNode = nodeOf(rootObj, rootDir.name, expandable = true)
        val subNode = nodeOf(subObj, "sub", expandable = true)
        val deepNode = nodeOf(deepObj, "deep.txt", expandable = false)

        val cache =
            mapOf(
                rootObj to listOf(subNode),
                subObj to listOf(deepNode),
            )

        val rows =
            buildFileTreeRows(
                root = rootNode,
                flags = FileTreeFlags(expanded = setOf(rootObj, subObj)),
                cache = cache,
                showHidden = false,
            )

        assertEquals(3, rows.size)
        assertEquals(listOf(0, 1, 2), rows.map { it.depth })
    }

    @Test
    fun hiddenFilesFilteredRootNever() {
        val (rootObj, rootDir) = tree()
        val rootNode = nodeOf(rootObj, rootDir.name, expandable = true)
        val kids =
            listOf(
                nodeOf(FileWrapper(File(rootDir, ".hidden")), ".hidden", expandable = false),
                nodeOf(FileWrapper(File(rootDir, "a.txt")), "a.txt", expandable = false),
            )

        val rows =
            buildFileTreeRows(
                root = rootNode,
                flags = FileTreeFlags(expanded = setOf(rootObj)),
                cache = mapOf(rootObj to kids),
                showHidden = false,
            )

        assertEquals("الملفّ المخفيّ يُحجب والمرئيّ يبقى مع الجذر", 2, rows.size)
        assertFalse(rows.any { it.node.name == ".hidden" })
        assertTrue(rows.any { it.node.name == "a.txt" })
    }

    @Test
    fun guideRailsMatchDepthForEveryRow() {
        val (rootObj, rootDir) = tree()
        val subObj = FileWrapper(File(rootDir, "sub"))
        val rootNode = nodeOf(rootObj, rootDir.name, expandable = true)
        val subNode = nodeOf(subObj, "sub", expandable = true)
        val deep = nodeOf(FileWrapper(File(File(rootDir, "sub"), "deep.txt")), "deep.txt", false)

        val rows =
            buildFileTreeRows(
                root = rootNode,
                flags = FileTreeFlags(expanded = setOf(rootObj, subObj)),
                cache = mapOf(rootObj to listOf(subNode), subObj to listOf(deep)),
                showHidden = false,
            )

        // خطأ في عدد الخطوط يعني خطوط دليل مرسومة في العمود الخطأ — عيب بصري
        // صامت لا يظهر إلا بالعين.
        rows.forEach { row ->
            assertEquals("كل صفّ يحمل خطًّا لكل سلف", row.depth, row.guides.size)
        }
    }

    @Test
    fun filterKeepsAncestorsOfMatches() {
        val (rootObj, rootDir) = tree()
        val subObj = FileWrapper(File(rootDir, "sub"))
        val rootNode = nodeOf(rootObj, rootDir.name, expandable = true)
        val subNode = nodeOf(subObj, "sub", expandable = true)
        val deep = nodeOf(FileWrapper(File(File(rootDir, "sub"), "deep.txt")), "deep.txt", false)
        val other = nodeOf(FileWrapper(File(rootDir, "a.txt")), "a.txt", false)

        val rows =
            buildFileTreeRows(
                root = rootNode,
                flags = FileTreeFlags(expanded = setOf(rootObj, subObj)),
                cache = mapOf(rootObj to listOf(subNode, other), subObj to listOf(deep)),
                showHidden = false,
                filter = "deep",
            )

        assertTrue("المطابقة نفسها ظاهرة", rows.any { it.node.name == "deep.txt" })
        assertTrue("سلف المطابقة ظاهر لتُفهم السياقة", rows.any { it.node.name == "sub" })
        assertFalse("غير المطابق يُخفى", rows.any { it.node.name == "a.txt" })
    }

    @Test
    fun rowsAreStable() {
        // التكرار الحتميّ شرطٌ لعمل مفاتيح LazyColumn: لو تغيّر الترتيب بين
        // إعادتَي تركيب لتطايرت الصفوف على الشاشة.
        val (rootObj, rootDir) = tree()
        val rootNode = nodeOf(rootObj, rootDir.name, expandable = true)
        val kids = (1..5).map { nodeOf(FileWrapper(File(rootDir, "f$it.txt")), "f$it.txt", false) }
        val cache = mapOf(rootObj to kids)

        val first = buildFileTreeRows(rootNode, FileTreeFlags(expanded = setOf(rootObj)), cache, false)
        val second = buildFileTreeRows(rootNode, FileTreeFlags(expanded = setOf(rootObj)), cache, false)

        assertEquals(first.map { it.key }, second.map { it.key })
    }

    @Test
    fun synchronousListingWorks() = runBlocking {
        // يحمي افتراضًا بنى عليه الإصلاح: `listFiles` يقرأ القرص فعلًا، فلا
        // نحتاج عقدة مسبقة لتحميل مجلد. هذا ما كان يمنع ظهور الملفات.
        val (rootObj, _) = tree()
        val names = rootObj.listFiles().map { it.getName() }.toSet()
        assertTrue(names.containsAll(setOf("a.txt", "b.txt", "sub")))
    }
}
