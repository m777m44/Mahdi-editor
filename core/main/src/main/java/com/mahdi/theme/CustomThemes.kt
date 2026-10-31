package com.mahdi.theme

import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mahdi.settings.Preference
import com.mahdi.utils.logError

/**
 * ثيمات المستخدم المخصّصة التي تُبنى من شاشة «تخصيص الثيم» (Color Wheel).
 *
 * تُحفظ كـ JSON في التفضيلات، وتُضاف إلى قائمة الثيمات في [ThemeManager] كأي ثيم مدمج،
 * فتعمل في الوضع الفاتح والداكن وفي المحرر والطرفية على السواء.
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
                val json = Preference.getString(KEY, "[]")
                val type = object : TypeToken<List<StoredTheme>>() {}.type
                val stored: List<StoredTheme> = gson.fromJson(json, type) ?: emptyList()
                _themes.clear()
                _themes.addAll(stored.map { it.toSpec().toThemeHolder() })
            }
            .onFailure { logError(it, "CustomThemes.load") }
    }

    /** شكل التخزين على القرص. */
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
    ) {
        fun toSpec(): ThemeSpec =
            ThemeSpec(
                id = id,
                name = name,
                style = runCatching { ThemeStyle.valueOf(style) }.getOrDefault(ThemeStyle.SOLID),
                colors =
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
                    ),
                dark = dark,
            )
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

    private fun persist(specs: List<ThemeSpec>) {
        runCatching { Preference.setString(KEY, gson.toJson(specs.map { it.toStored() })) }
            .onFailure { logError(it, "CustomThemes.persist") }
    }

    private fun currentSpecs(): List<ThemeSpec> =
        runCatching {
                val json = Preference.getString(KEY, "[]")
                val type = object : TypeToken<List<StoredTheme>>() {}.type
                gson.fromJson<List<StoredTheme>>(json, type).orEmpty().map { it.toSpec() }
            }
            .getOrDefault(emptyList())

    /** يحفظ ثيمًا (أو يحدّث ثيمًا بنفس المعرّف) ويعيد النسخة الجاهزة. */
    fun save(spec: ThemeSpec): ThemeHolder {
        ensureLoaded()
        val specs = currentSpecs().filterNot { it.id == spec.id } + spec
        persist(specs)
        val holder = spec.toThemeHolder()
        val index = _themes.indexOfFirst { it.id == spec.id }
        if (index >= 0) _themes[index] = holder else _themes.add(holder)
        return holder
    }

    /** يحذف ثيمًا مخصّصًا. */
    fun delete(id: String) {
        ensureLoaded()
        persist(currentSpecs().filterNot { it.id == id })
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
