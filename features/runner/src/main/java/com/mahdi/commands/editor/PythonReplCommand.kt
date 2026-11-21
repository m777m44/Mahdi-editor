package com.mahdi.commands.editor

import android.view.KeyEvent
import com.mahdi.TerminalLauncher
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.EditorNonActionContext
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables

/**
 * 9.8.0 — «وحدة تحكّم بايثون التفاعلية» (‏REPL) — نظير Pydroid Console.
 *
 * يفتح طرفية تشغّل `python3 -i` **بدون نافذة**، فتصبح كل سطر تكتبه تُنفَّذ
 * فورًا ويعود المؤشّر بسطر جديد: `>>>`. هذا هو الفرق الحقيقي بين «تشغيل ملف»
 * و«تجربة سطر» — تختبر `json.loads(...)` أو `requests.get(...)` قبل حفظها
 * في الملف.
 *
 * لماذا `-i` لا `-c "…"`: `-i` يُدخل الوضع التفاعلي **بعد** تنفيذ الأمر،
 * فيبقى المتغيّر الذي عرّفته متاحًا للسطر التالي. أما `-c` فينفّذ ويخرج.
 *
 * عند وجود ملف بايثون مفتوح نمرّر مساره: يرى المستخدم أن `import` من المشروع
 * نفسه يعمل أيضًا، وهذا أقرب ما يكون إلى Pydroid.
 */
class PythonReplCommand : EditorCommand() {
    override val id: String = "editor.python_repl"

    override fun getLabel(): String = "وحدة تحكّم بايثون"

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.terminal)

    override val defaultKeybinds: KeyCombination? =
        KeyCombination(keyCode = KeyEvent.KEYCODE_K, ctrl = true, shift = true)

    // متاحة دائمًا: مفيدة حتى بلا ملف بايثون مفتوح
    override fun isSupported(context: EditorNonActionContext): Boolean = true

    override fun isEnabled(context: EditorNonActionContext): Boolean = true

    override fun execute(context: EditorActionContext) {
        val file = context.editorTab.file
        val isPython = file?.getName()?.substringAfterLast('.', "")?.lowercase() in PY_EXTENSIONS

        // ‏-i: تفاعلي · -q: بلا بانر طويل · -u: بلا تخزين مؤقّت فتظهر النتيجة
        // فورًا. لا نفترض مسار المفسّر: قد يكون python3.11 أو 3.12 أو Symlink،
        // وPATH في الـproot هو مصدر الحقيقة — لذا نُنفّذه عبر الصدفة كما تفعل
        // بقية المشغّلات، فيعمل على كل إصدار.
        //
        // ولا نمرّر الملف نفسه: `python3 -i file.py` يُنفّذه ثم يفتح الوحدة،
        // وهذا يعني آثارًا جانبية (شبكة، ملفات) بمجرّد طلب «وحدة تحكّم». بدلًا
        // من ذلك نضع مجلد الملف في PYTHONPATH، فيعمل `import mymodule` ويبقى
        // التشغيل قرارًا منفصلًا.
        // المجلد يُقرأ من المسار المطلق مباشرةً: `getParentFile()` معلّقة
        // (suspend) في تجريد [FileObject]، ولا حاجة لنداء معلّق لمجرد قصّ مسار.
        // `file?.getAbsolutePath()` نصّ قد يكون null، فالقصّ يحتاج `?.` ثانيةً —
        // وإلا فسلسلة الاستدعاء تتوقّف عند القيمة القابلة للعدم.
        val dir = file?.getAbsolutePath()?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() }
        val prefix = if (isPython && dir != null) "PYTHONPATH=\"$dir:\${PYTHONPATH:-}\" " else ""
        val script = "${prefix}exec python3 -qiu"

        TerminalLauncher.launch(
            activity = context.currentActivity,
            exe = "/bin/bash",
            args = arrayOf("-c", script),
            id = "بايثون التفاعلي",
        )
    }

    private companion object {
        val PY_EXTENSIONS = setOf("py", "pyw", "pyi")
    }
}
