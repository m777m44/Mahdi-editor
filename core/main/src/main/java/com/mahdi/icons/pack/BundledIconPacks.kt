package com.mahdi.icons.pack

import android.content.Context
import com.mahdi.file.localDir
import com.mahdi.utils.logError
import java.io.File

/**
 * حزم الأيقونات المضمّنة (4.2.0).
 *
 * ثلاث حزم كاملة تعمل **بلا إنترنت وبلا متجر**: خطّية، ثنائية، ونيون. كل حزمة تحمل
 * أيقونة لكل امتداد معروف (104 امتدادات) + مجلدات وملفات افتراضية — 111 أيقونة SVG
 * للحزمة، 333 أيقونة في التطبيق.
 *
 * تُنسخ من `assets/iconpacks` إلى `icon_pack/` عند أول تشغيل وعند كل تحديث (يتغيّر
 * [ASSET_VERSION])، ثم يلتقطها [IconPackManager.indexLocalPacks] كحزم محلية عادية —
 * أي أنها تخضع لنفس مسار التطبيق والتحقق. حذف المستخدم لحزمة مضمّنة **لا** يُلغى في
 * كل إقلاع: العلامة مركزيّة، فلا تعود إلا مع تحديث التطبيق أو بزرّ الاستعادة.
 */
object BundledIconPacks {

    /** نسخة الأصول: تغييرها يعيد نسخ الحزم عند التحديث. */
    private const val ASSET_VERSION = "4.2.0"

    /** معرّفات الحزم الثلاث — ترتيبها هو ترتيب العرض. */
    val ids: List<String> = listOf("mahdi_line", "mahdi_duo", "mahdi_neon")

    private const val MARKER = ".bundled_version"

    private fun packRoot(context: Context): File = File(localDir(context), "icon_pack")

    private fun markerFile(context: Context): File = File(packRoot(context), MARKER)

    /** هل الأصول منسوخة وحديثة؟ */
    fun isInstalled(context: Context): Boolean =
        markerFile(context).let { it.exists() && runCatching { it.readText() }.getOrNull() == ASSET_VERSION }

    /**
     * ينسخ الحزم المضمّنة إن لزم. آمن للاستدعاء المتكرّر.
     *
     * @param force تجاهل العلامة وإعادة النسخ (زرّ الاستعادة).
     * @return عدد الحزم التي نُسخت الآن.
     */
    fun ensureInstalled(context: Context, force: Boolean = false): Int {
        if (!force && isInstalled(context)) return 0
        var copied = 0
        val root = packRoot(context)
        root.mkdirs()
        ids.forEach { id ->
            runCatching {
                    val target = File(root, id)
                    target.mkdirs()
                    copyAssetDir(context, "iconpacks/$id", target)
                    copied++
                }
                .onFailure { logError(it, "bundled icon pack $id") }
        }
        if (copied == ids.size) runCatching { markerFile(context).writeText(ASSET_VERSION) }
        return copied
    }

    private fun copyAssetDir(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            // ملف ورق
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        target.mkdirs()
        children.forEach { child -> copyAssetDir(context, "$assetPath/$child", File(target, child)) }
    }
}
