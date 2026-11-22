package com.mahdi.icons

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder

@Composable
fun MahdiIcon(
    icon: Icon,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
) {
    when (icon) {
        is Icon.ResourceIcon -> {
            Icon(
                painter = painterResource(icon.drawableRes),
                contentDescription = contentDescription,
                modifier = modifier,
                tint = tint,
            )
        }

        is Icon.ExternalResourceIcon -> {
            val theme = LocalContext.current.theme

            Icon(
                imageVector = ImageVector.vectorResource(theme = theme, resId = icon.drawableRes, res = icon.resources),
                contentDescription = contentDescription,
                modifier = modifier,
                tint = tint,
            )
        }

        is Icon.VectorIcon -> {
            Icon(imageVector = icon.vector, contentDescription = contentDescription, modifier = modifier, tint = tint)
        }

        is Icon.SvgIcon -> {
            AsyncImage(
                model = icon.file,
                imageLoader = rememberSvgImageLoader(),
                contentDescription = contentDescription,
                modifier = modifier,
                colorFilter = ColorFilter.tint(tint),
            )
        }

        is Icon.TextIcon -> {
            val textSize =
                when (icon.text.length) {
                    in 1..2 -> 14.sp
                    in 3..5 -> 12.sp
                    else -> 10.sp
                }

            Text(text = icon.text, fontFamily = FontFamily.Monospace, color = tint, fontSize = textSize, maxLines = 1)
        }
    }
}

/**
 * 9.9.0 — مُحمِّل SVG واحد مشترك، بدل مُحمِّل جديد لكل أيقونة.
 *
 * كان كل استدعاء يبني `ImageLoader` كاملًا (بلا ذاكرة ولا قرص)، وصفوف الشجرة
 * تنادي دالّة الأيقونة لكل صفّ ولكل ملفّ فرعي. النتيجة: فكّ SVG من جديد عشرات
 * المرّات في الثانية، ونبضات في المعالجة تظهر كأيقونات مبتورة أو متكرّرة.
 * هنا يُبنى مرّة واحدة لكل عملية، بذاكرة ٢٥٪ من الذاكرة المتاحة (سقف ٥٠ميغا)
 * وقرص معطّل — الأصول داخل التطبيق لا تتغيّر.
 */
private var sharedSvgLoader: ImageLoader? = null

/**
 * 10.0.1 — بصمة حزمة الأيقونات المثبَّتة.
 *
 * Coil يُخزّن الصورة بمفتاح = مسار الملفّ، فلو استُبدل `folder.svg` بمحتوى
 * جديد بقي **الرسم القديم** في الذاكرة يُعرض من جديد. حدث هذا فعلًا: أُصلحت
 * أيقونة المجلّد في 9.9.0 وبقيت المشوّهة تظهر على الجهاز.
 *
 * الحلّ: نُمرّر البصمة كـ`memoryCacheKey`، فيصير المفتاح «مسار + بصمة».
 * تغيّر محتوى الحزمة ⇒ مفتاح جديد ⇒ فكّ جديد، بلا مسح ذاكرة يدوي ولا وميض.
 */
private var iconPackVersion: String = ""

/** تُنادى عند تثبيت/تحديث حزمة أيقونات فتُبطل مفاتيح الذاكرة القديمة. */
fun bumpIconPackVersion(version: String) {
    iconPackVersion = version
}

@Composable
fun rememberSvgImageLoader(): ImageLoader {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        sharedSvgLoader
            ?: ImageLoader
                .Builder(context)
                .components { add(SvgDecoder.Factory()) }
                .memoryCache { coil.memory.MemoryCache.Builder(context).maxSizePercent(0.25).build() }
                .crossfade(80)
                .build()
                .also { sharedSvgLoader = it }
    }
}

/**
 * 10.0.1 — مفتاح ذاكرة Coil لأيقونة ملفّ.
 *
 * المسار وحده لا يكفي: الملفّ قد يُستبدل بمحتوى مصحَّح فيبقى الرسم القديم.
 * ضمّ بصمة الحزمة إلى المفتاح يجعل التصحيح يظهر فورًا.
 */
fun iconMemoryCacheKey(file: java.io.File): String = "${file.absolutePath}#$iconPackVersion"
