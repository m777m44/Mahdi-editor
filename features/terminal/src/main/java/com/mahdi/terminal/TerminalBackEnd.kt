package com.mahdi.terminal

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.mahdi.activities.terminal.Terminal
import com.mahdi.utils.dpToPx
import com.mahdi.settings.Settings
import com.mahdi.settings.terminal.TerminalCursorStyle
import com.mahdi.terminal.virtualkeys.SpecialButton
import com.mahdi.utils.logError
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient

class TerminalBackEnd : TerminalViewClient, TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {
        terminalView.get()?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {}

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        ClipboardUtils.copyText("Terminal", text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clip = ClipboardUtils.getText().toString()
        val emulator = terminalView.get()?.mEmulator ?: return
        if (clip.isNotBlank()) {
            emulator.paste(clip)
        }
    }

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {}

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun getTerminalCursorStyle(): Int {
        return when (Settings.terminal_cursor_style) {
            TerminalCursorStyle.BAR.value -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR
            TerminalCursorStyle.UNDERLINE.value -> TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE
            else -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
        }
    }

    override fun logError(tag: String?, message: String?) {
        Log.e(tag.toString(), message.toString())
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag.toString(), message.toString())
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag.toString(), message.toString())
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag.toString(), message.toString())
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag.toString(), message.toString())
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag.toString(), message.toString())
        e?.let { logError(it) }
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        e?.let { logError(it) }
    }

    /** حجم الخط الحالي بالبكسل — مصدر الحقيقة أثناء الإيماءة. */
    private var zoomPx = 0f

    /**
     * آخر قيمة أعدناها إلى `TerminalView`؛ هي التي يُضرب فيها عامل الإيماءة التالي،
     * فنحسب الخطوة الحقيقية = (القادم ÷ آخر قيمة).
     */
    private var lastReturned = 0f

    /** آخر قيمة حفظناها في الإعدادات — لكشف أي تغيير خارجي (أزرار + و −). */
    private var lastSavedDp = Int.MIN_VALUE

    /** وقت آخر إعادة بناء للمُصيّر — إطار واحد على الأكثر لكل 16ms. */
    private var lastRebuildAt = 0L

    /**
     * تكبير/تصغير بإصبعين — ناعم بلا اهتزاز (3.0.0).
     *
     * المشكلة القديمة: كان كل حدث لمس يُقرأ كأنه «حجم خط» فيُقصّ إلى 11px، ثم يعيد بناء
     * `TerminalRenderer` بالكامل — فتظهر قفزات وارتعاش. الحل:
     * 1. نفهم ما يرسله العرض: حاصل ضرب عوامل الإيماءة × آخر قيمة أعدناها، فنستخرج الخطوة.
     * 2. نضرب الحجم الحالي في الخطوة ونقصّه بين 8 و32dp (نفس حدود شريط الأدوات).
     * 3. لا نعيد بناء المُصيّر إلا عند تغيّر البكسل فعلًا، وبمعدّل إطار واحد كل 16ms.
     * 4. نحفظ الحجم بوحدات dp ليبقى بعد إعادة التشغيل، ونلتقط أي تغيير خارجي للخط.
     */
    override fun onScale(scale: Float): Float {
        val view = terminalView.get() ?: return scale
        val context = view.context
        val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
        val settingsPx = dpToPx(Settings.terminal_font_size.toFloat(), context).toFloat()

        // تغيير خارجي للحجم (شريط الأدوات) ← أعد الضبط من الإعدادات
        if (zoomPx <= 0f || Settings.terminal_font_size != lastSavedDp) {
            zoomPx = settingsPx.coerceAtLeast(1f)
            lastSavedDp = Settings.terminal_font_size
            lastReturned = zoomPx
        }

        val step = if (lastReturned > 0f) scale / lastReturned else 1f
        if (step.isFinite() && step in 0.5f..2f) {
            val minPx = dpToPx(8f, context).toFloat()
            val maxPx = dpToPx(32f, context).toFloat()
            val target = (zoomPx * step).coerceIn(minPx, maxPx)
            val snapped = Math.round(target).toFloat()
            val now = android.os.SystemClock.uptimeMillis()
            if (snapped != zoomPx && now - lastRebuildAt >= 16L) {
                zoomPx = snapped
                lastRebuildAt = now
                view.setTextSize(snapped.toInt())

                val dp = Math.round(snapped / density).coerceIn(8, 32)
                Settings.terminal_font_size = dp
                lastSavedDp = dp
            }
        }

        // ما نعيده يصبح أساس الضرب في الحدث التالي
        lastReturned = zoomPx
        return zoomPx
    }

    override fun onSingleTapUp(e: MotionEvent) {
        showSoftInput()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean {
        return false
    }

    override fun shouldEnforceCharBasedInput(): Boolean {
        return true
    }

    override fun shouldUseCtrlSpaceWorkaround(): Boolean {
        return true
    }

    override fun shouldSupportClipboardKeybindings(): Boolean {
        return Settings.terminal_clipboard_keybindings
    }

    override fun isTerminalViewSelected(): Boolean {
        return true
    }

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning) {
            val activity = Terminal.instance ?: return false
            val sessionBinder = activity.sessionBinder?.get() ?: return false
            sessionBinder.terminateSession(sessionBinder.getService().currentSession.value)
            if (sessionBinder.getService().sessionList.value.isEmpty()) {
                activity.finish()
            } else {
                activity.changeSession(sessionBinder.getService().sessionList.value.first())
            }
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        return false
    }

    override fun onLongPress(event: MotionEvent): Boolean {
        return false
    }

    // keys
    override fun readControlKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(SpecialButton.CTRL, true)
        return state != null && state
    }

    override fun readAltKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(SpecialButton.ALT, true)
        return state != null && state
    }

    override fun readShiftKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(SpecialButton.SHIFT, true)
        return state != null && state
    }

    override fun readFnKey(): Boolean {
        val state = virtualKeysView.get()?.readSpecialButton(SpecialButton.FN, true)
        return state != null && state
    }

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        return false
    }

    override fun onEmulatorSet() {
        setTerminalCursorBlinkingState(true)
    }

    private fun setTerminalCursorBlinkingState(start: Boolean) {
        if (terminalView.get()?.mEmulator != null) {
            terminalView.get()?.setTerminalCursorBlinkerState(start, true)
        }
    }

    private fun showSoftInput() {
        terminalView.get()?.requestFocus()
        terminalView.get()?.let { KeyboardUtils.showSoftInput(it) }
    }
}
