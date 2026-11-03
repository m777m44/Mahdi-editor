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
 * القائمة هنا مطابقة تمامًا لما هو معلن في `AndroidManifest.xml` (22 أيقونة). أي
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

    /** الأيقونات الـ22 — الترتيب هو نفسه ترتيب العرض في الشاشة. */
    val icons: List<AppIcon> =
        listOf(
            AppIcon("default", "IconDefault", "الافتراضية (ذهبية)", "Default (gold)", 0xFF12100C, 0xFFE3B341, "ic_launcher_default_foreground"),
            AppIcon("gold", "IconGold", "ذهبية فاتحة", "Light gold", 0xFF1C1710, 0xFFF5C451, "ic_launcher_gold_foreground"),
            AppIcon("blue", "IconBlue", "أزرق", "Blue", 0xFF0B2545, 0xFF4FA8FF, "ic_launcher_blue_foreground"),
            AppIcon("green", "IconGreen", "أخضر طرفية", "Terminal green", 0xFF0E2A1E, 0xFF3DDC84, "ic_launcher_green_foreground"),
            AppIcon("purple", "IconPurple", "بنفسجي", "Purple", 0xFF251238, 0xFFC084FC, "ic_launcher_purple_foreground"),
            AppIcon("dark", "IconDark", "أسود مطفأ", "Matte black", 0xFF0A0A0A, 0xFFE5E5E5, "ic_launcher_dark_foreground"),
            AppIcon("glass", "IconGlass", "زجاجي", "Glass", 0xFFDCE7F2, 0xFF2B4C6F, "ic_launcher_glass_foreground"),
            AppIcon("neon", "IconNeon", "نيون", "Neon", 0xFF0A0F1E, 0xFF39FF14, "ic_launcher_neon_foreground"),
            AppIcon("fire", "IconFire", "ناري", "Fire", 0xFF2A0A02, 0xFFFF7A18, "ic_launcher_fire_foreground"),
            AppIcon("ocean", "IconOcean", "محيط", "Ocean", 0xFF03202E, 0xFF22D3EE, "ic_launcher_ocean_foreground"),
            AppIcon("forest", "IconForest", "غابة", "Forest", 0xFF0B2A16, 0xFF86EFAC, "ic_launcher_forest_foreground"),
            AppIcon("sky", "IconSky", "سماوي", "Sky", 0xFF0B1E33, 0xFF38BDF8, "ic_launcher_sky_foreground"),
            AppIcon("rose", "IconRose", "وردي", "Rose", 0xFF2A0A18, 0xFFFB7185, "ic_launcher_rose_foreground"),
            AppIcon("mint", "IconMint", "نعناعي", "Mint", 0xFF062421, 0xFF5EEAD4, "ic_launcher_mint_foreground"),
            AppIcon("sunset", "IconSunset", "غروب", "Sunset", 0xFF2B1206, 0xFFFDBA74, "ic_launcher_sunset_foreground"),
            AppIcon("midnight", "IconMidnight", "منتصف الليل", "Midnight", 0xFF070B14, 0xFF818CF8, "ic_launcher_midnight_foreground"),
            AppIcon("royal", "IconRoyal", "ملكي", "Royal", 0xFF1A0B2E, 0xFFA78BFA, "ic_launcher_royal_foreground"),
            AppIcon("cyberpunk", "IconCyberpunk", "سايبربانك", "Cyberpunk", 0xFF17002B, 0xFFF0ABFC, "ic_launcher_cyberpunk_foreground"),
            AppIcon("retro", "IconRetro", "ريترو", "Retro", 0xFF2B2118, 0xFFEAC086, "ic_launcher_retro_foreground"),
            AppIcon("minimal", "IconMinimal", "بسيطة", "Minimal", 0xFFF5F5F5, 0xFF121212, "ic_launcher_minimal_foreground"),
            AppIcon("ember", "IconEmber", "جمر", "Ember", 0xFF1F0D02, 0xFFFB923C, "ic_launcher_ember_foreground"),
            AppIcon("arctic", "IconArctic", "قطبية", "Arctic", 0xFFE8F4FF, 0xFF0F4C81, "ic_launcher_arctic_foreground"),
        )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** معرّف الأيقونة المختارة حاليًا. */
    fun currentId(context: Context): String =
        prefs(context).getString(KEY_CURRENT, "default")?.takeIf { id -> icons.any { it.id == id } } ?: "default"

    /**
     * يطبّق الأيقونة: يفعّل aliasها ويعطّل البقية، ويحفظ الاختيار.
     *
     * @return `true` إن نجح التبديل الفعلي.
     */
    fun apply(context: Context, id: String): Boolean {
        val chosen = icons.firstOrNull { it.id == id } ?: return false
        val pm = context.packageManager
        val packageName = context.packageName
        var ok = true

        icons.forEach { icon ->
            val component = ComponentName(packageName, icon.aliasClassName)
            val state =
                if (icon.id == chosen.id) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            runCatching {
                    pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                }
                .onFailure {
                    // alias غير معلن (نسخة قديمة من البيان) — لا نُفشل التبديل كله
                    if (icon.id == chosen.id) ok = false
                    logError(it, "app icon alias ${icon.aliasClassName}")
                }
        }

        if (ok) prefs(context).edit().putString(KEY_CURRENT, chosen.id).apply()
        return ok
    }

    /**
     * يُصلح الحالة عند الإقلاع: إن كانت كل الأيقونات معطّلة (أو اختفت أيقونة التطبيق
     * من الشاشة الرئيسية بعد تحديث)، نُعيد تمكين الأيقونة المحفوظة. عملية آمنة ومتكرّرة.
     */
    fun restore(context: Context) {
        val id = currentId(context)
        val pm = context.packageManager
        val packageName = context.packageName
        val chosen = icons.firstOrNull { it.id == id } ?: icons.first()

        val enabledCount =
            icons.count { icon ->
                runCatching {
                        pm.getComponentEnabledSetting(ComponentName(packageName, icon.aliasClassName)) ==
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    }
                    .getOrDefault(false)
            }
        // إن كان المختار مفعّلًا والبقية متوقّفة لا نفعل شيئًا (لا نلمس النظام بلا داعٍ)
        val alreadyCorrect =
            runCatching {
                    pm.getComponentEnabledSetting(ComponentName(packageName, chosen.aliasClassName)) ==
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED && enabledCount == 1
                }
                .getOrDefault(false)
        if (alreadyCorrect) return
        runCatching { apply(context, chosen.id) }
    }

    /** اسم الأيقونة العربية/الإنجليزية للعرض. */
    fun nameOf(id: String, isArabic: Boolean): String =
        icons.firstOrNull { it.id == id }?.displayName(isArabic) ?: id

    /** هل المستخدم غيّر الأيقونة عن الافتراضية؟ */
    fun isCustomized(context: Context): Boolean = currentId(context) != "default"

    /** يخزّن آخر صورة اختارها المستخدم من المعرض (لعرضها في الشاشة). */
    fun rememberGalleryImage(context: Context, path: String) {
        prefs(context).edit().putString(KEY_GALLERY, path).apply()
    }

    fun galleryImage(context: Context): String? = prefs(context).getString(KEY_GALLERY, null)

    private const val KEY_GALLERY = "gallery_icon_path"

    /** الحجم المطلوب لأي صورة أيقونة (بكسل). */
    const val CUSTOM_ICON_SIZE = 512

    /** مجلد أيقونات المستخدم المخصّصة داخل مساحة التطبيق. */
    @Suppress("unused")
    fun customIconDir(context: Context) = java.io.File(context.filesDir, "icons/custom")
}
