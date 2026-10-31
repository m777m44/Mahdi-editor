package com.mahdi.theme

/**
 * «الثيمات المميزة» — نسخ ألوان مستوحاة من الثيمات الشهيرة في محرّرات الشيفرة والمواقع
 * (GitHub، Dracula، Nord، Tokyo Night، Catppuccin…).
 *
 * كل ثيم يُبنى من نفس ألوان 11 خانة التي يبنيها محرّك ثيمات Mahdi-Editor، فيعمل في الواجهة
 * والمحرّر والطرفية على السواء، ويُحفظ في «المخزون» كأي ثيم مخصّص للمستخدم.
 */
data class StoreTheme(
    val slug: String,
    val nameAr: String,
    val nameEn: String,
    val source: String,
    val spec: ThemeSpec,
) {
    /** معرّف الثيم داخل التطبيق (قابل للحذف كأي ثيم مخصّص). */
    val id: String
        get() = "custom-store-$slug"
}

private fun storeTheme(
    slug: String,
    nameAr: String,
    nameEn: String,
    source: String,
    background: Long,
    surface: Long,
    primary: Long,
    secondary: Long,
    accent: Long,
    text: Long? = null,
    dark: Boolean = true,
): StoreTheme =
    StoreTheme(
        slug = slug,
        nameAr = nameAr,
        nameEn = nameEn,
        source = source,
        spec =
            spec(
                id = "custom-store-$slug",
                name = "$nameAr · $nameEn",
                style = ThemeStyle.SOLID,
                background = background,
                surface = surface,
                primary = primary,
                secondary = secondary,
                accent = accent,
                text = text,
                dark = dark,
            ),
    )

/** كتالوج الثيمات المميّزة (16 ثيمًا). */
object StoreThemeCatalog {

    val themes: List<StoreTheme> =
        listOf(
            storeTheme(
                "github-dark",
                "جيت هب الداكن",
                "GitHub Dark",
                "GitHub",
                0xFF0D1117,
                0xFF161B22,
                0xFF58A6FF,
                0xFF1F6FEB,
                0xFF3FB950,
            ),
            storeTheme(
                "github-light",
                "جيت هب الفاتح",
                "GitHub Light",
                "GitHub",
                0xFFFFFFFF,
                0xFFF6F8FA,
                0xFF0969DA,
                0xFF0550AE,
                0xFF1A7F37,
                dark = false,
            ),
            storeTheme(
                "dracula",
                "دراكولا",
                "Dracula",
                "Dracula",
                0xFF282A36,
                0xFF44475A,
                0xFFBD93F9,
                0xFFFF79C6,
                0xFF50FA7B,
            ),
            storeTheme(
                "nord",
                "نورد",
                "Nord",
                "Nord",
                0xFF2E3440,
                0xFF3B4252,
                0xFF88C0D0,
                0xFF81A1C1,
                0xFFA3BE8C,
            ),
            storeTheme(
                "one-dark",
                "ون دارك",
                "One Dark Pro",
                "Atom",
                0xFF282C34,
                0xFF31353F,
                0xFF61AFEF,
                0xFFC678DD,
                0xFF98C379,
            ),
            storeTheme(
                "solarized-dark",
                "سولارايزد الداكن",
                "Solarized Dark",
                "Solarized",
                0xFF002B36,
                0xFF073642,
                0xFF268BD2,
                0xFF2AA198,
                0xFFB58900,
            ),
            storeTheme(
                "solarized-light",
                "سولارايزد الفاتح",
                "Solarized Light",
                "Solarized",
                0xFFFDF6E3,
                0xFFEEE8D5,
                0xFF268BD2,
                0xFF2AA198,
                0xFFCB4B16,
                dark = false,
            ),
            storeTheme(
                "monokai-pro",
                "مونوكاي برو",
                "Monokai Pro",
                "Monokai",
                0xFF2D2A2E,
                0xFF403E41,
                0xFFFFD866,
                0xFFFF6188,
                0xFFA9DC76,
            ),
            storeTheme(
                "tokyo-night",
                "طوكيو نايت",
                "Tokyo Night",
                "Tokyo Night",
                0xFF1A1B26,
                0xFF24283B,
                0xFF7AA2F7,
                0xFFBB9AF7,
                0xFF9ECE6A,
            ),
            storeTheme(
                "catppuccin-mocha",
                "كاتبوشين موكا",
                "Catppuccin Mocha",
                "Catppuccin",
                0xFF1E1E2E,
                0xFF313244,
                0xFFCBA6F7,
                0xFF89B4FA,
                0xFFA6E3A1,
            ),
            storeTheme(
                "gruvbox-dark",
                "غروف بوكس",
                "Gruvbox Dark",
                "Gruvbox",
                0xFF282828,
                0xFF3C3836,
                0xFFFABD2F,
                0xFF83A598,
                0xFFB8BB26,
            ),
            storeTheme(
                "ayu-mirage",
                "آيو ميراج",
                "Ayu Mirage",
                "Ayu",
                0xFF1F2430,
                0xFF242936,
                0xFFFFCC66,
                0xFF73D0FF,
                0xFFBAE67E,
            ),
            storeTheme(
                "material-palenight",
                "ماتيريال بالي نايت",
                "Material Palenight",
                "Material",
                0xFF292D3E,
                0xFF34384A,
                0xFF82AAFF,
                0xFFC792EA,
                0xFFC3E88D,
            ),
            storeTheme(
                "night-owl",
                "نايت آول",
                "Night Owl",
                "Night Owl",
                0xFF011627,
                0xFF0B2942,
                0xFF82AAFF,
                0xFFC792EA,
                0xFFADDB67,
            ),
            storeTheme(
                "cobalt2",
                "كوبالت",
                "Cobalt2",
                "Cobalt2",
                0xFF193549,
                0xFF1F4662,
                0xFFFFC600,
                0xFF2AFFDF,
                0xFF8A9EBB,
            ),
            storeTheme(
                "vs-light",
                "في إس الفاتح",
                "VS Code Light",
                "VS Code",
                0xFFFFFFFF,
                0xFFF3F3F3,
                0xFF007ACC,
                0xFF0098FF,
                0xFF16825D,
                dark = false,
            ),
        )

    fun byId(id: String): StoreTheme? = themes.find { it.id == id }

    fun bySlug(slug: String): StoreTheme? = themes.find { it.slug == slug }

    /** هل هذا الثيم من الثيمات المميّزة؟ */
    fun isStoreTheme(id: String): Boolean = themes.any { it.id == id }
}
