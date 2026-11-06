package com.mahdi.storage

import android.content.Context
import com.mahdi.exec.isTerminalInstalled
import com.mahdi.exec.ubuntuProcess
import com.mahdi.file.localDir
import com.mahdi.settings.terminal.awaitExit
import com.mahdi.utils.application
import com.mahdi.utils.getTempDir
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * حساب مساحة التطبيق وتنظيفها (5.0.2).
 *
 * ## ما كان معطوبًا قبل 5.0.2
 * شاشة «مساحة التخزين» كانت تبقى معلّقة على `…` إلى الأبد، وزرّ **تنظيف آمن**
 * يبدو كأنه لا يفعل شيئًا. السبب:
 *
 * 1. **مشي متكرّر على نفس الشجرة**: كان `report()` يحسب `sizeOf` لمنطقة البيانات
 *    كاملة، ثم يعيد المشي على `sandbox` و`home` و`bin` و`lib` و`cache` و`tmp`
 *    **كلًّا على حدة**. صورة أوبنتو وحدها تحتوي مئات آلاف الملفات، فصار العمل
 *    ملايين استدعاءات `listFiles` — أي دقائق على جهاز حقيقي، وسقف ٤٠٠ ألف عنصر
 *    لا يحمي من التكرار.
 * 2. **بلا مهلة وبلا إلغاء**: إن غادر المستخدم الشاشة استمرّ الفحص يشغّل المعالج.
 * 3. **`clean()` كان ينتظر أوبنتو ٩٠ ثانية** ثم يعيد **مشي الشجرة مرّتين** قبل
 *    وبعد، فتبقى الشاشة عالقة دقائق دون أي تقدّم.
 * 4. **الفشل كان صامتًا**: `runCatching { … }.getOrNull()` يُخفي أي خطأ فتظهر
 *    `…` بلا سبب معروف.
 *
 * ## ما صار الآن
 * - **مشي واحد** على منطقة البيانات، وكل ملف يُنسب إلى مجموعته أثناء المرور نفسه.
 * - **مهلة زمنية** (٤ ثوانٍ افتراضيًا) وإلغاء حقيقي عند مغادرة الشاشة؛ وما قيس
 *   يُعرض فورًا مع علامة «تقريبي»، وبإمكان المستخدم طلب فحص كامل.
 * - **ذاكرة نتيجة**: آخر فحص محفوظ يُعرض لحظيًا عند فتح الشاشة.
 * - **تنظيف متدرّج كل خطوة فيه بمهلة**: حذف فعلي محسوب بدقّة، وأوبنتو بحدّ ٢٠ ثانية
 *   ثم تُقتل العملية، والنتيجة تُعاد دائمًا — لا انتظار بلا نهاية.
 */
object AppStorage {

    data class Entry(
        val labelAr: String,
        val labelEn: String,
        val size: Long,
    ) {
        fun label(isArabic: Boolean) = if (isArabic) labelAr else labelEn
    }

    data class Report(
        val total: Long,
        val free: Long,
        val deviceFree: Long,
        val deviceTotal: Long,
        val groups: List<Entry>,
        val sandboxTop: List<Entry>,
        /** هل توقّف الفحص عند المهلة؟ (الأرقام عندها حدّ أدنى) */
        val partial: Boolean = false,
        /** هل هذه القيم محفوظة من فحص سابق؟ */
        val fromCache: Boolean = false,
        /** عدد الملفات التي مرّ عليها الفحص. */
        val scanned: Int = 0,
        /** زمن القياس (ميلي ثانية منذ الحقبة). */
        val measuredAt: Long = 0L,
        /** 5.5.0: أحجام مصنّفة بالمفتاح: cache/temp/tools/themes/extensions/languages/image/home/other/external. */
        val categories: Map<String, Long> = emptyMap(),
    )

    /** نتيجة التنظيف — تُعرض للمستخدم كما هي، نجحت أو بعضها فقط. */
    data class CleanResult(
        val freed: Long,
        val steps: Int,
        /** أخفق تنظيف داخل أوبنتو (مهلة أو خطأ) — ما عدا ذلك نجح. */
        val sandboxFailed: Boolean,
        val log: List<String>,
    )

    /** عنصر في مكدّس المشي: مجلد + مجموعته + اسمه داخل أوبنتو (إن وُجد). */
    private class Node(val dir: File, val bucket: Int, val sub: String?)

    // ————————————————————— المجموعات —————————————————————

    private const val B_OTHER = 0
    private const val B_SANDBOX = 1
    private const val B_HOME = 2
    private const val B_TOOLS = 3
    private const val B_ASSETS = 4
    private const val B_CACHE = 5
    private const val B_TEMP = 6
    private const val B_SANDBOX_SUB = 7

    // 5.5.0: تفصيل حسب النوع (يُستخدم في StorageManager)
    private const val B_THEMES = 8
    private const val B_EXTENSIONS = 9
    private const val B_LANGUAGES = 10
    private const val B_EXTERNAL = 11
    private const val B_LOGS = 12
    private const val BUCKET_COUNT = 13

    private const val MAX_ENTRIES = 200_000
    private const val DEFAULT_BUDGET_MS = 4_000L
    private const val FULL_BUDGET_MS = 20_000L

    private const val PREFS = "mahdi_storage_report"
    private const val KEY_TOTAL = "total"
    private const val KEY_GROUPS = "groups"
    private const val KEY_AT = "at"

    // ————————————————————— الفحص —————————————————————

    /**
     * يقيس مساحة التطبيق مرّة واحدة على خيط IO.
     *
     * @param budgetMs سقف زمني للفحص. عند تجاوزه يعود بما قيس مع [Report.partial].
     */
    suspend fun report(
        budgetMs: Long = DEFAULT_BUDGET_MS,
        onStep: (String) -> Unit = {},
    ): Report =
        withContext(Dispatchers.IO) {
            val app = application
            if (app == null) return@withContext emptyReport()

            val dataDir = app.filesDir.parentFile ?: app.filesDir
            val temp = getTempDir()
            val cache = app.cacheDir

            val deadline = System.nanoTime() + budgetMs * 1_000_000L
            val sizes = LongArray(BUCKET_COUNT)
            val sandboxSubs = LinkedHashMap<String, Long>()
            var scanned = 0
            var partial = false

            onStep("scan")

            val stack = ArrayDeque<Node>()

            // جذور المنطقة: نُصنّف كل جذر مرّة واحدة، ثم نمشي الشجرة كلها مرّة واحدة
            runCatching {
                dataDir.listFiles()?.forEach { top ->
                    when (top.name) {
                        "files" -> {
                            runCatching {
                                app.filesDir.listFiles()?.forEach { f ->
                                    when (f.name) {
                                        "local" -> {
                                            if (f.isDirectory) {
                                                runCatching {
                                                    f.listFiles()?.forEach { l ->
                                                        when {
                                                            l.name == "sandbox" && l.isDirectory ->
                                                                stack.addLast(Node(l, B_SANDBOX, null))
                                                            l.name == "home" && l.isDirectory ->
                                                                stack.addLast(Node(l, B_HOME, null))
                                                            (l.name == "bin" || l.name == "lib") && l.isDirectory ->
                                                                stack.addLast(Node(l, B_TOOLS, null))
                                                            l.name == "themes" && l.isDirectory ->
                                                                stack.addLast(Node(l, B_THEMES, null))
                                                            (l.name == "icon_pack" || l.name == "extensions") &&
                                                                l.isDirectory ->
                                                                stack.addLast(Node(l, B_EXTENSIONS, null))
                                                            l.name.contains("lang") && l.isDirectory ->
                                                                stack.addLast(Node(l, B_LANGUAGES, null))
                                                            else -> stack.addLast(Node(l, B_OTHER, null))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        "tmp" -> stack.addLast(Node(f, B_TEMP, null))
                                        "crash" -> stack.addLast(Node(f, B_LOGS, null))
                                        else -> stack.addLast(Node(f, B_OTHER, null))
                                    }
                                }
                            }
                        }
                        "cache" -> stack.addLast(Node(top, B_CACHE, null))
                        "tmp" -> stack.addLast(Node(top, B_TEMP, null))
                        else -> stack.addLast(Node(top, B_OTHER, null))
                    }
                }
            }

            // `cacheDir` و`getTempDir()` قد تكون خارج مسار `dataDir` نظريًا — نضمن دخولهما
            if (cache.absolutePath.startsWith(dataDir.absolutePath).not()) {
                stack.addLast(Node(cache, B_CACHE, null))
            }
            if (temp.absolutePath.startsWith(dataDir.absolutePath).not()) {
                stack.addLast(Node(temp, B_TEMP, null))
            }
            // 5.5.0: مجلد التطبيق على التخزين الخارجي (تنزيل التحديثات…) يُقاس منفصلًا
            runCatching {
                app.getExternalFilesDir(null)?.takeIf { it.isDirectory }?.let { ext ->
                    if (ext.absolutePath.startsWith(dataDir.absolutePath).not()) {
                        stack.addLast(Node(ext, B_EXTERNAL, null))
                    }
                }
            }

            val checkpoint = 2048

            walk@ while (stack.isNotEmpty()) {
                if (partial) break@walk
                val node = stack.removeLast()

                if (node.dir.isFile) {
                    sizes[node.bucket] += node.dir.length()
                    continue@walk
                }

                val children = runCatching { node.dir.listFiles() }.getOrNull() ?: continue@walk

                for (child in children) {
                    scanned++
                    if (scanned % checkpoint == 0) {
                        // إلغاء حقيقي عند مغادرة الشاشة
                        coroutineContext.ensureActive()
                        if (System.nanoTime() > deadline || scanned >= MAX_ENTRIES) {
                            partial = true
                            break@walk
                        }
                    }

                    if (isSymlink(child)) continue
                    // 5.5.0: ملفات وقفل مؤقتة — لا تُحسب ولا تُمسّ أثناء المشي
                    if (isTransient(child.name)) continue

                    if (child.isDirectory) {
                        when {
                            // داخل أوبنتو: نُفرّع المستوى الأول لمعرفة أكبر مجلداتها
                            node.bucket == B_SANDBOX ->
                                stack.addLast(Node(child, B_SANDBOX_SUB, child.name))
                            node.bucket == B_SANDBOX_SUB ->
                                stack.addLast(Node(child, B_SANDBOX_SUB, node.sub))
                            else -> stack.addLast(Node(child, node.bucket, node.sub))
                        }
                    } else {
                        // 5.5.0: أي ملف سجلّ يُحسب في تصنيف «السجلات» مهما كان مكانه
                        if (child.name.endsWith(".log")) {
                            sizes[B_LOGS] += child.length()
                        } else {
                            sizes[node.bucket] += child.length()
                        }
                        val sub = node.sub
                        if (node.bucket == B_SANDBOX_SUB && sub != null) {
                            sandboxSubs[sub] = (sandboxSubs[sub] ?: 0L) + child.length()
                        }
                    }
                }
            }

            // مجموع المجلدات الفرعية داخل أوبنتو يُضاف مرّة واحدة إلى «صورة أوبنتو»
            sizes[B_SANDBOX] += sizes[B_SANDBOX_SUB]
            sizes[B_SANDBOX_SUB] = 0L

            val groups =
                listOf(
                    Entry("صورة أوبنتو (النظام)", "Ubuntu image (system)", sizes[B_SANDBOX]),
                    Entry("ملفاتك داخل الطرفية", "Your terminal files", sizes[B_HOME]),
                    Entry("بيئات التشغيل والأدوات", "Runtimes & tools", sizes[B_TOOLS]),
                    Entry(
                        "الثيمات وحزم الأيقونات واللغات",
                        "Themes, icon packs & languages",
                        sizes[B_THEMES] + sizes[B_EXTENSIONS] + sizes[B_LANGUAGES],
                    ),
                    Entry("ذاكرة التخزين المؤقتة", "App cache", sizes[B_CACHE]),
                    Entry("ملفات مؤقتة", "Temporary files", sizes[B_TEMP]),
                    Entry("بقية البيانات", "Everything else", sizes[B_OTHER]),
                ).sortedByDescending { it.size }

            val total = sizes.sum()

            val sandboxTop =
                sandboxSubs.entries
                    .filter { it.value > 0 }
                    .sortedByDescending { it.value }
                    .take(7)
                    .map { Entry("${it.key}/", "${it.key}/", it.value) }

            val categories =
                linkedMapOf(
                    "cache" to sizes[B_CACHE],
                    "temp" to sizes[B_TEMP],
                    "tools" to sizes[B_TOOLS],
                    "themes" to sizes[B_THEMES],
                    "extensions" to sizes[B_EXTENSIONS],
                    "languages" to sizes[B_LANGUAGES],
                    "image" to sizes[B_SANDBOX],
                    "home" to sizes[B_HOME],
                    "other" to sizes[B_OTHER],
                    "external" to sizes[B_EXTERNAL],
                    "logs" to sizes[B_LOGS],
                )

            onStep("done")

            Report(
                total = total,
                free = dataDir.usableSpace,
                deviceFree = dataDir.usableSpace,
                deviceTotal = dataDir.totalSpace,
                groups = groups,
                sandboxTop = sandboxTop,
                partial = partial,
                scanned = scanned,
                measuredAt = System.currentTimeMillis(),
                categories = categories,
            )
                .also { if (!partial) saveCache(app, it) }
        }

    /** فحص كامل بمهلة أطول (يطلبه المستخدم صراحةً). */
    suspend fun fullReport(onStep: (String) -> Unit = {}): Report = report(FULL_BUDGET_MS, onStep)

    private fun emptyReport(): Report =
        Report(
            total = 0,
            free = 0,
            deviceFree = 0,
            deviceTotal = 0,
            groups = emptyList(),
            sandboxTop = emptyList(),
            measuredAt = 0,
        )

    // ————————————————————— الذاكرة —————————————————————

    private fun saveCache(context: Context, report: Report) {
        runCatching {
            val groups =
                org.json.JSONArray().apply {
                    report.groups.forEach { g ->
                        put(org.json.JSONObject().put("ar", g.labelAr).put("en", g.labelEn).put("size", g.size))
                    }
                }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_TOTAL, report.total)
                .putString(KEY_GROUPS, groups.toString())
                .putLong(KEY_AT, report.measuredAt)
                .apply()
        }
    }

    /** آخر نتيجة محفوظة (`null` إن لم يوجد فحص ناجح سابق). */
    fun cachedReport(context: Context): Report? =
        runCatching {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val at = prefs.getLong(KEY_AT, 0L)
                if (at <= 0L) return null

                val array = org.json.JSONArray(prefs.getString(KEY_GROUPS, "[]"))
                val groups =
                    (0 until array.length()).map { i ->
                        val obj = array.getJSONObject(i)
                        Entry(obj.optString("ar"), obj.optString("en"), obj.optLong("size"))
                    }

                val dataDir = context.filesDir.parentFile ?: context.filesDir
                Report(
                    total = prefs.getLong(KEY_TOTAL, 0L),
                    free = dataDir.usableSpace,
                    deviceFree = dataDir.usableSpace,
                    deviceTotal = dataDir.totalSpace,
                    groups = groups,
                    sandboxTop = emptyList(),
                    fromCache = true,
                    measuredAt = at,
                )
            }
            .getOrNull()

    // ————————————————————— التنظيف —————————————————————

    /**
     * تنظيف آمن متدرّج — كل خطوة محميّة وبمهلة، والنتيجة تُعاد دائمًا.
     *
     * لا يمسّ ملفاتك ولا الحزم المخزّنة للعمل بلا إنترنت (`~/.mahdi/cache/wheels`).
     */
    suspend fun cleanDetailed(onLog: (String) -> Unit = {}): CleanResult =
        withContext(Dispatchers.IO) {
            var freed = 0L
            var steps = 0
            var sandboxFailed = false
            val log = mutableListOf<String>()

            fun note(line: String) {
                log += line
                onLog(line)
            }

            val app = application
            if (app == null) return@withContext CleanResult(0, 0, false, log)

            // 1) ذاكرة التطبيق
            steps++
            freed += deleteChildren(app.cacheDir, ::note)

            // 2) مجلد الملفات المؤقتة للتطبيق
            steps++
            freed += deleteChildren(runCatching { getTempDir() }.getOrNull(), ::note)

            // 3) مجلد tmp المحلي
            steps++
            freed += deleteChildren(File(runCatching { localDir() }.getOrNull() ?: app.filesDir, "tmp"), ::note)

            // 4) مجلدات التشغيل القديمة في ~/mahdi-run (أقدم من ٣ أيام)
            steps++
            runCatching {
                val runDir = File(app.filesDir, "local/home/mahdi-run")
                val expiry = System.currentTimeMillis() - 3L * 24 * 3600 * 1000
                runDir.listFiles()
                    ?.filter { it.isFile && it.lastModified() < expiry }
                    ?.forEach { file ->
                        val size = runCatching { file.length() }.getOrDefault(0L)
                        if (runCatching { file.delete() }.getOrDefault(false)) freed += size
                    }
            }

            // 5) داخل أوبنتو — بمهلة قصيرة وكيل مضمون
            steps++
            val terminalReady = runCatching { isTerminalInstalled() }.getOrDefault(false)
            if (terminalReady) {
                val aptLists = File(app.filesDir, "local/sandbox/var/lib/apt/lists")
                val aptArchive = File(app.filesDir, "local/sandbox/var/cache/apt/archives")
                val beforeApt = measureQuick(aptLists) + measureQuick(aptArchive)

                val script =
                    """
                    set +e
                    apt-get clean >/dev/null 2>&1
                    rm -rf /var/lib/apt/lists/* 2>/dev/null
                    rm -rf /tmp/* /var/tmp/* 2>/dev/null
                    rm -rf /var/cache/apt/archives/*.deb 2>/dev/null
                    rm -rf /root/.cache/* 2>/dev/null
                    rm -rf /home/.mahdi/cache/pip 2>/dev/null
                    for d in /home/*/.cache /home/*/.npm/_cacache; do rm -rf "${'$'}d" 2>/dev/null; done
                    rm -f /root/.bash_history 2>/dev/null
                    echo MAHDI_CLEAN_OK
                    """.trimIndent()

                var process: Process? = null
                val code =
                    runCatching {
                        process = ubuntuProcess(command = listOf("/bin/bash", "-c", script))
                        process!!.awaitExit(SANDBOX_CLEAN_TIMEOUT_MS)
                    }
                        .onFailure { note("sandbox: ${it.javaClass.simpleName}") }
                        .getOrNull()

                if (code == null) {
                    // المهلة أو خطأ: نُنهي العملية ولا نُبقي التطبيق معلّقًا
                    runCatching { process?.let { if (it.isAlive) it.destroyForcibly() } }
                    sandboxFailed = true
                    note("sandbox timeout")
                }

                val afterApt = measureQuick(aptLists) + measureQuick(aptArchive)
                freed += (beforeApt - afterApt).coerceAtLeast(0)
            } else {
                note("terminal not installed")
            }

            note("freed=$freed")
            CleanResult(freed = freed, steps = steps, sandboxFailed = sandboxFailed, log = log)
        }

    /** نسخة مبسّطة تُعيد البايتات المحرّرة فقط (توافقًا مع الاستدعاءات القديمة). */
    suspend fun clean(onLog: (String) -> Unit = {}): Long = cleanDetailed(onLog).freed

    /** يحذف محتويات مجلد ويعيد مجموع ما حُذف فعلًا. */
    private fun deleteChildren(dir: File?, note: (String) -> Unit): Long {
        if (dir == null || !dir.exists()) return 0L

        var freed = 0L
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return 0L

        for (child in children) {
            val size = measureQuick(child)
            val deleted =
                runCatching {
                        if (child.isDirectory) child.deleteRecursively() else child.delete()
                    }
                    .getOrDefault(false)
            if (deleted) {
                freed += size
            } else {
                note("skip: ${child.name}")
            }
        }
        return freed
    }

    /**
     * يقيس حجم عنصر بميزانية قصيرة جدًا.
     *
     * الغرض: تقدير ما سيُحرَّر بلا أن نُغرق الزرّ بمشي طويل. أي تجاوز يُوقف
     * العدّ ويُعاد ما قيس — رقم أقل من الحقيقة، لا رقم خاطئ.
     */
    private fun measureQuick(file: File, budgetMs: Long = 400L): Long {
        if (!file.exists()) return 0
        if (isSymlink(file)) return 0
        if (file.isFile) return runCatching { file.length() }.getOrDefault(0L)

        val deadline = System.nanoTime() + budgetMs * 1_000_000L
        var total = 0L
        var visited = 0
        val stack = ArrayDeque<File>()
        stack.addLast(file)

        while (stack.isNotEmpty()) {
            if (visited > 40_000 || System.nanoTime() > deadline) break
            val dir = stack.removeLast()
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                visited++
                if (isSymlink(child)) continue
                if (child.isDirectory) stack.addLast(child) else total += runCatching { child.length() }.getOrDefault(0L)
            }
        }
        return total
    }

    /** حجم ملف أو مجلد بالبايت، متجاهلًا الروابط الرمزية لتجنّب الدوران اللانهائي. */
    fun sizeOf(file: File): Long = measureQuick(file, budgetMs = 3_000L)

    /**
     * ملفات عابرة لا تُحسب ولا تُنظَّف من المسار العادي:
     * `.tmp…` و`*.lock` — تكتبها أدوات التثبيت ثم تختفي، وقد تكون مفتوحة الآن.
     */
    fun isTransient(name: String): Boolean =
        name.startsWith(".tmp") || name.endsWith(".lock") || name == "lock"

    private fun isSymlink(file: File): Boolean =
        runCatching { java.nio.file.Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    /** صياغة مقروءة: بايت → ك.ب/م.ب/غ.ب. */
    fun format(bytes: Long): String {
        val locale = java.util.Locale.US
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(locale, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(locale, "%.1f MB", mb)
        return String.format(locale, "%.2f GB", mb / 1024.0)
    }

    /** أقصى زمن لتنظيف داخل أوبنتو قبل قتل العملية — لا انتظار ٩٠ ثانية كما كان. */
    private const val SANDBOX_CLEAN_TIMEOUT_MS = 20_000L

}
