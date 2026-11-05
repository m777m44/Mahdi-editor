package com.mahdi.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mahdi.utils.logError
import com.mahdi.utils.okHttpClient
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * تنزيل وتثبيت التحديث داخل التطبيق (5.0.0) — بلا متصفح وبلا صفحة تنزيل.
 *
 * التقدّم يُنشر عبر [progress] (0.0 – 1.0) وتُقرأ الحالة من [status]، فتظهر
 * الواجهة شريط تقدّم حقيقيًا لا مؤشّرًا دائريًا لا معنى له.
 */
object UpdateDownloader {

    sealed interface Status {
        data object Idle : Status
        data object Downloading : Status
        data class Done(val apk: File) : Status
        data class Failed(val reason: String) : Status
    }

    private val _progress = MutableStateFlow(0f)

    /** نسبة التقدّم من 0 إلى 1. */
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** يُعيد الحالة إلى البداية قبل محاولة جديدة. */
    fun reset() {
        _progress.value = 0f
        _status.value = Status.Idle
    }

    /**
     * ينزّل ملف APK إلى مجلد التطبيق الخارجي (لا يحتاج أي صلاحية تخزين).
     * الملف هناك مكشوف لـ FileProvider المُعلَن في البيان، فيُثبَّت مباشرة.
     */
    suspend fun download(context: Context, info: UpdateInfo) {
        withContext(Dispatchers.IO) {
            _progress.value = 0f
            _status.value = Status.Downloading

            try {
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                dir.mkdirs()

                // نحذف أي نسخة قديمة حتى لا يُثبَّت ملف ناقص من محاولة سابقة
                dir.listFiles { file -> file.name.startsWith("Mahdi-Editor-") && file.extension == "apk" }
                    ?.forEach { runCatching { it.delete() } }

                val target = File(dir, "Mahdi-Editor-${info.version}.apk")

                val request =
                    Request.Builder()
                        .url(info.downloadUrl)
                        .header("User-Agent", "Mahdi-Editor-App")
                        .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _status.value = Status.Failed("HTTP ${response.code}")
                        return@withContext
                    }

                    val body = response.body
                    val total = body.contentLength()

                    body.byteStream().use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var downloaded = 0L
                            var read = input.read(buffer)
                            while (read != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) {
                                    _progress.value = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                }
                                read = input.read(buffer)
                            }
                            output.flush()
                        }
                    }

                    if (!target.exists() || target.length() <= 0L) {
                        _status.value = Status.Failed("empty file")
                        return@withContext
                    }

                    _progress.value = 1f
                    _status.value = Status.Done(target)
                }
            } catch (t: Throwable) {
                logError(t, "update download")
                _status.value = Status.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    /**
     * يفتح مُثبّت الحزمة على ملف APK الذي نزّلناه.
     *
     * @return `false` إن كانت صلاحية «تثبيت تطبيقات من هذا المصدر» غير ممنوحة،
     * أو إن تعذّر إطلاق المُثبّت — لتُظهر الواجهة زرّ السماح بدل الفشل الصامت.
     */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists()) return false
        if (!AppUpdateChecker.canInstall(context)) return false

        return runCatching {
                val uri: Uri =
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)

                val intent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                context.startActivity(intent)
                true
            }
            .getOrElse {
                logError(it, "install update")
                false
            }
    }
}
