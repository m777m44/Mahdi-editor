package com.mahdi.storage

import android.content.Context
import com.mahdi.utils.application
import com.mahdi.utils.getTempDir
import com.mahdi.file.localDir
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `StorageManager` (5.5.0) — واجهة واحدة لقياس مساحة التطبيق وتنظيف **الآمن** فقط.
 *
 * ## القاعدة الذهبية
 * التنظيف **لا يمسّ أبدًا**:
 * - `data/` (ملفات المشاريع داخل التطبيق وكل ما يكتبه المستخدم)،
 * - `runtimes/` المثبّتة (بايثون/نود/بي إتش بي…)،
 * - `languages/` المثبّتة (لغات البرمجة المضافة)،
 * - `themes/` المخصّصة (ثيمات المستخدم).
 *
 * التنظيف هنا يقتصر على ثلاث مناطق معروفة يُعاد بناؤها تلقائيًا:
 * `cacheDir` للتطبيق، ومجلد الملفات المؤقتة، و`local/tmp` — مع فحص كل مسار قبل
 * الحذف ([isProtected]) فلا يمرّ حذف أبدًا داخل منطقة محميّة.
 *
 * ## القياس
 * لا يوجد مشي جديد للشجرة: [getStorageBreakdown] يقرأ نتيجة [AppStorage.report]
 * (مشي واحد بمهلة ٤ ثوانٍ وذاكرة نتيجة)، ويُعيد التصنيف الثمانية المطلوب. لهذا
 * لا يمكن أن تعلّق الشاشة حتى لو كان جهاز المستخدم بطيئًا.
 */
object StorageManager {

    /** التصنيفات الثمانية كما تُعرض للمستخدم. */
    enum class Category(val key: String, val labelAr: String, val labelEn: String) {
        CACHE("cache", "ذاكرة التخزين المؤقتة", "Cache"),
        RUNTIMES("runtimes", "بيئات التشغيل", "Runtimes"),
        LANGUAGES("languages", "لغات البرمجة المثبّتة", "Programming languages"),
        EXTENSIONS("extensions", "الإضافات وحزم الأيقونات", "Extensions & icon packs"),
        THEMES("themes", "الثيمات", "Themes"),
        LOGS("logs", "السجلات", "Logs"),
        OTHER("other", "بقية البيانات", "Everything else"),
        EXTERNAL("external", "التخزين الخارجي للتطبيق", "App external storage");

        fun label(isArabic: Boolean) = if (isArabic) labelAr else labelEn
    }

    /** سطر واحد من التفصيل. */
    data class Item(val category: Category, val size: Long)

    /** نتيجة التفصيل الكامل. */
    data class Breakdown(
        val total: Long,
        val free: Long,
        val items: List<Item>,
        /** هل توقّف القياس عند المهلة؟ (الأرقام حدّ أدنى) */
        val partial: Boolean,
        /** قيمة محفوظة من فحص سابق بدل قياس جديد. */
        val cached: Boolean,
    ) {
        fun sizeOf(category: Category): Long =
            items.firstOrNull { it.category == category }?.size ?: 0L

        fun label(isArabic: Boolean) = AppStorage.format(total)
    }

    /**
     * تفصيل المساحة حسب النوع.
     *
     * يقرأ من نفس المشي الواحد الذي تستخدمه شاشة «مساحة التخزين»؛ لا مشي إضافي
     * ولا خطر تعليق. عند غياب نتيجة سابقة يجري الفحص القياسي (٤ ثوانٍ كحد أقصى).
     */
    suspend fun getStorageBreakdown(
        context: Context? = null,
        budgetMs: Long = 4_000L,
    ): Breakdown =
        withContext(Dispatchers.IO) {
            val ctx = context ?: application
            val report = runCatching { AppStorage.report(budgetMs) }.getOrNull()
            if (report == null) {
                return@withContext Breakdown(0, 0, emptyList(), partial = true, cached = false)
            }
            fromReport(report, ctx)
        }

    /**
     * يحوّل نتيجة [AppStorage.Report] الجاهزة إلى تفصيل بالتصنيفات الثمانية —
     * بلا أي مشي إضافي. تستخدمه شاشة «مساحة التخزين» بعد فحصها الواحد.
     */
    fun fromReport(report: AppStorage.Report, context: Context? = null): Breakdown {
            val ctx = context ?: application
            val sizes = report.categories
            val items =
                listOf(
                    Item(Category.CACHE, (sizes["cache"] ?: 0L) + (sizes["temp"] ?: 0L)),
                    // «بيئات التشغيل» = صورة Ubuntu + الأدوات المثبّتة داخلها
                    Item(Category.RUNTIMES, (sizes["image"] ?: 0L) + (sizes["tools"] ?: 0L)),
                    Item(Category.LANGUAGES, sizes["languages"] ?: 0L),
                    Item(Category.EXTENSIONS, sizes["extensions"] ?: 0L),
                    Item(Category.THEMES, sizes["themes"] ?: 0L),
                    Item(Category.LOGS, sizes["logs"] ?: 0L),
                    // «بقية البيانات» = ملفات المستخدم داخل الطرفية + أي شيء غير مصنّف
                    Item(Category.OTHER, (sizes["home"] ?: 0L) + (sizes["other"] ?: 0L)),
                    Item(Category.EXTERNAL, sizes["external"] ?: 0L),
                )
            return Breakdown(
                total = report.total,
                free = report.free,
                items = items,
                partial = report.partial,
                cached = report.fromCache,
            )
    }

    // ————————————————————— التنظيف —————————————————————

    /**
     * ينظّف ذاكرة التخزين المؤقتة فقط (وليس الملفات المؤقتة ولا البيانات).
     *
     * @return البايتات المحرّرة فعلًا.
     */
    suspend fun cleanCache(): Long =
        withContext(Dispatchers.IO) {
            val ctx = application ?: return@withContext 0L
            cleanDir(ctx.cacheDir)
        }

    /**
     * ينظّف الملفات المؤقتة فقط: مجلد temp الخاص بالتطبيق و`local/tmp`.
     *
     * @return البايتات المحرّرة فعلًا.
     */
    suspend fun cleanTempFiles(): Long =
        withContext(Dispatchers.IO) {
            val ctx = application ?: return@withContext 0L
            var freed = 0L
            freed += cleanDir(runCatching { getTempDir() }.getOrNull())
            freed += cleanDir(File(runCatching { localDir() }.getOrNull() ?: ctx.filesDir, "tmp"))
            freed
        }

    /**
     * هل هذا المسار محميّ من الحذف؟
     *
     * محميّ = يقع داخل `data/` أو `runtimes/` أو `languages/` أو `themes/`
     * (نسبةً إلى منطقة بيانات التطبيق). نقارن بالمسار الكنسي حتى لا يتحايل
     * رابط رمزي أو `..`، ونفحص المقاطع **بعد** مجلد التطبيق فقط.
     */
    fun isProtected(file: File): Boolean {
        val path = runCatching { file.canonicalPath }.getOrElse { return true }
        val roots =
            listOfNotNull(
                application?.filesDir?.canonicalPath?.substringBeforeLast('/'),
                application?.filesDir?.parentFile?.canonicalPath,
            )
        val appRoot = roots.firstOrNull { path.startsWith(it) }
        val relative = if (appRoot != null) path.removePrefix(appRoot) else path
        val segments = relative.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return false
        if (segments.first() == "data") return true
        return segments.any { it == "runtimes" || it == "languages" || it == "themes" }
    }

    /**
     * مسارات يُسمح بحذف محتوياتها. كل ما عداها يُرفض بلا استثناء، وهذا هو التطبيق
     * العملي للقاعدة الذهبية: `data/` و`runtimes/` و`languages/` و`themes/` خارج القائمة.
     */
    private fun allowedRoots(): List<String> {
        val ctx = application ?: return emptyList()
        val roots =
            listOfNotNull(
                ctx.cacheDir,
                ctx.codeCacheDir,
                runCatching { getTempDir() }.getOrNull(),
                runCatching { localDir() }.getOrNull()?.let { File(it, "tmp") },
            )
        return roots.mapNotNull { dir ->
            runCatching { dir.canonicalPath }.getOrNull()
        }
    }

    /** يحذف محتويات مجلد واحد بعد التأكّد أنه داخل مسار مسموح، ويعيد المحرَّر. */
    private fun cleanDir(dir: File?): Long {
        if (dir == null || !dir.exists() || !dir.isDirectory) return 0L
        val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: return 0L
        if (canonical !in allowedRoots()) return 0L
        if (isProtected(dir)) return 0L

        var freed = 0L
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return 0L
        for (child in children) {
            // لا نمسّ ملفات أو أقفالًا عابرة قد تكون قيد الاستخدام الآن
            if (AppStorage.isTransient(child.name)) continue
            val size = runCatching { AppStorage.sizeOf(child) }.getOrDefault(0L)
            val deleted =
                runCatching {
                        if (child.isDirectory) child.deleteRecursively() else child.delete()
                    }
                    .getOrDefault(false)
            if (deleted) freed += size
        }
        return freed
    }
}
