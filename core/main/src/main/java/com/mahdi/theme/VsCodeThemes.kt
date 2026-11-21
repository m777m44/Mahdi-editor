package com.mahdi.theme

import android.content.Context
import android.content.res.AssetManager
import com.google.gson.JsonArray
import com.google.gson.JsonParser

/**
 * 9.8.0 — ثيمات VS Code المدمجة (Darcula · Abyss · Quiet Light).
 *
 * الأصول في `assets/vscode/themes/` (ملفات `darcula.json` وأخواتها) يُولّدها
 * `tools/vscode-import/import_assets.py` من أصول حزمة `vs-code-heroes`
 * (‏**ألوان ورموز فقط** — لا كود، ولا dex، ولا مفاتيح توقيع).
 *
 * كل ثيم يُبنى مرّة واحدة ويُخزَّن: بناءُ [ThemeHolder] يحسب مخططات Material 3
 * وألوان الطرفية، وهو عمل غير مجاني يتكرّر كل تشغيل. هنا يحدث **مرّة** عند
 * أول طلب، ثم يُعاد نفس الكائن.
 *
 * طريقة الدمج مع الشاشة: الثيمات الثلاثة **إضافات** إلى الأربعة الأصلية
 * (السمائي/الداكن/المظلم/الفاتح) — لا تحلّ محلّها، ولا يمكن حذفها لأنها في
 * الأصول لا في مجلد الثيمات المثبَّتة.
 */
object VsCodeThemes {

    private const val DIR = "vscode/themes/"
    const val DARK_ID = "vscode-darcula"
    const val ABYSS_ID = "vscode-abyss"
    const val LIGHT_ID = "vscode-quiet-light"

    /** ترتيب العرض: الداكنان أولًا ثم الفاتح، بعد الثيمات الأصلية. */
    private val ORDER = listOf(DARK_ID, ABYSS_ID, LIGHT_ID)

    /** معرّفات الثيمات الثلاثة (تُستخدم للتمييز بين «أساسي» و«مثبَّت»). */
    val IDS: Set<String> = ORDER.toSet()

    /** هل هذا المعرّف ثيمًا من هذه المجموعة؟ */
    fun isVsCodeTheme(id: String): Boolean = id in IDS

    private val cache = mutableMapOf<String, ThemeHolder>()

    /** جميع الثيمات المُولَّدة، مرتّبة. يُبنى مرّة واحدة ثم يُخزَّن. */
    @Synchronized
    fun all(context: Context): List<ThemeHolder> {
        if (cache.size == ORDER.size) return ORDER.mapNotNull { cache[it] }
        ORDER.forEach { id ->
            cache.getOrPut(id) { build(context, id) } // keep insertion order
        }
        // ترتيب صريح لأن getOrPut لا يضمنه لو سبق بناء جزئي
        val ordered = ORDER.mapNotNull { cache[it] }
        return ordered.ifEmpty { emptyList() }
    }

    /** الثيم بمعرّفه، أو null إن لم يوجد في الأصول. */
    fun get(context: Context, id: String): ThemeHolder? =
        all(context).firstOrNull { it.id == id }

    private fun build(context: Context, id: String): ThemeHolder {
        val file = when (id) {
            DARK_ID -> "darcula"
            ABYSS_ID -> "abyss"
            LIGHT_ID -> "quiet-light"
            else -> id
        }
        val text =
            runCatching { context.assets.open(DIR + file + ".json").use { it.readBytes().toString(Charsets.UTF_8) } }
                .getOrNull() ?: return emptyFallback(id)

        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return emptyFallback(id)

        fun color(key: String, fallback: Long): Long {
            val node = root.get(key) ?: return fallback
            if (node.isJsonNull) return fallback
            return hexToLong(node.asString) ?: fallback
        }

        val dark = root.get("dark")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
        val colors =
            MahdiColors(
                background = color("background", 0xFF101418),
                surface = color("surface", 0xFF18202A),
                primary = color("primary", 0xFF4EA1FF),
                secondary = color("secondary", 0xFF7DD3FC),
                accent = color("accent", 0xFF4EA1FF),
                text = color("text", 0xFFFFFFFF),
                textSecondary = computeTextSecondary(color("text", 0xFFFFFFFF), color("background", 0xFF101418), dark),
                outline = color("outline", 0xFF33404F),
                success = 0xFF10B981L,
                error = 0xFFEF4444L,
                warning = 0xFFF59E0BL,
            )

        val tokens = root.get("tokenColors")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        val label =
            root.get("label")?.takeIf { !it.isJsonNull }?.asString
                ?: root.get("name")?.takeIf { !it.isJsonNull }?.asString
                ?: id

        // الثيم الواحد يعمل بوجهين: بطبيعته وبنسخة مشتقّة للوضع الآخر، حتى لا
        // يشعر المستخدم بأن الوضع الليلي «لا يعمل» عند اختيار ثيم فاتح.
        val light = if (dark) lightTwin(colors) else colors
        val darkColors = if (dark) colors else darkTwin(colors)
        // `toLightScheme`/`toDarkScheme` خاصّتان بالملف الذي يعرّفهما؛ الغلافان
        // العامّان هما `asLightColorScheme`/`asDarkColorScheme` (وهما نفس الدالة).
        val lightScheme = light.asLightColorScheme()
        val darkScheme = darkColors.asDarkColorScheme()

        return ThemeHolder(
            id = id,
            name = label,
            inheritBase = false,
            lightScheme = lightScheme,
            darkScheme = darkScheme,
            lightTerminalColors = light.terminalLightPalette(),
            darkTerminalColors = darkColors.terminalDarkPalette(),
            lightEditorColors = emptyList(),
            darkEditorColors = emptyList(),
            // الرموز النحوية للثيم تُطبَّق في وضعه الطبيعي فقط، والوضع المشتقّ
            // يرث تلوين الثيمات الأصلية حتى يبقى الكود مقروءًا.
            lightTokenColors = if (!dark) tokens else JsonArray(),
            darkTokenColors = if (dark) tokens else JsonArray(),
            isLightSupported = true,
            isDarkSupported = true,
        )
    }

    /** نسخة فاتحة مشتقّة من ثيم داكن (تُستخدم لوضع النهار). */
    internal fun lightTwin(c: MahdiColors): MahdiColors =
        c.copy(
            background = 0xFFF7F8FA,
            surface = 0xFFFFFFFF,
            primary = mix(c.primary, 0xFF101418.toInt(), 0.25f),
            secondary = mix(c.secondary, 0xFF101418.toInt(), 0.25f),
            accent = mix(c.accent, 0xFF101418.toInt(), 0.25f),
            text = 0xFF171A1F,
            textSecondary = 0xFF5A6270,
            outline = 0xFFD5DAE1,
        )

    /** نسخة داكنة مشتقّة من ثيم فاتح (تُستخدم لوضع الليل). */
    internal fun darkTwin(c: MahdiColors): MahdiColors =
        c.copy(
            background = 0xFF101318,
            surface = 0xFF171B21,
            primary = mix(c.primary, 0xFFFFFFFF.toInt(), 0.35f),
            secondary = mix(c.secondary, 0xFFFFFFFF.toInt(), 0.25f),
            accent = mix(c.accent, 0xFFFFFFFF.toInt(), 0.35f),
            text = 0xFFF0F2F5,
            textSecondary = 0xFFA6AEBB,
            outline = 0xFF2C333D,
        )

    private fun computeTextSecondary(text: Long, background: Long, dark: Boolean): Long =
        mix(text, background, if (dark) 0.38f else 0.45f)

    private fun mix(a: Long, b: Int, ratio: Float): Long {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        fun m(x: Long, y: Long) = (x + (y - x) * ratio).toInt().coerceIn(0, 255)
        return (0xFF000000L or
            (m(ar, br.toLong()) shl 16).toLong() or
            (m(ag, bg.toLong()) shl 8).toLong() or
            m(ab, bb.toLong()).toLong())
    }

    private fun hexToLong(raw: String): Long? {
        val v = raw.trim().removePrefix("#")
        val full =
            when (v.length) {
                6 -> "FF$v"
                8 -> v
                else -> return null
            }
        return full.toLongOrNull(16)
    }

    /**
     * ثيم بديل آمن إن تلفت الأصول: لا نكسر الإقلاع بسبب ثيم.
     *
     * نستخدم [MahdiSky] مباشرةً لا [defaultTheme] تفاديًا لأي اعتماد دائري في
     * التهيئة: هذا الكائن يُبنى أثناء تحميل `ThemeManager` نفسه.
     */
    private fun emptyFallback(id: String): ThemeHolder = MahdiSky.toThemeHolder().copy(id = id)

    /** ثابت للاختبارات: أسماء الملفات المتوقّعة في الأصول. */
    val ASSET_FILES = listOf("darcula.json", "abyss.json", "quiet-light.json")

    /** هل الأصول موجودة وسليمة الشكل؟ يُستخدم في فحص الصحة داخل التطبيق. */
    fun verifyAssets(assets: AssetManager): List<String> {
        val problems = mutableListOf<String>()
        ASSET_FILES.forEach { name ->
            val text = runCatching { assets.open(DIR + name).use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            when {
                text == null -> problems += name
                !text.trimStart().startsWith("{") -> problems += name
                else -> {
                    val ok = runCatching { JsonParser.parseString(text).asJsonObject.has("background") }.getOrDefault(false)
                    if (!ok) problems += name
                }
            }
        }
        return problems
    }
}
