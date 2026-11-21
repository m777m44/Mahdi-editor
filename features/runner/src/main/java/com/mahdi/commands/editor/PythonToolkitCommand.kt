package com.mahdi.commands.editor

import com.mahdi.commands.Command
import com.mahdi.commands.CommandProvider
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.EditorNonActionContext
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables

/**
 * 9.8.0 — «أدوات بايثون»: قائمة فرعية واحدة تجمع كل ما يهمّ كاتب بايثون.
 *
 * لماذا تجميع لا أزرار منفصلة؟ الشريط السفلي في الهاتف مكان نادر: أربعة
 * أزرار بايثون جديدة تدفع «التراجع» و«الحفظ» خارج الشاشة. زرّ واحد يفتح
 * قائمة مرتّبة أسرع في الاستخدام الفعلي **وأخفّ** على التخطيط.
 *
 * القائمة تُبنى من الأوامر المسجّلة نفسها (‏لا نسخة ثانية منها)، فإن عُطّل
 * مشغّل أو غاب أمر لا يظهر في القائمة — لا زرّ معطّل صامت.
 */
class PythonToolkitCommand : EditorCommand() {
    override val id: String = "editor.python_toolkit"

    override fun getLabel(): String = "أدوات بايثون"

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.python)

    override val preferText: Boolean = true

    override fun getChildSearchPlaceholder(): String = "ابحث عن أداة بايثون…"

    override val childCommands: List<Command>
        get() =
            PYTHON_TOOLKIT_CHILD_IDS.mapNotNull { CommandProvider.getForId(it) }.filter { it.id != id }

    override fun execute(context: EditorActionContext) {
        // لا تنفيذ مباشر: `perform` يفتح القائمة الفرعية تلقائيًا
    }

    override fun isSupported(context: EditorNonActionContext): Boolean =
        context.editorTab.file?.getExtension()?.lowercase()?.removePrefix(".") in PYTHON_EXTENSIONS

    companion object {
        /**
         * ترتيب الأدوات بحسب تكرار الاستخدام على الهاتف:
         * تشغيل ← تشغيل تحديد ← وحدة تحكّم ← إصلاح استيرادات ← مقتطفات.
         */
        private val PYTHON_TOOLKIT_CHILD_IDS =
            listOf(
                "editor.python_run_script",
                "editor.python_run_selection",
                "editor.python_repl",
                "editor.python_auto_import",
                "editor.python_snippets",
            )

        val PYTHON_EXTENSIONS = setOf("py", "pyi", "pyw")
    }
}
