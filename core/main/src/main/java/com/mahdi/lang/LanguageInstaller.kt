package com.mahdi.lang

import android.app.Activity
import com.mahdi.TerminalLauncher
import com.mahdi.runtime.RuntimeCatalog
import com.mahdi.runtime.RuntimeInstaller
import com.mahdi.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * دورة حياة تثبيت/إزالة اللغات (4.0.0) — حالة واحدة صادقة لكل لغة.
 *
 * كانت المشكلة: التثبيت يفتح الطرفية وينسى، والحالة تُخمَّن من نصّ الشاشة، فتبقى
 * اللغة «قيد التثبيت» للأبد أو تظهر مثبَّتة وهي ليست كذلك. هنا:
 *
 * 1. **قبل البدء** تُسجَّل اللغة في [pending] فتعرض الواجهة «قيد التثبيت» فورًا.
 * 2. **التحقّق من القرص** لا من الواجهة: كل ثانية يُقرأ ملفّ العلامة الحقيقي،
 *    فالنجاح لا يُعلَن إلا بعد أن يكتبه السكربت.
 * 3. **مهلة صريحة 120 ثانية** ثم فشل مع سبب، و**تراجع (rollback)** يحذف العلامة
 *    المعلّقة حتى لا تبقى لغة «نصف مثبّتة».
 * 4. **الإزالة** تحذف العلامة وملفّ الحالة وسجلّ التفضيلات، وتُنادي
 *    `apt-get purge` داخل الـ sandbox عندما تكون هناك حزم، ويُلغى تسجيل بيئة التشغيل.
 */
object LanguageInstaller {

    private const val TIMEOUT_MS = 120_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watchers = mutableMapOf<String, Job>()

    private val _pending = MutableStateFlow<Set<String>>(emptySet())

    /** اللغات التي يجري تثبيتها الآن. */
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())

    /** رسالة الفشل لكل لغة (تُمحى عند إعادة المحاولة أو النجاح). */
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    /** هل التثبيت ممكن الآن؟ (لا بدّ من نشاط لفتح الطرفية) */
    fun canInstall(): Boolean = TerminalLauncher.handler != null

    /**
     * يبدأ تثبيت لغة.
     *
     * @return `false` إن لم يكن التثبيت ممكنًا (لا طرفية متاحة).
     */
    fun install(activity: Activity?, lang: CodeLanguage): Boolean {
        // المدمجة جاهزة أصلًا — نثبّتها في المخزون بلا طرفية ولا انتظار
        if (DefaultLanguages.isBundled(lang.id)) {
            LanguageInstallStore.markInstalled(lang.id)
            return true
        }
        if (activity == null || !canInstall()) return false

        clearError(lang.id)
        _pending.value = _pending.value + lang.id
        LanguageInstallStore.markPending(lang.id)

        TerminalLauncher.launch(
            activity = activity,
            exe = "/bin/bash",
            args = arrayOf("-lc", LanguageInstallStore.installCommand(lang.id)),
            id = "install-${lang.id}",
            terminatePreviousSession = true,
        )
        watch(lang.id)
        return true
    }

    /** يراقب لغة حتى تنجح أو تنتهي المهلة، ثم ينظّف الحالة. */
    private fun watch(id: String) {
        watchers.remove(id)?.cancel()
        watchers[id] =
            scope.launch {
                val started = System.currentTimeMillis()
                try {
                    while (true) {
                        delay(1000)
                        LanguageInstallStore.refresh()
                        if (LanguageInstallStore.isInstalled(id)) {
                            LanguageInstallStore.clearPending(id)
                            _errors.value = _errors.value - id
                            return@launch
                        }
                        // علامة موجودة على القرص لكنها لم تُقرأ بعد؟ refresh يتكفّل بها
                        if (System.currentTimeMillis() - started > TIMEOUT_MS) {
                            // تراجع: لا نترك علامة معلّقة ولا حالة «قيد التثبيت»
                            LanguageInstallStore.rollbackInstall(id)
                            _errors.value =
                                _errors.value + (id to (if (isArabic()) "انتهت المهلة" else "Timed out"))
                            return@launch
                        }
                    }
                } finally {
                    _pending.value = _pending.value - id
                }
            }
    }

    /**
     * يزيل لغة بالكامل.
     *
     * @return `false` إن كانت مدمجة (لا تُحذف).
     */
    fun uninstall(activity: Activity?, lang: CodeLanguage): Boolean {
        if (DefaultLanguages.isBundled(lang.id)) return false

        watchers.remove(lang.id)?.cancel()
        _pending.value = _pending.value - lang.id
        _errors.value = _errors.value - lang.id

        // 1) إلغاء تسجيل بيئة التشغيل إن كانت مربوطة بهذه اللغة
        RuntimeCatalog.forLanguage(lang.id)?.let { spec -> runCatching { RuntimeInstaller.forget(spec.id) } }

        // 2) حذف كل علامات/ملفات الحالة + سجلّ التفضيلات (هذه هي «الحذف الكامل» المحلي)
        LanguageInstallStore.uninstall(lang.id)

        // 3) إزالة الحزم الفعلية من الـ sandbox إن وُجدت — وإلا فلا شيء نُنفّذه
        val command = LanguageInstallStore.purgeCommand(lang.id, includeMarkers = false)
        if (command.isNotBlank() && activity != null && canInstall()) {
            TerminalLauncher.launch(
                activity = activity,
                exe = "/bin/bash",
                args = arrayOf("-lc", command),
                id = "uninstall-${lang.id}",
                terminatePreviousSession = true,
            )
        }
        return true
    }

    /** يمحو رسالة الفشل عند إعادة المحاولة. */
    fun clearError(id: String) {
        _errors.value = _errors.value - id
    }

    private fun isArabic(): Boolean = Settings.current_lang.startsWith("ar")
}
