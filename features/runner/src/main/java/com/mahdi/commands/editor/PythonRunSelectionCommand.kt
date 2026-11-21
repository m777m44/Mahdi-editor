package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.DefaultScope
import com.mahdi.TerminalLauncher
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.EditorNonActionContext
import com.mahdi.commands.KeyCombination
import com.mahdi.file.child
import com.mahdi.file.sandboxHomeDir
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 9.8.0 — «تشغيل التحديد» في محرّر بايثون (`Ctrl+Shift+Enter`).
 *
 * تحدّد أسطرًا في المحرّر وتضغط فتُنفَّذ **كما هي** في طرفية جديدة، بلا حفظ
 * الملف ولا تعديله — نظير Run Selection في Pydroid وVS Code. العمل:
 *
 *  1. نصّ التحديد، أو السطر الحالي إن لا يوجد تحديد.
 *  2. يُكتب إلى `local/home/mahdi-run/mahdi_selection_<n>.py`.
 *  3. تُفتح الطرفية على `python3` لهذا الملف.
 *
 * لماذا ملف مؤقّت لا `python3 -c "…"`: النصّ قد يحتوي علامات تنصيص وأسطرًا
 * متعدّدة وبايتات عربية؛ تمريره عبر shell كوسيط يحوّله إلى حقل مكسور أو
 * يُنفّذ أمرًا لم يُقصد. الملف ينجو من كل ذلك — والأهم: رسائل الخطأ تُشير
 * إلى سطر داخل النصّ نفسه، فتصحيحه سهل.
 */
class PythonRunSelectionCommand : EditorCommand() {
    override val id: String = "editor.python_run_selection"

    override fun getLabel(): String = "تشغيل التحديد"

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.python)

    override val defaultKeybinds: KeyCombination? =
        KeyCombination(keyCode = KeyEvent.KEYCODE_ENTER, ctrl = true, shift = true)

    override fun isSupported(context: EditorNonActionContext): Boolean =
        context.editorTab.file?.getName()?.substringAfterLast('.', "")?.lowercase() in PY_EXTENSIONS

    override fun isEnabled(context: EditorNonActionContext): Boolean = isSupported(context)

    override fun execute(context: EditorActionContext) {
        val editor = context.editor
        val selected = editor.getSelectedText()

        // بلا تحديد: السطر الحالي كاملًا — أشهر استخدام في Pydroid
        val snippet =
            selected?.takeIf { it.isNotBlank() }
                ?: runCatching { editor.text.getLineString(editor.cursor.leftLine) }.getOrNull()

        if (snippet.isNullOrBlank()) {
            toast("لا يوجد نصّ للتشغيل")
            return
        }

        val activity = context.currentActivity
        DefaultScope.launch {
            val path =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val runDir = sandboxHomeDir().child("mahdi-run").also { it.mkdirs() }
                        // اسم ثابت لكل تشغيل: ملف واحد لا يُراكم ملفات في التخزين
                        val file = runDir.child("mahdi_selection.py")
                        file.writeText(snippet)
                        file.absolutePath
                    }.getOrNull()
                } ?: return@launch

            // كل الجلسات في هذا المشروع تُشغَّل داخل ساندبوكس PRoot يرى فيه
            // `/home` = `local/home` على الجهاز؛ فالمسار المبني من الاسم فقط.
            val sandboxPath = "/home/mahdi-run/" + path.substringAfterLast('/')
            // نكتب المسار في السكربت نفسه بدل متغيّر بيئة: argv لا يمرّ عبر
            // تحليل صدفة، فلا يقبل أسطرًا جديدة ولا علامات تنصيص ولا حروفًا عربية
            // تفسد الأمر — وهذا أهم من جمال الكود هنا.
            val script = "exec python3 -u " + bashQuote(sandboxPath)
            withContext(Dispatchers.Main) {
                TerminalLauncher.launch(
                    activity = activity,
                    exe = "/bin/bash",
                    args = arrayOf("-c", script),
                    id = "تشغيل التحديد",
                )
            }
        }
    }

    /** يقتبس مسارًا اقتباسًا صدفة واحدًا آمنًا (`'…'` مع هروب `'`). */
    private fun bashQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        val PY_EXTENSIONS = setOf("py", "pyw", "pyi")
    }
}
