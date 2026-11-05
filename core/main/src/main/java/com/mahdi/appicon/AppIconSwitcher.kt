package com.mahdi.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.mahdi.editor.R
import com.mahdi.utils.logError

/**
 * تبديل أيقونة التطبيق (4.3.0).
 *
 * أندرويد لا يسمح بتغيير أيقونة التطبيق وقت التشغيل مباشرة، بل عبر **activity-alias**
 * لكل أيقونة: كل alias يحمل `icon` خاصًا وفلتر LAUNCHER. التبديل الحقيقي = تمكين
 * الـ alias المختار وتعطيل السابق عبر `PackageManager.setComponentEnabledSetting`.
 *
 * في 4.3.0 صارت الأيقونات **عشر صور أنمي ثلاثية الأبعاد** بحرف M مرسومة خصيصًا
 * (ثعلب، قط، روبوت، بومة، بطريق، تنين، أرنب، باندا، أكسولوتل، وهمستر) بدل الرموز
 * المتجهة الـ126. كل صورة أيقونة تكيّفية كاملة: خلفية + صورة أمامية بمقاس 108dp
 * مع منطقة آمنة 74dp فلا يُقصّ الرسم ولا حرف M على أي مشغّل.
 *
 * القائمة هنا مطابقة تمامًا لما هو معلن في `AndroidManifest.xml`. أي إضافة أيقونة
 * تحتاج: صورة في `mipmap-*` + صورة أمامية `_fg` + adaptive-icon.xml + alias في
 * البيان + سطرًا هنا.
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
        /** مورد الصورة الحقيقية للمعاينة (مطابق للأيقونة على الشاشة الرئيسية). */
        val previewRes: Int,
        /** لون الخلفية المستخرج من الصورة — يُستخدم كحشوة خلف المعاينة. */
        val backgroundColor: Long,
    ) {
        /** الاسم الكامل للمكوّن كما هو في البيان. */
        val aliasClassName: String get() = PACKAGE_PREFIX + aliasSimpleName

        fun displayName(isArabic: Boolean): String = if (isArabic) nameAr else nameEn
    }

    /** الأيقونات — ترتيبها هو ترتيب العرض. */
    val icons: List<AppIcon> =
        listOf(
            AppIcon("anime_fox", "IconAnimeFox", "الثعلب المبرمج", "Coder Fox", R.mipmap.ic_launcher_anime_fox, 0xFF030E2F),
            AppIcon("anime_cat", "IconAnimeCat", "القط العبقري", "Genius Cat", R.mipmap.ic_launcher_anime_cat, 0xFF1C073F),
            AppIcon("anime_robot", "IconAnimeRobot", "الروبوت النشيط", "Busy Robot", R.mipmap.ic_launcher_anime_robot, 0xFF000A0D),
            AppIcon("anime_owl", "IconAnimeOwl", "البومة الحكيمة", "Wise Owl", R.mipmap.ic_launcher_anime_owl, 0xFF170D0A),
            AppIcon("anime_penguin", "IconAnimePenguin", "البطريق الطرفية", "Terminal Penguin", R.mipmap.ic_launcher_anime_penguin, 0xFF1D466E),
            AppIcon("anime_dragon", "IconAnimeDragon", "التنين الصغير", "Little Dragon", R.mipmap.ic_launcher_anime_dragon, 0xFF040F19),
            AppIcon("anime_bunny", "IconAnimeBunny", "الأرنب الوردي", "Pink Bunny", R.mipmap.ic_launcher_anime_bunny, 0xFF1E0E12),
            AppIcon("anime_panda", "IconAnimePanda", "الباندا الفضائي", "Space Panda", R.mipmap.ic_launcher_anime_panda, 0xFF04091C),
            AppIcon("anime_axolotl", "IconAnimeAxolotl", "الأكسولوتل الأخضر", "Green Axolotl", R.mipmap.ic_launcher_anime_axolotl, 0xFF010C01),
            AppIcon("anime_hamster", "IconAnimeHamster", "الهمستر الذهبي", "Golden Hamster", R.mipmap.ic_launcher_anime_hamster, 0xFF130403),
        )

    /** الأيقونة الافتراضية (المعلنة `enabled=true` في البيان). */
    const val DEFAULT_ICON_ID = "anime_fox"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** معرّف الأيقونة المختارة حاليًا. */
    fun currentId(context: Context): String =
        prefs(context)
            .getString(KEY_CURRENT, DEFAULT_ICON_ID)
            ?.takeIf { id -> icons.any { it.id == id } }
            ?: DEFAULT_ICON_ID

    /** الأيقونة المختارة حاليًا ككائن كامل. */
    fun current(context: Context): AppIcon =
        icons.firstOrNull { it.id == currentId(context) } ?: icons.first()

    /**
     * يطبّق الأيقونة المختارة.
     *
     * التبديل الذكي: نفعّل alias المختار **ونعطّل السابق فقط** بدل مسح كل المكوّنات
     * في كل تبديل. المسح الشامل يحدث فقط عند `force = true` (بعد تحديث التطبيق أو
     * عند اختلال الحالة).
     *
     * @return `true` إن نجح تفعيل الأيقونة المختارة.
     */
    fun apply(context: Context, id: String, force: Boolean = false): Boolean {
        val chosen = icons.firstOrNull { it.id == id } ?: return false
        val pm = context.packageManager
        val packageName = context.packageName
        // عند أول تبديل لا يوجد معرّف محفوظ، والمعلَن افتراضيًا هو الافتراضية المفعّلة
        // في البيان — نعتبره «السابق» وإلا بقيت أيقونتان على الشاشة الرئيسية.
        val previousId = prefs(context).getString(KEY_CURRENT, null) ?: DEFAULT_ICON_ID
        var ok = true

        fun setState(icon: AppIcon, enabled: Boolean) {
            val component = ComponentName(packageName, icon.aliasClassName)
            val state =
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            runCatching { pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
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
     * يُصلح الحالة عند الإقلاع — **بلا أي كتابة على الخيط الرئيسي**.
     *
     * تاريخ هذا الإصلاح (4.3.1): في 4.3.0 كانت هذه الدالة تُستدعى من
     * `App.onCreate` على الخيط الرئيسي، وبعد تغيير معرّفات الأيقونات صارت تأخذ
     * مسار `force` في كل إقلاع أول بعد التحديث = **عشر عمليات كتابة في
     * PackageManager** متزامنة. على أجهزة MIUI/HyperOS هذه العمليات بطيئة
     * وتأخذ قفل الحزم، فيتجاوز الإقلاع مهلة ANR فيقتل النظام التطبيق — وهو ما
     * يراه المستخدم «يفتح التطبيق ثم يخرج وحده».
     *
     * الآن: قراءة واحدة، ولا نلمس شيئًا إن كانت الأيقونة مفعّلة أصلًا، ولا
     * نستدعي `force` أبدًا هنا. الاستدعاء نفسه انتقل إلى خيط IO في [App].
     */
    fun restore(context: Context) {
        val chosen = current(context)
        val pm = context.packageManager
        val component = ComponentName(context.packageName, chosen.aliasClassName)

        val state =
            runCatching { pm.getComponentEnabledSetting(component) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

        val alreadyActive =
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                // الافتراضية معلنة enabled=true في البيان، فحالة DEFAULT تعني مفعّلة
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && chosen.id == DEFAULT_ICON_ID)

        if (alreadyActive) return

        runCatching { apply(context, chosen.id, force = false) }
            .onFailure { logError(it, "Failed to restore app icon") }
    }

    /** اسم الأيقونة العربية/الإنجليزية للعرض. */
    fun nameOf(id: String, isArabic: Boolean): String =
        icons.firstOrNull { it.id == id }?.displayName(isArabic) ?: id

    /** هل المستخدم غيّر الأيقونة عن الافتراضية؟ */
    fun isCustomized(context: Context): Boolean = currentId(context) != DEFAULT_ICON_ID
}
