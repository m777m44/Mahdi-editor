package com.mahdi.appicon

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.mahdi.activities.main.MainActivity
import com.mahdi.utils.logError
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * أيقونة مخصّصة من معرض الهاتف (4.0.0).
 *
 * **قيد تقني حقيقي في أندرويد:** لا يمكن استبدال أيقونة التطبيق في الشاشة الرئيسية
 * بصورة عشوائية وقت التشغيل — الأيقونات يجب أن تكون معلنة في البيان (resources)،
 * و`activity-alias` لا يقبل تغيير أيقونته بعد التثبيت. لذلك:
 *
 * 1. تُقصّ الصورة إلى مربّع 512×512 وتُحفظ داخل التطبيق (بلا أي إذن إضافي، لأن القراءة
 *    تتم عبر `content://` من منتقي النظام).
 * 2. تُنشَأ **اختصار طويل الأمد** على الشاشة الرئيسية يفتح التطبيق بأيقونة صورتك
 *    (`ShortcutManagerCompat` + `setLongLived` + `requestPinShortcut`).
 * 3. تبقى الأيقونة الرسمية قابلة للتبديل بين الـ22 أيقونة المدمجة.
 *
 * النتيجة عملية وواضحة للمستخدم: صورتك تظهر كأيقونة حقيقية على الشاشة الرئيسية،
 * والأيقونة الرسمية تُختار من القائمة — بدل وعد لا يستطيع أندرويد الوفاء به.
 */
object CustomIconInstaller {

    private const val SHORTCUT_ID = "mahdi-custom-icon"
    private const val FILE_NAME = "mahdi_custom_icon.png"

    /** يقرأ الصورة من الـ URI، يقصّها مربّعة، ويحفظها، ثم ينشئ الاختصار. */
    suspend fun install(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                    val bitmap =
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            BitmapFactory.decodeStream(input)
                        } ?: return@runCatching false

                    val square = cropToSquare(bitmap, AppIconSwitcher.CUSTOM_ICON_SIZE)

                    val dir = File(context.filesDir, "icons/custom").apply { mkdirs() }
                    val file = File(dir, FILE_NAME)
                    FileOutputStream(file).use { out ->
                        square.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    AppIconSwitcher.rememberGalleryImage(context, file.absolutePath)

                    createShortcut(context, square)
                }
                .onFailure { logError(it, "custom app icon") }
                .getOrDefault(false)
        }

    /** يقصّ من المنتصف إلى مربّع ثم يعيد القياس إلى [size]×[size]. */
    fun cropToSquare(bitmap: Bitmap, size: Int): Bitmap {
        val side = minOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        val cropped = Bitmap.createBitmap(bitmap, x, y, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
        if (scaled !== cropped && !cropped.isRecycled) cropped.recycle()
        return scaled
    }

    /** ينشئ اختصارًا دائمًا على الشاشة الرئيسية بأيقونة الصورة. */
    private fun createShortcut(context: Context, bitmap: Bitmap): Boolean {
        val launchIntent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val shortcut =
            ShortcutInfoCompat
                .Builder(context, SHORTCUT_ID)
                .setShortLabel(shortLabel(context))
                .setLongLabel(shortLabel(context))
                .setIcon(IconCompat.createWithBitmap(bitmap))
                .setIntent(launchIntent)
                .setLongLived(true)
                .build()

        // يُضاف إلى قائمة الضغط المطوّل (والطلب المباشر إن دعمه المشغّل)
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    private fun shortLabel(context: Context): String =
        androidx.core.content.ContextCompat.getString(context, com.mahdi.resources.R.string.custom_icon_shortcut_label)

    /** هل توجد أيقونة مخصّصة محفوظة؟ */
    fun hasCustomIcon(context: Context): Boolean = AppIconSwitcher.galleryImage(context)?.let { File(it).exists() } == true

    /** يحذف الصورة المخصّصة والاختصار. */
    suspend fun remove(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                    AppIconSwitcher.galleryImage(context)?.let { File(it).delete() }
                    ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(SHORTCUT_ID))
                    true
                }
                .getOrDefault(false)
        }
}
