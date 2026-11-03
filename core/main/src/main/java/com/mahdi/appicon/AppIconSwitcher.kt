package com.mahdi.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.mahdi.utils.logError

/**
 * تبديل أيقونة التطبيق (4.0.0).
 *
 * أندرويد لا يسمح بتغيير أيقونة التطبيق وقت التشغيل مباشرة، بل عبر **activity-alias**
 * لكل أيقونة: كل alias يحمل `icon` خاصًا وفلتر LAUNCHER. التبديل الحقيقي = تمكين
 * الـ alias المختار وتعطيل البقية عبر `PackageManager.setComponentEnabledSetting`.
 *
 * القائمة هنا مطابقة تمامًا لما هو معلن في `AndroidManifest.xml` (126 أيقونة = 14 رمزًا × 9
 * لوحات ألوان). أي
 * إضافة أيقونة جديدة تحتاج سطرًا في الاثنين، وإلا صار أحدهم يشير إلى غير موجود.
 *
 * ملاحظة: التعطيل/التمكين يستخدم `DONT_KILL_APP` فلا يُغلق التطبيق عند التبديل.
 */
object AppIconSwitcher {

    private const val PREFS = "mahdi_app_icon"
    private const val KEY_CURRENT = "current_icon"
    private const val PACKAGE_PREFIX = "com.mahdi.application.icon."

    /** أيقونة واحدة قابلة للاختيار. */
    data class AppIcon(
        val id: String,
        val aliasSimpleName: String,
        val nameAr: String,
        val nameEn: String,
        /** لون الخلفية لعرض المعاينة داخل التطبيق (نفس لون الأيقونة الحقيقي). */
        val previewBackground: Long,
        /** لون الرمز في المعاينة. */
        val previewAccent: Long,
        /** اسم drawable المقدّمة (الرمز) داخل الوحدة `core/main`. */
        val foregroundDrawable: String,
    ) {
        /** الاسم الكامل للمكوّن كما هو في البيان. */
        val aliasClassName: String get() = PACKAGE_PREFIX + aliasSimpleName

        fun displayName(isArabic: Boolean): String = if (isArabic) nameAr else nameEn
    }

    /** الأيقونات الـ126 — 14 رمزًا × 9 لوحات ألوان، والترتيب هو ترتيب العرض. */
    val icons: List<AppIcon> =
        listOf(
            AppIcon("code_sky", "IconCodeSky", "أقواس الكود · سماوي", "Code · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_code_sky_foreground"),
            AppIcon("code_amber", "IconCodeAmber", "أقواس الكود · ذهبي", "Code · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_code_amber_foreground"),
            AppIcon("code_mint", "IconCodeMint", "أقواس الكود · نعناعي", "Code · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_code_mint_foreground"),
            AppIcon("code_violet", "IconCodeViolet", "أقواس الكود · بنفسجي", "Code · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_code_violet_foreground"),
            AppIcon("code_rose", "IconCodeRose", "أقواس الكود · وردي", "Code · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_code_rose_foreground"),
            AppIcon("code_slate", "IconCodeSlate", "أقواس الكود · رمادي", "Code · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_code_slate_foreground"),
            AppIcon("code_cyan", "IconCodeCyan", "أقواس الكود · فيروزي", "Code · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_code_cyan_foreground"),
            AppIcon("code_lime", "IconCodeLime", "أقواس الكود · ليموني", "Code · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_code_lime_foreground"),
            AppIcon("code_magenta", "IconCodeMagenta", "أقواس الكود · أرجواني", "Code · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_code_magenta_foreground"),
            AppIcon("terminal_sky", "IconTerminalSky", "الطرفية · سماوي", "Terminal · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_terminal_sky_foreground"),
            AppIcon("terminal_amber", "IconTerminalAmber", "الطرفية · ذهبي", "Terminal · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_terminal_amber_foreground"),
            AppIcon("terminal_mint", "IconTerminalMint", "الطرفية · نعناعي", "Terminal · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_terminal_mint_foreground"),
            AppIcon("terminal_violet", "IconTerminalViolet", "الطرفية · بنفسجي", "Terminal · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_terminal_violet_foreground"),
            AppIcon("terminal_rose", "IconTerminalRose", "الطرفية · وردي", "Terminal · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_terminal_rose_foreground"),
            AppIcon("terminal_slate", "IconTerminalSlate", "الطرفية · رمادي", "Terminal · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_terminal_slate_foreground"),
            AppIcon("terminal_cyan", "IconTerminalCyan", "الطرفية · فيروزي", "Terminal · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_terminal_cyan_foreground"),
            AppIcon("terminal_lime", "IconTerminalLime", "الطرفية · ليموني", "Terminal · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_terminal_lime_foreground"),
            AppIcon("terminal_magenta", "IconTerminalMagenta", "الطرفية · أرجواني", "Terminal · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_terminal_magenta_foreground"),
            AppIcon("braces_sky", "IconBracesSky", "أقواس معقوفة · سماوي", "Braces · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_braces_sky_foreground"),
            AppIcon("braces_amber", "IconBracesAmber", "أقواس معقوفة · ذهبي", "Braces · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_braces_amber_foreground"),
            AppIcon("braces_mint", "IconBracesMint", "أقواس معقوفة · نعناعي", "Braces · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_braces_mint_foreground"),
            AppIcon("braces_violet", "IconBracesViolet", "أقواس معقوفة · بنفسجي", "Braces · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_braces_violet_foreground"),
            AppIcon("braces_rose", "IconBracesRose", "أقواس معقوفة · وردي", "Braces · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_braces_rose_foreground"),
            AppIcon("braces_slate", "IconBracesSlate", "أقواس معقوفة · رمادي", "Braces · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_braces_slate_foreground"),
            AppIcon("braces_cyan", "IconBracesCyan", "أقواس معقوفة · فيروزي", "Braces · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_braces_cyan_foreground"),
            AppIcon("braces_lime", "IconBracesLime", "أقواس معقوفة · ليموني", "Braces · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_braces_lime_foreground"),
            AppIcon("braces_magenta", "IconBracesMagenta", "أقواس معقوفة · أرجواني", "Braces · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_braces_magenta_foreground"),
            AppIcon("hash_sky", "IconHashSky", "مربّع الشبكة · سماوي", "Hash · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_hash_sky_foreground"),
            AppIcon("hash_amber", "IconHashAmber", "مربّع الشبكة · ذهبي", "Hash · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_hash_amber_foreground"),
            AppIcon("hash_mint", "IconHashMint", "مربّع الشبكة · نعناعي", "Hash · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_hash_mint_foreground"),
            AppIcon("hash_violet", "IconHashViolet", "مربّع الشبكة · بنفسجي", "Hash · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_hash_violet_foreground"),
            AppIcon("hash_rose", "IconHashRose", "مربّع الشبكة · وردي", "Hash · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_hash_rose_foreground"),
            AppIcon("hash_slate", "IconHashSlate", "مربّع الشبكة · رمادي", "Hash · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_hash_slate_foreground"),
            AppIcon("hash_cyan", "IconHashCyan", "مربّع الشبكة · فيروزي", "Hash · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_hash_cyan_foreground"),
            AppIcon("hash_lime", "IconHashLime", "مربّع الشبكة · ليموني", "Hash · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_hash_lime_foreground"),
            AppIcon("hash_magenta", "IconHashMagenta", "مربّع الشبكة · أرجواني", "Hash · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_hash_magenta_foreground"),
            AppIcon("lambda_sky", "IconLambdaSky", "لامبدا · سماوي", "Lambda · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_lambda_sky_foreground"),
            AppIcon("lambda_amber", "IconLambdaAmber", "لامبدا · ذهبي", "Lambda · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_lambda_amber_foreground"),
            AppIcon("lambda_mint", "IconLambdaMint", "لامبدا · نعناعي", "Lambda · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_lambda_mint_foreground"),
            AppIcon("lambda_violet", "IconLambdaViolet", "لامبدا · بنفسجي", "Lambda · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_lambda_violet_foreground"),
            AppIcon("lambda_rose", "IconLambdaRose", "لامبدا · وردي", "Lambda · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_lambda_rose_foreground"),
            AppIcon("lambda_slate", "IconLambdaSlate", "لامبدا · رمادي", "Lambda · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_lambda_slate_foreground"),
            AppIcon("lambda_cyan", "IconLambdaCyan", "لامبدا · فيروزي", "Lambda · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_lambda_cyan_foreground"),
            AppIcon("lambda_lime", "IconLambdaLime", "لامبدا · ليموني", "Lambda · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_lambda_lime_foreground"),
            AppIcon("lambda_magenta", "IconLambdaMagenta", "لامبدا · أرجواني", "Lambda · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_lambda_magenta_foreground"),
            AppIcon("sigma_sky", "IconSigmaSky", "سيجما · سماوي", "Sigma · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_sigma_sky_foreground"),
            AppIcon("sigma_amber", "IconSigmaAmber", "سيجما · ذهبي", "Sigma · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_sigma_amber_foreground"),
            AppIcon("sigma_mint", "IconSigmaMint", "سيجما · نعناعي", "Sigma · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_sigma_mint_foreground"),
            AppIcon("sigma_violet", "IconSigmaViolet", "سيجما · بنفسجي", "Sigma · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_sigma_violet_foreground"),
            AppIcon("sigma_rose", "IconSigmaRose", "سيجما · وردي", "Sigma · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_sigma_rose_foreground"),
            AppIcon("sigma_slate", "IconSigmaSlate", "سيجما · رمادي", "Sigma · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_sigma_slate_foreground"),
            AppIcon("sigma_cyan", "IconSigmaCyan", "سيجما · فيروزي", "Sigma · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_sigma_cyan_foreground"),
            AppIcon("sigma_lime", "IconSigmaLime", "سيجما · ليموني", "Sigma · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_sigma_lime_foreground"),
            AppIcon("sigma_magenta", "IconSigmaMagenta", "سيجما · أرجواني", "Sigma · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_sigma_magenta_foreground"),
            AppIcon("branch_sky", "IconBranchSky", "الفرع · سماوي", "Branch · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_branch_sky_foreground"),
            AppIcon("branch_amber", "IconBranchAmber", "الفرع · ذهبي", "Branch · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_branch_amber_foreground"),
            AppIcon("branch_mint", "IconBranchMint", "الفرع · نعناعي", "Branch · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_branch_mint_foreground"),
            AppIcon("branch_violet", "IconBranchViolet", "الفرع · بنفسجي", "Branch · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_branch_violet_foreground"),
            AppIcon("branch_rose", "IconBranchRose", "الفرع · وردي", "Branch · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_branch_rose_foreground"),
            AppIcon("branch_slate", "IconBranchSlate", "الفرع · رمادي", "Branch · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_branch_slate_foreground"),
            AppIcon("branch_cyan", "IconBranchCyan", "الفرع · فيروزي", "Branch · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_branch_cyan_foreground"),
            AppIcon("branch_lime", "IconBranchLime", "الفرع · ليموني", "Branch · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_branch_lime_foreground"),
            AppIcon("branch_magenta", "IconBranchMagenta", "الفرع · أرجواني", "Branch · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_branch_magenta_foreground"),
            AppIcon("hexagon_sky", "IconHexagonSky", "سُدُسي · سماوي", "Hexagon · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_hexagon_sky_foreground"),
            AppIcon("hexagon_amber", "IconHexagonAmber", "سُدُسي · ذهبي", "Hexagon · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_hexagon_amber_foreground"),
            AppIcon("hexagon_mint", "IconHexagonMint", "سُدُسي · نعناعي", "Hexagon · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_hexagon_mint_foreground"),
            AppIcon("hexagon_violet", "IconHexagonViolet", "سُدُسي · بنفسجي", "Hexagon · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_hexagon_violet_foreground"),
            AppIcon("hexagon_rose", "IconHexagonRose", "سُدُسي · وردي", "Hexagon · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_hexagon_rose_foreground"),
            AppIcon("hexagon_slate", "IconHexagonSlate", "سُدُسي · رمادي", "Hexagon · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_hexagon_slate_foreground"),
            AppIcon("hexagon_cyan", "IconHexagonCyan", "سُدُسي · فيروزي", "Hexagon · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_hexagon_cyan_foreground"),
            AppIcon("hexagon_lime", "IconHexagonLime", "سُدُسي · ليموني", "Hexagon · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_hexagon_lime_foreground"),
            AppIcon("hexagon_magenta", "IconHexagonMagenta", "سُدُسي · أرجواني", "Hexagon · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_hexagon_magenta_foreground"),
            AppIcon("shield_sky", "IconShieldSky", "الدرع · سماوي", "Shield · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_shield_sky_foreground"),
            AppIcon("shield_amber", "IconShieldAmber", "الدرع · ذهبي", "Shield · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_shield_amber_foreground"),
            AppIcon("shield_mint", "IconShieldMint", "الدرع · نعناعي", "Shield · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_shield_mint_foreground"),
            AppIcon("shield_violet", "IconShieldViolet", "الدرع · بنفسجي", "Shield · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_shield_violet_foreground"),
            AppIcon("shield_rose", "IconShieldRose", "الدرع · وردي", "Shield · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_shield_rose_foreground"),
            AppIcon("shield_slate", "IconShieldSlate", "الدرع · رمادي", "Shield · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_shield_slate_foreground"),
            AppIcon("shield_cyan", "IconShieldCyan", "الدرع · فيروزي", "Shield · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_shield_cyan_foreground"),
            AppIcon("shield_lime", "IconShieldLime", "الدرع · ليموني", "Shield · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_shield_lime_foreground"),
            AppIcon("shield_magenta", "IconShieldMagenta", "الدرع · أرجواني", "Shield · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_shield_magenta_foreground"),
            AppIcon("layers_sky", "IconLayersSky", "الطبقات · سماوي", "Layers · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_layers_sky_foreground"),
            AppIcon("layers_amber", "IconLayersAmber", "الطبقات · ذهبي", "Layers · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_layers_amber_foreground"),
            AppIcon("layers_mint", "IconLayersMint", "الطبقات · نعناعي", "Layers · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_layers_mint_foreground"),
            AppIcon("layers_violet", "IconLayersViolet", "الطبقات · بنفسجي", "Layers · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_layers_violet_foreground"),
            AppIcon("layers_rose", "IconLayersRose", "الطبقات · وردي", "Layers · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_layers_rose_foreground"),
            AppIcon("layers_slate", "IconLayersSlate", "الطبقات · رمادي", "Layers · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_layers_slate_foreground"),
            AppIcon("layers_cyan", "IconLayersCyan", "الطبقات · فيروزي", "Layers · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_layers_cyan_foreground"),
            AppIcon("layers_lime", "IconLayersLime", "الطبقات · ليموني", "Layers · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_layers_lime_foreground"),
            AppIcon("layers_magenta", "IconLayersMagenta", "الطبقات · أرجواني", "Layers · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_layers_magenta_foreground"),
            AppIcon("infinity_sky", "IconInfinitySky", "اللانهاية · سماوي", "Infinity · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_infinity_sky_foreground"),
            AppIcon("infinity_amber", "IconInfinityAmber", "اللانهاية · ذهبي", "Infinity · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_infinity_amber_foreground"),
            AppIcon("infinity_mint", "IconInfinityMint", "اللانهاية · نعناعي", "Infinity · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_infinity_mint_foreground"),
            AppIcon("infinity_violet", "IconInfinityViolet", "اللانهاية · بنفسجي", "Infinity · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_infinity_violet_foreground"),
            AppIcon("infinity_rose", "IconInfinityRose", "اللانهاية · وردي", "Infinity · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_infinity_rose_foreground"),
            AppIcon("infinity_slate", "IconInfinitySlate", "اللانهاية · رمادي", "Infinity · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_infinity_slate_foreground"),
            AppIcon("infinity_cyan", "IconInfinityCyan", "اللانهاية · فيروزي", "Infinity · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_infinity_cyan_foreground"),
            AppIcon("infinity_lime", "IconInfinityLime", "اللانهاية · ليموني", "Infinity · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_infinity_lime_foreground"),
            AppIcon("infinity_magenta", "IconInfinityMagenta", "اللانهاية · أرجواني", "Infinity · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_infinity_magenta_foreground"),
            AppIcon("brackets_sky", "IconBracketsSky", "أقواس مربّعة · سماوي", "Brackets · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_brackets_sky_foreground"),
            AppIcon("brackets_amber", "IconBracketsAmber", "أقواس مربّعة · ذهبي", "Brackets · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_brackets_amber_foreground"),
            AppIcon("brackets_mint", "IconBracketsMint", "أقواس مربّعة · نعناعي", "Brackets · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_brackets_mint_foreground"),
            AppIcon("brackets_violet", "IconBracketsViolet", "أقواس مربّعة · بنفسجي", "Brackets · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_brackets_violet_foreground"),
            AppIcon("brackets_rose", "IconBracketsRose", "أقواس مربّعة · وردي", "Brackets · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_brackets_rose_foreground"),
            AppIcon("brackets_slate", "IconBracketsSlate", "أقواس مربّعة · رمادي", "Brackets · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_brackets_slate_foreground"),
            AppIcon("brackets_cyan", "IconBracketsCyan", "أقواس مربّعة · فيروزي", "Brackets · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_brackets_cyan_foreground"),
            AppIcon("brackets_lime", "IconBracketsLime", "أقواس مربّعة · ليموني", "Brackets · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_brackets_lime_foreground"),
            AppIcon("brackets_magenta", "IconBracketsMagenta", "أقواس مربّعة · أرجواني", "Brackets · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_brackets_magenta_foreground"),
            AppIcon("bolt_sky", "IconBoltSky", "الصاعقة · سماوي", "Bolt · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_bolt_sky_foreground"),
            AppIcon("bolt_amber", "IconBoltAmber", "الصاعقة · ذهبي", "Bolt · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_bolt_amber_foreground"),
            AppIcon("bolt_mint", "IconBoltMint", "الصاعقة · نعناعي", "Bolt · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_bolt_mint_foreground"),
            AppIcon("bolt_violet", "IconBoltViolet", "الصاعقة · بنفسجي", "Bolt · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_bolt_violet_foreground"),
            AppIcon("bolt_rose", "IconBoltRose", "الصاعقة · وردي", "Bolt · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_bolt_rose_foreground"),
            AppIcon("bolt_slate", "IconBoltSlate", "الصاعقة · رمادي", "Bolt · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_bolt_slate_foreground"),
            AppIcon("bolt_cyan", "IconBoltCyan", "الصاعقة · فيروزي", "Bolt · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_bolt_cyan_foreground"),
            AppIcon("bolt_lime", "IconBoltLime", "الصاعقة · ليموني", "Bolt · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_bolt_lime_foreground"),
            AppIcon("bolt_magenta", "IconBoltMagenta", "الصاعقة · أرجواني", "Bolt · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_bolt_magenta_foreground"),
            AppIcon("star_sky", "IconStarSky", "النجمة · سماوي", "Star · Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_star_sky_foreground"),
            AppIcon("star_amber", "IconStarAmber", "النجمة · ذهبي", "Star · Amber", 0xFF1C1408, 0xFFF5C451, "ic_launcher_star_amber_foreground"),
            AppIcon("star_mint", "IconStarMint", "النجمة · نعناعي", "Star · Mint", 0xFF06241D, 0xFF34D399, "ic_launcher_star_mint_foreground"),
            AppIcon("star_violet", "IconStarViolet", "النجمة · بنفسجي", "Star · Violet", 0xFF1B1030, 0xFFA78BFA, "ic_launcher_star_violet_foreground"),
            AppIcon("star_rose", "IconStarRose", "النجمة · وردي", "Star · Rose", 0xFF2A0B18, 0xFFFB7185, "ic_launcher_star_rose_foreground"),
            AppIcon("star_slate", "IconStarSlate", "النجمة · رمادي", "Star · Slate", 0xFF101620, 0xFFE2E8F0, "ic_launcher_star_slate_foreground"),
            AppIcon("star_cyan", "IconStarCyan", "النجمة · فيروزي", "Star · Cyan", 0xFF04202B, 0xFF22D3EE, "ic_launcher_star_cyan_foreground"),
            AppIcon("star_lime", "IconStarLime", "النجمة · ليموني", "Star · Lime", 0xFF101A05, 0xFFA3E635, "ic_launcher_star_lime_foreground"),
            AppIcon("star_magenta", "IconStarMagenta", "النجمة · أرجواني", "Star · Magenta", 0xFF26062B, 0xFFF0ABFC, "ic_launcher_star_magenta_foreground"),
        )

    /** الأيقونة الافتراضية عند أول تشغيل. */
    const val DEFAULT_ICON_ID = "code_sky"

    /** رموز التصفية في الشاشة (14 رمزًا) — تُشتق من المعرّف `symbol_palette`. */
    fun symbolOf(id: String): String = id.substringBefore('_')

    fun paletteOf(id: String): String = id.substringAfter('_', "")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** معرّف الأيقونة المختارة حاليًا. */
    fun currentId(context: Context): String =
        prefs(context).getString(KEY_CURRENT, DEFAULT_ICON_ID)?.takeIf { id -> icons.any { it.id == id } } ?: DEFAULT_ICON_ID

    /**
     * يطبّق الأيقونة المختارة (4.2.0 — محسّن لـ126 أيقونة).
     *
     * التبديل الذكي: نفعّل alias المختار **ونعطّل السابق فقط** بدل مسح 126 مكوّنًا في
     * كل تبديل (كان 126 استدعاء Binder لكل نقرة). المسح الشامل يحدث فقط عند
     * `force = true` (بعد تحديث التطبيق أو عند اختلال الحالة).
     *
     * @return `true` إن نجح تفعيل الأيقونة المختارة.
     */
    fun apply(context: Context, id: String, force: Boolean = false): Boolean {
        val chosen = icons.firstOrNull { it.id == id } ?: return false
        val pm = context.packageManager
        val packageName = context.packageName
        val previousId = prefs(context).getString(KEY_CURRENT, null)
        var ok = true

        fun setState(icon: AppIcon, enabled: Boolean) {
            val component = ComponentName(packageName, icon.aliasClassName)
            val state =
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            runCatching {
                    pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                }
                .onFailure {
                    // alias غير معلن (نسخة قديمة من البيان) — لا نُفشل التبديل كله
                    if (enabled) ok = false
                    logError(it, "app icon alias ${icon.aliasClassName}")
                }
        }

        // 1) أوقف السابق (إن كان مختلفًا) — استدعاء واحد
        val previous = icons.firstOrNull { it.id == previousId }
        if (previous != null && previous.id != chosen.id) setState(previous, false)

        // 2) فعّل المختار
        setState(chosen, true)

        // 3) مسح شامل عند الطلب فقط: يضمن ألّا تبقى أيقونتان ظاهرتين على الشاشة
        if (force) {
            icons.forEach { icon -> if (icon.id != chosen.id) setState(icon, false) }
        }

        if (ok) prefs(context).edit().putString(KEY_CURRENT, chosen.id).apply()
        return ok
    }

    /**
     * يُصلح الحالة عند الإقلاع (4.2.0 — بلا مسح 126 مكوّنًا).
     *
     * نتحقّق من مكوّن واحد فقط: هل alias المحفوظ مفعّل؟ إن كان كذلك لا نلمس شيئًا.
     * إن لم يكن (تحديث أزال الأيقونة من الشاشة الرئيسية، أو اختلال حالة) نُعيد التطبيق
     * بقوة مرة واحدة.
     */
    fun restore(context: Context) {
        val id = currentId(context)
        val chosen = icons.firstOrNull { it.id == id } ?: icons.first()
        val component = ComponentName(context.packageName, chosen.aliasClassName)

        val enabled =
            runCatching {
                    context.packageManager.getComponentEnabledSetting(component) ==
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                }
                .getOrDefault(false)

        if (enabled) return
        runCatching { apply(context, chosen.id, force = true) }
    }

    /** اسم الأيقونة العربية/الإنجليزية للعرض. */
    fun nameOf(id: String, isArabic: Boolean): String =
        icons.firstOrNull { it.id == id }?.displayName(isArabic) ?: id

    /** هل المستخدم غيّر الأيقونة عن الافتراضية؟ */
    fun isCustomized(context: Context): Boolean = currentId(context) != DEFAULT_ICON_ID

}
