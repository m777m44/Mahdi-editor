package com.mahdi.resources

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.DelicateCoroutinesApi

typealias drawables = R.drawable

typealias strings = R.string

typealias plurals = R.plurals

@OptIn(DelicateCoroutinesApi::class)
object Res {
    @JvmField var application: Application? = null

    /**
     * سياق مُهيّأ على لغة التطبيق المختارة.
     *
     * `AppCompatDelegate.setApplicationLocales` يُحدّث سياق الأنشطة، لكن `application.resources`
     * قد يبقى على لغة النظام، فتظهر بعض الرسائل (مثل نصوص النوافذ المنبثقة) بلغة مختلفة عن
     * بقية الواجهة. هذا السياق يضمن أن كل نص خارج Compose يتبع اللغة المختارة أيضًا.
     */
    private var localizedContext: Context? = null

    /** يُستدعى عند بدء التطبيق وعند تغيير اللغة. */
    fun updateLocale(locale: Locale) {
        val app = application ?: return
        localizedContext =
            runCatching {
                val config = Configuration(app.resources.configuration)
                config.setLocale(locale)
                app.createConfigurationContext(config)
            }
                .getOrNull()
    }

    /** السياق الذي تُقرأ منه النصوص: لغة التطبيق أولًا ثم لغة النظام. */
    fun context(): Context = localizedContext ?: application!!
}

inline fun Int.getString(context: Context = Res.context()): String {
    return ContextCompat.getString(context, this)
}

inline fun Int.getFilledString(vararg args: Any?, context: Context = Res.context()): String {
    return this.getString(context).fillPlaceholders(*args)
}

inline fun String.fillPlaceholders(vararg args: Any?): String {
    return String.format(this, *args)
}

inline fun Int.getDrawable(context: Context = Res.context()): Drawable? {
    return ContextCompat.getDrawable(context, this)
}

inline fun Int.getQuantityString(quantity: Int, vararg formatArgs: Any?, context: Context = Res.context()): String {
    return context.resources.getQuantityString(this, quantity, *formatArgs)
}
