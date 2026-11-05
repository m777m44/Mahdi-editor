package com.mahdi.ui.graphics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * تحويل أي `Drawable` إلى `Bitmap` مربّع جاهز للعرض (5.0.0).
 *
 * لماذا وُجد هذا الملف؟ لأن `painterResource` في Compose يدعم **VectorDrawable**
 * و**الصور النقطية** فقط. أيقونات التطبيق عندنا معلنة في `mipmap-anydpi-v26`
 * كـ `adaptive-icon` (طبقة خلفية + طبقة أمامية)، وهذا النوع **ليس** VectorDrawable
 * ولا صورة نقطية، فيرمي:
 *
 * `IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported`
 *
 * الحل: نقرأ الـ Drawable من الـ PackageManager (يفهم الأيقونات التكيّفية) ثم نرسمه
 * على Canvas بأنفسنا ونحوّله إلى `ImageBitmap` يُعرض بـ `Image(bitmap = …)`.
 */
fun Drawable.toSquareBitmap(sizePx: Int): Bitmap {
    val size = sizePx.coerceAtLeast(1)

    if (this is BitmapDrawable) {
        val source = bitmap
        if (source != null && !source.isRecycled && source.width == size && source.height == size) {
            return source
        }
    }

    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val previousBounds = bounds
    setBounds(0, 0, size, size)
    draw(canvas)
    bounds = previousBounds

    return output
}

/**
 * يقرأ أيقونة من الموارد ويُعيدها جاهزة للعرض، أو `null` إن تعذّر ذلك.
 *
 * كل الفشل يُلتقط هنا: شاشة الأيقونات **لا يجوز** أن تُسقط التطبيق بسبب أيقونة
 * واحدة تالفة على جهاز معيّن.
 */
@Composable
fun rememberIconImage(
    @DrawableRes resId: Int,
    sizePx: Int = 256,
): ImageBitmap? {
    val context = LocalContext.current
    return remember(resId, sizePx) {
        runCatching {
            val drawable = ContextCompat.getDrawable(context, resId) ?: return@runCatching null
            drawable.toSquareBitmap(sizePx).asImageBitmap()
        }.getOrNull()
    }
}
