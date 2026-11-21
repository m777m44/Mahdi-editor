package com.mahdi.commands.editor

import com.mahdi.commands.Command
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.EditorFileActionContext
import com.mahdi.commands.EditorFileCommand
import com.mahdi.commands.EditorNonActionContext
import com.mahdi.editor.complete.PythonSnippets
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.settings.Settings

/**
 * 9.7.0 — «مقتطف بايثون» في لوحة الأوامر.
 *
 * بدل أن يحفظ المستخدم اختصارات، يفتح لوحة الأوامر ويبحث «مقتطف» فيرى
 * كل المقتطفات مجموعاتٍ فرعية بأسمائها العربية. اختيار مقتطف يُدرجه في
 * موضع المؤشر **بالإزاحة الصحيحة** للسطر الحالي، فلا ينكسر داخل دالة.
 *
 * القائمة الفرعية تُبنى مرة واحدة كأوامر عادية، فيعمل عليها البحث والتقييم
 * والتثبيت في «الأخيرة» تمامًا كبقية الأوامر.
 */
class PythonSnippetCommand : EditorCommand() {
    override val id: String = "editor.python_snippets"

    override fun getLabel(): String = "مقتطف بايثون"

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.python)

    override val preferText: Boolean = true

    override val childCommands: List<Command>
        get() = PythonSnippets.ALL.map { snippet -> SnippetInsertCommand(snippet) }

    override fun getChildSearchPlaceholder(): String = "ابحث عن مقتطف…"

    override fun execute(context: EditorActionContext) {
        // لا تنفيذ مباشر: `perform` يفتح القائمة الفرعية تلقائيًا لأن childCommands غير فارغة.
    }

    override fun isSupported(context: EditorNonActionContext): Boolean =
        context.editorTab.file?.getExtension()?.lowercase()?.removePrefix(".") in PYTHON_EXTENSIONS

    private companion object {
        val PYTHON_EXTENSIONS = setOf("py", "pyi", "pyw")
    }
}

/** أمر واحد لكل مقتطف — يُدرجه في المؤشر. */
class SnippetInsertCommand(
    private val snippet: PythonSnippets.Snippet,
) : EditorFileCommand() {
    override val id: String = "editor.python_snippet.${snippet.title.hashCode()}"

    override fun getLabel(): String = snippet.title

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.python)

    override val preferText: Boolean = true

    override fun execute(context: EditorFileActionContext) {
        val editor = context.editor
        val cursor = editor.cursor
        val line = cursor.leftLine
        val column = cursor.leftColumn

        // إزاحة السطر الحالي: نأخذ ما قبل المؤشر في نفس السطر ونعدّ المسافات
        // البادئة. السطر الفارغ ← صفر، فلا تُضاف مسافات في مكان لا يحتاجها.
        val lineText = runCatching { editor.text.getLineString(line) }.getOrNull().orEmpty()
        val indent = countIndent(lineText)
        val body = PythonSnippets.indented(snippet.body, indent)

        runCatching {
            editor.text.insert(line, column, body)
            // المؤشر ينتقل إلى نهاية آخر سطر مُدرَج — جاهز للكتابة مباشرة.
            val lastLine = line + body.count { it == '\n' }
            val lastColumn = if (lastLine == line) column + body.length else body.substringAfterLast('\n').length
            editor.setSelection(lastLine, lastColumn)
            editor.ensureSelectionVisible()
        }
    }

    /**
     * عدد مسافات البادئة الفعلية. التبويب يُحسب بحجمه المُعدّ في المحرّر
     * ([Settings.tab_size]) لأنه مسافات في الملف بعد الحفظ التلقائي.
     */
    private fun countIndent(lineText: String): Int {
        val tabSize = Settings.tab_size.coerceIn(1, 8)
        var width = 0
        for (c in lineText) {
            when (c) {
                ' ' -> width++
                '\t' -> width += tabSize
                else -> return width
            }
        }
        return width
    }
}
