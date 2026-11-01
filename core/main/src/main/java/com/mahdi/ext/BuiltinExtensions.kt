package com.mahdi.ext

import com.mahdi.activities.settings.SettingsRoutes
import com.mahdi.tools.BuiltinTools

/** نوع الإضافة المدمجة: أداة تعمل على نصّ، أو قدرة نظام لها شاشة. */
enum class ExtensionKind { TOOL, CAPABILITY }

/**
 * إضافة مدمجة (3.0.0) — إمّا **أداة** من [BuiltinTools] أو **قدرة** تفتح شاشة قائمة.
 *
 * كل عنصر هنا حقيقي: لا يوجد زرّ يعرض «قريبًا». الأداة تنفّذ على النصّ فورًا،
 * والقدرة تنقلك إلى الشاشة التي تنفّذها (ثيمات، لغات، بيئات تشغيل…).
 */
data class BuiltinExtension(
    val id: String,
    val emoji: String,
    val nameAr: String,
    val nameEn: String,
    val descAr: String,
    val descEn: String,
    val kind: ExtensionKind,
    val version: String = "1.0.0",
    val author: String = "Mahdi-Editor-Code",
    val license: String = "MIT",
    /** معرّف الأداة في [BuiltinTools] حين تكون الإضافة أداة. */
    val toolId: String? = null,
    /** مسار الشاشة حين تكون الإضافة قدرة. */
    val route: String? = null,
) {
    fun name(isArabic: Boolean): String = if (isArabic) nameAr else nameEn

    fun description(isArabic: Boolean): String = if (isArabic) descAr else descEn

    /** البيان المنظّم كما يُكتب في `manifest.json` داخل الحزمة. */
    fun manifestJson(): String =
        """
        {
          "id": "$id",
          "name": "$nameEn",
          "version": "$version",
          "mainClass": "${toolId?.let { "com.mahdi.tools.$it.Tool" } ?: "com.mahdi.settings.$id.Screen"}",
          "type": "extension",
          "author": { "displayName": "$author" },
          "license": "$license",
          "minAppVersion": ${ExtensionApi.MIN_APP_VERSION}
        }
        """.trimIndent()
}

/**
 * كتالوج الإضافات المدمجة — **٣٦ إضافة** بلا إنترنت وبلا تثبيت (3.0.0):
 * ٢٤ أداة نصّية (١١٢ إجراءً) + ١٢ قدرة نظام موصولة بالثيمات واللغات والأوامر وشريط الحالة.
 *
 * الأدوات تُبنى مباشرة من [BuiltinTools] حتى يبقى العدد متزامنًا مع الشاشة الحقيقية،
 * والقدرات مسارات [SettingsRoutes] قائمة — لا نسخ مكرّر ولا وعود فارغة.
 */
object BuiltinExtensions {

    /** القدرات الاثنتا عشرة الموصولة بشاشات التطبيق. */
    private val capabilities =
        listOf(
            BuiltinExtension(
                id = "cap.themes",
                emoji = "🎨",
                nameAr = "الثيمات",
                nameEn = "Themes",
                descAr = "الثيمات الأربعة الأساسية وثيمات المتجر ثنائية الوضع",
                descEn = "The four base themes plus dual-mode store themes",
                kind = ExtensionKind.CAPABILITY,
                route = SettingsRoutes.Themes.route,
            ),
            BuiltinExtension(
                id = "cap.icons",
                emoji = "🖼️",
                nameAr = "حزم الأيقونات",
                nameEn = "Icon packs",
                descAr = "أيقونات الملفات والمجلدات من المتجر أو من حزمة محلية",
                descEn = "File and folder icons from the store or local packs",
                kind = ExtensionKind.CAPABILITY,
                route = SettingsRoutes.Extensions.route,
            ),
            BuiltinExtension(
                id = "cap.languages",
                emoji = "🗣️",
                nameAr = "لغات البرمجة",
                nameEn = "Languages",
                descAr = "٤٨ لغة مع تمييز صياغة و٥٠ قاعدة TextMate",
                descEn = "48 languages with highlighting and 50 TextMate grammars",
                kind = ExtensionKind.CAPABILITY,
                route = SettingsRoutes.SupportedLanguages.route,
            ),
            BuiltinExtension(
                id = "cap.ui_language",
                emoji = "🌐",
                nameAr = "لغة الواجهة",
                nameEn = "Interface language",
                descAr = "العربية أو الإنجليزية — كل واحدة كاملة بلا خلط",
                descEn = "Arabic or English, each complete on its own",
                kind = ExtensionKind.CAPABILITY,
                route = SettingsRoutes.LanguageScreen.route,
            ),
            BuiltinExtension(
                id = "cap.runtimes",
                emoji = "🧰",
                nameAr = "بيئات التشغيل",
                nameEn = "Runtimes",
                descAr = "Python وNode وPHP وRuby وغيرها بحالة تثبيت حقيقية",
                descEn = "Python, Node, PHP, Ruby and more with real install state",
                kind = ExtensionKind.CAPABILITY,
                route = SettingsRoutes.Runtimes.route,
            ),
            BuiltinExtension(
                id = "cap.runners",
                emoji = "▶️",
                nameAr = "المشغّلات",
                nameEn = "Runners",
                descAr = "تشغيل الملفّات داخل الطرفية مع تجهيز المكتبات تلقائيًا",
                descEn = "Run files in the terminal with automatic dependency setup",
                kind = ExtensionKind.CAPABILITY,
                route = SettingsRoutes.Runners.route,
            ),
            BuiltinExtension(
                id = "cap.formatters",
                emoji = "🧹",
                nameAr = "المنسّقات",
                nameEn = "Formatters",
                descAr = "تنسيق الأكواد تلقائيًا عند الحفظ",
                descEn = "Automatic code formatting on save",
                kind = ExtensionKind.CAPABILITY,
                route = SettingsRoutes.Formatters.route,
            ),
            BuiltinExtension(
                id = "cap.git",
                emoji = "🌿",
                nameAr = "Git",
                nameEn = "Git",
                descAr = "مستودعات وفروع والتزامات من داخل التطبيق",
                descEn = "Repositories, branches and commits inside the app",
                kind = ExtensionKind.CAPABILITY,
                route = SettingsRoutes.Git.route,
            ),
            BuiltinExtension(
                id = "cap.guide",
                emoji = "📘",
                nameAr = "الدليل",
                nameEn = "Guide",
                descAr = "توثيق المحرّر والطرفية والحزم والاتجاه",
                descEn = "Docs for the editor, terminal, packages and RTL",
                kind = ExtensionKind.CAPABILITY,
                route = SettingsRoutes.Guide.route,
            ),
            BuiltinExtension(
                id = "cap.studio",
                emoji = "🏗️",
                nameAr = "استوديو الحزم",
                nameEn = "Package studio",
                descAr = "توليد قوالب الثيمات والإضافات وفحص الحزم",
                descEn = "Generate theme/extension templates and lint packages",
                kind = ExtensionKind.CAPABILITY,
                route = SettingsRoutes.PackageStudio.route,
            ),
            BuiltinExtension(
                id = "cap.keybindings",
                emoji = "⌨️",
                nameAr = "اختصارات المفاتيح",
                nameEn = "Keybindings",
                descAr = "تخصيص أزرار لوحة المفاتيح الإضافية",
                descEn = "Customise the extra keyboard buttons",
                kind = ExtensionKind.CAPABILITY,
                route = SettingsRoutes.Keybindings.route,
            ),
            BuiltinExtension(
                id = "cap.terminal_font",
                emoji = "🔠",
                nameAr = "خط الطرفية",
                nameEn = "Terminal font",
                descAr = "حجم الخط الافتراضي 12 والتكبير بإصبعين",
                descEn = "Default size 12 with two-finger pinch zoom",
                kind = ExtensionKind.CAPABILITY,
                route = SettingsRoutes.TerminalFontScreen.route,
            ),
        )

    /** الإضافات الـ٣٦ مرتّبة: القدرات أولًا ثم الأدوات. */
    val all: List<BuiltinExtension> =
        capabilities +
            BuiltinTools.all.map { tool ->
                BuiltinExtension(
                    id = "tool.${tool.id}",
                    emoji = tool.emoji,
                    nameAr = tool.nameAr,
                    nameEn = tool.nameEn,
                    descAr = tool.descAr,
                    descEn = tool.descEn,
                    kind = ExtensionKind.TOOL,
                    toolId = tool.id,
                )
            }

    val toolCount: Int get() = all.count { it.kind == ExtensionKind.TOOL }

    val capabilityCount: Int get() = all.count { it.kind == ExtensionKind.CAPABILITY }

    val actionCount: Int get() = BuiltinTools.actionCount

    fun byId(id: String): BuiltinExtension? = all.firstOrNull { it.id == id }

    fun tools(): List<BuiltinExtension> = all.filter { it.kind == ExtensionKind.TOOL }

    fun capabilities(): List<BuiltinExtension> = all.filter { it.kind == ExtensionKind.CAPABILITY }

    /**
     * يسجّل كل إضافات النظام كأوامر في [ExtensionApi] (مرّة واحدة، بلا تكرار).
     *
     * هذا ما يجعل لوحة الأوامر تعرف الأدوات والقدرات: كل أمر يحمل مسار تنقّل
     * (`route`) أو معرّف أداة (`toolId`) ليتمكّن المستدعي من فتحه.
     */
    fun registerCommands() {
        all.forEach { extension ->
            ExtensionApi.registerCommand(
                ExtensionCommand(
                    id = "cmd.${extension.id}",
                    titleAr = extension.nameAr,
                    titleEn = extension.nameEn,
                    extensionId = extension.id,
                    route = extension.route ?: extension.toolId?.let { "builtin_tool/$it" },
                )
            )
        }
    }
}
