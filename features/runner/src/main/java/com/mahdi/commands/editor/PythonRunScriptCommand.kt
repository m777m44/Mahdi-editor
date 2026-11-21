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
import com.mahdi.runner.RunnerManager
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
 *  2. يشغّله بأول مشغّل ملفات مناسب **بلا أي نافذة** ([RunnerManager.runDirect]).
 *
 * لا يعتمد على `UniversalRunner` (يعيش في وحدة الطرفية التي تعتمد على هذه
 * الوحدة لا العكس)، بل على تجريد [RunnerManager] — فيبقى التشغيل صحيحًا حتى
 * لو تغيّر المشغّل الافتراضي أو أضاف امتدادٌ مشغّلًا خاصًّا.
 */
class PythonRunScriptCommand : EditorCommand() {
    override val id: String = "editor.python_run_script"

    override fun getLabel(): String = "تشغيل سكربت بايثون"

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.python)

    override val defaultKeybinds: KeyCombination? =
        KeyCombination(keyCode = KeyEvent.KEYCODE_R, ctrl = true)

    override fun isSupported(context: EditorNonActionContext): Boolean = context.editorTab.file.isPythonScript()

    override fun isEnabled(context: EditorNonActionContext): Boolean = context.editorTab.file.isPythonScript()

    override fun execute(context: EditorActionContext) {
        val tab = context.editorTab
        val file = tab.file ?: return
        val activity = context.currentActivity
        if (!file.isPythonScript()) return

        // حفظ ← تشغيل، بالترتيب داخل نفس الـ coroutine (كلا الخطوتين معلّقة).
        DefaultScope.launch {
            runCatching { tab.save() } // فشل الحفظ (ملف للقراءة فقط) لا يمنع التشغيل
            runCatching { RunnerManager.runDirect(activity, file, tab.projectRoot) }
        }
    }
}

/** 9.7.0: هل الملف سكربت بايثون بأحد امتداداته الثلاثة؟ */
private fun FileObject?.isPythonScript(): Boolean =
    this?.getName()?.substringAfterLast('.', "")?.lowercase() in setOf("py", "pyw", "pyi")
