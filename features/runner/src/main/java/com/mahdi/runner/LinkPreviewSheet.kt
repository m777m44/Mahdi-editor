package com.mahdi.runner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mahdi.resources.drawables
import com.mahdi.runner.runners.web.openLinkInPreview

/**
 * «معاينة رابط» (9.2.0) — مدخل المتصفّح الداخلي.
 *
 * المستخدم يطلب: فتح الروابط وتصفّحها **داخل التطبيق** كما في المتصفحات. هنا
 * مربّع واحد يقبل رابطًا كاملًا أو نطاقًا أو حتى عبارة بحث (تُحوَّل إلى بحث
 * Google)، مع زرّ لصق من الحافظة، وثلاثة اختصارات شائعة، ثم تُفتح النتيجة في
 * `PreviewBrowserActivity` — نفس المتصفّح الذي تُعرض فيه ملفات HTML، لا متصفّح
 * ثانٍ (شريط عنوان + رجوع/تقدّم + إعادة تحميل، كلها موجودة أصلًا).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkPreviewSheet() {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    var address by remember { mutableStateOf(RunnerUI.pendingLink) }

    fun dismiss() {
        RunnerUI.showLinkDialog.value = false
        RunnerUI.pendingLink = ""
    }

    fun go(raw: String) {
        val target = raw.trim()
        // بلا عنوان: نبدأ من صفحة بحث Google مباشرة — أقرب شيء إلى «متصفح»
        openLinkInPreview(context, target.ifEmpty { "https://www.google.com" })
        dismiss()
    }

    ModalBottomSheet(onDismissRequest = { dismiss() }, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 22.dp),
        ) {
            // ——— ترويسة متدرّجة: أيقونة + عنوان + سطر فرعي ———
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier.size(38.dp)
                            .background(
                                brush =
                                    Brush.linearGradient(
                                        listOf(Color(0xFF38BDF8), Color(0xFF2DD4BF))
                                    ),
                                shape = RoundedCornerShape(12.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(drawables.open_in_new),
                        contentDescription = null,
                        tint = Color(0xFF07141A),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isArabic) "معاينة رابط" else "Preview a link",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text =
                            if (isArabic) "يُفتح داخل التطبيق — أو اكتب بحثًا"
                            else "Opens inside the app — or type a search",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (isArabic) "الرابط أو البحث" else "Link or search") },
                placeholder = { Text("example.com") },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go(address) }),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val clip = readClipboard(context)
                            if (!clip.isNullOrBlank()) address = clip
                        }
                    ) {
                        Icon(
                            painter = painterResource(drawables.paste),
                            contentDescription = if (isArabic) "لصق" else "Paste",
                        )
                    }
                },
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { go(address) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF38BDF8),
                        contentColor = Color(0xFF07141A),
                    ),
            ) {
                Icon(
                    painter = painterResource(drawables.open_in_new),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isArabic) "فتح" else "Open",
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ——— اختصارات سريعة ———
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    ("Google" to "https://www.google.com"),
                    ("GitHub" to "https://github.com"),
                    ("YouTube" to "https://m.youtube.com"),
                ).forEach { (label, url) ->
                    AssistChip(
                        onClick = { go(url) },
                        label = { Text(label) },
                        colors =
                            AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }
    }
}

/** نصّ الحافظة إن وُجد — يُستخدم لتعبئة العنوان تلقائيًا وفي زرّ اللصق. */
private fun readClipboard(context: Context): String? {
    return runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData? = manager.primaryClip
            clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        }
        .getOrNull()
}
