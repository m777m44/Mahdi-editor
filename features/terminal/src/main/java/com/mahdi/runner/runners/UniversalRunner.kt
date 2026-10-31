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

    override fun matcher(fileObject: FileObject): Boolean {
        return Regex(
                ".*\\.(" +
                    "py|js|mjs|cjs|ts|java|kt|kts|rs|rb|php|c|cpp|cc|cxx|cs|sh|bash|zsh|fish|pl|lua|r|R|hs|" +
                        "f90|f95|f03|f08|pas|tcl|elm|fsx|fs|" +
                        // لغات أُضيفت في 2.0
                        "go|jl|groovy|scala|ml|mli|ex|exs|erl|sql|awk" +
                        ")$"
            )
            .matches(fileObject.getName())
    }

    @SuppressLint("SdCardPath")
    override suspend fun run(activity: Activity, fileObject: FileObject) {
        setupAssetFile("universal_runner")
        // محرّك التثبيت التلقائي للمكتبات (يفشل بصمت إن لم يوجد)
        setupAssetFile("auto_deps")
        // مثبّت بايثون الذكي + المخزن الدائم (يعمل حتى قبل إنشاء جلسة طرفية جديدة)
        setupPythonFile("mahdi_pip")

        if (fileObject !is FileWrapper) {
            dialogRes(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        val path = fileObject.getAbsolutePath()
        if (isOnExternalStorage(path)) {
            // ملفات /sdcard للقراءة فقط على أندرويد الحديث (Media is read-only)،
            // لذلك ننسخ السكربت إلى المجلد الرئيسي للطرفية ونشغّله من هناك.
            // التشغيل يبدأ فورًا بلا أي رسالة موافقة: زرّ ▶️ يشغّل السكربت مباشرة.
            launchFromSandboxHome(activity, fileObject)
            return
        }

        launchUniversalRunner(activity, fileObject)
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
}
