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
        )

    /** إجمالي الأفعال المتاحة في كل الأدوات. */
    val actionCount: Int get() = all.sumOf { it.actions.size }

    fun byId(id: String): BuiltinTool? = all.firstOrNull { it.id == id }

    // ——————————————————————————————————————————————————————————————
    // أدوات مساعدة
    // ——————————————————————————————————————————————————————————————

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
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
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
        return md.digest(toByteArray()).joinToString("") { b -> "%02x".format(b) }
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
        return buffer.joinToString("") { b -> "%02x".format(b) }
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
        return "#" + numbers.joinToString("") { n -> "%02X".format(n.coerceIn(0, 255)) }
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
