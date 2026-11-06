package com.mahdi.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.mahdi.editor.R
import com.mahdi.utils.logError
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * تبديل أيقونة التطبيق (4.3.0).
 *
 * أندرويد لا يسمح بتغيير أيقونة التطبيق وقت التشغيل مباشرة، بل عبر **activity-alias**
 * لكل أيقونة: كل alias يحمل `icon` خاصًا وفلتر LAUNCHER. التبديل الحقيقي = تمكين
 * الـ alias المختار وتعطيل السابق عبر `PackageManager.setComponentEnabledSetting`.
 *
 * 6.1.0: أُعيد رسم كل الصور — **إحدى عشرة أيقونة كرتونية ثلاثية الأبعاد** بحرف M
 * (روبوت مبرمج، والحرف M المجسّم، وثعلب، وقط، وباندا، وتنين، وبومة، وبطريق،
 * وأرنب، وهمستر، وأكسولوتل). كل صورة أيقونة تكيّفية كاملة: خلفية لونها مستخرج من
 * العمل الفني + صورة أمامية بمقاس 108dp مع منطقة آمنة (66٪) فلا يُقصّ الرسم على
 * أي مشغّل. الافتراضية الآن **الروبوت المبرمج**، والمصغّرة الداخلية تتبعه.
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
            AppIcon("anime_robot", "IconAnimeRobot", "الروبوت المبرمج", "Coder Robot", R.mipmap.ic_launcher_anime_robot, 0xFF0F2148),
            AppIcon("anime_mark", "IconAnimeMark", "الحرف M المجسّم", "Glossy M", R.mipmap.ic_launcher_anime_mark, 0xFF060C1E),
            AppIcon("anime_fox", "IconAnimeFox", "الثعلب المبرمج", "Coder Fox", R.mipmap.ic_launcher_anime_fox, 0xFF15213B),
            AppIcon("anime_cat", "IconAnimeCat", "القط العبقري", "Genius Cat", R.mipmap.ic_launcher_anime_cat, 0xFF0E1947),
            AppIcon("anime_panda", "IconAnimePanda", "الباندا الفضائي", "Space Panda", R.mipmap.ic_launcher_anime_panda, 0xFF214D58),
            AppIcon("anime_dragon", "IconAnimeDragon", "التنين الصغير", "Little Dragon", R.mipmap.ic_launcher_anime_dragon, 0xFF0F1E3B),
            AppIcon("anime_owl", "IconAnimeOwl", "البومة الحكيمة", "Wise Owl", R.mipmap.ic_launcher_anime_owl, 0xFF131E36),
            AppIcon("anime_penguin", "IconAnimePenguin", "البطريق الطرفية", "Terminal Penguin", R.mipmap.ic_launcher_anime_penguin, 0xFF122349),
            AppIcon("anime_bunny", "IconAnimeBunny", "الأرنب الوردي", "Pink Bunny", R.mipmap.ic_launcher_anime_bunny, 0xFF0E1842),
            AppIcon("anime_hamster", "IconAnimeHamster", "الهمستر الذهبي", "Golden Hamster", R.mipmap.ic_launcher_anime_hamster, 0xFF0D1433),
            AppIcon("anime_axolotl", "IconAnimeAxolotl", "الأكسولوتل الأخضر", "Green Axolotl", R.mipmap.ic_launcher_anime_axolotl, 0xFF0E1D41),
        )

    /** الأيقونة الافتراضية (المعلنة `enabled=true` في البيان). */
    const val DEFAULT_ICON_ID = "anime_robot"

    /**
     * الأيقونة المختارة كما تظهر **داخل الواجهة** (5.0.0).
     *
     * `null` = لم يُقرأ بعد؛ تقرأه [com.mahdi.ui.components.AppLogo] من التفضيلات.
     * كل تبديل ناجح يُحدّثه، فيتغيّر الشعار داخل التطبيق فورًا مع أيقونة الشاشة
     * الرئيسية بلا إعادة تشغيل.
     */
    val selectedId = MutableStateFlow<String?>(null)

    /** يُزامن حالة الواجهة مع القيمة المحفوظة (يُستدعى عند الإقلاع). */
    fun syncSelected(context: Context) {
        selectedId.value = currentId(context)
    }

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
     * يطبّق الأيقونة المختارة (6.0.0).
     *
     * ✅ المختار يُفعَّل، وكل البدائل تُعطَّل **في كل نقرة** — لا اعتماد على تفضيل
     * سابق ولا على حالة قد تكون اختلطت. `force` باقٍ للتوافق ولا يغيّر شيئًا الآن.
     *
     * @return `true` إن صارت أيقونة واحدة مفعّلة فعلًا (المختارة).
     */
    fun apply(context: Context, id: String, force: Boolean = false): Boolean {
        val chosen = icons.firstOrNull { it.id == id } ?: return false
        val pm = context.packageManager
        val packageName = context.packageName

        fun setState(icon: AppIcon, enabled: Boolean): Boolean {
            val component = ComponentName(packageName, icon.aliasClassName)
            val state =
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            return runCatching {
                    pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                    true
                }
                .onFailure { logError(it, "app icon alias ${icon.aliasClassName}") }
                .getOrDefault(false)
        }

        // 6.0.0: نفعّل المختار **أولًا**، ثم نعطّل كل البدائل دائمًا.
        //
        // قبل 6.0.0 كان التعطيل يقتصر على «السابق» المحفوظ في التفضيلات. إن اختلطت
        // الحالة (تحديث للتطبيق، أو تبديل سابق فشل في منتصفه، أو ROM أوقف الـ alias)
        // بقيت أيقونتان مفعّلتان — أو لم تتفعّل الجديدة إطلاقًا — فيظنّ المستخدم أن
        // التبديل لا يعمل. الآن الحالة تُعاد بناءً بالكامل في كل نقرة: واحدة مفعّلة
        // والباقي معطّل، ولا اعتماد على تفضيل قديم.
        val enabled = setState(chosen, true)

        icons.forEach { icon ->
            if (icon.id != chosen.id) setState(icon, false)
        }

        // القراءة بعد الكتابة: نتأكّد أن النظام طبّق الفعّل فعلًا قبل إعلان النجاح
        val applied =
            runCatching {
                    pm.getComponentEnabledSetting(ComponentName(packageName, chosen.aliasClassName)) ==
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                }
                .getOrDefault(enabled)

        if (applied || enabled) {
            prefs(context).edit().putString(KEY_CURRENT, chosen.id).apply()
            // 5.0.0: الشعار الداخلي (الشريط العلوي/شاشة البداية) يتبع نفس الاختيار
            selectedId.value = chosen.id
        }
        return applied || enabled
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
