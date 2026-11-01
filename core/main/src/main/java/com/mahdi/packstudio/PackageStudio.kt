package com.mahdi.packstudio

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mahdi.color.parseHex
import com.mahdi.color.toHex
import com.mahdi.theme.CustomThemes
import com.mahdi.theme.FeaturedTheme
import com.mahdi.theme.MahdiColors
import com.mahdi.theme.StoreThemeCatalog
import com.mahdi.theme.ThemeHolder
import com.mahdi.theme.asDarkColorScheme
import com.mahdi.theme.asLightColorScheme
import com.mahdi.theme.terminalDarkPalette
import com.mahdi.theme.terminalLightPalette
import java.io.File
import java.io.InputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * استوديو الحزم (2.8.0) — بناء حزم قياسية كاملة داخل التطبيق بلا حاسوب.
 *
 * يغطّي هذا الملف ثلاث قدرات لُخِّصت داخل Mahdi-Editor:
 * 1. **توليد القوالب**: قالب ثيم، قالب حزمة أيقونات، وقالب إضافة — كلها بحقوق
 *    `Mahdi-Editor-Code` وبهوية بصرية موحّدة، جاهزة للبناء أو للنشر.
 * 2. **التحقّق من المخططات**: فحص `manifest.json` و`theme.json` والملفات المرافقة
 *    وفق مخططات صارمة قبل التثبيت أو النشر.
 * 3. **الاستيراد**: قراءة أي `theme.json` قياسي (بلوحة فاتحة وأخرى داكنة) وتحويله
 *    إلى ثيم كامل داخل التطبيق بضغطة واحدة.
 *
 * كل النصوص والملفات المولَّدة تحمل اسم وهوية Mahdi-Editor-Code فقط.
 */
object PackageStudio {

    const val AUTHOR = "Mahdi-Editor-Code"
    const val LICENSE = "MIT"
    const val COPYRIGHT = "Mahdi-Editor-Code © 2026"
    const val MIN_APP_VERSION = 12
    const val MAX_PACK_BYTES = 25L * 1024 * 1024

    /** مجلد العمل الذي تُكتب فيه الحزم المولَّدة. */
    fun workspace(root: File): File {
        val dir = File(root, "Mahdi-Studio")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** الثيمات المتاحة لتوليد حزمة ثيم منها. */
    fun themeSources(): List<FeaturedTheme> = StoreThemeCatalog.themes

    // ——————————————————————————————————————————————————————————————
    // 1. توليد القوالب
    // ——————————————————————————————————————————————————————————————

    /** يولّد حزمة ثيم كاملة (فاتح + داكن) من ثيم مميّز في المخزون. */
    fun createThemePack(outDir: File, theme: FeaturedTheme, version: String = "1.0.0"): File =
        createThemePack(
            outDir = outDir,
            id = "com.mahdi.theme." + theme.slug.replace("-", "_"),
            nameAr = theme.nameAr,
            nameEn = theme.nameEn,
            light = theme.light,
            dark = theme.dark,
            version = version,
        )

    /**
     * يولّد حزمة ثيم قياسية `manifest.json` + `theme.json` بلوحتين منفصلتين.
     * الحزمة الناتجة تعمل في Mahdi-Editor وأي محرّر يقرأ الصيغة القياسية.
     */
    fun createThemePack(
        outDir: File,
        id: String,
        nameAr: String,
        nameEn: String,
        light: MahdiColors,
        dark: MahdiColors,
        version: String = "1.0.0",
    ): File {
        val name = "$nameAr · $nameEn"
        val manifest =
            JsonObject().apply {
                addProperty("id", id)
                addProperty("name", name)
                addProperty("version", version)
                addProperty("description", "ثيم مزدوج الوضع من استوديو Mahdi-Editor-Code")
                addProperty("type", "theme")
                add("author", JsonObject().apply { addProperty("displayName", AUTHOR) })
                addProperty("license", LICENSE)
                addProperty("copyright", COPYRIGHT)
                addProperty("minAppVersion", MIN_APP_VERSION)
                addProperty("inheritBase", false)
                add("tags", JsonArray().apply { add("theme"); add("light-dark") })
            }
        val entries =
            listOf(
                "manifest.json" to pretty(manifest).toByteArray(),
                "theme.json" to pretty(themeJson(light, dark)).toByteArray(),
                "README.md" to readme("ثيم", "theme", id, name).toByteArray(),
                "LICENSE" to licenseText().toByteArray(),
            )
        return writeZip(File(workspace(outDir), "$id-$version.mahdi"), entries)
    }

    /** يولّد حزمة أيقونات قياسية مع أيقونات SVG مبدئية بهوية التطبيق. */
    fun createIconPack(
        outDir: File,
        id: String = "com.mahdi.iconpack.studio",
        name: String = "حزمة أيقونات · Mahdi Studio",
        version: String = "1.0.0",
    ): File {
        val manifest =
            JsonObject().apply {
                addProperty("id", id)
                addProperty("name", name)
                addProperty("version", version)
                addProperty("description", "حزمة أيقونات مولَّدة من استوديو Mahdi-Editor-Code")
                addProperty("type", "icon_pack")
                add("author", JsonObject().apply { addProperty("displayName", AUTHOR) })
                addProperty("license", LICENSE)
                addProperty("copyright", COPYRIGHT)
                addProperty("minAppVersion", MIN_APP_VERSION)
                addProperty("applyTint", false)
                add(
                    "icons",
                    JsonObject().apply {
                        addProperty("defaultFile", "icons/file.svg")
                        addProperty("defaultFolder", "icons/folder.svg")
                        addProperty("defaultFolderExpanded", "icons/folder-open.svg")
                        add("folderNames", JsonObject())
                        add("folderNamesExpanded", JsonObject())
                        add("fileNames", JsonObject())
                        add("fileExtensions", JsonObject())
                        add("languageNames", JsonObject())
                    },
                )
            }
        val entries =
            listOf(
                "manifest.json" to pretty(manifest).toByteArray(),
                "icons/file.svg" to svgFile().toByteArray(),
                "icons/folder.svg" to svgFolder().toByteArray(),
                "icons/folder-open.svg" to svgFolder(open = true).toByteArray(),
                "README.md" to readme("حزمة أيقونات", "icon pack", id, name).toByteArray(),
                "LICENSE" to licenseText().toByteArray(),
            )
        return writeZip(File(workspace(outDir), "$id-$version.mahdi"), entries)
    }

    /** يولّد مشروع إضافة كامل: بيان + كود Kotlin نقطة بداية + تعليمات البناء. */
    fun createExtensionPack(
        outDir: File,
        id: String = "com.mahdi.extension.studio",
        name: String = "إضافة · Mahdi Studio",
        mainClass: String = "com.mahdi.extension.studio.Main",
        version: String = "1.0.0",
    ): File {
        val manifest =
            JsonObject().apply {
                addProperty("id", id)
                addProperty("name", name)
                addProperty("version", version)
                addProperty("description", "قالب إضافة من استوديو Mahdi-Editor-Code")
                addProperty("type", "extension")
                addProperty("mainClass", mainClass)
                add("author", JsonObject().apply { addProperty("displayName", AUTHOR) })
                addProperty("license", LICENSE)
                addProperty("copyright", COPYRIGHT)
                addProperty("minAppVersion", MIN_APP_VERSION)
            }
        val packageName = mainClass.substringBeforeLast('.')
        val className = mainClass.substringAfterLast('.')
        val sourcePath = "src/main/java/" + packageName.replace('.', '/') + "/$className.kt"
        val entries =
            listOf(
                "manifest.json" to pretty(manifest).toByteArray(),
                sourcePath to extensionSource(packageName, className).toByteArray(),
                "build.gradle.kts" to extensionGradle(packageName).toByteArray(),
                "README.md" to readme("إضافة", "extension", id, name).toByteArray(),
                "LICENSE" to licenseText().toByteArray(),
            )
        return writeZip(File(workspace(outDir), "$id-$version.mahdi"), entries)
    }

    // ——————————————————————————————————————————————————————————————
    // 2. التحقّق من المخططات
    // ——————————————————————————————————————————————————————————————

    data class PackIssue(val titleAr: String, val titleEn: String, val fatal: Boolean)

    data class PackReport(val issues: List<PackIssue>) {
        val ok: Boolean get() = issues.none { it.fatal }
        val errors: List<PackIssue> get() = issues.filter { it.fatal }
        val warnings: List<PackIssue> get() = issues.filterNot { it.fatal }
    }

    private val ID_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
    private val VERSION_REGEX = Regex("^\\d+\\.\\d+\\.\\d+$")

    /**
     * يفحص حزمة `.mahdi` (أو `.zip`/`.xed`) وفق المخططات: البنية، البيان،
     * نوع الحزمة، وملفات كل نوع. يعيد قائمة مشاكل بالعربية والإنجليزية.
     */
    fun validate(pack: File): PackReport {
        val issues = mutableListOf<PackIssue>()

        if (!pack.exists() || !pack.isFile) {
            return PackReport(
                listOf(PackIssue("الملف غير موجود", "File not found", true)),
            )
        }
        if (pack.length() > MAX_PACK_BYTES) {
            issues +=
                PackIssue(
                    "حجم الحزمة أكبر من ${MAX_PACK_BYTES / (1024 * 1024)} ميغابايت",
                    "Package is larger than ${MAX_PACK_BYTES / (1024 * 1024)} MB",
                    true,
                )
        }

        val entries = runCatching { listEntries(pack) }.getOrNull()
        if (entries == null) {
            return PackReport(
                issues + PackIssue("الملف ليس أرشيفًا صالحًا", "Not a valid archive", true),
            )
        }

        if ("manifest.json" !in entries) {
            issues += PackIssue("لا يوجد manifest.json في جذر الحزمة", "manifest.json missing at pack root", true)
            return PackReport(issues)
        }

        val manifest =
            runCatching { JsonParser.parseString(readEntry(pack, "manifest.json")!!).asJsonObject }
                .getOrNull()
        if (manifest == null) {
            return PackReport(
                issues + PackIssue("manifest.json ليس JSON صالحًا", "manifest.json is not valid JSON", true),
            )
        }

        val id = manifest.optString("id")
        val name = manifest.optString("name")
        val version = manifest.optString("version")
        val type = manifest.optString("type")

        if (id.isBlank() || !ID_REGEX.matches(id)) {
            issues +=
                PackIssue(
                    "المعرّف (id) يجب أن يكون بصيغة نطاق مقلوب مثل com.mahdi.theme.nord",
                    "id must look like com.mahdi.theme.nord",
                    true,
                )
        }
        if (name.isBlank()) issues += PackIssue("البيان بلا اسم (name)", "manifest has no name", true)
        if (!VERSION_REGEX.matches(version)) {
            issues +=
                PackIssue(
                    "الإصدار (version) يجب أن يكون ثلاثيًا مثل 1.0.0",
                    "version must be semver like 1.0.0",
                    true,
                )
        }
        if (manifest.getAsJsonObject("author")?.optString("displayName").isNullOrBlank()) {
            issues +=
                PackIssue(
                    "البيان بلا مؤلّف (author.displayName)",
                    "manifest has no author.displayName",
                    false,
                )
        }
        if (manifest.optString("license").isBlank()) {
            issues += PackIssue("لم يُذكر الترخيص (license)", "license is missing", false)
        }

        when (type) {
            "theme" -> {
                if ("theme.json" !in entries) {
                    issues += PackIssue("حزمة الثيم بلا theme.json", "theme pack has no theme.json", true)
                } else {
                    val theme =
                        runCatching { JsonParser.parseString(readEntry(pack, "theme.json")!!).asJsonObject }
                            .getOrNull()
                    if (theme == null) {
                        issues +=
                            PackIssue("theme.json غير صالح", "theme.json is not valid JSON", true)
                    } else {
                        listOf("light", "dark").forEach { mode ->
                            val block = theme.getAsJsonObject(mode)
                            if (block == null) {
                                issues +=
                                    PackIssue(
                                        "theme.json ينقصه قسم «$mode»",
                                        "theme.json has no \"$mode\" section",
                                        false,
                                    )
                            } else {
                                val base = block.getAsJsonObject("baseColors") ?: block
                                listOf("background", "surface", "primary").forEach { token ->
                                    if (!base.has(token)) {
                                        issues +=
                                            PackIssue(
                                                "قسم «$mode» ينقصه اللون $token",
                                                "\"$mode\" is missing $token",
                                                true,
                                            )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "icon_pack" -> {
                val icons = manifest.getAsJsonObject("icons")
                if (icons == null || icons.size() == 0) {
                    issues +=
                        PackIssue(
                            "حزمة الأيقونات بلا كائن icons",
                            "icon pack has no icons object",
                            true,
                        )
                } else {
                    icons.entrySet().forEach { entry ->
                        val value = entry.value
                        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                            val path = value.asString
                            if (path.isNotBlank() && path !in entries) {
                                issues +=
                                    PackIssue(
                                        "الأيقونة ${entry.key} تشير إلى ملف مفقود: $path",
                                        "${entry.key} points to a missing file: $path",
                                        true,
                                    )
                            }
                        }
                    }
                }
            }
            "extension" -> {
                if (manifest.optString("mainClass").isBlank()) {
                    issues += PackIssue("الإضافة بلا mainClass", "extension has no mainClass", true)
                }
                if (entries.none { it.endsWith(".apk") }) {
                    issues +=
                        PackIssue(
                            "لا يوجد ملف APK داخل حزمة الإضافة",
                            "no APK inside the extension pack",
                            false,
                        )
                }
            }
            "" -> issues += PackIssue("البيان بلا نوع (type)", "manifest has no type", true)
            else -> issues += PackIssue("نوع غير معروف: $type", "unknown type: $type", true)
        }

        if (entries.none { it.equals("README.md", ignoreCase = true) }) {
            issues += PackIssue("لا يوجد README.md (مستحسن)", "no README.md (recommended)", false)
        }

        return PackReport(issues)
    }

    // ——————————————————————————————————————————————————————————————
    // 3. الاستيراد
    // ——————————————————————————————————————————————————————————————

    data class ImportedTheme(val name: String, val light: MahdiColors, val dark: MahdiColors)

    /**
     * يقرأ ثيمًا قياسيًا من ملف `theme.json` مباشرة أو من أرشيف حزمة تحتوي `theme.json`.
     * يعيد `null` إن لم يكن الملف ثيمًا مفهومًا.
     */
    fun importTheme(file: File): ImportedTheme? {
        val text = readThemeText(file) ?: return null

        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return null
        val dark = root.getAsJsonObject("dark")?.let { colorsFromTokenSet(it) }
        val light = root.getAsJsonObject("light")?.let { colorsFromTokenSet(it) }
        val resolvedDark = dark ?: light ?: return null
        val resolvedLight = light ?: deriveOpposite(resolvedDark)
        val name =
            root.optString("name").ifBlank {
                file.parentFile?.name ?: file.nameWithoutExtension
            }
        return ImportedTheme(name = name, light = resolvedLight, dark = resolvedDark)
    }

    /**
     * يقرأ نص `theme.json` من ملف مباشرة أو من داخل أرشيف — بلا اعتماد على امتداد
     * الملف، لأن الملفات المختارة من مدير الملفات تصل غالبًا بامتداد مؤقّت.
     */
    private fun readThemeText(file: File): String? {
        val fromArchive =
            runCatching {
                    val names = listEntries(file)
                    val path = names.firstOrNull { it.equals("theme.json", ignoreCase = true) }
                    path?.let { readEntry(file, it) }
                }
                .getOrNull()
        if (!fromArchive.isNullOrBlank()) return fromArchive

        val raw = runCatching { file.readText() }.getOrNull() ?: return null
        return raw.trimStart().takeIf { it.startsWith("{") }
    }

    /** يثبّت الثيم المستورد في المخزون كثيم مخصّص ويعيده جاهزًا للتطبيق. */
    fun importThemeIntoApp(file: File, fallbackName: String? = null): ThemeHolder? {
        val imported = importTheme(file) ?: return null
        val id = "custom-import-" + System.currentTimeMillis().toString(36)
        val name = fallbackName ?: imported.name
        return CustomThemes.savePair(id = id, name = name, light = imported.light, dark = imported.dark)
    }

    // ——————————————————————————————————————————————————————————————
    // 4. الدليل المدمج
    // ——————————————————————————————————————————————————————————————

    /**
     * دليل صيغة الحزم، كاملًا داخل التطبيق وبلا إنترنت.
     * يشرح البنية والملفات المطلوبة لكل نوع، ويصلح كمرجع سريع للمطوّرين.
     */
    fun packageGuide(isArabic: Boolean): String =
        if (isArabic) {
            """
            كل حزمة أرشيف ZIP بامتداد .mahdi (أو .xed / .xedx) يحتوي على:

            manifest.json — إلزامي
              id            معرّف بنطاق مقلوب، مثل: com.mahdi.theme.nord
              name          اسم الحزمة الظاهر
              version       إصدار ثلاثي، مثل: 1.0.0
              type          theme | icon_pack | extension
              author        كائن فيه displayName
              license       الترخيص، ويُفضّل MIT
              minAppVersion أقل إصدار تطبيق يدعم الحزمة (12 وما بعده)

            theme.json — لحزم الثيمات
              light.baseColors   primary / surface / background / onSurface … إلخ
              dark.baseColors    المجموعة نفسها للوضع الداكن
              terminalColors     color0…color21 و cursor (اختياري)
              وحدة الحزمة تُثبَّت كثيم كامل بلوحتين في المخزون.

            icons + manifest.icons — لحزم الأيقونات
              defaultFile / defaultFolder / defaultFolderExpanded
              fileNames / fileExtensions / folderNames / languageNames
              كل مسار داخل الحزمة يجب أن يوجد فعلًا، ويفضّل SVG.

            mainClass + ملف APK — للإضافات
              mainClass يشير إلى صنف يرث ExtensionAPI ويقبل ExtensionContext.
              الحزمة تُبنى كمكتبة Android ثم تُغلَّف مع manifest.json.

            التحقّق قبل النشر: استخدم «فحص حزمة…» أعلاه؛ يفحص المعرّف والإصدار والنوع
            ووجود الملفات المطلوبة ومسار كل أيقونة، ويعرض الأخطاء بالعربية.
            """.trimIndent()
        } else {
            """
            Every pack is a ZIP archive with a .mahdi extension (.xed / .xedx also accepted) containing:

            manifest.json — required
              id            reverse-domain id, e.g. com.mahdi.theme.nord
              name          display name
              version       semver, e.g. 1.0.0
              type          theme | icon_pack | extension
              author        object with displayName
              license       preferably MIT
              minAppVersion minimum supported app version (12+)

            theme.json — theme packs
              light.baseColors   primary / surface / background / onSurface …
              dark.baseColors    the same token set for the dark palette
              terminalColors     color0…color21 and cursor (optional)
              The pack installs as one theme holding both palettes.

            icons + manifest.icons — icon packs
              defaultFile / defaultFolder / defaultFolderExpanded
              fileNames / fileExtensions / folderNames / languageNames
              Every referenced path must exist inside the pack; SVG is preferred.

            mainClass + an APK — extensions
              mainClass points to a class extending ExtensionAPI that accepts ExtensionContext.
              Build it as an Android library, then bundle it with manifest.json.

            Validate before publishing: use "Validate pack…" above. It checks id, version,
            type, required files and every icon path, and lists blocking errors.
            """.trimIndent()
        }

    // ——————————————————————————————————————————————————————————————
    // أدوات داخلية
    // ——————————————————————————————————————————————————————————————

    private fun JsonObject.optString(key: String): String =
        runCatching { get(key)?.takeIf { !it.isJsonNull }?.asString }.getOrNull().orEmpty()

    private fun colorsFromTokenSet(mode: JsonObject): MahdiColors? {
        val base = mode.getAsJsonObject("baseColors") ?: mode

        fun color(vararg keys: String): Long? {
            keys.forEach { key ->
                val raw = runCatching { base.get(key)?.asString }.getOrNull()
                if (!raw.isNullOrBlank()) {
                    val parsed = raw.parseHex() ?: return@forEach
                    return parsed.toArgb().toLong() and 0xFFFFFFFFL
                }
            }
            return null
        }

        val background = color("background", "surface") ?: return null
        val surface = color("surface") ?: background
        val primary = color("primary") ?: return null
        val secondary = color("secondary", "primaryContainer") ?: primary
        val accent = color("tertiary", "inversePrimary") ?: secondary
        val isLight = Color(background).luminance() > 0.45f
        val text = color("onBackground", "onSurface") ?: if (isLight) 0xFF11130FL else 0xFFF8F6F0L
        val textSecondary = color("onSurfaceVariant") ?: (text and 0x00FFFFFFL) or 0xB0000000L
        val outline = color("outline", "outlineVariant") ?: textSecondary
        val error = color("error") ?: 0xFFEF4444
        val success = color("success", "tertiaryContainer") ?: 0xFF10B981
        val warning = color("warning") ?: 0xFFF59E0B

        return MahdiColors(
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
    }

    /** يشتق لوحة معاكسة بسيطة عند غياب أحد الوضعين في الثيم المستورد. */
    private fun deriveOpposite(colors: MahdiColors): MahdiColors {
        val invert = { value: Long ->
            val c = Color(value and 0xFFFFFFFFL)
            val inv = Color(1f - c.red, 1f - c.green, 1f - c.blue)
            val mixed = androidx.compose.ui.graphics.lerp(inv, Color.White, 0.72f)
            mixed.toArgb().toLong() and 0xFFFFFFFFL
        }
        val background = invert(colors.background)
        val surface = invert(colors.surface)
        return colors.copy(
            background = background,
            surface = surface,
            text = 0xFF11130FL,
            textSecondary = 0xFF4A4F47L,
            outline = 0xFF9AA096L,
        )
    }

    private fun pretty(element: JsonObject): String = GsonBuilder().setPrettyPrinting().create().toJson(element)

    private fun schemeJson(scheme: ColorScheme): JsonObject =
        JsonObject().apply {
            addProperty("primary", scheme.primary.toHex())
            addProperty("onPrimary", scheme.onPrimary.toHex())
            addProperty("primaryContainer", scheme.primaryContainer.toHex())
            addProperty("onPrimaryContainer", scheme.onPrimaryContainer.toHex())
            addProperty("secondary", scheme.secondary.toHex())
            addProperty("onSecondary", scheme.onSecondary.toHex())
            addProperty("secondaryContainer", scheme.secondaryContainer.toHex())
            addProperty("onSecondaryContainer", scheme.onSecondaryContainer.toHex())
            addProperty("tertiary", scheme.tertiary.toHex())
            addProperty("onTertiary", scheme.onTertiary.toHex())
            addProperty("tertiaryContainer", scheme.tertiaryContainer.toHex())
            addProperty("onTertiaryContainer", scheme.onTertiaryContainer.toHex())
            addProperty("error", scheme.error.toHex())
            addProperty("onError", scheme.onError.toHex())
            addProperty("errorContainer", scheme.errorContainer.toHex())
            addProperty("onErrorContainer", scheme.onErrorContainer.toHex())
            addProperty("background", scheme.background.toHex())
            addProperty("onBackground", scheme.onBackground.toHex())
            addProperty("surface", scheme.surface.toHex())
            addProperty("onSurface", scheme.onSurface.toHex())
            addProperty("surfaceVariant", scheme.surfaceVariant.toHex())
            addProperty("onSurfaceVariant", scheme.onSurfaceVariant.toHex())
            addProperty("outline", scheme.outline.toHex())
            addProperty("outlineVariant", scheme.outlineVariant.toHex())
            addProperty("inverseSurface", scheme.inverseSurface.toHex())
            addProperty("inverseOnSurface", scheme.inverseOnSurface.toHex())
            addProperty("inversePrimary", scheme.inversePrimary.toHex())
            addProperty("surfaceTint", scheme.surfaceTint.toHex())
            addProperty("scrim", scheme.scrim.toHex())
        }

    private fun terminalJson(props: Properties): JsonObject =
        JsonObject().apply {
            props.entries.forEach { entry -> addProperty(entry.key.toString(), entry.value.toString()) }
        }

    private fun themeJson(light: MahdiColors, dark: MahdiColors): JsonObject =
        JsonObject().apply {
            add(
                "light",
                JsonObject().apply {
                    add("baseColors", schemeJson(light.asLightColorScheme()))
                    add("terminalColors", terminalJson(light.terminalLightPalette()))
                    add("editorColors", JsonObject())
                    add("tokenColors", JsonArray())
                },
            )
            add(
                "dark",
                JsonObject().apply {
                    add("baseColors", schemeJson(dark.asDarkColorScheme()))
                    add("terminalColors", terminalJson(dark.terminalDarkPalette()))
                    add("editorColors", JsonObject())
                    add("tokenColors", JsonArray())
                },
            )
            addProperty("copyright", COPYRIGHT)
        }

    private fun licenseText(): String =
        buildString {
            appendLine("MIT License")
            appendLine()
            appendLine("Copyright (c) 2026 $COPYRIGHT")
            appendLine()
            appendLine("Permission is hereby granted, free of charge, to any person obtaining a copy")
            appendLine("of this software and associated documentation files (the \"Software\"), to deal")
            appendLine("in the Software without restriction, including without limitation the rights")
            appendLine("to use, copy, modify, merge, publish, distribute, sublicense, and/or sell")
            appendLine("copies of the Software, and to permit persons to whom the Software is")
            appendLine("furnished to do so, subject to the following conditions:")
            appendLine()
            appendLine("The above copyright notice and this permission notice shall be included in all")
            appendLine("copies or substantial portions of the Software.")
            appendLine()
            appendLine("THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR")
            appendLine("IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,")
            appendLine("FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE")
            appendLine("AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER")
            appendLine("LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,")
            appendLine("OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE")
            appendLine("SOFTWARE.")
        }

    private fun readme(kindAr: String, kindEn: String, id: String, name: String): String =
        buildString {
            appendLine("# $name")
            appendLine()
            appendLine("نوع الحزمة: $kindAr ($kindEn)")
            appendLine("المعرّف: `$id`")
            appendLine("الترخيص: $LICENSE")
            appendLine("الحقوق: $COPYRIGHT")
            appendLine()
            appendLine("## البنية")
            appendLine()
            appendLine("```")
            appendLine("manifest.json   — بيان الحزمة (id, name, version, type, author)")
            appendLine("theme.json      — لوحة فاتحة ولوحة داكنة (لحزم الثيمات)")
            appendLine("icons/          — ملفات SVG (لحزم الأيقونات)")
            appendLine("src/            — كود المصدر (للإضافات)")
            appendLine("```")
            appendLine()
            appendLine("## التثبيت")
            appendLine()
            appendLine("افتح الملف داخل Mahdi-Editor من مدير الملفات، أو من «تصفح الإضافات ← استوديو الحزم».")
            appendLine()
            appendLine("---")
            appendLine(COPYRIGHT)
        }

    private fun svgFile(): String =
        """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#7CC0FF" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/></svg>"""

    private fun svgFolder(open: Boolean = false): String =
        if (open) {
            """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#7CC0FF" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8V6a2 2 0 0 1 2-2h4l2 3h6a2 2 0 0 1 2 2v1"/><path d="M3 8h18l-2 10H5z"/></svg>"""
        } else {
            """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#7CC0FF" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7a2 2 0 0 1 2-2h4l2 3h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>"""
        }

    private fun extensionSource(packageName: String, className: String): String =
        buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import androidx.annotation.Keep")
            appendLine("import com.mahdi.extension.ExtensionAPI")
            appendLine("import com.mahdi.extension.ExtensionContext")
            appendLine()
            appendLine("/**")
            appendLine(" * إضافة Mahdi-Editor-Code — مولَّدة من استوديو الحزم.")
            appendLine(" *")
            appendLine(" * Copyright (c) 2026 $COPYRIGHT")
            appendLine(" */")
            appendLine("@Keep")
            appendLine("class $className(context: ExtensionContext) : ExtensionAPI(context) {")
            appendLine()
            appendLine("    override fun onLoad() {")
            appendLine("        context.logInfo(\"Mahdi extension loaded\")")
            appendLine("    }")
            appendLine()
            appendLine("    override fun onDispose() {")
            appendLine("        // أوقف كل المهام هنا")
            appendLine("    }")
            appendLine("}")
        }

    private fun extensionGradle(packageName: String): String =
        buildString {
            appendLine("// قالب إضافة Mahdi-Editor-Code — $COPYRIGHT")
            appendLine("plugins {")
            appendLine("    id(\"com.android.library\")")
            appendLine("    id(\"org.jetbrains.kotlin.android\")")
            appendLine("}")
            appendLine()
            appendLine("android {")
            appendLine("    namespace = \"$packageName\"")
            appendLine("    compileSdk = 37")
            appendLine()
            appendLine("    defaultConfig { minSdk = 26 }")
            appendLine("    compileOptions {")
            appendLine("        sourceCompatibility = JavaVersion.VERSION_21")
            appendLine("        targetCompatibility = JavaVersion.VERSION_21")
            appendLine("    }")
            appendLine("}")
            appendLine()
            appendLine("dependencies {")
            appendLine("    compileOnly(project(\":core:main\"))")
            appendLine("    compileOnly(\"androidx.annotation:annotation:1.9.1\")")
            appendLine("}")
        }

    private fun writeZip(dest: File, entries: List<Pair<String, ByteArray>>): File {
        dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                val entry = ZipEntry(name)
                entry.time = System.currentTimeMillis()
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return dest
    }

    private fun listEntries(pack: File): List<String> =
        ZipFile(pack).use { zip -> zip.entries().toList().map { it.name } }

    private fun readEntry(pack: File, name: String): String? =
        ZipFile(pack).use { zip ->
            val entry = zip.getEntry(name) ?: return null
            zip.getInputStream(entry).use { input: InputStream -> input.readBytes().decodeToString() }
        }
}
