package com.mahdi.theme

/**
 * «الثيمات المميزة» — 40 ثيمًا مزدوج الوضع (فاتح + داكن) داخل متجر Mahdi-Editor.
 *
 * كل ثيم يُبنى من **11 خانة لون** لكل وضع، تمامًا كما تبنيها حزم الثيمات القياسية
 * (`manifest.json` + `theme.json` بـ`light` و`dark`)، فيعمل في الواجهة والمحرّر
 * والطرفية على السواء ويُحفظ في «المخزون» كأي ثيم مخصّص للمستخدم.
 *
 * الألوان مصمّمة ومختارة لهذا المشروع، وقيمة [source] توضّح المدرسة الجمالية
 * المستوحى منها الثيم فقط.
 */
data class FeaturedTheme(
    val slug: String,
    val nameAr: String,
    val nameEn: String,
    val source: String,
    val light: MahdiColors,
    val dark: MahdiColors,
) {
    /** معرّف الثيم داخل التطبيق (قابل للحذف كأي ثيم مخصّص). */
    val id: String
        get() = "custom-store-$slug"

    /** الاسم الظاهر للثيم. */
    fun displayName(isArabic: Boolean): String = if (isArabic) nameAr else nameEn
}

/** ترتيب الألوان في المصفوفة: الخلفية، السطح، الأساسي، الثانوي، التمييز. */
private fun colorsOf(palette: LongArray, isDark: Boolean): MahdiColors =
    spec(
            id = "tmp",
            name = "",
            style = ThemeStyle.SOLID,
            background = palette[0],
            surface = palette[1],
            primary = palette[2],
            secondary = palette[3],
            accent = palette[4],
            dark = isDark,
        )
        .colors

/**
 * تعريف ثيم مزدوج الوضع.
 *
 * @param darkPalette الخلفية، السطح، الأساسي، الثانوي، التمييز — للوضع الداكن
 * @param lightPalette الترتيب نفسه — للوضع الفاتح
 */
private fun featured(
    slug: String,
    nameAr: String,
    nameEn: String,
    source: String,
    darkPalette: LongArray,
    lightPalette: LongArray,
): FeaturedTheme =
    FeaturedTheme(
        slug = slug,
        nameAr = nameAr,
        nameEn = nameEn,
        source = source,
        light = colorsOf(lightPalette, isDark = false),
        dark = colorsOf(darkPalette, isDark = true),
    )

/** كتالوج الثيمات المميّزة. */
object StoreThemeCatalog {

    val themes: List<FeaturedTheme> =
        listOf(
            // ————— 1. مدارس المحرّرات العالمية —————
            featured(
                "github",
                "جيت هب",
                "GitHub",
                "GitHub",
                longArrayOf(0xFF0D1117, 0xFF161B22, 0xFF58A6FF, 0xFF1F6FEB, 0xFF3FB950),
                longArrayOf(0xFFFFFFFF, 0xFFF6F8FA, 0xFF0969DA, 0xFF0550AE, 0xFF1A7F37),
            ),
            featured(
                "dracula",
                "دراكولا",
                "Dracula",
                "Dracula",
                longArrayOf(0xFF282A36, 0xFF343746, 0xFFBD93F9, 0xFFFF79C6, 0xFF50FA7B),
                longArrayOf(0xFFF8F8F2, 0xFFFFFFFF, 0xFF7C4DBE, 0xFFB33F86, 0xFF1E9E52),
            ),
            featured(
                "nord",
                "نورد",
                "Nord",
                "Nord",
                longArrayOf(0xFF2E3440, 0xFF3B4252, 0xFF88C0D0, 0xFF81A1C1, 0xFFA3BE8C),
                longArrayOf(0xFFECEFF4, 0xFFFFFFFF, 0xFF5E81AC, 0xFF4C6A92, 0xFF7C9A63),
            ),
            featured(
                "one-dark",
                "ون دارك",
                "One Dark",
                "Atom",
                longArrayOf(0xFF282C34, 0xFF31353F, 0xFF61AFEF, 0xFFC678DD, 0xFF98C379),
                longArrayOf(0xFFFAFAFA, 0xFFFFFFFF, 0xFF4078F2, 0xFFA626A4, 0xFF50A14F),
            ),
            featured(
                "solarized",
                "سولارايزد",
                "Solarized",
                "Solarized",
                longArrayOf(0xFF002B36, 0xFF073642, 0xFF268BD2, 0xFF2AA198, 0xFFB58900),
                longArrayOf(0xFFFDF6E3, 0xFFFFFBF0, 0xFF1B6E9E, 0xFF1F7A73, 0xFF8A6A00),
            ),
            featured(
                "monokai",
                "مونوكاي",
                "Monokai",
                "Monokai",
                longArrayOf(0xFF2D2A2E, 0xFF3A373B, 0xFFFFD866, 0xFFFF6188, 0xFFA9DC76),
                longArrayOf(0xFFFAF8F5, 0xFFFFFFFF, 0xFFB8860B, 0xFFC0306A, 0xFF4E9A2F),
            ),
            featured(
                "tokyo-night",
                "طوكيو نايت",
                "Tokyo Night",
                "Tokyo Night",
                longArrayOf(0xFF1A1B26, 0xFF24283B, 0xFF7AA2F7, 0xFFBB9AF7, 0xFF9ECE6A),
                longArrayOf(0xFFE6E7ED, 0xFFF7F8FA, 0xFF2E5CB8, 0xFF7A4FBF, 0xFF4F7A2A),
            ),
            featured(
                "catppuccin",
                "كاتبوشين",
                "Catppuccin",
                "Catppuccin",
                longArrayOf(0xFF1E1E2E, 0xFF313244, 0xFFCBA6F7, 0xFF89B4FA, 0xFFA6E3A1),
                longArrayOf(0xFFEFF1F5, 0xFFFFFFFF, 0xFF8839EF, 0xFF1E66F5, 0xFF40A02B),
            ),
            featured(
                "catppuccin-latte",
                "كاتبوشين لاتيه",
                "Catppuccin Latte",
                "Catppuccin",
                longArrayOf(0xFF4C4F69, 0xFF5C5F77, 0xFFD20F39, 0xFFFE640B, 0xFFDF8E1D),
                longArrayOf(0xFFEFF1F5, 0xFFF7F8FA, 0xFF8839EF, 0xFF1E66F5, 0xFF40A02B),
            ),
            featured(
                "gruvbox",
                "غروفبوكس",
                "Gruvbox",
                "Gruvbox",
                longArrayOf(0xFF282828, 0xFF3C3836, 0xFFFABD2F, 0xFF83A598, 0xFFB8BB26),
                longArrayOf(0xFFFBF1C7, 0xFFFFF9E5, 0xFF8A6A00, 0xFF3C6E66, 0xFF6E7A1E),
            ),
            featured(
                "ayu",
                "آيو",
                "Ayu",
                "Ayu",
                longArrayOf(0xFF1F2430, 0xFF242936, 0xFFFFCC66, 0xFF73D0FF, 0xFFBAE67E),
                longArrayOf(0xFFFAFAFA, 0xFFFFFFFF, 0xFFD97E00, 0xFF1F8AC4, 0xFF5E9E1C),
            ),
            featured(
                "material",
                "ماتيريال",
                "Material",
                "Material",
                longArrayOf(0xFF292D3E, 0xFF34384A, 0xFF82AAFF, 0xFFC792EA, 0xFFC3E88D),
                longArrayOf(0xFFF7F7FA, 0xFFFFFFFF, 0xFF4A6FD4, 0xFF8C4FB3, 0xFF5E9E4A),
            ),
            featured(
                "night-owl",
                "نايت آول",
                "Night Owl",
                "Night Owl",
                longArrayOf(0xFF011627, 0xFF0B2942, 0xFF82AAFF, 0xFFC792EA, 0xFFADDB67),
                longArrayOf(0xFFF6F8FA, 0xFFFFFFFF, 0xFF2E5CB8, 0xFF8C4FB3, 0xFF4F8A2A),
            ),
            featured(
                "cobalt",
                "كوبالت",
                "Cobalt",
                "Cobalt",
                longArrayOf(0xFF193549, 0xFF1F4662, 0xFFFFC600, 0xFF2AFFDF, 0xFF8A9EBB),
                longArrayOf(0xFFF0F4F8, 0xFFFFFFFF, 0xFFB07C00, 0xFF00897B, 0xFF4A6076),
            ),
            featured(
                "rose-pine",
                "روز باين",
                "Rosé Pine",
                "Rosé Pine",
                longArrayOf(0xFF191724, 0xFF1F1D2E, 0xFFEBBCBA, 0xFFC4A7E7, 0xFF9CCFD8),
                longArrayOf(0xFFFAF4ED, 0xFFFFFDFB, 0xFFB4637A, 0xFF907AA9, 0xFF56949F),
            ),
            featured(
                "kanagawa",
                "كاناغاوا",
                "Kanagawa",
                "Kanagawa",
                longArrayOf(0xFF1F1F28, 0xFF2A2A37, 0xFF7E9CD8, 0xFF957FB8, 0xFF98BB6C),
                longArrayOf(0xFFF2ECBC, 0xFFF8F3D8, 0xFF4C6A9C, 0xFF6C5B96, 0xFF6A8437),
            ),
            featured(
                "everforest",
                "إيفرفورست",
                "Everforest",
                "Everforest",
                longArrayOf(0xFF2D353B, 0xFF343F44, 0xFFA7C080, 0xFF83C092, 0xFFDBBC7F),
                longArrayOf(0xFFFDF6E3, 0xFFFFFCF0, 0xFF8DA101, 0xFF35A77C, 0xFFDFA000),
            ),
            featured(
                "synthwave",
                "سينث ويف",
                "Synthwave",
                "Synthwave",
                longArrayOf(0xFF1A1029, 0xFF241634, 0xFFFF7EDB, 0xFF72F1B8, 0xFF36F9F6),
                longArrayOf(0xFFF6EEFF, 0xFFFFFFFF, 0xFFC126A8, 0xFF128F63, 0xFF0E8C8C),
            ),
            featured(
                "cyberpunk",
                "سايبربانك",
                "Cyberpunk",
                "Cyberpunk",
                longArrayOf(0xFF0A0A12, 0xFF14141F, 0xFFFCE738, 0xFFFF2E97, 0xFF00E5FF),
                longArrayOf(0xFFFBE7FA, 0xFFFFFFFF, 0xFF9A7B00, 0xFFC40076, 0xFF00707F),
            ),
            featured(
                "amoled",
                "أسود كامل",
                "AMOLED",
                "AMOLED",
                longArrayOf(0xFF000000, 0xFF0A0A0A, 0xFF38BDF8, 0xFF7DD3FC, 0xFF34D399),
                longArrayOf(0xFFFFFFFF, 0xFFF8F8F8, 0xFF0369A1, 0xFF075985, 0xFF047857),
            ),
            // ————— 2. هوية Mahdi-Editor —————
            featured(
                "kiyani",
                "كِياني",
                "Kiyani",
                "Mahdi-Editor",
                longArrayOf(0xFF0A1F1F, 0xFF112E2E, 0xFF14B8A6, 0xFF0D9488, 0xFFF5D76E),
                longArrayOf(0xFFF2FBF9, 0xFFFFFFFF, 0xFF0F766E, 0xFF115E59, 0xFFB08000),
            ),
            featured(
                "kiyani-light",
                "كِياني الفاتح",
                "Kiyani Light",
                "Mahdi-Editor",
                longArrayOf(0xFF0E2B2A, 0xFF16403E, 0xFF2DD4BF, 0xFF14B8A6, 0xFFE7C766),
                longArrayOf(0xFFFFFFFF, 0xFFF0FDFA, 0xFF0D9488, 0xFF0F766E, 0xFFB08000),
            ),
            featured(
                "baghdad",
                "بغداد",
                "Baghdad",
                "Mahdi-Editor",
                longArrayOf(0xFF1A1206, 0xFF261B0B, 0xFFD4A017, 0xFFB87333, 0xFF40C4B0),
                longArrayOf(0xFFFDF7E7, 0xFFFFFDF5, 0xFF8A6200, 0xFF8C4A1E, 0xFF0F8F80),
            ),
            featured(
                "sahara",
                "صحراء",
                "Sahara",
                "Mahdi-Editor",
                longArrayOf(0xFF1C1710, 0xFF26201A, 0xFFE0A458, 0xFFC98B4B, 0xFF9BBE6A),
                longArrayOf(0xFFFBF3E4, 0xFFFFFDF7, 0xFF9A6415, 0xFF7A4E14, 0xFF5E7A33),
            ),
            featured(
                "tigris",
                "دجلة",
                "Tigris",
                "Mahdi-Editor",
                longArrayOf(0xFF061A24, 0xFF0B2733, 0xFF2FA8CC, 0xFF1D7F9E, 0xFF7FD4E8),
                longArrayOf(0xFFEEF8FC, 0xFFFFFFFF, 0xFF12657F, 0xFF0E4E62, 0xFF2E8FA8),
            ),
            featured(
                "euphrates",
                "الفرات",
                "Euphrates",
                "Mahdi-Editor",
                longArrayOf(0xFF061F17, 0xFF0B2C21, 0xFF3FBF8F, 0xFF2A9A73, 0xFFB9E39A),
                longArrayOf(0xFFF0FBF5, 0xFFFFFFFF, 0xFF12755A, 0xFF0E5A45, 0xFF4A7C2E),
            ),
            featured(
                "arabic-night",
                "ليل عربي",
                "Arabic Night",
                "Mahdi-Editor",
                longArrayOf(0xFF12101C, 0xFF1B1828, 0xFFC9A227, 0xFF2FA9A0, 0xFF9B6DD6),
                longArrayOf(0xFFF7F3FF, 0xFFFFFFFF, 0xFF8A6A00, 0xFF12766F, 0xFF5F3E9E),
            ),
            featured(
                "royal",
                "ملكي",
                "Royal",
                "Mahdi-Editor",
                longArrayOf(0xFF1A0B2E, 0xFF241040, 0xFFF5D76E, 0xFF7C3AED, 0xFFE9D5FF),
                longArrayOf(0xFFFBF5FF, 0xFFFFFFFF, 0xFF7C3AED, 0xFF5B21B6, 0xFF9A6B00),
            ),
            featured(
                "midnight",
                "منتصف الليل",
                "Midnight",
                "Mahdi-Editor",
                longArrayOf(0xFF0B1020, 0xFF141A2E, 0xFF6C8CFF, 0xFF9B7BFF, 0xFF4FD1C5),
                longArrayOf(0xFFF3F5FC, 0xFFFFFFFF, 0xFF3A5BD9, 0xFF6B47C9, 0xFF0E8F86),
            ),
            featured(
                "sunset",
                "غروب",
                "Sunset",
                "Mahdi-Editor",
                longArrayOf(0xFF1F1220, 0xFF2B1A2C, 0xFFF97316, 0xFFEC4899, 0xFFFACC15),
                longArrayOf(0xFFFFF6EC, 0xFFFFFFFF, 0xFFC2410C, 0xFFBE185D, 0xFFA16207),
            ),
            featured(
                "forest",
                "غابة",
                "Forest",
                "Mahdi-Editor",
                longArrayOf(0xFF071C12, 0xFF0D2A1C, 0xFF16A34A, 0xFF15803D, 0xFFF59E0B),
                longArrayOf(0xFFF1FAF3, 0xFFFFFFFF, 0xFF15803D, 0xFF166534, 0xFFB45309),
            ),
            featured(
                "ocean",
                "محيط",
                "Ocean",
                "Mahdi-Editor",
                longArrayOf(0xFF052435, 0xFF0A3348, 0xFF0EA5E9, 0xFF0284C7, 0xFF67E8F9),
                longArrayOf(0xFFF0F9FF, 0xFFFFFFFF, 0xFF0369A1, 0xFF075985, 0xFF0E7490),
            ),
            featured(
                "neon",
                "نيون",
                "Neon",
                "Mahdi-Editor",
                longArrayOf(0xFF0A0A0A, 0xFF141414, 0xFF00F0FF, 0xFFFF00E5, 0xFF7CFF00),
                longArrayOf(0xFFFFFFFF, 0xFFF5F5F5, 0xFF007C87, 0xFF9B008C, 0xFF3E8500),
            ),
            featured(
                "rose-gold",
                "ذهبي وردي",
                "Rose Gold",
                "Mahdi-Editor",
                longArrayOf(0xFF211014, 0xFF2E1720, 0xFFE8A87C, 0xFFC97B84, 0xFFF4D6A8),
                longArrayOf(0xFFFFF4F1, 0xFFFFFFFF, 0xFFB4643E, 0xFFA34E5A, 0xFF9A6B1F),
            ),
            featured(
                "ember",
                "جمر",
                "Ember",
                "Mahdi-Editor",
                longArrayOf(0xFF181010, 0xFF241717, 0xFFFF6B35, 0xFFE03E1A, 0xFFF2B134),
                longArrayOf(0xFFFFF5F0, 0xFFFFFFFF, 0xFFC2410C, 0xFF9A3412, 0xFF9A6B00),
            ),
            featured(
                "lavender",
                "لافندر",
                "Lavender",
                "Mahdi-Editor",
                longArrayOf(0xFF16121F, 0xFF201A2C, 0xFFB8A1E8, 0xFF8E7CC3, 0xFF9FE3D0),
                longArrayOf(0xFFF8F5FF, 0xFFFFFFFF, 0xFF6D50B8, 0xFF513A8F, 0xFF1F8E7C),
            ),
            featured(
                "mint",
                "نعناعي",
                "Mint",
                "Mahdi-Editor",
                longArrayOf(0xFF0B1F1B, 0xFF122B26, 0xFF4ADE80, 0xFF2CB67D, 0xFF7DD3FC),
                longArrayOf(0xFFF0FDF8, 0xFFFFFFFF, 0xFF15803D, 0xFF166534, 0xFF0369A1),
            ),
            featured(
                "paper",
                "ورقي",
                "Paper",
                "Mahdi-Editor",
                longArrayOf(0xFF2A2622, 0xFF35302A, 0xFFD9B382, 0xFFB08D5B, 0xFF8FB98A),
                longArrayOf(0xFFFAF6EE, 0xFFFFFDF8, 0xFF7A5A28, 0xFF6B4E22, 0xFF4E6B45),
            ),
            featured(
                "vs-light",
                "في إس الفاتح",
                "VS Light",
                "VS Code",
                longArrayOf(0xFF1E1E1E, 0xFF252526, 0xFF569CD6, 0xFF4EC9B0, 0xFFCE9178),
                longArrayOf(0xFFFFFFFF, 0xFFF3F3F3, 0xFF007ACC, 0xFF0098FF, 0xFF16825D),
            ),
        )

    fun byId(id: String): FeaturedTheme? = themes.find { it.id == id }

    fun bySlug(slug: String): FeaturedTheme? = themes.find { it.slug == slug }

    /** هل هذا الثيم من الثيمات المميّزة؟ */
    fun isStoreTheme(id: String): Boolean = themes.any { it.id == id }
}
