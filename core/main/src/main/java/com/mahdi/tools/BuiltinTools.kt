package com.mahdi.tools

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.UUID

/**
 * «الإضافات المدمجة» — أدوات جاهزة داخل Mahdi-Editor بلا تثبيت (2.8.2).
 *
 * كل أداة هنا تعمل بلا إنترنت وبلا إذن إضافي، وتظهر في تبويب «الإضافات» داخل المتجر
 * كإضافة مدمجة لها شاشة خاصة: إدخال، أزرار تنفيذ، ونتيجة قابلة للنسخ أو المشاركة.
 *
 * الأدوات مبنيّة على نصوص فقط، فالنتيجة قابلة للتنبّؤ ومستقلة عن أي خدمة خارجية.
 */
data class ToolAction(
    val id: String,
    val labelAr: String,
    val labelEn: String,
    val run: (String) -> String,
)

data class BuiltinTool(
    val id: String,
    val emoji: String,
    val nameAr: String,
    val nameEn: String,
    val descAr: String,
    val descEn: String,
    val actions: List<ToolAction>,
) {
    fun name(isArabic: Boolean): String = if (isArabic) nameAr else nameEn

    fun description(isArabic: Boolean): String = if (isArabic) descAr else descEn
}

object BuiltinTools {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    // ——————————————————————————————————————————————————————————————
    // ١ · النصوص
    // ——————————————————————————————————————————————————————————————

    private val textTool =
        BuiltinTool(
            id = "text",
            emoji = "🔤",
            nameAr = "أدوات النص",
            nameEn = "Text tools",
            descAr = "حالة الأحرف، عكس النص، عدّ الكلمات والأسطر، وترتيب الأسطر",
            descEn = "Case conversion, reverse, word/line counts and line sorting",
            actions =
                listOf(
                    action("upper", "أحرف كبيرة", "UPPERCASE") { it.uppercase(Locale.ROOT) },
                    action("lower", "أحرف صغيرة", "lowercase") { it.lowercase(Locale.ROOT) },
                    action("title", "أول كل كلمة", "Title Case") { it.toTitleCase() },
                    action("sentence", "أول الجملة", "Sentence case") { it.toSentenceCase() },
                    action("reverse", "عكس النص", "Reverse") { it.reversed() },
                    action("stats", "إحصاءات", "Statistics") { it.describeText() },
                    action("sort_az", "ترتيب أبجدي", "Sort A→Z") { it.lines().sortedBy { l -> l.lowercase(Locale.ROOT) }.joinToString("\n") },
                    action("sort_za", "ترتيب عكسي", "Sort Z→A") { it.lines().sortedByDescending { l -> l.lowercase(Locale.ROOT) }.joinToString("\n") },
                    action("unique", "إزالة التكرار", "Remove duplicates") { it.lines().distinct().joinToString("\n") },
                    action("no_empty", "حذف الأسطر الفارغة", "Drop empty lines") { it.lines().filter { l -> l.isNotBlank() }.joinToString("\n") },
                    action("trim", "تشذيب الفراغات", "Trim lines") { it.lines().joinToString("\n") { l -> l.trim() } },
                    action("squeeze", "دمج الفراغات", "Squeeze spaces") { it.replace(Regex("[ \\t]{2,}"), " ") },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٢ · الترميز
    // ——————————————————————————————————————————————————————————————

    private val codecTool =
        BuiltinTool(
            id = "codec",
            emoji = "🔐",
            nameAr = "الترميز والتشفير",
            nameEn = "Encoding",
            descAr = "Base64 وURL وHex وROT13 — في الاتجاهين",
            descEn = "Base64, URL, Hex and ROT13 — both directions",
            actions =
                listOf(
                    action("b64e", "Base64 تشفير", "Base64 encode") { Base64.getEncoder().encodeToString(it.toByteArray()) },
                    action("b64d", "Base64 فك", "Base64 decode") {
                        String(Base64.getDecoder().decode(it.filterNot { c -> c.isWhitespace() }))
                    },
                    action("b64ue", "Base64URL تشفير", "Base64URL encode") {
                        Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray())
                    },
                    action("urle", "URL تشفير", "URL encode") { java.net.URLEncoder.encode(it, "UTF-8") },
                    action("urld", "URL فك", "URL decode") { java.net.URLDecoder.decode(it, "UTF-8") },
                    action("hexe", "Hex تشفير", "Hex encode") { it.toByteArray().joinToString(" ") { b -> "%02X".format(b) } },
                    action("hexd", "Hex فك", "Hex decode") {
                        val cleaned = it.replace(Regex("[^0-9a-fA-F]"), "")
                        require(cleaned.length % 2 == 0) { "طول Hex فردي" }
                        cleaned.chunked(2).map { h -> h.toInt(16).toByte() }.toByteArray().decodeToString()
                    },
                    action("rot13", "ROT13", "ROT13") { it.rot13() },
                    action("json_escape", "تهريب JSON", "Escape JSON") { it.jsonEscape() },
                    action("json_unescape", "فك تهريب JSON", "Unescape JSON") { it.jsonUnescape() },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٣ · البصمات
    // ——————————————————————————————————————————————————————————————

    private val hashTool =
        BuiltinTool(
            id = "hash",
            emoji = "#️⃣",
            nameAr = "البصمات (Hash)",
            nameEn = "Hashes",
            descAr = "MD5 وSHA-1 وSHA-256 وSHA-512",
            descEn = "MD5, SHA-1, SHA-256 and SHA-512",
            actions =
                listOf(
                    action("md5", "MD5", "MD5") { it.digest("MD5") },
                    action("sha1", "SHA-1", "SHA-1") { it.digest("SHA-1") },
                    action("sha256", "SHA-256", "SHA-256") { it.digest("SHA-256") },
                    action("sha512", "SHA-512", "SHA-512") { it.digest("SHA-512") },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٤ · JSON
    // ——————————————————————————————————————————————————————————————

    private val jsonTool =
        BuiltinTool(
            id = "json",
            emoji = "🧩",
            nameAr = "أدوات JSON",
            nameEn = "JSON tools",
            descAr = "تنسيق وتصغير والتحقّق وترتيب المفاتيح",
            descEn = "Format, minify, validate and sort keys",
            actions =
                listOf(
                    action("pretty", "تنسيق", "Pretty print") {
                        gson.toJson(JsonParser.parseString(it))
                    },
                    action("minify", "تصغير", "Minify") {
                        com.google.gson.JsonParser.parseString(it).toString()
                    },
                    action("validate", "تحقّق", "Validate") {
                        runCatching { JsonParser.parseString(it) }
                            .map { "✔ JSON صالح — ${it.toString().length} حرفًا" }
                            .getOrElse { e -> "✘ خطأ: ${e.message}" }
                    },
                    action("sort_keys", "ترتيب المفاتيح", "Sort keys") {
                        gson.toJson(JsonParser.parseString(it).sortKeysRecursively())
                    },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٥ · العربية
    // ——————————————————————————————————————————————————————————————

    private val arabicTool =
        BuiltinTool(
            id = "arabic",
            emoji = "🕌",
            nameAr = "أدوات عربية",
            nameEn = "Arabic tools",
            descAr = "إزالة التشكيل والتطويل، تطبيع الحروف، وتحويل الأرقام",
            descEn = "Remove diacritics, normalize letters and convert digits",
            actions =
                listOf(
                    action("strip_tashkeel", "إزالة التشكيل", "Strip diacritics") { it.stripArabicMarks() },
                    action("normalize", "تطبيع الحروف", "Normalize letters") { it.normalizeArabic() },
                    action("digits_to_ascii", "أرقام لاتينية", "Digits → 0-9") { it.arabicDigitsToAscii() },
                    action("digits_to_arabic", "أرقام عربية", "Digits → ٠-٩") { it.asciiDigitsToArabic() },
                    action("strip_tatweel", "إزالة التطويل", "Strip tatweel") { it.replace("ـ", "") },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٦ · الأسطر
    // ——————————————————————————————————————————————————————————————

    private val linesTool =
        BuiltinTool(
            id = "lines",
            emoji = "📏",
            nameAr = "أدوات الأسطر",
            nameEn = "Line tools",
            descAr = "ترقيم، نقاط، فواصل، وتجميع الأسطر",
            descEn = "Numbering, bullets, separators and joining",
            actions =
                listOf(
                    action("number", "ترقيم الأسطر", "Number lines") {
                        it.lines().mapIndexed { i, l -> "${i + 1}. $l" }.joinToString("\n")
                    },
                    action("bullets", "نقاط", "Bullets") { it.lines().joinToString("\n") { l -> "• $l" } },
                    action("csv", "فصل بفواصل", "Join with commas") { it.lines().joinToString(", ") { l -> l.trim() } },
                    action("one_line", "سطر واحد", "Collapse to one line") { it.replace(Regex("\\s+"), " ").trim() },
                    action("split_csv", "تفكيك الفواصل", "Split on commas") { it.split(",").joinToString("\n") { p -> p.trim() } },
                    action("wrap_80", "تغليف ٨٠", "Wrap at 80") { it.wrap(80) },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٧ · المولّدات
    // ——————————————————————————————————————————————————————————————

    private val generatorTool =
        BuiltinTool(
            id = "generate",
            emoji = "🎲",
            nameAr = "المولّدات",
            nameEn = "Generators",
            descAr = "UUID وكلمات مرور ورموز عشوائية",
            descEn = "UUIDs, strong passwords and random tokens",
            actions =
                listOf(
                    action("uuid", "٥ معرّفات UUID", "5 UUIDs") {
                        (1..5).joinToString("\n") { UUID.randomUUID().toString() }
                    },
                    action("password", "كلمة مرور قوية", "Strong password") { randomPassword(20) },
                    action("token", "رمز عشوائي", "Random token") { randomToken(32) },
                    action("digits", "٦ أرقام", "6 digits") { (1..6).joinToString("") { kotlin.random.Random.nextInt(0, 10).toString() } },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٨ · الأسماء البرمجية
    // ——————————————————————————————————————————————————————————————

    private val namingTool =
        BuiltinTool(
            id = "naming",
            emoji = "🐫",
            nameAr = "تحويل الأسماء",
            nameEn = "Naming",
            descAr = "camelCase وsnake_case وkebab-case وCONSTANT_CASE",
            descEn = "camelCase, snake_case, kebab-case and CONSTANT_CASE",
            actions =
                listOf(
                    action("camel", "camelCase", "camelCase") { it.toWords().toCamel() },
                    action("pascal", "PascalCase", "PascalCase") { it.toWords().toPascal() },
                    action("snake", "snake_case", "snake_case") { it.toWords().joinToString("_") },
                    action("kebab", "kebab-case", "kebab-case") { it.toWords().joinToString("-") },
                    action("constant", "CONSTANT_CASE", "CONSTANT_CASE") { it.toWords().joinToString("_").uppercase(Locale.ROOT) },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٩ · الألوان
    // ——————————————————————————————————————————————————————————————

    private val colorTool =
        BuiltinTool(
            id = "color",
            emoji = "🎨",
            nameAr = "أدوات الألوان",
            nameEn = "Color tools",
            descAr = "HEX ↔ RGB وتحويل الصيغ",
            descEn = "HEX ↔ RGB and format conversion",
            actions =
                listOf(
                    action("hex_to_rgb", "HEX ← RGB", "HEX → RGB") { it.hexToRgb() },
                    action("rgb_to_hex", "RGB ← HEX", "RGB → HEX") { it.rgbToHex() },
                    action("to_lower_hex", "hex صغير", "Lowercase hex") { it.trim().lowercase(Locale.ROOT) },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ١٠ · الاستخراج
    // ——————————————————————————————————————————————————————————————

    private val extractTool =
        BuiltinTool(
            id = "extract",
            emoji = "🔎",
            nameAr = "الاستخراج",
            nameEn = "Extract",
            descAr = "سحب الروابط والبريد والأرقام والوسوم من أي نص",
            descEn = "Pull URLs, emails, numbers and hashtags out of any text",
            actions =
                listOf(
                    action("urls", "الروابط", "URLs") {
                        Regex("https?://[^\\s\"'<>)]+").findAll(it).joinToString("\n") { m -> m.value }
                    },
                    action("emails", "البريد", "Emails") {
                        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").findAll(it).joinToString("\n") { m -> m.value }
                    },
                    action("numbers", "الأرقام", "Numbers") {
                        Regex("-?\\d+([.,]\\d+)?").findAll(it).joinToString("\n") { m -> m.value }
                    },
                    action("hashtags", "الوسوم", "Hashtags") {
                        Regex("#[\\p{L}0-9_]+").findAll(it).joinToString("\n") { m -> m.value }
                    },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ١١ · CSV
    // ——————————————————————————————————————————————————————————————

    private val csvTool =
        BuiltinTool(
            id = "csv",
            emoji = "📊",
            nameAr = "أدوات CSV",
            nameEn = "CSV tools",
            descAr = "تحويل الجدول إلى JSON أو أسطر مصفوفة",
            descEn = "Turn a table into JSON or array lines",
            actions =
                listOf(
                    action("to_json", "إلى JSON", "To JSON") { it.csvToJson() },
                    action("to_rows", "أسطر مفصولة", "Row list") {
                        it.lineSequence().drop(1).joinToString("\n") { row -> row.replace(",", " | ") }
                    },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ١٢ · المقارنة
    // ——————————————————————————————————————————————————————————————

    private val diffTool =
        BuiltinTool(
            id = "diff",
            emoji = "🆚",
            nameAr = "مقارنة نصوص",
            nameEn = "Text diff",
            descAr = "قارن نسختين سطرًا بسطر (اكتب النسختين مفصولتين بسطر «---»)",
            descEn = "Compare two versions line by line (separate them with a \"---\" line)",
            actions =
                listOf(
                    action("diff", "قارن", "Compare") { it.simpleDiff() },
                    action("same", "الأسطر المشتركة", "Common lines") {
                        val (a, b) = it.splitOnSeparator()
                        a.lines().filter { l -> l.isNotBlank() && l in b.lines() }.distinct().joinToString("\n")
                    },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ١٣ · الويب والوسوم
    // ——————————————————————————————————————————————————————————————

    private val markupTool =
        BuiltinTool(
            id = "markup",
            emoji = "🏷️",
            nameAr = "الوسوم والترميز",
            nameEn = "Markup",
            descAr = "تهريب HTML/XML، إزالة الوسوم، وتحويل CSV إلى جدول Markdown",
            descEn = "HTML/XML escaping, tag stripping and CSV→Markdown tables",
            actions =
                listOf(
                    action("html_escape", "تهريب HTML", "Escape HTML") {
                        it.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                    },
                    action("html_unescape", "فك تهريب HTML", "Unescape HTML") {
                        it.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
                    },
                    action("strip_tags", "إزالة الوسوم", "Strip tags") {
                        it.replace(Regex("<[^>]+>"), "").replace(Regex("[ \\t]{2,}"), " ").trim()
                    },
                    action("md_table", "جدول Markdown", "CSV → Markdown") { it.csvToMarkdownTable() },
                    action("xml_minify", "تصغير XML", "Minify XML") {
                        it.replace(Regex(">\\s+<"), "><").trim()
                    },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ١٤ · Regex
    // ——————————————————————————————————————————————————————————————

    private val regexTool =
        BuiltinTool(
            id = "regex",
            emoji = "🧪",
            nameAr = "اختبار Regex",
            nameEn = "Regex tester",
            descAr = "السطر الأول هو النمط، وبقيّة النص هو المادّة — استخرج أو استبدل أو امسح",
            descEn = "First line is the pattern, the rest is the input — find, replace or strip",
            actions =
                listOf(
                    action("find", "المطابقات", "Find all") { it.regexMatches() },
                    action("count", "عدد المطابقات", "Count") {
                        val (pattern, text) = it.splitPattern()
                        "عدد المطابقات: ${Regex(pattern).findAll(text).count()}"
                    },
                    action("strip", "حذف المطابقات", "Remove matches") {
                        val (pattern, text) = it.splitPattern()
                        text.replace(Regex(pattern), "")
                    },
                    action("lines", "أسطر مطابقة", "Matching lines") {
                        val (pattern, text) = it.splitPattern()
                        val regex = Regex(pattern)
                        text.lines().filter { line -> regex.containsMatchIn(line) }.joinToString("\n")
                    },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ١٥ · SQL وCSS
    // ——————————————————————————————————————————————————————————————

    private val sqlTool =
        BuiltinTool(
            id = "sql",
            emoji = "🗄️",
            nameAr = "SQL وCSS",
            nameEn = "SQL & CSS",
            descAr = "تنسيق بسيط للاستعلامات، حذف التعليقات، وتصغير CSS",
            descEn = "Basic SQL formatting, comment stripping and CSS minification",
            actions =
                listOf(
                    action("sql_format", "تنسيق SQL", "Format SQL") { it.formatSql() },
                    action("sql_strip", "حذف التعليقات", "Strip comments") {
                        it.replace(Regex("--[^\\n]*"), "").replace(Regex("/\\*[\\s\\S]*?\\*/"), "").trim()
                    },
                    action("css_minify", "تصغير CSS", "Minify CSS") {
                        it.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
                            .replace(Regex("\\s+"), " ")
                            .replace(Regex("\\s*([{};:,])\\s*"), "$1")
                            .trim()
                    },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ١٦ · JWT والرموز
    // ——————————————————————————————————————————————————————————————

    private val jwtTool =
        BuiltinTool(
            id = "jwt",
            emoji = "🎫",
            nameAr = "فك JWT",
            nameEn = "JWT decoder",
            descAr = "يقرأ الترويسة والمحتوى من رمز JWT بلا تحقّق توقيع",
            descEn = "Reads the header and payload of a JWT without verifying the signature",
            actions =
                listOf(
                    action("decode", "فك الرمز", "Decode") { it.decodeJwt() },
                    action("payload", "المحتوى فقط", "Payload only") { it.decodeJwtPayload() },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ١٧ · الأنظمة العددية
    // ——————————————————————————————————————————————————————————————

    private val baseTool =
        BuiltinTool(
            id = "bases",
            emoji = "🔢",
            nameAr = "الأنظمة العددية",
            nameEn = "Number bases",
            descAr = "تحويل عشري/ثنائي/ثماني/ست عشري وأرقام رومانية",
            descEn = "Convert dec/bin/oct/hex and Roman numerals",
            actions =
                listOf(
                    action("to_bin", "إلى ثنائي", "To binary") { it.parseNumber().toString(2) },
                    action("to_oct", "إلى ثماني", "To octal") { it.parseNumber().toString(8) },
                    action("to_hex", "إلى ست عشري", "To hex") { it.parseNumber().toString(16).uppercase(Locale.ROOT) },
                    action("to_dec", "إلى عشري", "To decimal") { it.parseNumber().toString() },
                    action("to_roman", "روماني", "To Roman") { it.parseNumber().toRoman() },
                    action("bits", "بتّات", "Bit breakdown") { it.parseNumber().toBitBreakdown() },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ١٨ · Unicode
    // ——————————————————————————————————————————————————————————————

    private val unicodeTool =
        BuiltinTool(
            id = "unicode",
            emoji = "🈚",
            nameAr = "Unicode",
            nameEn = "Unicode",
            descAr = "تهريب \\u وفكّه، وحساب نقاط الترميز",
            descEn = "Escape/unescape \\u sequences and list code points",
            actions =
                listOf(
                    action("escape", "تهريب Unicode", "Escape") {
                        it.map { c -> if (c.code < 128) c.toString() else "\\u%04x".format(Locale.ROOT, c.code) }.joinToString("")
                    },
                    action("unescape", "فك Unicode", "Unescape") { it.unicodeUnescape() },
                    action("codepoints", "نقاط الترميز", "Code points") {
                        it.take(40).map { c -> "%s U+%04X".format(Locale.ROOT, c, c.code) }.joinToString("\n")
                    },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ١٩ · الوحدات والوقت
    // ——————————————————————————————————————————————————————————————

    private val unitsTool =
        BuiltinTool(
            id = "units",
            emoji = "📐",
            nameAr = "الوحدات والوقت",
            nameEn = "Units & time",
            descAr = "حرارة وطول ووزن، وتحويل الطوابع الزمنية",
            descEn = "Temperature, length, weight and timestamps",
            actions =
                listOf(
                    action("c_to_f", "°م ← °ف", "°C → °F") { "%.1f".format(Locale.ROOT, it.parseNumber() * 9.0 / 5.0 + 32) },
                    action("f_to_c", "°ف ← °م", "°F → °C") { "%.1f".format(Locale.ROOT, (it.parseNumber() - 32) * 5.0 / 9.0) },
                    action("m_to_ft", "متر ← قدم", "m → ft") { "%.2f".format(Locale.ROOT, it.parseNumber() * 3.28084) },
                    action("kg_to_lb", "كغ ← باوند", "kg → lb") { "%.2f".format(Locale.ROOT, it.parseNumber() * 2.20462) },
                    action("ts_to_date", "طابع ← تاريخ", "Timestamp → date") { it.timestampToDate() },
                    action("date_to_ts", "تاريخ ← طابع", "Date → timestamp") { it.dateToTimestamp() },
                    action("now", "الآن", "Now") { System.currentTimeMillis().let { "${it / 1000}\n$it" } },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٢٠ · الشبكة والصلاحيات
    // ——————————————————————————————————————————————————————————————

    private val netTool =
        BuiltinTool(
            id = "net",
            emoji = "🌐",
            nameAr = "الشبكة والصلاحيات",
            nameEn = "Network & perms",
            descAr = "حاسبة قناع الشبكة وحاسبة صلاحيات chmod",
            descEn = "Subnet mask calculator and chmod calculator",
            actions =
                listOf(
                    action("cidr", "قناع الشبكة", "Subnet mask") { it.cidrInfo() },
                    action("chmod", "صلاحيات chmod", "chmod") { it.chmodInfo() },
                    action("port", "منفذ؟", "Valid port") {
                        val port = it.trim().toIntOrNull()
                        if (port != null && port in 1..65535) "✔ منفذ صالح" else "✘ منفذ غير صالح"
                    },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٢١ · تحليل النص
    // ——————————————————————————————————————————————————————————————

    private val analysisTool =
        BuiltinTool(
            id = "analysis",
            emoji = "📈",
            nameAr = "تحليل النص",
            nameEn = "Text analysis",
            descAr = "تكرار الكلمات والحروف، الأسطر المكرّرة، وقوّة كلمة المرور",
            descEn = "Word/char frequency, duplicate lines and password strength",
            actions =
                listOf(
                    action("word_freq", "تكرار الكلمات", "Word frequency") { it.wordFrequency() },
                    action("char_freq", "تكرار الحروف", "Char frequency") { it.charFrequency() },
                    action("dupes", "الأسطر المكرّرة", "Duplicate lines") {
                        it.lines().groupingBy { l -> l }.eachCount().filter { e -> e.value > 1 }
                            .entries.joinToString("\n") { e -> "${e.value}× ${e.key}" }
                    },
                    action("strength", "قوّة كلمة المرور", "Password strength") { it.passwordStrength() },
                    action("palindrome", "متناظرة؟", "Palindrome") {
                        val clean = it.filter { c -> c.isLetterOrDigit() }.lowercase(Locale.ROOT)
                        if (clean == clean.reversed()) "✔ متناظرة" else "✘ غير متناظرة"
                    },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٢٢ · التنسيق والأرقام
    // ——————————————————————————————————————————————————————————————

    private val formatTool =
        BuiltinTool(
            id = "format",
            emoji = "🔡",
            nameAr = "تنسيق الأرقام والنصوص",
            nameEn = "Number & text format",
            descAr = "فواصل الآلاف، تصفير، ترقيم عشري، وتنظيف المسافات",
            descEn = "Thousands separators, padding, decimals and whitespace cleanup",
            actions =
                listOf(
                    action("thousands", "فواصل الآلاف", "Thousands") { "%,d".format(Locale.ROOT, it.parseNumber().toLong()) },
                    action("pad", "تصفير لليسار", "Zero pad") { it.lines().joinToString("\n") { l -> l.padStart(6, '0') } },
                    action("decimals", "منزلتان عشريتان", "Two decimals") { "%.2f".format(Locale.ROOT, it.parseNumber()) },
                    action("slug", "Slug", "Slug") { it.slugify() },
                    action("clean", "تنظيف المسافات", "Clean spaces") {
                        it.replace('\u00A0', ' ').replace(Regex("[ \\t]+"), " ").lines().joinToString("\n") { l -> l.trim() }
                    },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٢٣ · مولّدات إضافية
    // ——————————————————————————————————————————————————————————————

    private val extraGeneratorTool =
        BuiltinTool(
            id = "generate2",
            emoji = "✨",
            nameAr = "مولّدات إضافية",
            nameEn = "More generators",
            descAr = "نص عربي بديل، إيموجي، ألوان عشوائية، وبيانات وهمية",
            descEn = "Arabic placeholder text, emoji, random colors and mock data",
            actions =
                listOf(
                    action("lorem_ar", "نص عربي بديل", "Arabic lorem") {
                        "هذا نصٌّ تجريبيٌّ قصير يُستخدم لمعاينة التنسيق والاتجاه في المحرّر، " +
                            "ويحتوي أرقامًا 123 وأرقامًا عربية ١٢٣ ورموزًا %42 لاختبار العرض."
                    },
                    action("lorem_en", "Lorem ipsum", "Lorem ipsum") {
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor " +
                            "incididunt ut labore et dolore magna aliqua. 42% done."
                    },
                    action("emoji", "إيموجي", "Emoji row") { "🚀 ✅ ⚙️ 📦 🎨 🧰 🔧 📘 🌐 ⌨️ 🧪 🔐 📊 🕌 📏 🎲" },
                    action("colors", "٥ ألوان عشوائية", "5 random colors") {
                        val rnd = java.security.SecureRandom()
                        (1..5).joinToString("\n") { "#%06X".format(Locale.ROOT, rnd.nextInt(0x1000000)) }
                    },
                    action("mock_json", "JSON وهمي", "Mock JSON") {
                        "{\n  \"id\": ${(1000..9999).random()},\n  \"name\": \"Mahdi\",\n  \"active\": true\n}"
                    },
                ),
        )

    // ——————————————————————————————————————————————————————————————
    // ٢٤ · المقارنة والتشابه
    // ——————————————————————————————————————————————————————————————

    private val compareTool =
        BuiltinTool(
            id = "compare2",
            emoji = "🔀",
            nameAr = "التشابه والفرق",
            nameEn = "Similarity",
            descAr = "نسبة تشابه نصّين، والكلمات المشتركة والمختلفة",
            descEn = "Similarity ratio plus shared and unique words",
            actions =
                listOf(
                    action("similarity", "نسبة التشابه", "Similarity") {
                        val (a, b) = it.splitOnSeparator()
                        if (b.isBlank()) {
                            "ضع النصّين مفصولين بسطر ---"
                        } else {
                            val setA = a.toWords().toSet()
                            val setB = b.toWords().toSet()
                            val common = setA.intersect(setB).size
                            val total = setA.union(setB).size
                            "التشابه: %.1f%% (%d من %d كلمة)".format(Locale.ROOT, if (total == 0) 0.0 else common * 100.0 / total, common, total)
                        }
                    },
                    action("common", "كلمات مشتركة", "Shared words") {
                        val (a, b) = it.splitOnSeparator()
                        a.toWords().toSet().intersect(b.toWords().toSet()).joinToString("\n")
                    },
                    action("unique", "كلمات فريدة", "Unique words") {
                        val (a, b) = it.splitOnSeparator()
                        a.toWords().toSet().minus(b.toWords().toSet()).joinToString("\n")
                    },
                ),
        )

    /** كل الأدوات المدمجة بترتيب العرض. */
    val all: List<BuiltinTool> =
        listOf(
            textTool,
            namingTool,
            arabicTool,
            linesTool,
            codecTool,
            hashTool,
            jsonTool,
            extractTool,
            csvTool,
            colorTool,
            generatorTool,
            diffTool,
            markupTool,
            regexTool,
            sqlTool,
            jwtTool,
            baseTool,
            unicodeTool,
            unitsTool,
            netTool,
            analysisTool,
            formatTool,
            extraGeneratorTool,
            compareTool,
        )

    /** إجمالي الأفعال المتاحة في كل الأدوات. */
    val actionCount: Int get() = all.sumOf { it.actions.size }

    fun byId(id: String): BuiltinTool? = all.firstOrNull { it.id == id }

    // ——————————————————————————————————————————————————————————————
    // أدوات مساعدة
    // ——————————————————————————————————————————————————————————————

    // ——————————————————————————————————————————————————————————————
    // أدوات مساعدة للأدوات الموسّعة (3.0.0)
    // ——————————————————————————————————————————————————————————————

    private fun String.csvToMarkdownTable(): String {
        val rows = lineSequence().filter { it.isNotBlank() }.toList()
        if (rows.isEmpty()) return ""
        val cells = rows.map { row -> splitCsvRow(row) }
        val columns = cells.maxOf { it.size }
        val out = StringBuilder()
        for ((index, row) in cells.withIndex()) {
            out.append("| ")
            for (c in 0 until columns) {
                out.append(row.getOrElse(c) { "" }).append(" | ")
            }
            out.append('\n')
            if (index == 0) {
                out.append("|")
                repeat(columns) { out.append(" --- |") }
                out.append('\n')
            }
        }
        return out.toString().trimEnd()
    }

    private fun String.splitPattern(): Pair<String, String> {
        val newline = indexOf('\n')
        if (newline <= 0) return trim() to ""
        return substring(0, newline).trim() to substring(newline + 1)
    }

    private fun String.regexMatches(): String {
        val (pattern, text) = splitPattern()
        if (text.isBlank()) return "السطر الأول هو النمط، وما بعده النص"
        return Regex(pattern).findAll(text).joinToString("\n") { m -> m.value }
    }

    private val SQL_KEYWORDS =
        listOf(
            "SELECT", "FROM", "WHERE", "GROUP BY", "ORDER BY", "HAVING", "LIMIT", "OFFSET",
            "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN", "JOIN", "ON", "INSERT INTO",
            "VALUES", "UPDATE", "SET", "DELETE FROM", "CREATE TABLE", "ALTER TABLE", "DROP TABLE",
            "UNION ALL", "UNION", "AND", "OR", "AS", "DESC", "ASC",
        )

    private fun String.formatSql(): String {
        var text = replace(Regex("\\s+"), " ").trim()
        SQL_KEYWORDS.forEach { keyword ->
            text = text.replace(Regex("(?i)\\b" + Regex.escape(keyword) + "\\b"), keyword)
        }
        listOf("SELECT", "FROM", "WHERE", "GROUP BY", "ORDER BY", "HAVING", "LIMIT", "VALUES", "SET")
            .forEach { keyword ->
                text = text.replace(" $keyword ", "\n$keyword ")
            }
        return text.trim()
    }

    private fun String.jwtParts(): List<String> = trim().split('.')

    private fun String.decodeJwtPayload(): String {
        val parts = jwtParts()
        if (parts.size < 2) return "✘ ليس رمز JWT صالحًا"
        return runCatching {
            val json = String(Base64.getUrlDecoder().decode(parts[1].padEnd((parts[1].length + 3) / 4 * 4, '=')))
            gson.toJson(JsonParser.parseString(json))
        }
            .getOrElse { e -> "✘ ${e.message}" }
    }

    private fun String.decodeJwt(): String {
        val parts = jwtParts()
        if (parts.size < 2) return "✘ ليس رمز JWT صالحًا"
        val header =
            runCatching {
                String(Base64.getUrlDecoder().decode(parts[0].padEnd((parts[0].length + 3) / 4 * 4, '=')))
            }
                .getOrElse { "{}" }
        return "الترويسة:\n$header\n\nالمحتوى:\n" + decodeJwtPayload() +
            "\n\n(التوقيع غير مُتحقَّق منه — هذه الأداة للقراءة فقط)"
    }

    /** يقرأ عددًا بـ 0x (ست عشري) أو 0b (ثنائي) أو 0o (ثماني) أو عشري. */
    private fun String.parseNumber(): Long {
        val text = trim().replace(" ", "").replace(",", "")
        require(text.isNotEmpty()) { "أدخل عددًا" }
        val sign = if (text.startsWith("-")) -1 else 1
        val body = text.removePrefix("-").removePrefix("+").lowercase(Locale.ROOT)
        return sign * when {
            body.startsWith("0x") -> body.drop(2).toLong(16)
            body.startsWith("0b") -> body.drop(2).toLong(2)
            body.startsWith("0o") -> body.drop(2).toLong(8)
            else -> body.toLong()
        }
    }

    private fun Long.toRoman(): String {
        if (this <= 0 || this > 3999) return "خارج النطاق (1-3999)"
        val values = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
            50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
        var n = this
        return buildString {
            values.forEach { (value, symbol) ->
                while (n >= value) {
                    append(symbol)
                    n -= value
                }
            }
        }
    }

    private fun Long.toBitBreakdown(): String {
        val hex = toString(16).uppercase(Locale.ROOT)
        val bits = toString(2)
        return """
            عشري: $this
            ست عشري: 0x$hex
            ثنائي: $bits
            عدد البتّات: ${bits.trimStart('-').length}
            البايتات: ${(bits.trimStart('-').length + 7) / 8}
        """.trimIndent()
    }

    private fun String.unicodeUnescape(): String {
        val regex = Regex("\\\\u([0-9a-fA-F]{4})")
        return replace(regex) { match -> match.groupValues[1].toInt(16).toChar().toString() }
    }

    private val ISO_FORMAT = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun String.timestampToDate(): String {
        val raw = trim().replace(",", "")
        val value = raw.toLongOrNull() ?: return "✘ أدخل طابعًا زمنيًا بالأرقام"
        // ثوانٍ أم ميلي ثانية؟ الطوابع الحالية بالميلي ثانية أكبر بكثير
        val millis = if (value < 100_000_000_000L) value * 1000 else value
        return "${ISO_FORMAT.format(java.util.Date(millis))}\n${java.util.Date(millis).toInstant()}"
    }

    private fun String.dateToTimestamp(): String =
        runCatching {
            val date = ISO_FORMAT.parse(trim()) ?: return "✘ صيغة غير مفهومة (yyyy-MM-dd HH:mm:ss)"
            val millis = date.time
            "ثوانٍ: ${millis / 1000}\nميلي: $millis"
        }
            .getOrElse { "✘ صيغة غير مفهومة (yyyy-MM-dd HH:mm:ss)" }

    private fun String.cidrInfo(): String {
        val text = trim()
        val slash = text.indexOf('/')
        if (slash <= 0) return "اكتب العنوان هكذا: 192.168.1.0/24"
        val ip = text.substring(0, slash)
        val prefix = text.substring(slash + 1).toIntOrNull() ?: return "✘ طول البادئة غير صالح"
        if (prefix !in 0..32) return "✘ طول البادئة يجب أن يكون 0-32"
        val octets = ip.split('.').map { it.toIntOrNull() ?: return "✘ عنوان IPv4 غير صالح" }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return "✘ عنوان IPv4 غير صالح"
        val value = octets.fold(0L) { acc, part -> (acc shl 8) or part.toLong() }
        val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val network = value and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
        val hosts = if (prefix >= 31) 0L else (1L shl (32 - prefix)) - 2
        fun render(v: Long) = (0..3).joinToString(".") { shift -> ((v shr (24 - shift * 8)) and 0xFF).toString() }
        return """
            الشبكة: ${render(network)}/$prefix
            القناع: ${render(mask)}
            البثّ: ${render(broadcast)}
            عدد الأجهزة القابلة للاستخدام: $hosts
            النطاق: ${render(network)} — ${render(broadcast)}
        """.trimIndent()
    }

    private fun String.chmodInfo(): String {
        val text = trim()
        if (Regex("^[0-7]{3,4}$").matches(text)) {
            val digits = text.takeLast(3)
            val flags = "rwx"
            val rendered =
                digits.map { d ->
                    val n = d.digitToInt()
                    buildString {
                        append(if (n and 4 != 0) 'r' else '-')
                        append(if (n and 2 != 0) 'w' else '-')
                        append(if (n and 1 != 0) 'x' else '-')
                    }
                }.joinToString("")
            return "$text\n$rendered\n(المالك · المجموعة · الآخرون: $flags)"
        }
        val cleaned = text.takeLast(9)
        if (cleaned.length != 9) return "أدخل 755 أو rwxr-xr-x"
        val value =
            cleaned.chunked(3).joinToString("") { group ->
                var n = 0
                if (group[0] == 'r') n += 4
                if (group[1] == 'w') n += 2
                if (group[2] == 'x') n += 1
                n.toString()
            }
        return "$cleaned\n$value"
    }

    private fun String.wordFrequency(): String =
        trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.trim('.', ',', ':', ';', '؟', '!', '،', '.') }
            .filter { it.isNotBlank() }
            .groupingBy { it.lowercase(Locale.ROOT) }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(25)
            .joinToString("\n") { "${it.value}× ${it.key}" }

    private fun String.charFrequency(): String =
        filterNot { it.isWhitespace() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(25)
            .joinToString("\n") { "${it.value}× ${it.key}" }

    private fun String.passwordStrength(): String {
        if (isEmpty()) return "أدخل كلمة مرور"
        var score = 0
        if (length >= 8) score += 25
        if (length >= 14) score += 20
        if (any { it.isLowerCase() } && any { it.isUpperCase() }) score += 20
        if (any { it.isDigit() }) score += 15
        if (any { !it.isLetterOrDigit() }) score += 20
        val label =
            when {
                score >= 85 -> "قوية جدًا"
                score >= 65 -> "قوية"
                score >= 45 -> "متوسطة"
                score >= 25 -> "ضعيفة"
                else -> "ضعيفة جدًا"
            }
        return "النتيجة: $score/100 — $label\nالطول: $length"
    }

    private fun String.slugify(): String =
        trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
            .trim('-')

    private fun action(id: String, ar: String, en: String, runBlock: (String) -> String) =
        ToolAction(id = id, labelAr = ar, labelEn = en, run = { input -> runCatching { runBlock(input) }.getOrElse { e -> "✘ ${e.message ?: "خطأ غير معروف"}" } })

    private fun String.toTitleCase(): String =
        split(" ").joinToString(" ") { word ->
            if (word.isEmpty()) word else word[0].uppercase(Locale.ROOT) + word.drop(1).lowercase(Locale.ROOT)
        }

    private fun String.toSentenceCase(): String {
        val trimmed = trim()
        if (trimmed.isEmpty()) return trimmed
        return trimmed[0].uppercase(Locale.ROOT) + trimmed.drop(1)
    }

    private fun String.describeText(): String {
        val lines = if (isEmpty()) 0 else lines().size
        val words = trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val chars = length
        val bytes = toByteArray().size
        val arabic = count { it in '\u0600'..'\u06FF' }
        return """
            الأسطر: $lines
            الكلمات: $words
            الحروف: $chars
            البايتات (UTF-8): $bytes
            حروف عربية: $arabic
        """.trimIndent()
    }

    private fun String.rot13(): String =
        map { c ->
            when (c) {
                in 'a'..'z' -> 'a' + ((c - 'a' + 13) % 26)
                in 'A'..'Z' -> 'A' + ((c - 'A' + 13) % 26)
                else -> c
            }
        }.joinToString("")

    private fun String.jsonEscape(): String =
        buildString {
            this@jsonEscape.forEach { c ->
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (c < ' ') append("\\u%04x".format(Locale.ROOT, c.code)) else append(c)
                }
            }
        }

    private fun String.jsonUnescape(): String =
        replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")

    private fun String.digest(algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        return md.digest(toByteArray()).joinToString("") { b -> "%02x".format(Locale.ROOT, b) }
    }

    private fun JsonElement.sortKeysRecursively(): JsonElement =
        when {
            isJsonObject -> {
                val sorted = JsonObject()
                asJsonObject.entrySet().sortedBy { it.key }.forEach { (key, value) ->
                    sorted.add(key, value.sortKeysRecursively())
                }
                sorted
            }
            isJsonArray -> {
                val array = JsonArray()
                asJsonArray.forEach { item -> array.add(item.sortKeysRecursively()) }
                array
            }
            else -> this
        }

    private fun String.stripArabicMarks(): String = replace(Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")

    private fun String.normalizeArabic(): String =
        replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')

    private fun String.arabicDigitsToAscii(): String =
        map { c ->
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                else -> c
            }
        }.joinToString("")

    private fun String.asciiDigitsToArabic(): String =
        map { c -> if (c in '0'..'9') '٠' + (c - '0') else c }.joinToString("")

    private fun String.wrap(width: Int): String =
        split("\n").joinToString("\n") { paragraph ->
            if (paragraph.length <= width) {
                paragraph
            } else {
                val out = StringBuilder()
                var lineLen = 0
                paragraph.split(" ").forEach { word ->
                    if (lineLen > 0 && lineLen + word.length + 1 > width) {
                        out.append('\n')
                        lineLen = 0
                    } else if (lineLen > 0) {
                        out.append(' ')
                        lineLen++
                    }
                    out.append(word)
                    lineLen += word.length
                }
                out.toString()
            }
        }

    private fun randomPassword(length: Int): String {
        val alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#%&*+-=?"
        val random = java.security.SecureRandom()
        return (1..length).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private fun randomToken(bytes: Int): String {
        val random = java.security.SecureRandom()
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return buffer.joinToString("") { b -> "%02x".format(Locale.ROOT, b) }
    }

    private fun String.toWords(): List<String> =
        replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace(Regex("[_\\-.]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.lowercase(Locale.ROOT) }

    private fun List<String>.toCamel(): String =
        if (isEmpty()) {
            ""
        } else {
            first() + drop(1).joinToString("") { w -> w.replaceFirstChar { c -> c.uppercase(Locale.ROOT) } }
        }

    private fun List<String>.toPascal(): String =
        joinToString("") { w -> w.replaceFirstChar { c -> c.uppercase(Locale.ROOT) } }

    private fun String.hexToRgb(): String {
        val hex = trim().removePrefix("#")
        require(hex.length == 6 || hex.length == 3) { "قيمة HEX غير صالحة" }
        val full = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
        val r = full.substring(0, 2).toInt(16)
        val g = full.substring(2, 4).toInt(16)
        val b = full.substring(4, 6).toInt(16)
        return "rgb($r, $g, $b)"
    }

    private fun String.rgbToHex(): String {
        val numbers = Regex("\\d{1,3}").findAll(this).map { it.value.toInt() }.take(3).toList()
        require(numbers.size == 3) { "أدخل ثلاث قيم RGB" }
        return "#" + numbers.joinToString("") { n -> "%02X".format(Locale.ROOT, n.coerceIn(0, 255)) }
    }

    private fun String.splitOnSeparator(): Pair<String, String> {
        val marker = Regex("^---+\\s*$", RegexOption.MULTILINE).find(this) ?: return this to ""
        return substring(0, marker.range.first).trimEnd() to substring(marker.range.last + 1).trimStart()
    }

    private fun String.simpleDiff(): String {
        val (a, b) = splitOnSeparator()
        if (b.isBlank()) return "ضع النسختين مفصولتين بسطر ---"
        val aLines = a.lines()
        val bLines = b.lines()
        val out = StringBuilder()
        val max = maxOf(aLines.size, bLines.size)
        var added = 0
        var removed = 0
        for (i in 0 until max) {
            val left = aLines.getOrNull(i)
            val right = bLines.getOrNull(i)
            when {
                left == right -> out.append("  ").append(left ?: "").append('\n')
                left == null -> {
                    out.append("+ ").append(right ?: "").append('\n'); added++
                }
                right == null -> {
                    out.append("- ").append(left).append('\n'); removed++
                }
                else -> {
                    out.append("- ").append(left).append('\n')
                    out.append("+ ").append(right).append('\n')
                    added++
                    removed++
                }
            }
        }
        out.append('\n').append("— أُضيف: $added · حُذف: $removed")
        return out.toString()
    }

    private fun String.csvToJson(): String {
        val rows = lineSequence().filter { it.isNotBlank() }.toList()
        require(rows.size >= 2) { "أدخل صف العناوين وصفًّا واحدًا على الأقل" }
        val headers = splitCsvRow(rows.first())
        val array = JsonArray()
        rows.drop(1).forEach { row ->
            val values = splitCsvRow(row)
            val obj = JsonObject()
            headers.forEachIndexed { i, header -> obj.addProperty(header, values.getOrElse(i) { "" }) }
            array.add(obj)
        }
        return gson.toJson(array)
    }

    private fun splitCsvRow(row: String): List<String> =
        row.split(",").map { it.trim().trim('"') }
}
