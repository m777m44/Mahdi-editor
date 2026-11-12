package com.mahdi.editor.status

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * «وضع الشاشة الكاملة» أثناء الكتابة (9.0.0).
 *
 * يخفي شريط الحالة وشريط التنقّل فيبقى للمحرّر كل ارتفاع الشاشة — كما في بيئات
 * التطوير الاحترافية. الأشرطة **لا تُفقد**: سحب واحد من أعلى أو أسفل الشاشة
 * يُظهرها مؤقّتًا (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`)، وزرّا الرجوع
 * والرئيسية في شريط المحرّر يبقيان ظاهرين دائمًا.
 *
 * مطفأ افتراضيًا من «الإعدادات ‹ المحرّر ‹ شاشة كاملة أثناء الكتابة»، ويُعاد
 * إظهار الأشرطة تلقائيًا عند مغادرة المحرّر أو إغلاق التطبيق — بلا «شاشة بلا
 * مخرج» إن تعطّل أي شيء.
 */
@Composable
fun ImmersiveEditingEffect(enabled: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(enabled, activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }

        if (enabled && controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            // العودة من المحرّر (أو تبديل التبويب) تُعيد الأشرطة فورًا
            if (enabled) {
                runCatching { controller?.show(WindowInsetsCompat.Type.systemBars()) }
            }
        }
    }
}
