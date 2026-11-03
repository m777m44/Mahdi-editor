package com.mahdi.commands.editor

import com.mahdi.DefaultScope
import com.mahdi.commands.Command
import com.mahdi.commands.EditorActionContext
import com.mahdi.commands.EditorCommand
import com.mahdi.commands.EditorNonActionContext
import com.mahdi.encoding.EncodingManager
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.tabs.editor.EditorTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * «الترميز» (4.1.0) — قائمة ترميزات حقيقية لكل ملف على حدة.
 *
 * سبب إضافتها: الترميز كان إعدادًا **عامًّا** فقط، فمن فتح ملفًا بترميز Windows-1256
 * أو UTF-16 ورأى رموزًا مشوّهة لم يجد طريقة لتصحيح العرض، وكان الحفظ أحيانًا يستبدل
 * الأحرف العربية بـ `?` بلا إنذار.
 *
 * الآن: اختيار أي ترميز **يعيد قراءة الملف** به ويجعله ترميز الحفظ لهذا الملف،
 * والمحرّر يتحوّل تلقائيًا إلى UTF-8 بدل إتلاف النص عند تعذّر الترميز.
 */
class EncodingCommand : EditorCommand() {
    override val id: String = "editor.encoding"

    override fun getLabel(): String = strings.encoding_menu.getString()

    override fun execute(context: EditorActionContext) {
        // القائمة تُفتح تلقائيًا لأن childCommands غير فارغة
    }

    override fun isEnabled(context: EditorNonActionContext): Boolean = context.editorTab.file != null

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.text)

    override fun getChildSearchPlaceholder(): String = strings.encoding_menu.getString()

    /**
     * تُبنى عند كل فتح حتى تحمل علامة ✓ الترميز الحالي للملف الذي نحرّره الآن.
     */
    override val childCommands: List<Command>
        get() {
            val current =
                runCatching {
                    (commandContext.mainViewModel.currentTab as? EditorTab)?.currentCharset
                }.getOrNull()
            return EncodingManager.supported.map { encoding ->
                val isCurrent = current != null && current.name().equals(encoding.name, ignoreCase = true)
                object : EditorCommand() {
                    override val id: String = "editor.encoding.${encoding.name.lowercase().replace('-', '_')}"

                    override fun getLabel(): String =
                        // الاسم العربي + الاسم التقني، مع ✓ للترميز المستخدم الآن
                        if (isCurrent) "✓ ${encoding.displayName}" else encoding.displayName

                    override fun execute(context: EditorActionContext) {
                        DefaultScope.launch(Dispatchers.IO) {
                            context.editorTab.reopenWithEncoding(encoding.charset)
                        }
                    }

                    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.text)

                    override fun isEnabled(context: EditorNonActionContext): Boolean =
                        context.editorTab.file != null
                }
            }
        }
}
