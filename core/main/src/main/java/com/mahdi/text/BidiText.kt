package com.mahdi.text

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.FontWeight

/**
 * معالجة الأرقام في السياق العربي (3.0.0).
 *
 * خوارزمية الاتجاه الثنائي في Unicode (UBA) تعامل الأرقام كسلسلة **من اليسار إلى اليمين**
 * داخل جملة عربية، فإذا التصقت بمقطع عربي أو بعلامات متّصلة (`%`، `:`، `-`) التبست
 * حدودها وظهرت معكوسة: `100` ← `001`، و`42%` ← `%42`.
 *
 * الحل المعياري هو **عزل** الرقم بين علامتي:
 * - `U+2066` LRI — Left-to-Right Isolate (بداية العزل)
 * - `U+2069` PDI — Pop Directional Isolate (نهاية العزل)
 *
 * العزل علامات تحكّم غير مرئية ولا تُخزَّن في القيمة المنطقية للنص، فيبقى النص
 * المخزَّن نظيفًا وتُضاف العلامات عند العرض فقط.
 */
object BidiText {

    /** بداية عزل من اليسار إلى اليمين. */
    const val LRI = '\u2066'

    /** نهاية العزل. */
    const val PDI = '\u2069'

    /** علامات التحكّم التي تُضاف وتُزال — تُرشَّح من أي نص مُدخل. */
    private val CONTROL_CHARS = charArrayOf(LRI, PDI, '\u2067', '\u2068', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E')

    private val DIGIT_RUN = Regex("[0-9]+")

    /** يزيل كل علامات التحكّم من نص (قبل التخزين أو عند لصق نص مُلوَّث). */
    fun stripControls(input: String): String {
        if (input.none { it in CONTROL_CHARS }) return input
        return input.filterNot { it in CONTROL_CHARS }
    }

    /**
     * يعزل كل سلسلة أرقام بين LRI…PDI.
     *
     * العملية تكرارية الأمان (idempotent): تُزال العلامات القديمة أولًا ثم تُضاف من جديد،
     * فلا تتراكم العلامات إن نُودِيت الدالة أكثر من مرة على النص نفسه.
     */
    fun isolateNumbers(input: String): String {
        val clean = stripControls(input)
        if (clean.none { it.isDigit() }) return clean
        return DIGIT_RUN.replace(clean) { match -> "$LRI${match.value}$PDI" }
    }

    /** هل النص يحتوي حروفًا عربية؟ */
    fun hasArabic(input: String): Boolean = input.any { it in '\u0600'..'\u06FF' }

    /**
     * يجهّز نصًا للعرض: يعزل الأرقام فقط إذا كان النص عربيًا.
     *
     * الأسطر اللاتينية لا تحتاج عزلًا (اتجاهها الأساسي LTR)، فتركها كما هي يقلّل
     * علامات التحكّم بلا فائدة.
     */
    fun display(input: String): String = if (hasArabic(input)) isolateNumbers(input) else input

    /**
     * يبني نصًا معزول الأرقام مع إبقاء الأرقام بخطّ أحادي العرض عند الطلب.
     * يُستخدم لعرض النسب والإصدارات داخل بطاقات عربية.
     */
    fun annotated(input: String, numberStyle: SpanStyle? = null): AnnotatedString =
        buildAnnotatedString {
            val text = display(input)
            append(text)
            if (numberStyle != null) {
                DIGIT_RUN.findAll(text).forEach { match ->
                    addStyle(numberStyle, match.range.first, match.range.last + 1)
                }
            }
        }
}

/** نسخة نصية جاهزة للعرض مع أرقام معزولة. */
fun String.bidiNumbers(): String = BidiText.display(this)

/**
 * نص آمن للأرقام في أي سياق (RTL أو LTR).
 *
 * `TextDirection.ContentOrLtr` يجعل اتجاه النص يتبع محتواه، والعزل يضمن أن كل رقم
 * يُعرض بترتيبه الصحيح مهما كان ما حوله.
 */
@Composable
fun NumberSafeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text.bidiNumbers(),
        modifier = modifier,
        style = style.copy(textDirection = TextDirection.ContentOrLtr),
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
    )
}

/**
 * تحويل عرض يضيف LRI/PDI حول كل رقم **بلا تغيير النص المنطقي**.
 *
 * يُستخدم مع حقول الإدخال الرقمية: المستخدم يكتب `100` فيُخزَّن `100`، بينما يُعرض
 * معزولًا فلا ينقلب ترتيبه، ويبقى مؤشّر الكتابة في مكانه الصحيح.
 */
object BidiNumberTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        // خريطة: موضع في النص المُحوَّل → موضع في النص الأصلي
        val map = IntArray(original.length + 1)
        val out = StringBuilder(original.length + 8)
        var index = 0
        var inRun = false
        while (index < original.length) {
            val ch = original[index]
            val isDigit = ch in '0'..'9'
            if (isDigit && !inRun) {
                out.append(BidiText.LRI)
                inRun = true
            } else if (!isDigit && inRun) {
                out.append(BidiText.PDI)
                inRun = false
            }
            map[index] = out.length
            out.append(ch)
            index++
        }
        if (inRun) out.append(BidiText.PDI)
        map[original.length] = out.length

        val transformed = AnnotatedString(out.toString(), text.spanStyles, text.paragraphStyles)
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = map[offset.coerceIn(0, original.length)]

                override fun transformedToOriginal(offset: Int): Int {
                    val clamped = offset.coerceIn(0, out.length)
                    // أول موضع أصلي تتجاوز خريطته الموضع المُحوَّل
                    for (i in map.indices) {
                        if (map[i] >= clamped) return i
                    }
                    return original.length
                }
            }
        return TransformedText(transformed, mapping)
    }
}

/**
 * حقل إدخال رقمي آمن: يُظهر الأرقام بالترتيب الصحيح داخل واجهة عربية،
 * ويُعيد النص النظيف بلا علامات تحكّم إلى الحالة.
 */
@Composable
fun NumberSafeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = BidiText.stripControls(value),
        onValueChange = { onValueChange(BidiText.stripControls(it)) },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        visualTransformation = BidiNumberTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
    )
}
