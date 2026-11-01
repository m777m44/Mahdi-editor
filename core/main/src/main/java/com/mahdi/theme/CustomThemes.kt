package com.mahdi.theme

import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mahdi.settings.Preference
import com.mahdi.utils.logError

/**
 * ثيمات المستخدم المخصّصة والثيمات المثبَّتة من المتجر.
 *
 * كل ثيم يُحفظ كـ JSON في التفضيلات، ويمكن أن يحمل:
 * - **لوحة واحدة** (11 لونًا) تُشتق منها النسختان الفاتحة والداكنة تلقائيًا، أو
 * - **لوحتين منفصلتين** (فاتحة وداكنة) كما في حزم الثيمات القياسية `theme.json`.
 *
 * تُضاف كلها إلى قائمة الثيمات في [ThemeManager] كأي ثيم مدمج، فتعمل في الواجهة
 * والمحرّر والطرفية على السواء.
 */
object CustomThemes {
    private const val KEY = "custom_theme_specs"
    private const val ID_PREFIX = "custom-"

    private val gson = Gson()

    private val _themes = mutableStateListOf<ThemeHolder>()
    private var loaded = false

    /** الثيمات المخصّصة جاهزة للعرض. */
    val themes: List<ThemeHolder>
        get() {
            ensureLoaded()
            return _themes
        }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
                _themes.clear()
                _themes.addAll(readStored().map { it.toHolder() })
            }
            .onFailure { logError(it, "CustomThemes.load") }
    }

    /** شكل التخزين على القرص (يتحمّل وجود اللوحة الفاتحة من عدمه). */
    private data class StoredTheme(
        val id: String,
        val name: String,
        val style: String,
        val dark: Boolean,
        val background: Long,
        val surface: Long,
        val primary: Long,
        val secondary: Long,
        val accent: Long,
        val text: Long,
        val textSecondary: Long,
        val outline: Long,
        val success: Long,
        val error: Long,
        val warning: Long,
        // لوحة الوضع الفاتح — اختيارية (الثيمات أحادية اللوحة لا تحملها)
        val lightBackground: Long? = null,
        val lightSurface: Long? = null,
        val lightPrimary: Long? = null,
        val lightSecondary: Long? = null,
        val lightAccent: Long? = null,
        val lightText: Long? = null,
        val lightTextSecondary: Long? = null,
        val lightOutline: Long? = null,
    ) {
        fun darkColors(): MahdiColors =
            MahdiColors(
                background = background,
                surface = surface,
                primary = primary,
                secondary = secondary,
                accent = accent,
                text = text,
                textSecondary = textSecondary,
                outline = outline,
                success = success,
                error = error,
                warning = warning,
            )

        /** لوحة الوضع الفاتح إن حُفظت صراحةً. */
        fun lightColorsOrNull(): MahdiColors? {
            val bg = lightBackground ?: return null
            return MahdiColors(
                background = bg,
                surface = lightSurface ?: bg,
                primary = lightPrimary ?: primary,
                secondary = lightSecondary ?: secondary,
                accent = lightAccent ?: accent,
                text = lightText ?: text,
                textSecondary = lightTextSecondary ?: textSecondary,
                outline = lightOutline ?: outline,
                success = success,
                error = error,
                warning = warning,
            )
        }

        fun toSpec(): ThemeSpec =
            ThemeSpec(
                id = id,
                name = name,
                style = runCatching { ThemeStyle.valueOf(style) }.getOrDefault(ThemeStyle.SOLID),
                colors = darkColors(),
                dark = dark,
            )

        /** يبني الثيم كاملًا: بلوحتين إن وُجدت الفاتحة، وإلا باشتقاقها. */
        fun toHolder(): ThemeHolder {
            val light = lightColorsOrNull()
            return if (light != null) {
                themeHolderFrom(id = id, name = name, light = light, dark = darkColors())
            } else {
                toSpec().toThemeHolder()
            }
        }
    }

    private fun ThemeSpec.toStored(): StoredTheme =
        StoredTheme(
            id = id,
            name = name,
            style = style.name,
            dark = dark,
            background = colors.background,
            surface = colors.surface,
            primary = colors.primary,
            secondary = colors.secondary,
            accent = colors.accent,
            text = colors.text,
            textSecondary = colors.textSecondary,
            outline = colors.outline,
            success = colors.success,
            error = colors.error,
            warning = colors.warning,
        )

    private fun readStored(): List<StoredTheme> =
        runCatching {
                val json = Preference.getString(KEY, "[]")
                val type = object : TypeToken<List<StoredTheme>>() {}.type
                gson.fromJson<List<StoredTheme>>(json, type).orEmpty()
            }
            .getOrDefault(emptyList())

    private fun persist(stored: List<StoredTheme>) {
        runCatching { Preference.setString(KEY, gson.toJson(stored)) }
            .onFailure { logError(it, "CustomThemes.persist") }
    }

    /** يحفظ ثيمًا بلوحة واحدة (أو يحدّث ثيمًا بنفس المعرّف) ويعيد النسخة الجاهزة. */
    fun save(spec: ThemeSpec): ThemeHolder {
        ensureLoaded()
        val stored = readStored().filterNot { it.id == spec.id } + spec.toStored()
        persist(stored)
        val holder = spec.toThemeHolder()
        val index = _themes.indexOfFirst { it.id == spec.id }
        if (index >= 0) _themes[index] = holder else _themes.add(holder)
        return holder
    }

    /**
     * يحفظ ثيمًا **بلوحتين منفصلتين** (فاتحة وداكنة) — يُستخدم لثيمات المتجر
     * ولأي ثيم يُستورد من حزمة قياسية تحتوي `light` و`dark`.
     */
    fun savePair(id: String, name: String, light: MahdiColors, dark: MahdiColors): ThemeHolder {
        ensureLoaded()
        val base = ThemeSpec(id = id, name = name, style = ThemeStyle.SOLID, colors = dark, dark = true)
        val stored =
            base.toStored().copy(
                lightBackground = light.background,
                lightSurface = light.surface,
                lightPrimary = light.primary,
                lightSecondary = light.secondary,
                lightAccent = light.accent,
                lightText = light.text,
                lightTextSecondary = light.textSecondary,
                lightOutline = light.outline,
            )
        persist(readStored().filterNot { it.id == id } + stored)

        val holder = themeHolderFrom(id = id, name = name, light = light, dark = dark)
        val index = _themes.indexOfFirst { it.id == id }
        if (index >= 0) _themes[index] = holder else _themes.add(holder)
        return holder
    }

    /** يحذف ثيمًا مخصّصًا. */
    fun delete(id: String) {
        ensureLoaded()
        persist(readStored().filterNot { it.id == id })
        _themes.removeAll { it.id == id }
    }

    /** يحوّل تعريف ثيم إلى JSON قابل للنسخ أو المشاركة. */
    fun encode(spec: ThemeSpec): String = gson.toJson(spec.toStored())

    /** يقرأ تعريف ثيم من JSON، ويعيد `null` إن كان غير صالح. */
    fun decode(json: String): ThemeSpec? =
        runCatching { gson.fromJson(json, StoredTheme::class.java)?.toSpec() }.getOrNull()?.takeIf { it.name.isNotBlank() }

    /** هل هذا المعرّف ثيمًا مخصّصًا؟ */
    fun isCustom(id: String): Boolean = id.startsWith(ID_PREFIX)

    /** معرّف جديد لثيم مخصّص. */
    fun newId(): String = ID_PREFIX + System.currentTimeMillis().toString(36)
}
