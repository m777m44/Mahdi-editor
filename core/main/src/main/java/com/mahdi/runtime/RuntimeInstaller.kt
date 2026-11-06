package com.mahdi.runtime

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.mahdi.SandboxedProcessRegistry
import com.mahdi.TerminalLauncher
import com.mahdi.file.child
import com.mahdi.file.sandboxDir
import com.mahdi.file.sandboxHomeDir
import com.mahdi.utils.application
import com.mahdi.utils.logError
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * مُدار تثبيت بيئات التشغيل (3.0.0) — الحالة الحقيقية لا الوهمية.
 *
 * كل حالة تُشتق من ثلاث حقائق على القرص، لا من مجرّد ضغطة زر:
 * 1. **الثنائي** موجود داخل جذر الـ sandbox: `sandbox/usr/bin/<binary>`.
 * 2. **علامة النجاح** التي يكتبها مثبّت الطرفية بعد التحقّق من تشغيل الأمر:
 *    `~/.mahdi/installed/<id>` ومحتواها `version=…`.
 * 3. **ملف الحالة الحيّة** أثناء التثبيت: `~/.mahdi/install-status/<id>`.
 *
 * فلو فشل `apt` (شبكة، مساحة، حزمة محذوفة) تظهر «فشل» مع السبب وبزرّ إعادة،
 * ولا تبقى الواجهة تدور بلا نهاية ولا تظهر «مثبت» كذبًا.
 */
object RuntimeInstaller {

    private const val PREFS = "mahdi_runtime_installs"
    private const val KEY_STATE_PREFIX = "state_"

    /** حالة ملفّ الحالة التي يكتبها الحذف الناجح. */
    private const val STATE_REMOVED = "removed"

    /** أقصى زمن لانتظار `apt purge` قبل إيقافه (٥ دقائق). */
    private const val REMOVE_TIMEOUT_MS = 5 * 60 * 1000L

    /** صلاحية ملفّ الحالة الحيّة (ساعة) قبل اعتباره أثرًا قديمًا. */
    private const val STATUS_TTL_SECONDS = 60 * 60

    /** «جارٍ الحذف» أثر قصير العمر: إن انقطعت العملية لا تبقى البطاقة معلّقة. */
    private const val REMOVING_TTL_SECONDS = 120

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watcher: Job? = null

    private val _status = MutableStateFlow<Map<String, RuntimeStatus>>(emptyMap())

    /** حالات كل بيئات التشغيل، مرتّبة بترتيب الكتالوج. */
    val status: StateFlow<Map<String, RuntimeStatus>> = _status.asStateFlow()

    private val prefs: SharedPreferences?
        get() = application?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** مجلد ملفات الحالة كما يراه التطبيق (مقابل `/home/.mahdi/install-status` داخل الطرفية). */
    private val statusDir: File
        get() = File(sandboxHomeDir(), ".mahdi/install-status").also { runCatching { it.mkdirs() } }

    /** مجلد علامات النجاح (مقابل `/home/.mahdi/installed`). */
    private val markerDir: File
        get() = File(sandboxHomeDir(), ".mahdi/installed").also { runCatching { it.mkdirs() } }

    private fun specStatusFile(id: String): File = File(statusDir, id)

    private fun specMarkerFile(id: String): File = File(markerDir, id)

    /** يقرأ الملفّات ويعيد بناء الحالات (يُستدعى عند بدء التطبيق وعند فتح الشاشة). */
    fun initialize() {
        scope.launch { refresh() }
    }

    /** تحديث فوري لكل الحالات من القرص. */
    suspend fun refresh() {
        val result = withContext(Dispatchers.IO) {
            RuntimeCatalog.all.associate { spec -> spec.id to readStatus(spec) }
        }
        _status.value = result
        persist(result)
    }

    /** حالة بيئة واحدة (أو غير مثبَّتة إن لم تُقرأ بعد). */
    fun statusOf(id: String): RuntimeStatus? = _status.value[id]

    private fun readStatus(spec: RuntimeSpec): RuntimeStatus {
        val binary = firstExistingBinary(spec)
        val marker = specMarkerFile(spec.id)
        val markerVersion = readValue(marker, "version")
        val live = readKeyValues(specStatusFile(spec.id))
        val liveState = live["state"].orEmpty()
        val liveTime = live["time"]?.toLongOrNull() ?: 0L
        val fresh = liveTime == 0L || (System.currentTimeMillis() / 1000 - liveTime) < STATUS_TTL_SECONDS

        // 0) حذف جارٍ الآن: البطاقة تُظهر «جارٍ الحذف…» حتى لو كان الثنائي موجودًا
        val removingFresh = liveTime == 0L || (System.currentTimeMillis() / 1000 - liveTime) < REMOVING_TTL_SECONDS
        if (liveState == "removing" && removingFresh) {
            return RuntimeStatus(spec.id, spec.nameAr, spec.nameEn, InstallState.Removing, null, spec.sizeMb, spec.packages)
        }

        // 0.1) قرار الحذف يعلو على فحص الثنائي (6.2.0)
        //
        // بعض الحزم أساسية في أوبنتو (perl-base توفّر /usr/bin/perl) فلا يمحوها
        // `apt purge`، فيعود الفحص ليقول «مثبَّت» بعد الحذف ويبقى زرّ الحذف الأحمر
        // ظاهرًا إلى الأبد — وهو ما شكا منه المستخدم. السكربت يكتب بعد الحذف
        // `state=removed` مع `survived=1|0`، فنصدّقه بدلًا من تخمين حالة القرص.
        if (!marker.exists() && live["state"] == STATE_REMOVED) {
            val state = if (live["survived"] == "1") InstallState.Bundled else InstallState.NotInstalled
            return RuntimeStatus(spec.id, spec.nameAr, spec.nameEn, state, null, spec.sizeMb, spec.packages)
        }

        // 1) مثبَّت ومتحقَّق منه فعليًا
        if (binary != null || marker.exists()) {
            val version = markerVersion.ifBlank { live["version"].orEmpty() }.ifBlank { null }
            persistState(spec.id, "installed")
            return RuntimeStatus(
                id = spec.id,
                nameAr = spec.nameAr,
                nameEn = spec.nameEn,
                state = InstallState.Installed(version),
                version = version,
                sizeMb = spec.sizeMb,
                packages = spec.packages,
            )
        }

        // 2) حالة حيّة من الطرفية (تثبيت جارٍ أو فشل مسجَّل)
        if (fresh) {
            when (liveState) {
                "installing", "queued", "verifying" -> {
                    val progress = live["progress"]?.toIntOrNull()?.coerceIn(0, 100) ?: 0
                    val state =
                        if (liveState == "verifying" || progress >= 88) {
                            InstallState.Extracting
                        } else {
                            InstallState.Downloading(progress)
                        }
                    return RuntimeStatus(spec.id, spec.nameAr, spec.nameEn, state, null, spec.sizeMb, spec.packages)
                }
                "failed" -> {
                    val reason = live["msg"].orEmpty().ifBlank { "فشل غير معروف" }
                    return RuntimeStatus(
                        spec.id,
                        spec.nameAr,
                        spec.nameEn,
                        InstallState.Failed(reason),
                        null,
                        spec.sizeMb,
                        spec.packages,
                    )
                }
            }
        }

        // 3) غير مثبَّتة
        return RuntimeStatus(spec.id, spec.nameAr, spec.nameEn, InstallState.NotInstalled, null, spec.sizeMb, spec.packages)
    }

    private fun firstExistingBinary(spec: RuntimeSpec): File? {
        val binDir = sandboxDir().child("usr/bin")
        if (!binDir.exists()) return null
        return spec.binaries.map { File(binDir, it) }.firstOrNull { it.exists() }
    }

    /**
     * يبدأ تثبيت بيئة تشغيل.
     *
     * @param activity النشاط الذي يُفتح منه تبويب الطرفية (يلزم لأن التثبيت يجري داخل الـ sandbox).
     * @return `false` إن لم يكن بالإمكان البدء (لا نشاط أو لا طرفية).
     */
    fun install(activity: Activity?, spec: RuntimeSpec): Boolean {
        if (activity == null) return false

        // نحفظ «قيد التثبيت» فورًا حتى تظهر الحالة قبل أن يبدأ الأمر بالطباعة
        markPending(spec.id)

        val command = buildInstallCommand(spec)
        TerminalLauncher.launch(
            activity = activity,
            exe = "/bin/bash",
            args = arrayOf("-lc", command),
            id = "install-${spec.id}",
            terminatePreviousSession = true,
        )
        startWatcher()
        return true
    }

    /** يلغي التثبيت (يوقف المراقبة ويعيد الحالة إلى «غير مثبَّتة»). */
    fun cancel(id: String) {
        runCatching { specStatusFile(id).delete() }
        markNotInstalled(id)
    }

    /**
     * يحذف بيئة تشغيل فعليًا (6.1.0، وأُعيدت كتابته في 6.2.0).
     *
     * قبل 6.1.0 كانت الإزالة «نسيانًا» فقط: تُمسح علامة التثبيت وتبقى الحزم في
     * الجذر، فتعود البيئة «مثبَّتة» بعد أول فحص.
     *
     * في 6.2.0 صار الحذف:
     * - **في الخلفية** عبر `SandboxedProcessRegistry` بلا فتح الطرفية وقفل الشاشة
     *   أمام المستخدم، ثم تتحدّث البطاقة نفسها إلى «غير مثبَّت» أو «مضمّن».
     * - **صادقًا** في الحالتين: إن كانت الحزمة أساسية في أوبنتو (مثل `perl-base`
     *   التي توفّر `/usr/bin/perl`) فالنتيجة «مضمّن» لا «مثبَّت»، فيختفي زرّ
     *   الحذف بدل أن يبقى أحمر إلى الأبد.
     *
     * @return `false` إن لم يكن بالإمكان البدء (لا نشاط ولا مزوّد عمليات).
     */
    fun uninstall(activity: Activity?, spec: RuntimeSpec): Boolean {
        // حالة فورية: البطاقة تتحوّل إلى «جارٍ الحذف…» قبل أن يبدأ apt
        markRemoving(spec.id)

        val provider = SandboxedProcessRegistry.provider
        if (provider != null) {
            val command = buildRemoveCommand(spec)
            scope.launch {
                val exitCode =
                    runCatching {
                            val process = provider(listOf("/bin/bash", "-lc", command), null, emptyList())
                                ?: return@runCatching -1
                            // لا أحد يقرأ المخرجات: أغلِق المدخل حتى لا ينتظر أي أمر إدخالًا
                            runCatching { process.outputStream.close() }
                            // waitFor(مهلة) لا withTimeout: الثانية لا تقطع نداءً حاجزًا
                            val finished =
                                runCatching { process.waitFor(REMOVE_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
                                    .getOrDefault(false)
                            if (finished) {
                                process.exitValue()
                            } else {
                                runCatching { process.destroyForcibly() }
                                -1
                            }
                        }
                        .onFailure { logError(it, "RuntimeInstaller.uninstall(${spec.id})") }
                        .getOrDefault(-1)

                if (exitCode == 0) {
                    // الحقيقة تُقرأ من القرص بعد انتهاء الأمر، لا من نجاح الضغط
                    finalizeRemoval(spec)
                } else {
                    // لم يبدأ الحذف أو فشل فشلًا صريحًا: لا ندّعي شيئًا ونعيد الحالة الحقيقية
                    runCatching { specStatusFile(spec.id).delete() }
                    markNotInstalled(spec.id)
                }
                refresh()
            }
            return true
        }

        // احتياط: إن لم يكن مزوّد العمليات مسجّلًا (نسخة قديمة من الطرفية) نفتح جلسة
        if (activity == null) {
            cancel(spec.id)
            return false
        }
        TerminalLauncher.launch(
            activity = activity,
            exe = "/bin/bash",
            args = arrayOf("-lc", buildRemoveCommand(spec)),
            id = "remove-${spec.id}",
            terminatePreviousSession = true,
        )
        startWatcher()
        return true
    }

    /** أمر الحذف الكامل: السكربت إن وُجد، وإلا إزالة مباشرة بـ apt. */
    fun buildRemoveCommand(spec: RuntimeSpec): String {
        val script = "\$LOCAL/bin/install_runtime"
        val args =
            listOf(spec.id, spec.packages, spec.binaries.joinToString(" "), spec.nameAr)
                .joinToString(" ") { shellQuote(it) }
        val safeId = shellQuote(spec.id)
        val fallback =
            "set -e; export DEBIAN_FRONTEND=noninteractive; " +
                "mkdir -p ~/.mahdi/installed ~/.mahdi/install-status; " +
                "apt-get remove -y --purge ${spec.packages} >/dev/null 2>&1 || true; " +
                "rm -f ~/.mahdi/installed/$safeId; " +
                "printf 'state=removed\\nprogress=100\\nsurvived=0\\nmsg=\\ntime=%s\\n' \"\$(date +%s)\" " +
                "> ~/.mahdi/install-status/$safeId"
        return "if [ -x \"$script\" ]; then bash \"$script\" --remove $args; else $fallback; fi"
    }

    /** يزيل علامة التثبيت فقط بلا حذف الحزم (يُستدعى عند حذف لغة). */
    fun forget(id: String) {
        runCatching { specMarkerFile(id).delete() }
        runCatching { specStatusFile(id).delete() }
        persistState(id, "not_installed")
        markNotInstalled(id)
    }

    /** أمر التثبيت الكامل الذي يُمرَّر إلى `bash -lc` داخل الـ sandbox. */
    fun buildInstallCommand(spec: RuntimeSpec): String {
        val script = "\$LOCAL/bin/install_runtime"
        val args =
            listOf(spec.id, spec.packages, spec.postInstall, spec.versionCommand)
                .joinToString(" ") { shellQuote(it) }
        // fallback: إن لم يوجد السكربت (نسخة قديمة من الطرفية) نُنفّذ التثبيت المباشر
        val fallback =
            "set -e; export DEBIAN_FRONTEND=noninteractive; mkdir -p ~/.mahdi/installed ~/.mahdi/install-status; " +
                "printf 'state=installing\\nprogress=10\\nmsg=\\n' > ~/.mahdi/install-status/${spec.id}; " +
                "apt-get update -qq && apt-get install -y ${spec.packages}; " +
                "printf 'version=%s\\n' \"\$(${spec.versionCommand} 2>/dev/null | head -n1)\" > ~/.mahdi/installed/${spec.id}; " +
                "printf 'state=installed\\nprogress=100\\nmsg=\\n' > ~/.mahdi/install-status/${spec.id}"
        return "if [ -x \"$script\" ]; then bash \"$script\" $args; else $fallback; fi"
    }

    private fun shellQuote(value: String): String =
        if (value.isBlank()) "''" else "'" + value.replace("'", "'\\''") + "'"

    // ——————————————————————————————————————————————————————————————
    // المراقبة الحيّة
    // ——————————————————————————————————————————————————————————————

    private fun startWatcher() {
        if (watcher?.isActive == true) return
        watcher =
            scope.launch {
                repeat(450) { // ١٥ دقيقة كحد أقصى
                    refresh()
                    val busy = _status.value.values.any { it.isBusy }
                    if (!busy) return@launch
                    delay(1000)
                }
            }
    }

    // ——————————————————————————————————————————————————————————————
    // أدوات القراءة/الكتابة
    // ——————————————————————————————————————————————————————————————

    private fun readKeyValues(file: File): Map<String, String> =
        runCatching {
                if (!file.exists()) return emptyMap()
                file.readLines().mapNotNull { line ->
                    val index = line.indexOf('=')
                    if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
                }.toMap()
            }
            .getOrDefault(emptyMap())

    private fun readValue(file: File, key: String): String = readKeyValues(file)[key].orEmpty()

    private fun markPending(id: String) {
        val current = _status.value
        val spec = RuntimeCatalog.byId(id) ?: return
        val existing = current[id]
        _status.value =
            current +
                (id to
                    RuntimeStatus(
                        id = spec.id,
                        nameAr = spec.nameAr,
                        nameEn = spec.nameEn,
                        state = InstallState.Queued,
                        version = existing?.version,
                        sizeMb = spec.sizeMb,
                        packages = spec.packages,
                    ))
        persistState(id, "queued")
    }

    private fun markNotInstalled(id: String) {
        val current = _status.value
        val entry = current[id] ?: return
        _status.value = current + (id to entry.copy(state = InstallState.NotInstalled, version = null))
    }

    /** حالة فورية عند بدء الحذف: البطاقة تعرض «جارٍ الحذف…» قبل أن يبدأ apt. */
    private fun markRemoving(id: String) {
        val spec = RuntimeCatalog.byId(id) ?: return
        val entry =
            _status.value[id]
                ?: RuntimeStatus(
                    id = spec.id,
                    nameAr = spec.nameAr,
                    nameEn = spec.nameEn,
                    state = InstallState.NotInstalled,
                    version = null,
                    sizeMb = spec.sizeMb,
                    packages = spec.packages,
                )
        _status.value = _status.value + (id to entry.copy(state = InstallState.Removing))
        persistState(id, "removing")
        writeStatusFile(id, "removing", "survived" to "")
    }

    /**
     * يُثبّت نتيجة الحذف بعد انتهاء الأمر (6.2.0).
     *
     * العلامة تُمسح دائمًا، والحالة النهائية تُشتق من القرص: إن بقي الثنائي بعد
     * `apt purge` فالحزمة أساسية في أوبنتو ⇒ «مضمّن» لا «مثبَّت»، فيختفي زرّ
     * الحذف بدل أن يعود بعد ثانية.
     */
    private fun finalizeRemoval(spec: RuntimeSpec) {
        val live = readKeyValues(specStatusFile(spec.id))
        // السكربت يكتب state=installed حين يفشل الحذف (قفل apt أو انقطاع شبكة):
        // عندها لا نمسح العلامة ولا ندّعي أن شيئًا حُذف.
        if (live["state"] == "installed" && specMarkerFile(spec.id).exists()) return

        runCatching { specMarkerFile(spec.id).delete() }
        val survived = firstExistingBinary(spec) != null
        writeStatusFile(spec.id, STATE_REMOVED, "survived" to if (survived) "1" else "0")
        persistState(spec.id, if (survived) "bundled" else "not_installed")
    }

    /** يكتب ملفّ الحالة الحيّة الذي يقرأه [readStatus] (`state=…· survived=…· time=…`). */
    private fun writeStatusFile(id: String, state: String, vararg extra: Pair<String, String>) {
        runCatching {
                val values =
                    buildList {
                        add("state" to state)
                        extra.forEach { add(it) }
                        add("msg" to "")
                        add("time" to (System.currentTimeMillis() / 1000L).toString())
                    }
                specStatusFile(id).apply {
                    parentFile?.mkdirs()
                    writeText(values.joinToString(separator = "\n", postfix = "\n") { "${it.first}=${it.second}" })
                }
            }
            .onFailure { logError(it, "RuntimeInstaller.writeStatusFile($id)") }
    }

    private fun persist(map: Map<String, RuntimeStatus>) {
        runCatching {
            val editor = prefs?.edit() ?: return
            map.forEach { (id, status) ->
                editor.putString(
                    KEY_STATE_PREFIX + id,
                    when (val s = status.state) {
                        is InstallState.Installed -> "installed:${s.version.orEmpty()}"
                        InstallState.Bundled -> "bundled"
                        is InstallState.Downloading -> "downloading:${s.progress}"
                        InstallState.Extracting -> "extracting"
                        InstallState.Removing -> "removing"
                        InstallState.Queued -> "queued"
                        is InstallState.Failed -> "failed:${s.error}"
                        InstallState.NotInstalled -> "not_installed"
                    },
                )
            }
            editor.apply()
        }
            .onFailure { logError(it, "RuntimeInstaller.persist") }
    }

    private fun persistState(id: String, value: String) {
        runCatching { prefs?.edit()?.putString(KEY_STATE_PREFIX + id, value)?.apply() }
    }

    /** النسخة المحفوظة آخر مرة (لعرض فوري قبل قراءة القرص). */
    fun cachedVersion(id: String): String? =
        prefs?.getString(KEY_STATE_PREFIX + id, null)?.substringAfter("installed:", "")?.takeIf { it.isNotBlank() }

    /** عدد البيئات الجاهزة. */
    val readyCount: Int
        get() = _status.value.values.count { it.isReady }
}
