package com.mahdi.encoding

import java.nio.charset.Charset

/**
 * ترميز نصّي معروض في الواجهة مع اسم عربي مقروء.
 *
 * @property charset الترميز الفعلي من `java.nio.charset`
 * @property labelAr الاسم العربي المعروض
 * @property labelEn الاسم الإنجليزي/التقني
 * @property group مجموعة العرض (عربي، عالمي، شرق آسيوي…)
 */
data class MahdiEncoding(
    val charset: Charset,
    val labelAr: String,
    val labelEn: String,
    val group: String,
) {
    val name: String
        get() = charset.name()

    val displayName: String
        get() = "$labelAr — $name"
}

/**
 * مدير الترميزات: قائمة منسّقة بأكثر من عشرين ترميزًا، وكشف تلقائي يعتمد على
 * بصمة BOM ثم صلاحية UTF-8 ثم تخمين الترميز العربي، وأخيرًا Latin-1 كحلٍّ أخير.
 *
 * كل الترميزات تُطلب عبر [Charset.forName] مع تجاهل ما لا تدعمه المنصّة، فلا يتعطّل التطبيق
 * على جهاز ينقصه ترميز معيّن.
 */
object EncodingManager {

    private data class Entry(val names: List<String>, val labelAr: String, val labelEn: String, val group: String)

    private val catalog =
        listOf(
            Entry(listOf("UTF-8"), "UTF-8 (افتراضي)", "UTF-8", GROUP_UNIVERSAL),
            Entry(listOf("UTF-16LE"), "UTF-16 LE", "UTF-16 Little Endian", GROUP_UNIVERSAL),
            Entry(listOf("UTF-16BE"), "UTF-16 BE", "UTF-16 Big Endian", GROUP_UNIVERSAL),
            Entry(listOf("UTF-16"), "UTF-16", "UTF-16", GROUP_UNIVERSAL),
            Entry(listOf("UTF-32LE"), "UTF-32 LE", "UTF-32 Little Endian", GROUP_UNIVERSAL),
            Entry(listOf("UTF-32BE"), "UTF-32 BE", "UTF-32 Big Endian", GROUP_UNIVERSAL),
            Entry(listOf("US-ASCII"), "ASCII", "US-ASCII", GROUP_UNIVERSAL),
            Entry(listOf("ISO-8859-1"), "لاتيني 1 (ISO-8859-1)", "ISO-8859-1 Latin-1", GROUP_UNIVERSAL),
            Entry(listOf("ISO-8859-6"), "عربي (ISO-8859-6)", "ISO-8859-6 Arabic", GROUP_ARABIC),
            Entry(listOf("windows-1256"), "عربي (Windows-1256)", "Windows-1256 Arabic", GROUP_ARABIC),
            Entry(listOf("windows-1252"), "غربي (Windows-1252)", "Windows-1252", GROUP_UNIVERSAL),
            Entry(listOf("windows-1251"), "سيريلي (Windows-1251)", "Windows-1251 Cyrillic", GROUP_UNIVERSAL),
            Entry(listOf("KOI8-R"), "روسي (KOI8-R)", "KOI8-R", GROUP_UNIVERSAL),
            Entry(listOf("GBK"), "صيني مبسّط (GBK)", "GBK", GROUP_EAST_ASIA),
            Entry(listOf("GB18030"), "صيني شامل (GB18030)", "GB18030", GROUP_EAST_ASIA),
            Entry(listOf("Big5"), "صيني تقليدي (Big5)", "Big5", GROUP_EAST_ASIA),
            Entry(listOf("Shift_JIS", "Shift-JIS"), "ياباني (Shift-JIS)", "Shift_JIS", GROUP_EAST_ASIA),
            Entry(listOf("EUC-JP"), "ياباني (EUC-JP)", "EUC-JP", GROUP_EAST_ASIA),
            Entry(listOf("EUC-KR"), "كوري (EUC-KR)", "EUC-KR", GROUP_EAST_ASIA),
            Entry(listOf("TIS-620"), "تايلاندي (TIS-620)", "TIS-620", GROUP_EAST_ASIA),
            Entry(listOf("VISCII"), "فيتنامي (VISCII)", "VISCII", GROUP_UNIVERSAL),
            Entry(listOf("MacRoman"), "ماكنتوش رومان", "MacRoman", GROUP_UNIVERSAL),
            Entry(listOf("IBM437"), "DOS (IBM437)", "IBM437", GROUP_UNIVERSAL),
        )

    const val GROUP_UNIVERSAL = "عالمي"
    const val GROUP_ARABIC = "عربي"
    const val GROUP_EAST_ASIA = "شرق آسيوي"

    /** الترميزات المتاحة فعلًا على هذا الجهاز، بالترتيب المعروض. */
    val supported: List<MahdiEncoding> =
        catalog.mapNotNull { entry ->
            val charset = entry.names.firstNotNullOfOrNull { n -> runCatching { Charset.forName(n) }.getOrNull() }
            charset?.let { MahdiEncoding(it, entry.labelAr, entry.labelEn, entry.group) }
        }

    /** الترميزات الموصى بها (المجموعة العربية والعالمية أولًا). */
    val recommended: List<MahdiEncoding>
        get() = supported.filter { it.group != GROUP_EAST_ASIA }.ifEmpty { supported }

    /** كل ترميزات المنصّة عدا الموصى بها. */
    fun others(recommendedNames: Set<String>): List<MahdiEncoding> =
        Charset.availableCharsets().values.filterNot { it.name() in recommendedNames }.sortedBy { it.name() }

    /** يعيد ترميزًا بالاسم، أو UTF-8 إن كان الاسم غير معروف. */
    fun byName(name: String?): Charset =
        name?.let { n -> runCatching { Charset.forName(n) }.getOrNull() } ?: Charsets.UTF_8

    /** اسم معروض بالعربية لترميز، أو اسمه التقني إن لم يكن في القائمة. */
    fun displayName(charset: Charset): String =
        supported.firstOrNull { it.name.equals(charset.name(), ignoreCase = true) }?.labelAr ?: charset.name()

    // ——————————————— الكشف التلقائي ———————————————

    /** الترتيب: BOM ← UTF-8 صالح ← تخمين عربي ← Latin-1. */
    fun detect(bytes: ByteArray): Charset {
        bomCharset(bytes)?.let { return it }
        if (looksLikeUtf8(bytes)) return Charsets.UTF_8

        val arabic = runCatching { Charset.forName("windows-1256") }.getOrNull()
        if (arabic != null && looksLikeArabicWindows1256(bytes)) return arabic

        return Charset.forName("ISO-8859-1")
    }

    /** يقرأ بصمة BOM إن وُجدت ويعيد الترميز المقابل ويحذف البايتات من [out] إن طُلب. */
    fun bomCharset(bytes: ByteArray): Charset? {
        fun at(i: Int): Int = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
        return when {
            at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF -> Charsets.UTF_8
            at(0) == 0xFF && at(1) == 0xFE && at(2) == 0x00 && at(3) == 0x00 ->
                runCatching { Charset.forName("UTF-32LE") }.getOrNull()
            at(0) == 0x00 && at(1) == 0x00 && at(2) == 0xFE && at(3) == 0xFF ->
                runCatching { Charset.forName("UTF-32BE") }.getOrNull()
            at(0) == 0xFF && at(1) == 0xFE -> runCatching { Charset.forName("UTF-16LE") }.getOrNull()
            at(0) == 0xFE && at(1) == 0xFF -> runCatching { Charset.forName("UTF-16BE") }.getOrNull()
            else -> null
        }
    }

    /** هل البايتات تسلسل UTF-8 صالح؟ (تحقّق يدوي، لا يعتمد على الاستثناءات) */
    fun looksLikeUtf8(bytes: ByteArray): Boolean {
        var i = 0
        var multibyte = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b <= 0x7F -> {
                    i++
                    continue
                }
                // 2 بايت
                b in 0xC2..0xDF -> {
                    if (i + 1 >= bytes.size) return false
                    if (!isContinuation(bytes[i + 1])) return false
                    multibyte++
                    i += 2
                }
                // 3 بايت
                b in 0xE0..0xEF -> {
                    if (i + 2 >= bytes.size) return false
                    if (!isContinuation(bytes[i + 1]) || !isContinuation(bytes[i + 2])) return false
                    multibyte++
                    i += 3
                }
                // 4 بايت
                b in 0xF0..0xF4 -> {
                    if (i + 3 >= bytes.size) return false
                    if (!isContinuation(bytes[i + 1]) || !isContinuation(bytes[i + 2]) || !isContinuation(bytes[i + 3])) {
                        return false
                    }
                    multibyte++
                    i += 4
                }
                else -> return false
            }
        }
        // TEXT لملفات ASCII فقط: نعتبره UTF-8 (مقبول)
        return multibyte >= 0
    }

    private fun isContinuation(b: Byte): Boolean {
        val v = b.toInt() and 0xFF
        return v in 0x80..0xBF
    }

    /**
     * تخمين الترميز العربي القديم (Windows-1256): الحروف العربية في هذا الترميز تقع في
     * النطاق 0xC1..0xFE، بينما Latin-1 يستخدم هذا النطاق لحروف غربية مركّبة.
     * إذا كانت نسبة عالية من البايتات في نطاق الحروف العربية، نرجّح Windows-1256.
     */
    private fun looksLikeArabicWindows1256(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val sample = bytes.take(minOf(bytes.size, 8192))
        var arabicRange = 0
        var highBytes = 0
        for (b in sample) {
            val v = b.toInt() and 0xFF
            if (v >= 0x80) {
                highBytes++
                if (v in 0xC1..0xFE) arabicRange++
            }
        }
        if (highBytes < 8) return false
        // نسبة عالية من البايتات العالية تقع في نطاق الحروف العربية، مع وجود بايتات عالية كافية
        return arabicRange.toFloat() / highBytes.toFloat() >= 0.75f
    }
}
