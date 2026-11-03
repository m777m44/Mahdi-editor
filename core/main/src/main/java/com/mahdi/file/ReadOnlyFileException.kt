package com.mahdi.file

import java.io.FileNotFoundException
import java.io.IOException

/**
 * يُرمى حين يكون الملف أو المستند **للقراءة فقط** (4.1.0).
 *
 * هذا هو سبب رسالة `Media is read-only` الشهيرة: أندرويد 11+ يعطي التطبيق إذن **قراءة**
 * فقط للمستندات التي فُتحت من تطبيق آخر أو من `/Android/data`، فينجح `openInputStream`
 * ويفشل `openOutputStream`، ويظهر الاستثناء الخام بلا حلّ واضح للمستخدم.
 *
 * بوجود هذا النوع يستطيع المحرّر أن يتصرّف بذكاء بدل عرض رسالة خطأ: يفتح «حفظ باسم»
 * مباشرة ويحوّل التبويب إلى الملف الجديد، فلا يفقد المستخدم نصّه أبدًا.
 */
class ReadOnlyFileException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** هل فشل هذا الاستثناء بسبب كون المستند للقراءة فقط؟ */
fun Throwable.isReadOnlyDocument(): Boolean {
    val text = (message ?: "") + " " + (cause?.message ?: "")
    val lower = text.lowercase()
    return this is ReadOnlyFileException ||
        lower.contains("read-only") ||
        lower.contains("readonly") ||
        lower.contains("read only") ||
        lower.contains("media is read-only") ||
        lower.contains("eacces") ||
        lower.contains("operation not permitted") ||
        lower.contains("permission denial") ||
        (this is SecurityException && !lower.contains("permission denial of open"))
}

/** استثناء موحّد لعمليات الكتابة التي رفضها المزوّد. */
internal fun Throwable.asWriteFailure(uri: String): Throwable =
    when {
        this is ReadOnlyFileException -> this
        this is FileNotFoundException && isReadOnlyDocument() ->
            ReadOnlyFileException("Document is read-only: $uri", this)
        isReadOnlyDocument() ->
            ReadOnlyFileException(
                "الملف للقراءة فقط (read-only document): $uri",
                this,
            )
        else -> this
    }
