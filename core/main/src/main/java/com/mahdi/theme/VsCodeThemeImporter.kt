package com.mahdi.theme

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull

/**
 * 9.8.0 — استيراد ثيمات VS Code (‏`.tmTheme` و`*-color-theme.json`) إلى Mahdi-Editor.
 *
 * لماذا هنا لا في طبقة الواجهة؟ لأن التحويل **نقي**: نصّ الملف ← خانات ألوان
 * إحدى عشرة + قائمة رموز TextMate. لا أندرويد ولا I/O، فيُختبر في `src/test`
 * بملفات الثيمات الحقيقية نفسها.
 *
 * الملفات المدعومة (نفس ما يفهمه VS Code):
 * - **JSON**: `{ "colors": { "editor.background": … }, "tokenColors": [...] }`
 * - **tmTheme**: ‏plist بصيغة Apple — يُقرأ بـ[plistDicts] (قارئ صغير مستقل،
 *   فلم نُضِف تبعية جديدة لأجل ستّة مفاتيح).
 *
 * ما نستخرجه:
 * 1. **ألوان الواجهة** من `colors` (`editor.background` · `editor.foreground` ·
 *    `activityBar.background` · `button.background` …) مع اشتقاق الباقي.
 * 2. **قواعد التلوين النحوي** من `tokenColors` كما هي — تُمرَّر إلى محرّك
 *    TextMate في soraX **بلا تحويل**، فيرسم الكود بألوان الثيم الأصلي.
 */
object VsCodeThemeImporter {

    /** أقل عدد مفاتيح `colors` نقبله كثيم واجهة كامل. */
    const val MIN_COLORS = 3

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** نتيجة الاستيراد: خانات الواجهة + الرموز النحوية الخام (JSON). */
    data class Imported(
        val background: Long,
        val surface: Long,
        val primary: Long,
        val secondary: Long,
        val accent: Long,
        val text: Long,
        val outline: Long,
        /** رموز TextMate بصيغة VS Code كما هي (تُمرَّر إلى المحرّك). */
        val tokens: String,
        val dark: Boolean,
        /** اسم الثيم الأصلي كما كتبه صاحبه. */
        val sourceName: String?,
    )

    /**
     * يحوّل نصّ ثيم VS Code إلى خانات Mahdi-Editor.
     *
     * @return null إن كان النصّ ليس ثيمًا مفهومًا (‏JSON تالف أو plist بلا
     *   `settings`) — الرفض الصريح أفضل من ثيم أسود لا يُقرأ.
     */
    fun import(text: String, fallbackDark: Boolean = true): Imported? {
        val trimmed = text.trimStart()
        return when {
            trimmed.startsWith("{") -> importJson(trimmed, fallbackDark)
            trimmed.startsWith("<?xml") || trimmed.startsWith("<plist") || trimmed.contains("<dict>") ->
                importPlist(trimmed, fallbackDark)
            else -> null
        }
    }

    // ————————————————————————————————— JSON (VS Code) —————————————————————————

    private fun importJson(text: String, fallbackDark: Boolean): Imported? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return null
        val obj = root as? kotlinx.serialization.json.JsonObject ?: return null
        val colors = obj["colors"] as? kotlinx.serialization.json.JsonObject
        val tokens = obj["tokenColors"]?.toString().orEmpty()

        // بعض الملفات القديمة تضع الألوان في `settings` فقط (‏tmTheme بصيغة JSON)
        if (colors == null || colors.size < MIN_COLORS) {
            val settings = obj["settings"] as? kotlinx.serialization.json.JsonArray ?: return null
            val first = (settings.firstOrNull() as? kotlinx.serialization.json.JsonObject)?.get("settings")
                as? kotlinx.serialization.json.JsonObject ?: return null
            return fromBackgroundAndForeground(
                background = first.string("background"),
                foreground = first.string("foreground"),
                tokens = tokens.ifEmpty { settings.toString() },
                name = obj.string("name"),
                fallbackDark = fallbackDark,
            )
        }

        val bg = colors.string("editor.background")
            ?: colors.string("background")
        val fg = colors.string("editor.foreground")
            ?: colors.string("foreground")
        if (bg == null) return null

        val accent =
            colors.string("button.background")
                ?: colors.string("activityBar.activeBorder")
                ?: colors.string("focusBorder")
                ?: colors.string("statusBarItem.remoteBackground")
        val primary = accent ?: lerpHex(bg, fg ?: "#FFFFFF", 0.35f)
        val surface =
            colors.string("editorWidget.background")
                ?: colors.string("sideBar.background")
                ?: colors.string("activityBar.background")
                ?: lerpHex(bg, fg ?: "#FFFFFF", 0.06f)
        val secondary =
            colors.string("activityBar.background")
                ?: lerpHex(bg, primary, 0.25f)
        val outline =
            colors.string("editorLineNumber.foreground")
                ?: colors.string("editorWidget.border")
                ?: lerpHex(bg, fg ?: "#FFFFFF", 0.18f)

        return Imported(
            background = hex(bg) ?: return null,
            surface = hex(surface) ?: hex(bg)!!,
            primary = hex(primary) ?: hex(fg) ?: 0xFFFFFFFF,
            secondary = hex(secondary) ?: hex(primary) ?: 0xFF888888,
            accent = hex(accent ?: primary) ?: hex(primary) ?: 0xFFFFFFFF,
            text = hex(fg) ?: if (isDark(bg)) 0xFFFFFFFF else 0xFF111111,
            outline = hex(outline) ?: lerpHex(bg, fg ?: "#FFFFFF", 0.18f).let { hex(it) } ?: 0xFF888888,
            tokens = tokens,
            dark = isDark(bg),
            sourceName = obj.string("name"),
        )
    }

    // ————————————————————————————————— tmTheme (plist) —————————————————————————

    private fun importPlist(text: String, fallbackDark: Boolean): Imported? {
        // نستخدم الشكل المنظّم (scope/settings) لا نصوصًا خامّة: كل ما نُخرجه
        // هنا يجب أن يُمرَّر إلى محرّك TextMate كقواعد صحيحة.
        val rules = plistRules(text)
        val base = rules.firstOrNull { it.scope == null } ?: return null
        val bg = base.foregroundOrBackground("background") ?: return null

        val tokensJson =
            buildString {
                append('[')
                var first = true
                rules.forEach { rule ->
                    if (rule.scope == null) return@forEach
                    if (rule.foreground == null && rule.background == null && rule.fontStyle == null) return@forEach
                    if (!first) append(',')
                    first = false
                    append("{\"scope\":[")
                    val scopes = rule.scope.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    append(scopes.joinToString(",") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" })
                    append("],\"settings\":{")
                    val parts = mutableListOf<String>()
                    rule.foreground?.let { parts += "\"foreground\":\"$it\"" }
                    rule.background?.let { parts += "\"background\":\"$it\"" }
                    rule.fontStyle?.let { parts += "\"fontStyle\":\"$it\"" }
                    append(parts.joinToString(","))
                    append("}}")
                }
                append(']')
            }

        return fromBackgroundAndForeground(
            background = bg,
            foreground = base.foregroundOrBackground("foreground"),
            tokens = tokensJson,
            name = plistString(text, "name"),
            fallbackDark = fallbackDark,
        )
    }

    /** قاعدة واحدة في plist. */
    internal data class PlistRule(
        val scope: String?,
        val foreground: String?,
        val background: String?,
        val fontStyle: String?,
    ) {
        fun foregroundOrBackground(key: String): String? =
            when (key) {
                "foreground" -> foreground
                "background" -> background
                else -> null
            }
    }

    /**
     * يقرأ قواعد plist الترتيبية.
     *
     * البنية: `<dict>` يحوي `scope` و`settings`؛ و`settings` قاموس داخلي فيه
     * `foreground`/`background`/`fontStyle`. القاعدة العامة هي الـdict التي بلا
     * `scope` — أوّل واحدة منها تُعطي ألوان المحرّر.
     */
    internal fun plistRules(text: String): List<PlistRule> {
        // plist متداخل: `<key>settings</key><array><dict>…</dict></array>`،
        // وكل `<dict>` بدوره يحوي `<key>settings</key><dict>…</dict>`.
        // regex لا يعرف التداخل، فنمرّ على الرموز بمكدّس حاويات (قاموس أو
        // مصفوفة). كل حاوية تعرف المفتاح الذي فُتحت تحته، فتُلحق نفسها بقيمة
        // الأب عند الإغلاق — وهذا الفرق بين «القاعدة» و«قاموس ألوانها».
        val rules = mutableListOf<PlistRule>()
        val stack = ArrayDeque<Pair<Any, String?>>()
        var pendingKey: String? = null

        TOKEN.findAll(text).forEach { token ->
            val raw = token.value
            when {
                raw == "<dict>" -> {
                    stack.addLast(HashMap<String, Any?>() to pendingKey)
                    pendingKey = null
                }
                raw == "<array>" -> {
                    stack.addLast(mutableListOf<Any>() to pendingKey)
                    pendingKey = null
                }
                raw == "</dict>" || raw == "</array>" -> {
                    val frame = stack.removeLastOrNull() ?: return@forEach
                    val container = frame.first
                    val parentKey = frame.second
                    if (stack.isEmpty()) return@forEach // جذر plist: ليس قاعدة

                    // إلحاق الحاوية بالأب (تحت المفتاح المؤجّل)
                    val key = parentKey
                    if (key != null) {
                        when (val parent = stack.last().first) {
                            is MutableMap<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                (parent as MutableMap<String, Any?>)[key] = container
                            }
                            is MutableList<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                (parent as MutableList<Any>).add(container)
                            }
                        }
                    } else {
                        val parent = stack.last().first
                        if (parent is MutableList<*>) {
                            @Suppress("UNCHECKED_CAST")
                            (parent as MutableList<Any>).add(container)
                        }
                    }

                    if (container !is Map<*, *> || parentKey != null) return@forEach

                    val scope = container["scope"] as? String
                    val settings = container["settings"] as? Map<*, *>
                    val fg = (settings?.get("foreground") as? String) ?: (container["foreground"] as? String)
                    val bg = (settings?.get("background") as? String) ?: (container["background"] as? String)
                    val fs = (settings?.get("fontStyle") as? String) ?: (container["fontStyle"] as? String)
                    // القاعدة العامة: بلا scope وفيها خلفية/أمامية
                    if (scope == null && fg == null && bg == null) return@forEach
                    rules += PlistRule(scope, fg, bg?.takeIf { it.isNotEmpty() }, fs?.takeIf { it.isNotEmpty() })
                }
                raw.startsWith("<key>") -> pendingKey = raw.removePrefix("<key>").removeSuffix("</key>").trim()
                raw.startsWith("<string>") -> {
                    val key = pendingKey
                    if (key != null) {
                        val top = stack.lastOrNull()?.first
                        if (top is MutableMap<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            (top as MutableMap<String, Any?>)[key] =
                                raw.removePrefix("<string>").removeSuffix("</string>").trim()
                        }
                        pendingKey = null
                    }
                }
            }
        }
        return rules
    }

    private val TOKEN = Regex("""<dict>|</dict>|<array>|</array>|<key>[^<]*</key>|<string>[^<]*</string>""")

    private fun plistString(text: String, key: String): String? =
        Regex("""<key>\s*$key\s*</key>\s*<string>([^<]*)</string>""").find(text)?.groupValues?.get(1)?.trim()

    // ————————————————————————————————— مساعدات —————————————————————————————————

    private fun fromBackgroundAndForeground(
        background: String?,
        foreground: String?,
        tokens: String,
        name: String?,
        fallbackDark: Boolean,
    ): Imported? {
        val bg = background ?: return null
        val bgLong = hex(bg) ?: return null
        val dark = if (background != null) isDark(bg) else fallbackDark
        val fg = foreground?.let { hex(it) }
            ?: if (dark) 0xFFFFFFFF else 0xFF111111
        val primary = lerpHex(bg, foreground ?: (if (dark) "#FFFFFF" else "#000000"), 0.45f).let { hex(it) } ?: fg
        return Imported(
            background = bgLong,
            surface = lerpHex(bg, foreground ?: "#FFFFFF", 0.08f).let { hex(it) } ?: bgLong,
            primary = primary,
            secondary = lerpHex(bg, foreground ?: "#FFFFFF", 0.30f).let { hex(it) } ?: primary,
            accent = primary,
            text = fg,
            outline = lerpHex(bg, foreground ?: "#FFFFFF", 0.22f).let { hex(it) } ?: 0xFF777777,
            tokens = tokens,
            dark = dark,
            sourceName = name,
        )
    }

    /**
     * `#RGB` · `#RGBA` · `#RRGGBB` · `#RRGGBBAA` (‏VS Code) · `#AARRGGBB` (‏tmTheme)
     * ← `0xAARRGGBB`.
     *
     * ثمانية أرقام تعني شيئين مختلفين في العالمين، والتمييز هنا **قاعدة لا
     * تخمين**: VS Code يقرأ `#RRGGBBAA` (الشفافية آخرًا)، وtmTheme يقرأ
     * `#AARRGGBB` (الشفافية أوّلًا). نعتبره VS Code إذا كانت الشفافية **آخرًا**
     * وليست `FF`؛ وإلا فالثيم معتم والترتيب لا يؤثّر في اللون الظاهر.
     *
     * ملاحظة: كان هنا خطأ ينتج **عشرة** أرقام hex في الحالة `#AARRGGBB`
     * المعتادة (‏`FF` + ثمانية أرقام)، فتخرج ألوان عشوائية. الحساب الآن على
     * الأرقام مباشرةً لا على النصّ.
     */
    fun hex(raw: String?): Long? {
        val value = raw?.trim()?.removePrefix("#") ?: return null
        // أطوال مسموحة فقط. `3..8` كانت تقبل ٥ و٧ أرقام، فينفجر `pair(6)`
        // على نصّ من سبعة → استثناء يُسقط الاستيراد بدل رفض اللون.
        if (value.length != 3 && value.length != 4 && value.length != 6 && value.length != 8) return null
        if (value.any { it.digitToIntOrNull(16) == null }) return null

        fun nibble(c: Char): Long = c.digitToInt(16).toLong()
        fun pair(index: Int): Long = (nibble(value[index]) shl 4) or nibble(value[index + 1])
        // تكرار الرقم الواحد: `A` ← `AA` (‏68 من 8) — نفس ما تفعله المتصفّحات.
        fun single(index: Int): Long = nibble(value[index]) * 17L

        return when (value.length) {
            3 -> 0xFF000000L or (single(0) shl 16) or (single(1) shl 8) or single(2)
            4 -> (single(3) shl 24) or (single(0) shl 16) or (single(1) shl 8) or single(2)
            6 -> 0xFF000000L or (pair(0) shl 16) or (pair(2) shl 8) or pair(4)
            else -> {
                // ثمانية أرقام: إمّا RRGGBBAA (‏VS Code) أو AARRGGBB (‏tmTheme).
                // الصيغة نفسها لا تفرّق، فنحتكم إلى القرائن بالترتيب:
                //  1) آخر رقمين `FF` → شفافية معتمة بصيغة VS Code ⇒ RRGGBBAA.
                //  2) أوّل رقمين `FF` → الشفافية أمامًا (‏tmTheme) ⇒ AARRGGBB.
                //  3) غير ذلك غامض ⇒ نُرجّح AARRGGBB لأن مصدر الألوان ذات
                //     الثمانية أرقام هنا هو ملفات tmTheme.
                val trailingAlpha = pair(6)
                val leadingAlpha = pair(0)
                val asVsCode = trailingAlpha == 0xFFL
                if (asVsCode) {
                    (trailingAlpha shl 24) or (pair(0) shl 16) or (pair(2) shl 8) or pair(4)
                } else {
                    (leadingAlpha shl 24) or (pair(2) shl 16) or (pair(4) shl 8) or trailingAlpha
                }
            }
        } and 0xFFFFFFFFL
    }

    /** هل الخلفية داكنة؟ (‏luminance نسبية تقريبية كافية للتصنيف). */
    fun isDark(hexColor: String): Boolean {
        val value = hex(hexColor) ?: return true
        val r = (value shr 16) and 0xFF
        val g = (value shr 8) and 0xFF
        val b = value and 0xFF
        // إضاءة مدركة أبسط من sRGB: كافية لتمييز #1E1E1E من #F5F5F5
        return (0.299 * r + 0.587 * g + 0.114 * b) < 140
    }

    /** يمزج لونين بنسبة [ratio] (0 = الأول، 1 = الثاني). */
    fun lerpHex(a: String, b: String, ratio: Float): String {
        val ca = hex(a) ?: 0xFF000000
        val cb = hex(b) ?: 0xFFFFFFFF
        fun ch(v: Long, shift: Int) = ((v shr shift) and 0xFF).toInt()
        fun mix(x: Int, y: Int) = (x + (y - x) * ratio).toInt().coerceIn(0, 255)
        val r = mix(ch(ca, 16), ch(cb, 16))
        val g = mix(ch(ca, 8), ch(cb, 8))
        val bl = mix(ch(ca, 0), ch(cb, 0))
        return "#%02X%02X%02X".format(r, g, b)
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    /** اسم حزمة الثيمات التي نورّدها مع التطبيق (مرجع للتوثيق والاختبارات). */
    const val BUNDLED_THEME_PACK_ID = "vscode-heroes"
}

/** ملف ثيم واحد جاهز للتثبيت في مكتبة الثيمات. */
@Serializable
data class VsCodeThemeAsset(
    val id: String,
    val name: String,
    val fileName: String,
)
