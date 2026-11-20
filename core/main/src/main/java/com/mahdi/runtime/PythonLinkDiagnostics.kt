package com.mahdi.runtime

/**
 * 9.6.0 — تشخيص عطل الربط الديناميكي لبايثون، بلا أي اعتماد على أندرويد.
 *
 * الرسالة التي يراها المستخدم إنجليزية خام، ولا يقول له أحد ما معناها:
 *
 * ```
 * CANNOT LINK EXECUTABLE "/data/user/0/com.mahdi.editor/tmp/tmp…/Pyahmed.so":
 * library "libpython3.11.so.1.0" not found: needed by main executable
 * ```
 *
 * الخلاصة: **الملف التنفيذي سليم، والمشكلة في مسار البحث**. أي غلاف بايثون
 * (‏Pyahmed.so أو أي نسخة مستوردة) بُني ليعتمد على `libpythonX.Y.so.1.0`،
 * وهذه المكتبة غير موجودة في مسار الرابط (‏LD_LIBRARY_PATH · RPATH · ld.so.cache).
 *
 * هذا الصنف يترجم الرسالة إلى سبب عربي وإجراء واحد، ويُستخدم في:
 *  • فحص صحة الطرفية (`terminalChecks`)
 *  • سجلّ التشغيل (`UniversalRunner` عبر `self_heal.sh`)
 *
 * المنطق نفسه مُطبَّق في `features/terminal/src/main/assets/terminal/pylink.sh`
 * لأن الإصلاح الحقيقي يحدث داخل أوبنتو؛ هذا الصنف للعرض والتوجيه فقط.
 */
object PythonLinkDiagnostics {

    /** علامات أن الخطأ من الرابط الديناميكي لا من السكربت. */
    private val MARKERS =
        listOf(
            "CANNOT LINK EXECUTABLE",
            "error while loading shared libraries",
            "not found: needed by",
        )

    /** اسم المكتبة المفقودة: libpython3.11.so.1.0 */
    private val SONAME = Regex("libpython[0-9.]*\\.so[0-9.]*")

    /** هل النص يحمل بصمة عطل ربط بايثون؟ */
    fun isLinkError(output: String?): Boolean {
        if (output.isNullOrBlank()) return false
        return MARKERS.any { output.contains(it) } && SONAME.containsMatchIn(output)
    }

    /** اسم المكتبة المفقودة كما ورد، أو `libpython` إن لم يُذكر. */
    fun soname(output: String?): String =
        output?.let { SONAME.find(it)?.value } ?: "libpython"

    /** رقم إصدار بايثون المطلوب (‏3.11)، أو `null`. */
    fun version(output: String?): String? {
        val name = soname(output)
        return Regex("libpython([0-9]+\\.[0-9]+)")
            .find(name)
            ?.groupValues
            ?.getOrNull(1)
    }

    /**
     * سبب عربي واحد + إجراء واحد، أو `null` إن لم يكن عطل ربط.
     *
     * القاعدة المتبعة في كل التطبيق: **سبب واحد، إجراء واحد** — بلا نص إنجليزي
     * خام ولا خطوتين متعارضتين.
     */
    fun arabicReason(output: String?, isArabic: Boolean = true): String? {
        if (!isLinkError(output)) return null
        val name = soname(output)
        return if (isArabic) {
            "مكتبة بايثون المشتركة مفقودة: $name — أصلحها بـ mh pyfix"
        } else {
            "Missing shared Python library: $name — repair with mh pyfix"
        }
    }
}
