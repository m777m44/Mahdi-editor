package com.mahdi.editor.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahdi.editor.Editor
import com.mahdi.file.child
import com.mahdi.file.sandboxHomeDir
import com.mahdi.lsp.LspConnectionStatus
import com.mahdi.lsp.LspConnector
import com.mahdi.text.BidiText
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * «شريط حالة المحرّر» (9.0.0) — سطر واحد أسفل المحرّر، كما في بيئات التطوير
 * الاحترافية (VS Code / Pydroid)، بلا أزرار ولا قوائم:
 *
 * ```
 * Python 3.12 ● LSP يعمل              السطر 12 · العمود 4
 * ```
 *
 * - **رقم السطر والعمود** يتحدّثان فورًا مع المؤشّر والتحديد.
 * - **حالة خادم اللغة** نقطة ملوّنة + كلمة واحدة، بلا تفاصيل تقنية.
 * - **إصدار بايثون** للملفات `.py` فقط، ويُقرأ مرة واحدة لكل عملية تطبيق.
 * - الأرقام معزولة بـLRI…PDI فتبقى `12` كما هي في واجهة عربية.
 *
 * المكوّن للقراءة فقط: مستمعان خفيفان على المحرّر، يُلغَيان عند مغادرة الشاشة.
 */
@Composable
fun EditorStatusBar(
    editor: Editor?,
    extension: String,
    lspConnector: LspConnector?,
    modifier: Modifier = Modifier,
) {
    // حالة الخادم تُجمَع من مصدرها الحقيقي (StateFlow) لا تُقرأ قراءةً واحدة:
    // فتظهر `يُعاد…` أثناء الإحياء الصامت ثم `يعمل` بانتهائه بلا أي تدخّل.
    val context = LocalContext.current

    val lspStatus by
        produceState<LspConnectionStatus?>(initialValue = null, lspConnector) {
            val server = lspConnector?.primaryServer()
            if (server == null) {
                value = null
                return@produceState
            }
            server.instances.collectLatest { instances ->
                val instance = instances.firstOrNull()
                if (instance == null) {
                    value = LspConnectionStatus.NOT_RUNNING
                } else {
                    instance.status.collect { value = it }
                }
            }
        }

    // 9.1.0: هل الخادم مثبَّت أصلًا؟ خادم غير مثبَّت **ليس خطأً**، ولا يجوز أن
    // يُعرض كعطل. يُقرأ مرة واحدة عند ظهور الشريط (فحص ملف واحد على القرص).
    val lspInstalled by
        produceState<Boolean?>(initialValue = null, lspConnector) {
            val server = lspConnector?.primaryServer()
            value =
                if (server == null) {
                    false
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching { server.isInstalled(context) }.getOrDefault(false)
                    }
                }
        }

    // إصدار بايثون يُقرأ على خيط خلفي مرة واحدة (تنفيذ عملية عملية على الخيط
    // الرئيسي كان يُجمّد أول إطار لملف .py) ثم يُخزَّن للعملية كلها.
    val pythonLabel by
        produceState<String?>(initialValue = null, extension) {
            if (!extension.equals("py", ignoreCase = true)) {
                value = null
                return@produceState
            }
            value = withContext(Dispatchers.IO) { PythonVersion.short }
        }

    var line by remember(editor) { mutableStateOf(1) }
    var column by remember(editor) { mutableStateOf(1) }
    var isSelection by remember(editor) { mutableStateOf(false) }

    fun refresh() {
        val target = editor ?: return
        val cursor = target.cursor
        val newLine = cursor.leftLine + 1
        val newColumn = cursor.leftColumn + 1
        val newSelection = cursor.isSelected
        if (newLine != line) line = newLine
        if (newColumn != column) column = newColumn
        if (newSelection != isSelection) isSelection = newSelection
    }

    DisposableEffect(editor) {
        val target = editor
        if (target == null) {
            onDispose {}
        } else {
            val selections =
                target.subscribeAlways(SelectionChangeEvent::class.java) { refresh() }
            val content =
                target.subscribeAlways(ContentChangeEvent::class.java) { refresh() }
            // قراءة أولية عند الظهور (الملف قد يُفتح ومؤشّره في سطر محفوظ)
            refresh()
            onDispose {
                selections.unsubscribe()
                content.unsubscribe()
            }
        }
    }

    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        // خط انحدار رقيق أعلى الشريط — هوية بصرية بلا ضجيج
        Box(
            Modifier.fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF38BDF8), Color(0xFF2DD4BF), Color(0xFFA78BFA))
                    )
                )
        )
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                pythonLabel?.let { version ->
                    StatusText("Python $version", Color(0xFF2DD4BF))
                    VerticalDot(divider)
                }
                LspChip(
                    status = lspStatus,
                    installed = lspInstalled,
                    onClick = { lspConnector?.openLspSettings() },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusText("السطر $line", muted)
                VerticalDot(divider)
                StatusText("العمود $column", muted)
                if (isSelection) {
                    VerticalDot(divider)
                    StatusText("تحديد", Color(0xFFF5D76E))
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(
        text = BidiText.display(text),
        style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.ContentOrLtr),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun VerticalDot(color: Color) {
    Spacer(Modifier.width(6.dp))
    Box(Modifier.size(2.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(6.dp))
}

/**
 * نقطة حالة + كلمة واحدة، وبلمسة واحدة تُفتح «إدارة خوادم اللغة».
 *
 * 9.1.0: هذا هو **البديل الوحيد** لرسالة «تعذّر الاتصال بخادم اللغة» التي كانت
 * تظهر كـSnackbar وتغطّي أسفل المحرّر. الحالة هنا دائمة الظهور وهادئة وغير
 * مقاطِعة، واللمسة عليها تقود إلى مكان الإصلاح — بلا نصّ تقني ولا Traceback.
 */
@Composable
private fun LspChip(status: LspConnectionStatus?, installed: Boolean?, onClick: () -> Unit) {
    val (color, label) =
        when {
            installed == false -> Color(0xFF94A3B8) to "LSP غير مثبّت"
            status == LspConnectionStatus.RUNNING -> Color(0xFF34D399) to "LSP يعمل"
            status == LspConnectionStatus.STARTING ||
                status == LspConnectionStatus.RESTARTING ||
                status == LspConnectionStatus.STOPPING -> Color(0xFFF5D76E) to "LSP يبدأ…"
            status == LspConnectionStatus.CRASHED ||
                status == LspConnectionStatus.TIMEOUT -> Color(0xFFFB923C) to "LSP يُعاد…"
            status == LspConnectionStatus.NOT_RUNNING -> Color(0xFF94A3B8) to "LSP خامل"
            else -> Color(0xFF94A3B8) to "LSP —"
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.12f))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
    }
}

/**
 * إصدار بايثون داخل الحاوية — يُقرأ **من ملف** لا بتشغيل عملية.
 *
 * ## الخطأ الذي أصلحته 9.1.0
 * كان يُنفَّذ `ProcessBuilder("sh","-c","python3 -c …")` من **عملية التطبيق**
 * مباشرة. وبايثون لا يوجد على أندرويد خارج الحاوية، فكانت النتيجة رسالة
 * أندرويد نفسها تُعرض مكان الرقم في شريط الحالة:
 *
 * > Python sh: python3: inaccessible or not found
 *
 * وهي بالضبط السطر الأحمر الذي ظهر أسفل اللقطة.
 *
 * ## البديل
 * سكربتات الطرفية تكتب الإصدار الحقيقي في `~/.mahdi/python_version` لحظة اختيار
 * المفسّر (انظر `mahdi_python_bin` في `utils.sh`). نقرأ الملف — عملية إدخال/إخراج
 * واحدة لا أكثر — ونتحقّق من صيغته قبل عرضه. إن لم يوجد (المستخدم لم يفتح
 * الطرفية بعد) لا نعرض رقمًا إطلاقًا، فلا رقم وهمي ولا رسالة خطأ.
 */
internal object PythonVersion {
    @Volatile private var cached: String? = null

    /** `3.12.3` أو `null` إن لم يُعرف بعد. */
    val short: String?
        // لا نُخزّن `null`: قد يُفتح الملف قبل أول طرفية، ثم يوجد بعدها.
        get() = cached ?: detect()?.also { cached = it }

    private val VERSION_REGEX = Regex("^\\d+\\.\\d+(\\.\\d+)?$")

    private fun detect(): String? =
        runCatching {
                val file = sandboxHomeDir().child(".mahdi").child("python_version")
                if (!file.exists()) return@runCatching null
                val text = file.readText().trim().lineSequence().firstOrNull().orEmpty().trim()
                text.takeIf { it.matches(VERSION_REGEX) }
            }
            .getOrNull()
}
