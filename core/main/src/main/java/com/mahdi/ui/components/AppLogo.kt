package com.mahdi.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mahdi.appicon.AppIconSwitcher
import com.mahdi.ui.graphics.rememberIconImage

/**
 * شعار التطبيق الداخلي (5.0.0) — يعرض **نفس الأيقونة** التي اختارها المستخدم
 * للشاشة الرئيسية، لا أيقونة ثابتة.
 *
 * يُستخدم في الشريط العلوي للإعدادات، وشاشة مركز التحديثات، وشاشة الأيقونات.
 *
 * لماذا `Image(bitmap = …)` لا `painterResource`؟ لأن أيقوناتنا `adaptive-icon`
 * في `mipmap-anydpi-v26` و`painterResource` يرفض هذا النوع ويُسقط التطبيق —
 * انظر [com.mahdi.ui.graphics.rememberIconImage].
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    cornerRadius: Dp = size / 4,
    showBackground: Boolean = true,
) {
    val context = LocalContext.current
    val selected by AppIconSwitcher.selectedId.collectAsState()

    // القيمة المبدئية من التفضيلات: تظهر صحيحة حتى قبل أي تبديل في هذه الجلسة
    val initialId = remember { AppIconSwitcher.currentId(context) }
    val currentId = selected ?: initialId

    val icon =
        remember(currentId) {
            AppIconSwitcher.icons.firstOrNull { it.id == currentId } ?: AppIconSwitcher.icons.first()
        }

    // نطلب من الأيقونة بعدد البكسلات الفعلي على الشاشة (لا 512 دائمًا) حتى لا
    // نستهلك ميغابايت لكل شعار صغير في الشريط العلوي.
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceIn(48, 768)
    val bitmap = rememberIconImage(icon.previewRes, sizePx = sizePx)

    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .then(
                    if (showBackground) {
                        Modifier.background(
                            Brush.linearGradient(
                                listOf(Color(icon.backgroundColor), Color(0xFF0B1220)),
                            ),
                        )
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = icon.displayName(isArabic = true),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
