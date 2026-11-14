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
import com.mahdi.file.localBinDir
import com.mahdi.file.sandboxHomeDir
import com.mahdi.icons.Icon
import com.mahdi.lang.LanguageRegistry
import com.mahdi.lsp.LspMemoryGuard
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

/**
 * سقوف نسخ المشروع إلى مجلد التشغيل (9.3.0).
 *
 * `object` لا يقبل `companion object` في Kotlin، فالثوابت هنا على مستوى الملف.
 */
private const val MAX_COPY_FILES = 400
private const val MAX_COPY_BYTES = 60L * 1024 * 1024
private const val MAX_COPY_FILE_BYTES = 12L * 1024 * 1024
private const val MAX_COPY_DEPTH = 4

/** مجلدات ثقيلة لا تُنسخ مع المشروع. */
private val SKIP_NAMES =
    setOf(
        "node_modules",
        ".git",
        "__pycache__",
        ".venv",
        "venv",
        "env",
        "build",
        "dist",
        ".gradle",
        ".idea",
        ".vscode",
        "target",
        ".next",
    )

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
        // 5.5.0: إصلاح ذاتي — يلتقط المكتبة الناقصة من stderr ويثبّتها ويعيد التشغيل
        setupAssetFile("self_heal")
        setupAssetFile("progress")
        // مثبّت بايثون الذكي + المخزن الدائم (يعمل حتى قبل إنشاء جلسة طرفية جديدة)
        setupPythonFile("mahdi_pip")
        setupPythonFile("mahdi_heal")

        // 9.1.0: تفريغ الذاكرة قبل التشغيل — خوادم اللغة (Pyright وNode) تستهلك
        // مئات الميغابايتات، وكان قاتل الذاكرة يقتل جلسة الطرفية بعد ثوانٍ من
        // تشغيل سكربت ثقيل (‏`[Process completed (signal 9)]`).
        // الإيقاف مؤقت، محميّ، ولا يتوقّف تشغيل السكربت إن فشل.
        runCatching { LspMemoryGuard.freezeForRun() }

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
        // 9.3.0: الحروف العربية ليست «أحرفًا غريبة». كان الاستبدال يحوّل
        // `بيانات.py` إلى `py` (بلا امتداد!) فيفشل التعرّف على اللغة.
        // الآن نُزيل فقط ما يفسد المسارات ومحارف التحكّم، ونُبقي كل حرف آخر.
        val cleaned =
            base.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|\u0000]"), "_").trim().trim('.')
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

        // 9.3.0 — «المشروع لا الملف»:
        //
        // كان النسخ ينقل **ملفًا واحدًا** إلى /home/mahdi-run، فمشروع من GitHub
        // (‏main.py + utils.py + requirements.txt + data/) يُشغَّل وحده فينهار عند
        // أول `import utils` أو أوّل قراءة ملف مجاور — ويبدو كأن التطبيق «ينهار
        // عند تشغيل أي مشروع». الآن ننسخ مجلد المشروع كاملًا (بحدود آمنة) ونشغّل
        // الملف داخل نسخته، فتعمل الواردات النسبية والملفات المرجعية.
        val parent = runCatching { fileObject.getParentFile() }.getOrNull()
        val folderName =
            runCatching { parent?.getName().orEmpty() }.getOrDefault("").ifBlank { "project" }
        val projectName = sanitizeName(folderName).ifBlank { "project" }
        val projectDir = File(runDir, projectName)

        val copiedProject =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (parent == null || !parent.isDirectory()) {
                        false
                    } else {
                        projectDir.deleteRecursively()
                        projectDir.mkdirs()
                        val budget = CopyBudget()
                        // 9.3.1: النسخ لم يعد «كل شيء أو لا شيء». كان أي ملف
                        // واحد غير مقروء (أو تجاوز الحدّ) يُسقط النسخة كاملة،
                        // فيُشغَّل الملف وحده وتضيع مجلدات المشروع (‏core/ ·
                        // utils/) — وهو بالضبط سبب «المكتبة core غير مثبّتة».
                        copyProjectTree(parent, projectDir, budget, depth = 0)
                        File(projectDir, name).exists()
                    }
                }.getOrDefault(false)
            }

        // مسار التشغيل: نسخة المشروع إن نجحت، وإلا الملف وحده (سلوك 4.2.0)
        val runPath: String
        val workDir: String
        if (copiedProject) {
            runPath = "/home/mahdi-run/$projectName/$name"
            workDir = "/home/mahdi-run/$projectName"
        } else {
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
            runPath = "/home/mahdi-run/$name"
            workDir = "/home/mahdi-run"
        }

        launchTerminal(
            activity = activity,
            terminalCommand =
                TerminalCommand(
                    sandbox = true,
                    exe = "/bin/bash",
                    args = arrayOf(localBinDir().child("universal_runner").absolutePath, runPath),
                    id = strings.universal_runner.getString(),
                    terminatePreviousSession = true,
                    workingDir = workDir,
                ),
        )
    }

    /** حدود نسخ المشروع: لا ننسخ مستودعًا ضخمًا بلا سقف. */
    private class CopyBudget(
        var files: Int = 0,
        var bytes: Long = 0,
    ) {
        val exhausted: Boolean
            get() = files >= MAX_COPY_FILES || bytes >= MAX_COPY_BYTES
    }

    /**
     * ينسخ شجرة مشروع صغيرة إلى مجلد التشغيل، مع تجاهل المجلدات الثقيلة
     * (‏node_modules · .git · __pycache__ · venv) والملفات الضخمة.
     *
     * 9.3.1: **لا يُسقط النسخة بسبب ملف واحد.** أي عنصر يتعذّر قراءته أو
     * يتجاوز الحدّ يُتجاوز، وما نُسخ يبقى. هذا هو الفرق بين مشروع يعمل ومشروع
     * ينهار عند أول `import core`.
     */
    private suspend fun copyProjectTree(
        source: FileObject,
        target: File,
        budget: CopyBudget,
        depth: Int,
    ) {
        if (depth > MAX_COPY_DEPTH || budget.exhausted) return

        // 9.3.1: المجلدات أولًا — حزم المشروع (‏core/ · utils/) تُنسخ قبل أن
        // تستهلك الميزانية ملفات الصور والبيانات.
        val children =
            runCatching { source.listFiles() }.getOrNull()?.sortedWith(
                compareBy({ child -> runCatching { !child.isDirectory() }.getOrDefault(true) })
            ) ?: return

        for (child in children) {
            if (budget.exhausted) return
            val childName = runCatching { child.getName() }.getOrNull().orEmpty()
            if (childName.isEmpty() || childName in SKIP_NAMES) continue

            val destination = File(target, childName)
            val isDir = runCatching { child.isDirectory() }.getOrDefault(false)

            if (isDir) {
                destination.mkdirs()
                copyProjectTree(child, destination, budget, depth + 1)
            } else {
                val size = runCatching { child.length() }.getOrDefault(0L)
                if (size > MAX_COPY_FILE_BYTES) continue
                if (budget.bytes + size > MAX_COPY_BYTES) continue
                val copied =
                    runCatching {
                            child.getInputStream().use { input ->
                                destination.outputStream().use { output -> input.copyTo(output) }
                            }
                            true
                        }
                        .getOrDefault(false)
                if (copied) {
                    budget.files += 1
                    budget.bytes += size
                }
            }
        }
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
