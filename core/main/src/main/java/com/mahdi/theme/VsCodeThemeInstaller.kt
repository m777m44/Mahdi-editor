package com.mahdi.theme

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mahdi.App.Companion.themeManager
import com.mahdi.utils.logError

/**
 * 9.8.0 — تثبيت ثيم VS Code من ملف يختاره المستخدم.
 *
 * يقبل ما يقبله VS Code نفسه: `*.tmTheme` (‏plist) و`*-color-theme.json`
 * و`theme.json` بصيغة `{ colors, tokenColors }`.
 *
 * الخطوات: قراءة الملف ← تحويله بـ[VsCodeThemeImporter] ← حفظ اللوحتين
 * (فاتحة وداكنة مشتقّة) في [CustomThemes] ← تسجيله في [ThemeManager] ← تطبيقه
 * فورًا. الخطوة الأخيرة مقصودة: من يستورد ثيمًا يريد **رؤيته** لا أن يبحث عنه
 * مجدّدًا في القائمة.
 */
object VsCodeThemeInstaller {

    /** أقصى حجم مقبول لملف ثيم — الثيمات الحقيقية كلها أصغر من 200 كيلوبايت. */
    private const val MAX_BYTES = 2L * 1024 * 1024

    sealed interface Result {
        /** نجاح: المعرّف والاسم والثيم المبني. */
        data class Success(val id: String, val name: String, val holder: ThemeHolder) : Result

        /** نتيجة التحليل: ألوان جاهزة، لم تُحفظ بعد — تُثبَّت بـ[commit]. */
        data class Ready(
            val id: String,
            val label: String,
            val light: MahdiColors,
            val dark: MahdiColors,
            val tokens: String,
        ) : Result

        /** سبب الفشل بالعربية، جاهز للعرض كما هو. */
        data class Failure(val reason: String) : Result
    }

    /**
     * يقرأ ملفًا من [uri] ويثبّته.
     *
     * @return نجاح بمعرّف الثيم، أو فشل بسبب عربيّ واضح — لا استثناء ولا
     *   traceback إنجليزي يظهر للمستخدم.
     */
    fun installFromUri(context: Context, uri: Uri): Result {
        val name = displayName(context, uri)
            ?: return Result.Failure("تعذّر فتح الملف المختار")

        val text =
            runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.size > MAX_BYTES) return@use null
                        bytes.toString(Charsets.UTF_8)
                    }
                }
                .getOrNull()
                ?: return Result.Failure("الملف كبير جدًّا أو غير مقروء (الحدّ ٢ ميغابايت)")

        val imported =
            VsCodeThemeImporter.import(text)
                ?: return Result.Failure("هذا ليس ملف ثيم VS Code صالحًا (‏tmTheme أو color-theme.json)")

        val id = "custom-vscode-" + slug(name)
        val label =
            imported.sourceName?.takeIf { it.isNotBlank() }
                ?: name.substringBeforeLast('.').takeIf { it.isNotBlank() }
                ?: "ثيم VS Code"
        val colors = imported.toColors()
        return Result.Ready(id, label, colors.first, colors.second, imported.tokens)
    }

    /**
     * يحفظ الثيم ويُطبّقه — يُستدعى على **الخيط الرئيسي**.
     *
     * السبب: [CustomThemes.savePair] و[com.mahdi.theme.ThemeManager.setTheme]
     * يكتبان حالة Compose (`mutableStateListOf` / `mutableStateOf`)، وكتابتها
     * خارج الخيط الرئيسي خطأ. لهذا فصلنا القراءة والتحليل (‏IO) عن الحفظ
     * والتطبيق (‏Main) بدل أن نفترض خيطًا داخل الدالة.
     */
    fun commit(context: Context, ready: Result.Ready): Result {
        val holder =
            runCatching {
                    // الرموز تُحفظ **مع الثيم** لا في ملف منفصل: لو بقيت في ملف
                    // خارج `CustomThemes` لما قرأها أحد بعد إعادة التشغيل، فيفقد
                    // المستورد تلوين الكود ويبقى له الاسم والألوان فقط.
                    CustomThemes.savePair(
                        id = ready.id,
                        name = ready.label,
                        light = ready.light,
                        dark = ready.dark,
                        tokenColors = ready.tokens,
                    )
                }
                .onFailure { logError(it, "VS Code theme save failed") }
                .getOrElse { return Result.Failure("تعذّر حفظ الثيم — تحقّق من مساحة التخزين") }

        runCatching {
                themeManager.addCustomTheme(holder)
                themeManager.setTheme(ready.id, holder)
            }
            .onFailure { logError(it, "VS Code theme apply failed") }

        return Result.Success(ready.id, ready.label, holder)
    }

    private fun displayName(context: Context, uri: Uri): String? =
        runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) null
                    else {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    }
                } ?: uri.lastPathSegment
            }
            .getOrNull()

    /** معرّف من اسم الملف: أحرف/أرقام لاتينية فقط، والباقي شرطة. */
    private fun slug(name: String): String {
        val base = name.substringBeforeLast('.').lowercase()
        val cleaned = base.map { if (it.isLetterOrDigit() && it.code < 128) it else '-' }.joinToString("")
        return cleaned.trim('-').replace(Regex("-+"), "-").take(40).ifEmpty { "theme" }
    }

    // ————————————————————— التحويل إلى لوحتين —————————————————————

    /** لوحتان (فاتحة/داكنة) من ثيم مستورد: بطبيعته وبنسخة مشتقّة للوضع الآخر. */
    private fun VsCodeThemeImporter.Imported.toColors(): Pair<MahdiColors, MahdiColors> {
        val light = toLightColors()
        val dark = toDarkColors()
        return light to dark
    }

    private fun VsCodeThemeImporter.Imported.toDarkColors(): MahdiColors {
        val base =
            MahdiColors(
                background = background,
                surface = surface,
                primary = primary,
                secondary = secondary,
                accent = accent,
                text = text,
                textSecondary = if (dark) mixColor(text, background, 0.38f) else 0xFFA6AEBB,
                outline = outline,
                success = 0xFF10B981L,
                error = 0xFFEF4444L,
                warning = 0xFFF59E0BL,
            )
        return if (dark) base else VsCodeThemes.darkTwin(base)
    }

    private fun VsCodeThemeImporter.Imported.toLightColors(): MahdiColors {
        val base =
            MahdiColors(
                background = background,
                surface = surface,
                primary = primary,
                secondary = secondary,
                accent = accent,
                text = text,
                textSecondary = if (!dark) mixColor(text, background, 0.45f) else 0xFFA6AEBB,
                outline = outline,
                success = 0xFF059669L,
                error = 0xFFDC2626L,
                warning = 0xFFD97706L,
            )
        return if (!dark) base else VsCodeThemes.lightTwin(base)
    }

    private fun mixColor(a: Long, b: Long, ratio: Float): Long {
        fun ch(v: Long, s: Int) = (v shr s) and 0xFF
        fun m(x: Long, y: Long) = (x + (y - x) * ratio).toInt().coerceIn(0, 255)
        return (0xFF000000L or
            (m(ch(a, 16), ch(b, 16)).toLong() shl 16) or
            (m(ch(a, 8), ch(b, 8)).toLong() shl 8) or
            m(ch(a, 0), ch(b, 0)).toLong())
    }
}
