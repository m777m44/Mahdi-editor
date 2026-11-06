package com.mahdi.theme

import com.mahdi.App.Companion.themeManager
import com.mahdi.settings.Settings

/**
 * تثبيت/إزالة الثيمات من مكان واحد (4.0.0).
 *
 * سابقًا كان التثبيت مكتوبًا داخل شاشة المتجر فقط، فلم تكن شاشة الثيمات قادرة على
 * تثبيت أو حذف شيء (كل ما فيها «تطبيق» و«حذف» لثيمات سبق تثبيتها). الآن كل من:
 * - شاشة **الإعدادات ‹ الثيمات**
 * - قسم الثيمات في **الإعدادات ‹ الثيمات**
 * يمرّان من هنا، فالنتيجة نفسها دائمًا: الثيم يُحفظ في المخزون ويُسجَّل في مدير الثيمات
 * ويظهر فورًا في «ثيمات مثبّتة».
 */
object ThemeInstaller {

    /** هل الثيم مثبَّت في المخزون (مخصّص أو من المتجر)؟ */
    fun isInstalled(id: String): Boolean = runCatching { CustomThemes.themes.any { it.id == id } }.getOrDefault(false)

    /** هل الثيم من ثيمات المتجر المميّزة؟ */
    fun isStoreTheme(id: String): Boolean = StoreThemeCatalog.themes.any { it.id == id }

    /**
     * يثبّت ثيم متجر (لوحة فاتحة + لوحة داكنة) ويعيد تعريفه، أو `null` عند الفشل.
     * لا يُطبَّق تلقائيًا — التطبيق قرار منفصل حتى يبقى زرّ «تثبيت» بمعناه الحرفي.
     */
    fun install(theme: FeaturedTheme, apply: Boolean = false): ThemeHolder? =
        runCatching {
                val holder =
                    CustomThemes.savePair(
                        id = theme.id,
                        name = "${theme.nameAr} · ${theme.nameEn}",
                        light = theme.light,
                        dark = theme.dark,
                    )
                themeManager.addCustomTheme(holder)
                if (apply) themeManager.setTheme(theme.id, holder)
                holder
            }
            .getOrNull()

    /**
     * يحذف ثيمًا مثبَّتًا بالكامل: من المخزون على القرص، ومن قائمة المدير،
     * وإن كان هو الثيم النشط يعيد التطبيق إلى الثيم الأساسي «السمائي».
     */
    fun remove(id: String) {
        // الثيمات الأساسية لا تُحذف — المخصّصة وثيمات المتجر فقط
        if (!CustomThemes.isCustom(id)) return
        runCatching { CustomThemes.delete(id) }
        runCatching { themeManager.removeCustomTheme(id) }
        if (Settings.theme == id) {
            runCatching { themeManager.setTheme(DEFAULT_THEME_ID) }
        }
    }
}
