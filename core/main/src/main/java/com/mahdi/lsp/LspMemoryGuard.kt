package com.mahdi.lsp

import com.mahdi.DefaultScope
import com.mahdi.utils.logError
import com.mahdi.utils.logInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «حارس الذاكرة» (9.1.0) — يُفرِّغ الذاكرة قبل تشغيل سكربت ثقيل.
 *
 * ## المشكلة في اللقطات
 * سكربت بايثون (telebot + aiohttp) ينتهي ثم تموت الطرفية فورًا بـ
 * `[Process completed (signal 9)]`. الإشارة ٩ هي SIGKILL: قاتل الذاكرة في
 * أندرويد (LMK) قتل الجلسة لأن التطبيق كان يستهلك ذاكرةً كبيرة.
 *
 * وأكبر مستهلك في التطبيق ليس المحرّر نفسه، بل **خوادم اللغة**: كل خادم يعمل
 * داخل `proot` منفصل، وPyright مثلًا = عملية Node كاملة (٢٠٠–٤٠٠ ميغابايت).
 * كانت تعمل في الخلفية بينما يشغّل المستخدم سكربتًا ثقيلًا على جهاز بذاكرة
 * ٣–٤ غيغابايت، فيقع التطبيق تحت ضغط قاتل الذاكرة.
 *
 * ## الحلّ
 * عند الضغط على «تشغيل» تُوقَف خوادم اللغة **مؤقتًا** (لا تُحذف ولا تُعطَّل
 * تثبيتًا)، ويُمنع إحياؤها الصامت دقيقةً واحدة، ثم تعود وحدها عند أول فتح ملف
 * بعد انتهاء التشغيل. لا رسالة، ولا إعداد يفعّله المستخدم: هذا سلوك افتراضي
 * لأنه إصلاح لانهيار، لا خيار.
 *
 * كل شيء محميّ: أي فشل يُسجَّل ويُتجاهَل — تشغيل السكربت لا يتوقّف أبدًا.
 */
object LspMemoryGuard {

    private val _frozen = MutableStateFlow(false)

    /**
     * هل الخوادم موقوفة الآن لتوفير الذاكرة؟
     *
     * حالة قابلة للمراقبة (لا متغيّر عادي): المحرّر يشترك فيها ليُعيد ربط خوادم
     * اللغة تلقائيًا لحظة الإفراج، فلا يبقى ملف مفتوح بلا إكمال بعد العودة من
     * الطرفية.
     */
    val frozen: StateFlow<Boolean> = _frozen.asStateFlow()

    val isFrozen: Boolean get() = _frozen.value

    /**
     * يُجمّد الخوادم قبل تشغيل سكربت.
     *
     * @return عدد الخوادم التي كانت تعمل وأُوقفت — للتشخيص فقط.
     */
    suspend fun freezeForRun(): Int =
        withContext(Dispatchers.IO) {
            _frozen.value = true
            var stopped = 0
            val servers =
                runCatching {
                        LspRegistry.builtInServers.value +
                            LspRegistry.extensionServers.value +
                            LspRegistry.externalServers.value
                    }
                    .getOrDefault(emptyList())

            servers.forEach { server ->
                runCatching {
                        server.instances.value.forEach { instance ->
                            instance.pauseForMemory()
                            stopped += 1
                        }
                    }
                    .onFailure { logError(it, "freeze ${server.id}") }
            }

            if (stopped > 0) {
                logInfo("LSP memory guard: stopped $stopped instance(s) before running a script")
            }
            stopped
        }

    /**
     * يُطلق التجميد ويُعيد تشغيل الخوادم التي أُوقفت.
     *
     * يُستدعى عند مغادرة الطرفية أو عند فتح ملف في المحرّر. الاستئناف يجري في
     * الخلفية عبر `startAllInstances()` — المسار المخصّص لإعادة تشغيل خادم
     * موقوف (يمسح `DefinitionPrevention` ثم يُقلع ويُعيد ربط المحرّرات).
     */
    fun release() {
        if (!_frozen.value) return
        _frozen.value = false
        DefaultScope.launch {
            runCatching { resumeAll() }.onFailure { logError(it, "resume language servers") }
        }
    }

    private suspend fun resumeAll() {
        val servers =
            LspRegistry.builtInServers.value +
                LspRegistry.extensionServers.value +
                LspRegistry.externalServers.value
        servers.forEach { server ->
            runCatching { server.startAllInstances(quiet = true) }
                .onFailure { logError(it, "resume ${server.id}") }
        }
    }
}
