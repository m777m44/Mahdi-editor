package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.EditorNonActionContext
import com.mahdi.commands.KeyCombination
import com.mahdi.editor.complete.PythonAutoImport
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.utils.toast

/**
 * 9.8.0 — «إصلاح الاستيرادات» لمحرّر بايثون (`Ctrl+Shift+I`).
 *
 * تكتب السكربت كما تفكّر: `json.loads(...)` · `requests.get(...)` ·
 * `Path("x")`، ثم تضغط مرّة فيُضاف ما ينقص ويُرتَّب الموجود. نظير
 * *Organize Imports* في VS Code و*Auto Import* في Pydroid.
 *
 * قرارات تصميم:
 * - **لا نلمس شيئًا إن لم يكن هناك نقص أو ترتيب مطلوب**: `undo` لا يفقد شيئًا،
 *   ولا يعيد المحرّر كتابة ملف سليم.
 * - التغيير **عملية واحدة** في `undo` — لا عشر خطوات لأن المستخدم يجرّب.
 * - رسالة واحدة قصيرة بالعربية تخبر بما حدث (كم استيرادًا أُضيف).
 */
class PythonAutoImportCommand : EditorCommand() {
    override val id: String = "editor.python_auto_import"

    override fun getLabel(): String = "إصلاح الاستيرادات"

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.python)

    override val defaultKeybinds: KeyCombination? =
        KeyCombination(keyCode = KeyEvent.KEYCODE_I, ctrl = true, shift = true)

    override fun isSupported(context: EditorNonActionContext): Boolean =
        context.editorTab.file?.getName()?.substringAfterLast('.', "")?.lowercase() in PY_EXTENSIONS

    override fun isEnabled(context: EditorNonActionContext): Boolean =
        isSupported(context) && context.editorTab.editorState.editable

    override fun execute(context: EditorActionContext) {
        val editor = context.editor
        val original = editor.text.toString()
        if (original.isEmpty()) return

        val added = PythonAutoImport.scan(original).missing.size
        val afterAdd = PythonAutoImport.addMissing(original)
        val organized = PythonAutoImport.sortImports(afterAdd)

        if (organized == original) {
            // لا إصلاح مطلوب: نقول ذلك صراحةً بدل «لا شيء حدث» المحيّر
            toast("الاستيرادات سليمة — لا شيء للإصلاح")
            return
        }

        // استبدال واحد للنصّ كله = خطوة undo واحدة.
        // `setText` في soraX يمسح مكدّس التراجع، لذا نستعمل `text.replace` بدلًا
        // منه حتى يبقى Ctrl+Z قادرًا على إرجاع الإصلاح بضغطة واحدة.
        val lastLine = editor.text.lineCount - 1
        editor.text.replace(0, 0, lastLine, editor.text.getColumnCount(lastLine), organized)

        toast(
            when {
                added > 0 -> "أُضيف $added استيرادًا ورُتِّبت الاستيرادات"
                else -> "رُتِّبت الاستيرادات"
            },
        )
    }

    private companion object {
        val PY_EXTENSIONS = setOf("py", "pyw", "pyi")
    }
}
