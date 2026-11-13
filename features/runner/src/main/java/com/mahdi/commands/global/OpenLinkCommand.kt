package com.mahdi.commands.global

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import com.mahdi.commands.ActionContext
import com.mahdi.commands.GlobalCommand
import com.mahdi.commands.KeyCombination
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.runner.RunnerUI

/**
 * «فتح رابط» (9.2.0) — يفتح المتصفّح الداخلي على رابط أو بحث.
 *
 * إن كانت الحافظة تحتوي رابطًا (أو نطاقًا) يُملأ المربّع به تلقائيًا، فتصبح
 * العملية ضغطتين: انسخ الرابط من أي تطبيق ← «فتح رابط» ← «فتح».
 */
object OpenLinkCommand : GlobalCommand() {
    override val id: String = "global.open_link"

    override fun getLabel(): String = strings.open_link.getString()

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.open_in_new)

    override fun execute(context: ActionContext) {
        RunnerUI.pendingLink = clipboardLink(context.currentActivity).orEmpty()
        RunnerUI.showLinkDialog.value = true
    }

    override val defaultKeybinds: KeyCombination =
        KeyCombination(keyCode = KeyEvent.KEYCODE_L, ctrl = true, shift = true)
}

/** رابط جاهز من الحافظة (يُقبل النطاق المجرّد مثل `github.com` أيضًا). */
private fun clipboardLink(context: Context): String? {
    val text =
        runCatching {
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip: ClipData? = manager.primaryClip
                clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            }
            .getOrNull()
            ?.trim()
            ?: return null

    if (text.isEmpty() || text.length > 2048) return null
    val looksLikeLink =
        text.startsWith("http://") ||
            text.startsWith("https://") ||
            text.startsWith("www.") ||
            (text.contains('.') && !text.contains(' ') && !text.contains('\n'))
    return if (looksLikeLink) text else null
}
