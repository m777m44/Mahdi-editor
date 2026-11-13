package com.mahdi.lsp

import com.mahdi.utils.logError
import com.mahdi.utils.logInfo
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * «مدير خوادم اللغة» — إعادة تشغيل صامتة بلا إزعاج (9.0.0).
 *
 * ## المشكلة التي حلّها
 * كانت اللقطة تُظهر فور فتح ملف بايثون:
 *
 * > تعذّر الاتصال بخادم اللغة: Python (توقّف الخادم فجأة). افتح «إدارة» لتثبيت
 * > الخادم أو إعادة تشغيله.
 *
 * وهذا تنبيه **مبكر جدًا**: خادم Pyright ينهار أحيانًا عند الإقلاع لضغط الذاكرة
 * ثم يعمل تمامًا بعد إعادة المحاولة. كان التطبيق يعرض الخطأ من أول محاولة، فيظنّ
 * المستخدم أن الإكمال والتشخيص معطّلان، ويُقطع تركيزه أثناء الكتابة.
 *
 * ## ما يفعله الآن
 * 1. أي انهيار أو مهلة ⇒ **إعادة تشغيل صامتة** بتراجع تصاعدي (1s, 2s, 4s).
 * 2. نجاح أي محاولة ⇒ تصفير العدّاد، وسكتٌ تام (لا Snackbar إطلاقًا).
 * 3. ثلاث محاولات فاشلة متتالية لهذا الخادم ⇒ يتوقّف عن المحاولة ويُعلَن «معطّل»
 *    لهذه الجلسة، فتُعرض **رسالة واحدة هادئة** فيها زرّ «إدارة» فقط، بلا تفاصيل
 *    تقنية ولا Traceback.
 * 4. كل الحالة في الذاكرة لكل خادم (وليس لكل ملف): فتح عشرة ملفات بايثون لا
 *    يُنتج عشرة محاولات ولا عشرة تنبيهات.
 *
 * كل شيء محميّ: أي استثناء يُسجَّل ويعود `false` — لا يُسقط المحرّر أبدًا.
 */
object LspManager {

    /** عدد المحاولات الصامتة قبل إظهار أي رسالة للمستخدم. */
    const val MAX_SILENT_ATTEMPTS: Int = 3

    /** عدّاد المحاولات المتتالية لكل خادم (المفتاح: server.id). */
    private val attempts = ConcurrentHashMap<String, Int>()

    /** خوادم استُنفدت محاولاتها في هذه الجلسة — لا نُكرّر المحاولة عليها. */
    private val degraded = ConcurrentHashMap.newKeySet<String>()

    /** خوادم يجري إحياؤها الآن — يمنع إعادة تشغيل متزامنة لنفس الخادم. */
    private val restoring = ConcurrentHashMap.newKeySet<String>()

    /**
     * حدث «استُنفدت المحاولات» — يُبَثّ مرة واحدة عند بلوغ الحد، فتنتظر الواجهة
     * هذا الحدث بدل أن تفحص الحالة بعد كل محاولة. `extraBufferCapacity` كافٍ
     * لأن الأحداث نادرة، و`tryEmit` لا يُعلّق أي خيط.
     */
    private val _degradedEvents = MutableSharedFlow<LspServer>(extraBufferCapacity = 16)
    val degradedEvents: SharedFlow<LspServer> = _degradedEvents.asSharedFlow()

    /** آخر مرة عُرض فيها تنبيه لهذا الخادم — تنبيه واحد لكل خادم كل ٣٠ ثانية. */
    private val lastNotice = ConcurrentHashMap<String, Long>()

    /** مهلة تنبيه «إدارة» الواحد لكل خادم. */
    const val NOTICE_THROTTLE_MS: Long = 30_000L

    /** هل يحين عرض تنبيه لهذا الخادم الآن؟ (ويسجّل الوقت إن كان الجواب نعم) */
    fun shouldNotify(server: LspServer): Boolean {
        val now = System.currentTimeMillis()
        val last = lastNotice[server.id] ?: 0L
        if (now - last < NOTICE_THROTTLE_MS) return false
        lastNotice[server.id] = now
        return true
    }

    private fun degrade(server: LspServer) {
        degraded.add(server.id)
        _degradedEvents.tryEmit(server)
    }

    /** هل استُنفدت محاولات هذا الخادم؟ (تقرأه الواجهة لإظهار «إدارة» فقط) */
    fun isDegraded(server: LspServer): Boolean = degraded.contains(server.id)

    /** عدد المحاولات الحالية — للتشخيص فقط. */
    fun attemptCount(server: LspServer): Int = attempts[server.id] ?: 0

    /** يُنادى عند نجاح الاتصال: يعيد الخادم إلى حالة «سليم». */
    fun noteSuccess(server: LspServer) {
        attempts.remove(server.id)
        degraded.remove(server.id)
    }

    /** يُنادى عند إعادة تشغيل يدوية (من «إدارة»): يمنح الخادم فرصةً جديدة. */
    fun reset(server: LspServer) {
        attempts.remove(server.id)
        degraded.remove(server.id)
    }

    /**
     * يحاول إحياء الخادم بصمت.
     *
     * @return `true` إذا كان الخادم يعمل الآن، أو ما زال أمامه محاولات،
     *   و`false` إذا استُنفدت المحاولات ويجب إبلاغ المستخدم **مرة واحدة**.
     */
    suspend fun recover(instance: LspServerInstance): Boolean {
        val server = instance.server
        val key = server.id

        if (degraded.contains(key)) return false

        // 9.1.0: أثناء تشغيل سكربت تكون الخوادم موقوفة عمدًا لتوفير الذاكرة —
        // لا نحاول إحياءها في ذلك الوقت، وإلا عدنا إلى نفس ضغط الذاكرة.
        if (LspMemoryGuard.isFrozen) return true

        val n = attempts.merge(key, 1) { current, _ -> current + 1 } ?: 1
        if (n > MAX_SILENT_ATTEMPTS) {
            degrade(server)
            logInfo("LSP ${server.serverName} degraded after $MAX_SILENT_ATTEMPTS silent attempts")
            return false
        }

        // تراجع تصاعدي هادئ: 1s · 2s · 4s
        delay(1000L shl (n - 1).coerceIn(0, 2))

        if (!restoring.add(key)) return true
        try {
            logInfo("Silent LSP restart ${server.serverName} (attempt $n/$MAX_SILENT_ATTEMPTS)")

            val started =
                withContext(Dispatchers.IO) {
                    runCatching { instance.startSilently() }.getOrDefault(false)
                }

            if (started) {
                attempts.remove(key)
                degraded.remove(key)
                return true
            }

            // فشل الإحياء ≠ إعلان معطّل بعد: نُبقي العدّاد ونعود true حتى تُستنفد المحاولات
            if (n >= MAX_SILENT_ATTEMPTS) {
                degrade(server)
                return false
            }
            return true
        } catch (t: Throwable) {
            logError(t, "silent LSP restart failed")
            return false
        } finally {
            restoring.remove(key)
        }
    }

    /** يقفل هذا الخادم صامتًا بلا أي محاولة إضافية (يُستدعى من الإعدادات). */
    fun markDegraded(server: LspServer) {
        degraded.add(server.id)
    }

    /** كل الخوادم المعطّلة الآن — تعرضها شاشة «إدارة» لإعادة التشغيل. */
    fun degradedServers(): Set<String> = degraded.toSet()

    /**
     * وصف قصير جدًا للحالة يصلح لسطر واحد في شريط الحالة
     * (`يعمل` · `يُعاد التشغيل` · `متوقف`)، بلا تفاصيل تقنية.
     */
    fun describe(status: LspConnectionStatus, server: LspServer?): String =
        when {
            server != null && isDegraded(server) -> "متوقف"
            status == LspConnectionStatus.RUNNING -> "يعمل"
            status == LspConnectionStatus.STARTING ||
                status == LspConnectionStatus.RESTARTING -> "يبدأ…"
            status == LspConnectionStatus.CRASHED || status == LspConnectionStatus.TIMEOUT -> "يُعاد…"
            else -> "خامل"
        }
}
