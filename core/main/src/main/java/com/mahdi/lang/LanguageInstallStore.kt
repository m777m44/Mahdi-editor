package com.mahdi.lang

import android.content.Context
import android.content.SharedPreferences
import com.mahdi.file.sandboxHomeDir
import com.mahdi.utils.application
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * «المخزون» — سجلّ اللغات المثبَّتة فعليًا داخل التطبيق (2.7.0).
 *
 * التثبيت يجري فعليًا داخل طرفية Ubuntu المدمجة (apt-get)، وعند نجاحه يكتب السكربت
 * ملفّ علامة داخل مجلد المستخدم في الـ sandbox:
 *
 * ```
 * /home/.mahdi/installed/<language-id>
 * ```
 *
 * وهذا المجلد مربوط من الجهاز إلى `sandboxHomeDir()/.mahdi/installed`، فيقرأ التطبيق
 * الملفّات مباشرة ويعرف — بلا شبكة وبلا تخمين — ما هو مثبَّت حقًا. وتُحفظ القائمة أيضًا
 * في `SharedPreferences` كمرآة سريعة للعرض الفوري قبل الفحص.
 */
object LanguageInstallStore {

    private const val PREFS = "mahdi_language_installs"
    private const val KEY_INSTALLED = "installed_ids"
    private const val MARKER_DIR = ".mahdi/installed"

    private val _installed = MutableStateFlow<Set<String>>(DefaultLanguages.bundled)

    /** معرّفات اللغات المثبَّتة (تتحدّث تلقائيًا بعد كل تثبيت أو إزالة). */
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    private val prefs: SharedPreferences?
        get() = application?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** مجلد العلامات على الجهاز — نفس ما يراه الـ sandbox باسم `/home/.mahdi/installed`. */
    val markerDir: File
        get() = File(sandboxHomeDir(), MARKER_DIR).also { runCatching { it.mkdirs() } }

    /** ملفّ علامة لغة معيّنة. */
    fun markerFile(id: String): File = File(markerDir, id)

    /** هل اللغة مثبَّتة؟ (يقرأ الحالة المحفوظة حاليًا). */
    fun isInstalled(id: String): Boolean = id in _installed.value

    /** يقرأ الملفّات من القرص + المرآة المحفوظة ويعيد بناء الحالة. */
    fun refresh() {
        val fromDisk =
            runCatching {
                    markerDir.listFiles()
                        ?.filter { it.isFile && !it.name.startsWith(".") }
                        ?.map { it.name }
                        ?.toSet()
                        ?: emptySet()
                }
                .getOrDefault(emptySet())

        val fromPrefs = prefs?.getStringSet(KEY_INSTALLED, emptySet())?.toSet() ?: emptySet()

        // 4.0.0: اللغات المدمجة (بايثون/بي إتش بي/HTML) مثبَّتة دائمًا — ليست تخمينًا بل حقيقة التطبيق
        _installed.value = fromDisk + fromPrefs + DefaultLanguages.bundled
        prefs?.edit()?.putStringSet(KEY_INSTALLED, _installed.value)?.apply()
    }

    /** يُسجّل اللغة كمثبَّتة (بعد نجاح أمر التثبيت أو بوجود علامة على القرص). */
    fun markInstalled(id: String) {
        val updated = _installed.value + id
        _installed.value = updated
        prefs?.edit()?.putStringSet(KEY_INSTALLED, updated)?.apply()
    }

    /** يزيل اللغة من المخزون (يزيل العلامة أيضًا). */
    fun unmark(id: String) {
        runCatching { markerFile(id).delete() }
        val updated = _installed.value - id
        _installed.value = updated
        prefs?.edit()?.putStringSet(KEY_INSTALLED, updated)?.apply()
    }

    // ————— إدارة الحالة الكاملة (4.0.0) —————

    /** يُعلِّم اللغة «قيد التثبيت» ويحفظ ذلك فورًا (يُقرأ عند إعادة فتح الشاشة). */
    fun markPending(id: String) {
        runCatching { File(markerDir, ".pending-$id").writeText(System.currentTimeMillis().toString()) }
    }

    /** يمحو علامة «قيد التثبيت». */
    fun clearPending(id: String) {
        runCatching { File(markerDir, ".pending-$id").delete() }
    }

    /** هل اللغة قيد التثبيت حاليًا (من القرص)؟ */
    fun isPending(id: String): Boolean = File(markerDir, ".pending-$id").exists()

    /**
     * تراجع كامل عن تثبيت لم يكتمل: يزيل العلامة والعلامة المعلّقة وملفّ الحالة
     * ويُنكر تسجيلها في التفضيلات — حتى لا تبقى لغة «نصف مثبّتة».
     */
    fun rollbackInstall(id: String) {
        clearPending(id)
        runCatching { markerFile(id).delete() }
        runCatching { File(markerDir, ".state-$id").delete() }
        unmark(id)
    }

    /**
     * حذف محلّي كامل: العلامة، ملفّ الحالة، علامة الانتظار، وسجلّ التفضيلات.
     * (إزالة الحزم نفسها تأتي من [purgeCommand] الذي يُنفَّذ في الـ sandbox.)
     */
    fun uninstall(id: String) {
        clearPending(id)
        runCatching { markerFile(id).delete() }
        runCatching { File(markerDir, ".state-$id").delete() }
        unmark(id)
    }

    /**
     * أمر إزالة الحزم من داخل الـ sandbox.
     *
     * @param includeMarkers إن كان `true` يضيف حذف العلامات إلى نفس الأمر.
     */
    fun purgeCommand(id: String, includeMarkers: Boolean = true): String {
        val packages = LanguageToolchain.packagesFor(id)
        val rm = if (includeMarkers) "; rm -f ~/$MARKER_DIR/$id ~/$MARKER_DIR/.pending-$id" else ""
        if (packages.isBlank()) return if (includeMarkers) "rm -f ~/$MARKER_DIR/$id ~/$MARKER_DIR/.pending-$id" else ""
        return "export DEBIAN_FRONTEND=noninteractive; " +
            "apt-get -y purge $packages >/dev/null 2>&1; apt-get -y autoremove >/dev/null 2>&1$rm; " +
            "echo \"✔ $id removed\""
    }

    /**
     * أمر التثبيت الكامل: يثبّت الحزم ثم يكتب علامة النجاح.
     *
     * إن كانت اللغة لا تحتاج حزمًا (جاهزة داخل الصورة) يكتب العلامة فورًا فقط.
     */
    fun installCommand(id: String): String {
        val packages = LanguageToolchain.packagesFor(id)
        val marker =
            "mkdir -p ~/$MARKER_DIR && printf '%s' \"${'$'}(date +%Y-%m-%dT%H:%M:%S)\" > ~/$MARKER_DIR/$id"
        return if (packages.isBlank()) {
            "set -e; $marker; echo \"✔ $id ready\""
        } else {
            "set -e; export DEBIAN_FRONTEND=noninteractive; " +
                "apt-get update -qq && apt-get install -y $packages; $marker; echo \"✔ $id installed\""
        }
    }

    /** أمر إزالة العلامة من داخل الـ sandbox (يُستخدم عند «إلغاء التثبيت»). */
    fun uninstallCommand(id: String): String = "rm -f ~/$MARKER_DIR/$id"

    /** عدد اللغات المثبَّتة حاليًا. */
    val installedCount: Int
        get() = _installed.value.size
}
