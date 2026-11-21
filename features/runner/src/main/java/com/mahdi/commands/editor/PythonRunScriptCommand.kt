package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.DefaultScope
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.EditorNonActionContext
import com.mahdi.commands.KeyCombination
import com.mahdi.file.FileObject
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.runner.runners.UniversalRunner
import kotlinx.coroutines.launch

/**
 * 9.7.0 — «تشغيل سكربت بايثون» (`Ctrl+R`): نظير زر Run في Pydroid.
 *
 * [RunCommand] الأساسي (‏F5) يفتح نافذة اختيار المشغّل إن وُجد أكثر من مشغّل
 * مناسب — سلوك صحيح، لكنه يقطع تدفّق من يقول «شغّل هذا الملف الآن» أثناء
 * الكتابة. هذا الأمر:
 *
 *  1. **يحفظ** الملف أولًا — أشهر خطأ في تطبيقات بايثون للهاتف: تعدّل الكود
 *     ثم تشغّله فتظهر مخرجات النسخة القديمة.
 *  2. يشغّله مباشرة بالمحرّك الموحّد **بلا أي نافذة**.
 *
 * المخرجات تظهر في الطرفية نفسها التي يعرفها المستخدم (ومعها بطاقة التقدّم
 * وتثبيت المكتبات التلقائي)، فلا تدفّق تشغيل ثانٍ يجب تعلّمه.
 */
class PythonRunScriptCommand : EditorCommand() {
    override val id: String = "editor.python_run_script"

    override fun getLabel(): String = "تشغيل سكربت بايثون"

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.python)

    override val defaultKeybinds: KeyCombination? =
        KeyCombination(keyCode = KeyEvent.KEYCODE_R, ctrl = true)

    override fun isSupported(context: EditorNonActionContext): Boolean = context.editorTab.file.isPythonFile()

    override fun isEnabled(context: EditorNonActionContext): Boolean = context.editorTab.file.isPythonFile()

    override fun execute(context: EditorActionContext) {
        val tab = context.editorTab
        val file = tab.file ?: return
        val activity = context.currentActivity
        if (!file.isPythonFile()) return

        // حفظ ← تشغيل، بالترتيب داخل نفس الـ coroutine (كلا الخطوتين معلّقة).
        DefaultScope.launch {
            runCatching { tab.save() } // فشل الحفظ (ملف للقراءة فقط) لا يمنع التشغيل
            runCatching { UniversalRunner.run(activity, file) }
        }
    }

    private fun FileObject?.isPythonFile(): Boolean =
        this?.getName()?.substringAfterLast('.', "")?.lowercase() in PYTHON_EXTENSIONS

    private companion object {
        val PYTHON_EXTENSIONS = setOf("py", "pyw", "pyi")
    }
}
