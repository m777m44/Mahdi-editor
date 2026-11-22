package com.mahdi.filetree

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahdi.settings.Settings

/**
 * 9.9.0 — قياسات شجرة الملفات.
 *
 * كل ما يعتمد على الخطّ أو التكبير يُحسب هنا **مرّة واحدة** بحسب الإعدادات،
 * فلا يُكرّر كل صفٍّ حساباته ولا تُقاس النصوص مئات المرّات أثناء التمرير.
 */
data class FileTreeMetrics(
    /** اسم الملف (السطر الأول). */
    val nameSize: TextUnit,
    /** السطر الثاني من الاسم الطويل — أصغر قليلًا فلا يزاحم الأول. */
    val nameLineSize: TextUnit,
    /** حجم الملف / عدد العناصر بجانب الاسم. */
    val metaSize: TextUnit,
    /** شيفرون الطيّ. */
    val glyphSize: TextUnit,
    /** أيقونة نوع الملف — تكبر مع التكبير. */
    val iconSize: Dp,
    /** مسافة الإزاحة لكل مستوى تداخل. */
    val indent: Dp,
    /** الحشو الرأسي لكل صفّ. */
    val rowPadding: Dp,
    /** هل الاسم على سطرين حين يطول؟ */
    val twoLines: Boolean,
    /** قطر شيفرون الطيّ. */
    val chevron: Dp,
    /** ارتفاع سطر الاسم الواحد — يُستخدم لتثبيت ارتفاع الصفّ. */
    val lineBox: Dp,
    /** المسافة بين الأيقونة والاسم. */
    val iconGap: Dp,
    /** سماكة خطوط دليل الشجرة. */
    val guideStroke: Dp,
    /** نصف قطر زوايا خلفية الصفّ المحدَّد. */
    val corner: Dp,
) {
    /**
     * ارتفاع الصفّ **بالضبط**.
     *
     * الصفّ يعلن ارتفاعه ولا يتركه للقياس الضمني: أوّلًا لأنه مطلب
     * `animateItem()` في LazyColumn (الحركة تحتاج ارتفاعًا معروفًا مسبقًا)،
     * وثانيًا لأن صفوفًا بارتفاعات متغيّرة أثناء التكبير تجعل التمرير يقفز
     * وتفقد القائمة موضعها. الارتفاع محسوب من الخطّ نفسه، فهو يتبع التكبير
     * والكثافة بدقّة، ولا يقلّ أبدًا عن ٤٤dp — حدّ اللمس المريح.
     */
    val rowHeight: Dp
        get() {
            val lines = if (twoLines) 2 else 1
            val content = lineBox * lines + rowPadding * 2
            val minimum = if (twoLines) 52.dp else 44.dp
            return if (content > minimum) content else minimum
        }
}

/** أقلّ تكبير مقبول. تحته يصير النصّ غير مقروء على الهاتف. */
const val FILE_TREE_MIN_SCALE = 0.80f

/** أعلى تكبير مقبول. فوقه يُهدم تخطيط الصفّ بلا فائدة. */
const val FILE_TREE_MAX_SCALE = 1.80f

/** قيمة التكبير الافتراضية. */
const val FILE_TREE_DEFAULT_SCALE = 1.0f

/**
 * يبني القياسات من الإعدادات الحالية.
 *
 * [sizeScale] يأتي من إيماءة القرص أثناء التمرير، ويُطبَّق فورًا على كل صفّ
 * من غير كتابة في التخزين — والكتابة تحدث عند انتهاء الإيماءة فقط، فلا تُقرأ
 * التفضيلات مئات المرّات في الثانية.
 */
@Composable
fun rememberFileTreeMetrics(sizeScale: Float): FileTreeMetrics {
    // قراءتها هنا يجعل القياسات تعيد التركيب عند تغييرها من شاشة أخرى.
    val density = Settings.file_tree_density
    val storedScale = Settings.file_tree_scale
    val twoLines = Settings.file_tree_two_lines
    val scale = (sizeScale.takeIf { it > 0f } ?: storedScale).coerceIn(FILE_TREE_MIN_SCALE, FILE_TREE_MAX_SCALE)

    return remember(density, twoLines, scale) {
        val base =
            when (density) {
                0 -> 12.sp
                2 -> 14.sp
                else -> 13.sp
            }
        FileTreeMetrics(
            nameSize = base * scale,
            nameLineSize = (base * 0.86f) * scale,
            metaSize = (base * 0.68f) * scale,
            glyphSize = (base * 0.95f) * scale,
            iconSize = (16.dp * scale).coerceIn(13.dp, 30.dp),
            indent = (15.dp * scale).coerceIn(9.dp, 28.dp),
            rowPadding =
                when (density) {
                        0 -> 2.dp
                        2 -> 7.dp
                        else -> 4.dp
                    }
                    .let { if (scale > 1.28f) it + 1.dp else it },
            twoLines = twoLines,
            chevron = (12.dp * scale).coerceIn(10.dp, 20.dp),
            // ١٧sp لارتفاع السطر عند ١٤sp: نسبة مقروءة لا متلاصقة ولا متباعدة.
            lineBox = (base.value * 1.22f * scale).dp,
            iconGap = (8.dp * scale).coerceIn(5.dp, 14.dp),
            guideStroke = (1.dp * scale).coerceIn(0.8.dp, 2.dp),
            corner = (10.dp * scale).coerceIn(7.dp, 18.dp),
        )
    }
}

/**
 * 9.9.0 — التكبير بإيماءة القرص (pinch) بلا إفساد التمرير.
 *
 * **لماذا لا `Modifier.transformable`:** تلك الإيماءة تعتبر تحريك إصبع واحد
 * «تحريكًا» فتستهلكه، فيتوقّف تمرير القائمة — وهو أسوأ من غياب التكبير.
 * هنا المُكتشِف **لا يفعل شيئًا** إلا عند وجود إصبعين أو أكثر، ويستهلك
 * الحدث في `Initial` (قبل أن تراه القائمة) فقط حين يتغيّر التكبير فعلًا:
 *
 *  - إصبع واحد → لا شيء إطلاقًا، فالتمرير والنقر كما كانا بالضبط.
 *  - إصبعان → يُحسب التكبير، يُبلَّغ الأب، يُستهلك الحدث فلا تتحرّك القائمة.
 *
 * ولأن التكبير **قيمة تُقاس بها** القياسات (لا `graphicsLayer`) يعيد النصّ
 * لفّه على المساحة الجديدة، وتكبر الأيقونات والإزاحة معه.
 */
@Composable
fun FileTreePinchZoom(onZoom: (Float) -> Unit, content: @Composable () -> Unit) {
    val initial = Settings.file_tree_scale.coerceIn(FILE_TREE_MIN_SCALE, FILE_TREE_MAX_SCALE)
    val scale = remember { mutableFloatStateOf(initial) }
    val report by rememberUpdatedState(onZoom)

    Box(
        modifier =
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var pastSlop = false
                    var startDistance = 0f

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size < 2) break

                        // مسافة مبدئية ثم نسبة التغيّر — بلا قفزات في أوّل لمسة.
                        val distance =
                            (pressed[1].position - pressed[0].position).getDistance()
                        if (!pastSlop) {
                            if (startDistance == 0f) startDistance = distance
                            if (kotlin.math.abs(distance - startDistance) > viewConfiguration.touchSlop) {
                                pastSlop = true
                            }
                            continue
                        }

                        val change = event.calculateZoom()
                        if (change != 1f) {
                            val next = (scale.floatValue * change).coerceIn(FILE_TREE_MIN_SCALE, FILE_TREE_MAX_SCALE)
                            if (next != scale.floatValue) {
                                scale.floatValue = next
                                report(next)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        content()
    }
}
