package com.mahdi.terminal

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.KeyboardUtils
import com.mahdi.activities.terminal.Terminal
import com.mahdi.settings.Settings
import com.mahdi.settings.terminal.TerminalCursorStyle
import com.mahdi.terminal.virtualkeys.SpecialButton
import com.mahdi.utils.dpToPx
import com.mahdi.utils.logError
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient
import kotlin.math.abs
import kotlin.math.roundToInt

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

    // ————— حالة التكبير (3.3.0) —————
    /** الحجم المرجعي (sp) عند بداية الإيماءة. */
    private var baseSp = 0f

    /** عامل التراكم منذ بداية الإيماءة. */
    private var zoom = 1f

    /** آخر عامل أعدناه إلى [com.termux.view.TerminalView]. */
    private var lastReturned = 1f

    /** آخر حجم طُبّق فعلًا (sp) — لكشف التغيير الخارجي. */
    private var appliedSp = 0f

    /** وقت آخر حدث تكبير. */
    private var lastScaleAt = 0L

    /** آخر قيمة أعدناها في حال وصول نسبة غير صالحة. */
    private var lastZoom = 1f

    private companion object {
        const val MIN_FONT_SP = 8f
        const val MAX_FONT_SP = 32f
        const val ZOOM_STEP_SP = 0.5f
        const val GESTURE_GAP_MS = 220L
    }

    /**
     * تكبير/تصغير بإصبعين — متدرّج بلا قفزات (3.3.0).
     *
     * كانت المشكلة: `TerminalView` يمرّر **حاصل ضرب عوامل الإيماءة** لا الحجم، فإذا عومل
     * كأنه حجم خط (أو قُصَّ إلى حدّ أدنى) قفز الخط من 12 إلى 11 أو 3 فورًا عند أول حركة.
     *
     * الحل الآن ثلاثي:
     * 1. **حجم مرجعي** [baseSp] يُلتقط عند بداية كل إيماءة، و[zoom] تراكم نسبي يبدأ من 1.
     *    الحجم المستهدف = baseSp × zoom، فلا تراكم أسّي ولا قفزة افتتاحية.
     * 2. **تنعيم** [smoothStep]: الحجم المعروض يقترب من الهدف بخطوة 0.5sp كحدّ أقصى للمرّة،
     *    فتبدو الحركة متدرّجة حتى لو وصلت أحداث اللمس متباعدة.
     * 3. **حفظ تلقائي**: كل خطوة تُحفظ في الإعدادات، فيبقى الحجم بعد إعادة التشغيل،
     *    ولا يعيد أي recomposition ضبطه إلى القيمة القديمة.
     *
     * المدى 8–32sp، وأي تغيير خارجي (زرّا + و −) يُلتقط تلقائيًا كنقطة مرجعية جديدة.
     */
    override fun onScale(scale: Float): Float {
        val view = terminalView.get() ?: return scale
        if (!scale.isFinite()) return lastZoom
        val now = SystemClock.uptimeMillis()
        val settingsSp = Settings.terminal_font_size.toFloat().coerceIn(MIN_FONT_SP, MAX_FONT_SP)

        val idleGap = now - lastScaleAt > GESTURE_GAP_MS
        val externalChange = abs(settingsSp - appliedSp) > 1.5f

        // بداية إيماءة جديدة أو تغيير خارجي: التقط المرجع وأعد ضبط التراكم
        if (baseSp <= 0f || idleGap || externalChange) {
            baseSp = settingsSp
            zoom = 1f
            appliedSp = settingsSp
            // نعيد 1 ونعتمدها أساسًا للحدث القادم: العرض يضربها في نسبة الإيماءة التالية،
            // فتُستخرج الخطوة الحقيقية بلا أي قفزة افتتاحية مهما كانت قيمته القديمة.
            lastReturned = 1f
            lastZoom = 1f
            lastScaleAt = now
            return 1f
        }

        lastScaleAt = now

        // الخطوة الحقيقية = النسبة الجديدة ÷ آخر قيمة أعدناها
        val rawStep = if (lastReturned != 0f) scale / lastReturned else 1f
        val step = if (rawStep.isFinite()) rawStep.coerceIn(0.5f, 2f) else 1f

        zoom = (zoom * step).coerceIn(MIN_FONT_SP / baseSp, MAX_FONT_SP / baseSp)
        val target = (baseSp * zoom).coerceIn(MIN_FONT_SP, MAX_FONT_SP)

        // تنعيم الخطوة: 0.5sp على الأكثر في التحديث الواحد
        val smoothed = smoothStep(appliedSp, target, ZOOM_STEP_SP)
        if (abs(smoothed - appliedSp) >= 0.1f) {
            appliedSp = smoothed
            view.setTextSize(dpToPx(smoothed, view.context))
            // حفظ القيمة المقرّبة (8..32) ليبقى الحجم بعد إعادة الفتح
            Settings.terminal_font_size = smoothed.roundToInt().coerceIn(MIN_FONT_SP.toInt(), MAX_FONT_SP.toInt())
        }

        // نُعيد عامل التراكم نفسه الذي سنقسم عليه في الحدث القادم
        lastReturned = zoom
        lastZoom = zoom
        return zoom
    }

    /** يقترب من الهدف بخطوة [maxStep] كحدّ أقصى. */
    private fun smoothStep(current: Float, target: Float, maxStep: Float): Float {
        val delta = target - current
        return when {
            abs(delta) <= maxStep -> target
            delta > 0 -> current + maxStep
            else -> current - maxStep
        }
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
