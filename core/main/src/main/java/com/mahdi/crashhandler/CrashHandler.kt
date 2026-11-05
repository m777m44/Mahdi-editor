package com.mahdi.crashhandler

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.util.Log
import com.mahdi.file.child
import com.mahdi.file.createDirIfNot
import com.mahdi.settings.debugOptions.HarmlessException
import com.mahdi.utils.application
import com.mahdi.utils.logError
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * معالج الانهيار (4.3.1).
 *
 * قبل 4.3.1 كان أي فشل في `startActivity(CrashActivity)` — وشاشة الانهيار نفسها
 * كانت **محذوفة من البيان** في 4.3.0 — يُنفّذ `exitProcess(1)`: فيرى المستخدم
 * التطبيق **يُغلق بلا أي رسالة**، ولا يبقى أثر يشرح السبب.
 *
 * الآن:
 * 1. **كل انهيار يُكتب على القرص** (`files/crash/last_crash.txt`) قبل أي محاولة عرض،
 *    فيبقى الأثر حتى لو فشل كل شيء آخر.
 * 2. تُحاول شاشة الانهيار؛ وإن تعذّرت **لا نقتل التطبيق**: نسجّل الخطأ ونكمل.
 * 3. عند الإقلاع التالي يقرأ [surfaceLastCrash] التقرير ويُدرجه في «سجلّات
 *    التطبيق» ثم يحذفه، فيعرف المستخدم والمسؤول سبب الإغلاق.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val CRASH_DIR = "crash"
    private const val CRASH_FILE = "last_crash.txt"
    private const val MAX_REPORT_BYTES = 64 * 1024

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        // 1) الأثر على القرص أولًا — قبل أي محاولة عرض قد تفشل
        runCatching { persist(ex, thread) }

        runCatching {
            // الأقواس الدولارية داخل نص Kotlin تُهرَّب حتى تبقى أسماء الأصناف حرفية
            val viewState = "android.view.View" + '\u0024' + "BaseSavedState"
            val scrollState = "android.widget.HorizontalScrollView" + '\u0024' + "SavedState"
            if (ex.message.toString().contains(viewState) || ex.message.toString().contains(scrollState)) {
                Log.w("CrashHandler", "Ignoring crash")
            }

            val intent = Intent(application!!, CrashActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            var cause = ex.cause.toString()
            val prefix = "java.lang.Throwable:"
            if (cause.startsWith(prefix)) {
                cause = cause.removePrefix(prefix)
            }

            intent.putExtra("force_crash", ex is HarmlessException)
            intent.putExtra("error_cause", cause)
            intent.putExtra("msg", ex.message)

            val stringWriter = StringWriter()
            val printWriter = PrintWriter(stringWriter)
            ex.printStackTrace(printWriter)
            val stackTraceString = stringWriter.toString()

            intent.putExtra("stacktrace", stackTraceString)
            intent.putExtra("thread", thread.name)

            application!!.startActivity(intent)
        }
            .onFailure { failure ->
                // 4.3.1: لا خروج صامت. شاشة الانهيار قد تكون غائبة أو معطّلة —
                // نكتفي بالتسجيل ونُبقي التطبيق حيًّا ليكمل المستخدم عمله.
                failure.printStackTrace()
                runCatching { logError(ex, "uncaught exception (crash screen unavailable)") }
                runCatching { logError(failure, "crash screen failed") }
            }

        // إبقاء الخيط الرئيسي حيًّا بعد عرض الشاشة حتى لا يموت التطبيق فجأة.
        // 4.3.2: بحدّ أقصى ثلاث محاولات — قبل ذلك كانت الحلقة لا تنتهي: أي خطأ
        // متكرّر في الخيط الرئيسي كان يُنشئ خيطًا جديدًا في كل دورة، فيتحوّل
        // الانهيار إلى دوامة استهلاك (خيوط لا نهائية) بدل إغلاق نظيف.
        if (Looper.myLooper() != null) {
            var recoveries = 0
            while (recoveries < 3) {
                try {
                    Looper.loop()
                    return
                } catch (t: Throwable) {
                    recoveries++
                    t.printStackTrace()
                    runCatching { logError(t, "main looper recovery #$recoveries") }
                }
            }
            // ثلاث محاولات فاشلة = الحالة غير قابلة للاستمرار
            logErrorOrExit(IllegalStateException("main looper kept throwing; giving up"))
        }
    }

    /** يكتب تقرير الانهيار على القرص (يبقى بعد إعادة التشغيل). */
    private fun persist(ex: Throwable, thread: Thread) {
        val ctx = application ?: return
        val dir = ctx.filesDir.child(CRASH_DIR).apply { createDirIfNot() }
        val file = File(dir, CRASH_FILE)

        val writer = StringWriter()
        PrintWriter(writer).use { print ->
            print.println("thread: ${thread.name}")
            print.println("time: ${System.currentTimeMillis()}")
            ex.printStackTrace(print)
        }

        var text = writer.toString()
        if (text.length > MAX_REPORT_BYTES) {
            text = text.take(MAX_REPORT_BYTES)
        }
        file.writeText(text)
    }

    /**
     * يقرأ تقرير آخر انهيار ويُدرجه في السجلّات ثم يحذفه.
     *
     * يُستدعى من [com.mahdi.App] على خيط IO عند الإقلاع، فلا يُبطئ الواجهة.
     */
    suspend fun surfaceLastCrash(context: Context) {
        runCatching {
            val file = File(context.filesDir.child(CRASH_DIR), CRASH_FILE)
            if (!file.exists()) return

            val text = runCatching { file.readText() }.getOrNull().orEmpty()
            if (text.isNotBlank()) {
                logError("آخر انهيار محفوظ:\n$text")
                CrashReport.lastCrash = text
            }
            file.delete()
        }
    }

    fun logErrorOrExit(throwable: Throwable) {
        runCatching { logError(throwable, "thread crash") }
            .onFailure {
                it.printStackTrace()
                // لا نملك حتى سجلًّا — آخر حلّ هو الخروج
                exitProcess(-1)
            }
    }
}

/** آخر تقرير انهيار — يُقرأ في شاشة «سجلّات التطبيق». */
object CrashReport {
    @Volatile
    var lastCrash: String? = null
        internal set
}
