package com.mahdi.runner.runners

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Environment
import com.mahdi.exec.TerminalCommand
import com.mahdi.exec.launchTerminal
import com.mahdi.file.FileObject
import com.mahdi.file.FileWrapper
import com.mahdi.file.child
import com.mahdi.file.sandboxHomeDir
import com.mahdi.file.localBinDir
import com.mahdi.icons.Icon
import com.mahdi.lang.LanguageRegistry
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.runner.FileRunner
import com.mahdi.terminal.setupAssetFile
import com.mahdi.terminal.setupPythonFile
import com.mahdi.utils.dialogRes
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UniversalRunner : FileRunner() {

    override val id = "universal"
    override val label = strings.universal_runner.getString()
    override val description = strings.universal_runner_desc.getString()

    /**
     * كل لغات `LanguageRegistry` قابلة للتشغيل المباشر في 2.5.0.
     *
     * القائمة تُبنى من الامتدادات نفسها، فلا تبقى لغة «تحرير فقط» بعد اليوم.
     */
    override fun matcher(fileObject: FileObject): Boolean {
        val name = fileObject.getName()
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return false
        return extension in RUNNABLE_EXTENSIONS
    }

    @SuppressLint("SdCardPath")
    override suspend fun run(activity: Activity, fileObject: FileObject) {
        setupAssetFile("universal_runner")
        // محرّك التثبيت التلقائي للمكتبات (يفشل بصمت إن لم يوجد)
        setupAssetFile("auto_deps")
        // مثبّت بايثون الذكي + المخزن الدائم (يعمل حتى قبل إنشاء جلسة طرفية جديدة)
        setupPythonFile("mahdi_pip")

        // 4.2.0: أي ملف من أي مكان — بما فيه مستندات فُتحت من تطبيق آخر (content://)
        if (fileObject !is FileWrapper) {
            launchFromAnywhere(activity, fileObject)
            return
        }

        val path = fileObject.getAbsolutePath()
        if (isOnExternalStorage(path) && !hasAllFilesAccess()) {
            // بلا إذن «كل الملفات» يكون /sdcard للقراءة فقط، فننسخ السكربت إلى مجلد
            // الطرفية القابل للكتابة. أما ومع الإذن فنشغّله في مكانه ليصل إلى
            // ملفاته المجاورة (imports، بيانات، مجلدات مشروع) بلا نسخ ولا فقدان.
            launchFromSandboxHome(activity, fileObject)
            return
        }

        launchUniversalRunner(activity, fileObject)
    }

    /**
     * هل يملك التطبيق إذن «الوصول لكل الملفات»؟
     *
     * بدونه تظهر /sdcard داخل الطرفية فارغة أو للقراءة فقط على أندرويد 11+، وهذا
     * السبب الحقيقي لقصر التشغيل على مجلد Download أو على مجلد التطبيق.
     */
    private fun hasAllFilesAccess(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            runCatching { android.os.Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            true
        }

    /**
     * تشغيل مستند ليس ملفًا مباشرًا (‏content:// من أي مزوّد: التنزيلات، Drive،
     * مدير ملفات آخر…). ننسخ بايتاته إلى `/home/mahdi-run` داخل الطرفية ثم نشغّله
     * هناك — فيعمل من **أي مكان في ملفات الهاتف** لا من Download وحده.
     */
    private suspend fun launchFromAnywhere(activity: Activity, fileObject: FileObject) {
        val name = sanitizeName(fileObject.getName())
        val runDir = sandboxHomeDir().child("mahdi-run").also { it.mkdirs() }
        val target = File(runDir, name)

        val copied =
            withContext(Dispatchers.IO) {
                runCatching {
                    fileObject.getInputStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.exists() && target.length() > 0
                }.getOrDefault(false)
            }

        if (!copied) {
            dialogRes(title = strings.attention.getString(), msg = strings.unreadable_file.getString(), onOk = {})
            return
        }

        launchTerminal(
            activity = activity,
            terminalCommand =
                TerminalCommand(
                    sandbox = true,
                    exe = "/bin/bash",
                    args = arrayOf(localBinDir().child("universal_runner").absolutePath, "/home/mahdi-run/$name"),
                    id = strings.universal_runner.getString(),
                    terminatePreviousSession = true,
                    workingDir = "/home/mahdi-run",
                ),
        )
    }

    /** يمنع أسماء فيها مسارات أو أحرف غريبة عند النسخ إلى مجلد التشغيل. */
    private fun sanitizeName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_', '.')
        return if (cleaned.isBlank()) "mahdi_script" else cleaned
    }

    /** هل المسار على التخزين الخارجي (‏/sdcard أو /storage)؟ */
    @SuppressLint("SdCardPath")
    private fun isOnExternalStorage(path: String): Boolean =
        path.startsWith("/sdcard") ||
            path.startsWith("/storage/") ||
            path.startsWith(Environment.getExternalStorageDirectory().absolutePath)

    /**
     * ينسخ الملف إلى `~/mahdi-run` داخل الطرفية ثم يشغّله هناك.
     *
     * السبب: مجلد `/sdcard` جاهز للقراءة فقط في أندرويد 11+، فتشغيل الملف من مكانه يمنع السكربت
     * من كتابة أي مخرجات بجانبه، ويمنع `chmod`. النسخة تعمل في مجلد قابل للكتابة.
     */
    suspend fun launchFromSandboxHome(activity: Activity, fileObject: FileObject) {
        val name = fileObject.getName()
        val runDir = sandboxHomeDir().child("mahdi-run").also { it.mkdirs() }
        val target = File(runDir, name)

        val copied =
            withContext(Dispatchers.IO) {
                runCatching {
                    fileObject.getInputStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.exists()
                }.getOrDefault(false)
            }

        if (!copied) {
            // تعذّر النسخ: نشغّل الملف الأصلي كما هو
            launchUniversalRunner(activity, fileObject)
            return
        }

        launchTerminal(
            activity = activity,
            terminalCommand =
                TerminalCommand(
                    sandbox = true,
                    exe = "/bin/bash",
                    args = arrayOf(localBinDir().child("universal_runner").absolutePath, "/home/mahdi-run/$name"),
                    id = strings.universal_runner.getString(),
                    terminatePreviousSession = true,
                    workingDir = "/home/mahdi-run",
                ),
        )
    }

    suspend fun launchUniversalRunner(activity: Activity, fileObject: FileObject) {
        launchTerminal(
            activity = activity,
            terminalCommand =
                TerminalCommand(
                    sandbox = true,
                    exe = "/bin/bash",
                    args = arrayOf(localBinDir().child("universal_runner").absolutePath, fileObject.getAbsolutePath()),
                    id = strings.universal_runner.getString(),
                    terminatePreviousSession = true,
                    workingDir = fileObject.getParentFile()?.getAbsolutePath() ?: "/",
                ),
        )
    }

    override fun getIcon(context: Context): Icon {
        return Icon.ResourceIcon(drawables.run)
    }

    /** امتدادات إضافية عملية تُشغَّل عبر المحرّك الموحّد. */
    private val EXTRA_EXTENSIONS =
        setOf(
            "pyw", "mjs", "cjs", "tsx", "cc", "cxx", "kts", "htm", "xhtml",
            "zsh", "fish", "svg", "yml", "markdown", "s", "cmd", "cfg", "conf",
            "patch", "log", "f90", "f95", "f03", "f08", "awk", "fsx", "fs", "elm",
            // 4.1.0: حِزَم وسكربتات إضافية يدعمها المشغّل الشامل الآن
            "pyz", "zipapp", "jar", "cgi",
        )

    /**
     * امتدادات اللغات القابلة للتشغيل.
     *
     * **مصدر واحد للحقيقة:** تُشتق مباشرةً من `LanguageRegistry`، فأي لغة تُضاف هناك تصبح
     * قابلة للتشغيل تلقائيًا بلا تعديل يدوي هنا. وأُضيفت بضع الامتدادات الشائعة التي لا تظهر
     * كمدخلات لغة مستقلة (ملفات البناء والسجلات والفروقات).
     */
    private val RUNNABLE_EXTENSIONS: Set<String> by lazy {
        LanguageRegistry.all.flatMap { language -> language.extensions.map { it.lowercase() } }
            .toSet() + EXTRA_EXTENSIONS
    }

}
