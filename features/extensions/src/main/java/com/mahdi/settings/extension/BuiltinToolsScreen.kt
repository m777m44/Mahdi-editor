package com.mahdi.settings.extension

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.SkyBadge
import com.mahdi.tools.BuiltinTool
import com.mahdi.tools.BuiltinTools
import com.mahdi.utils.copyToClipboard

/**
 * «الإضافات المدمجة» داخل تبويب الإضافات (2.8.2).
 *
 * اثنتا عشرة إضافة جاهزة تعمل بلا تثبيت وبلا إنترنت: أدوات نص، ترميز، بصمات، JSON،
 * عربية، أسطر، مولّدات، تسمية برمجية، ألوان، استخراج، CSV، ومقارنة نصوص.
 */
@Composable
fun BuiltinToolsSection(
    query: String,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit,
) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    var expanded by remember { mutableStateOf(true) }

    val tools =
        remember(query) {
            if (query.isBlank()) {
                BuiltinTools.all
            } else {
                BuiltinTools.all.filter { tool ->
                    tool.nameAr.contains(query, ignoreCase = true) ||
                        tool.nameEn.contains(query, ignoreCase = true) ||
                        tool.descAr.contains(query, ignoreCase = true) ||
                        tool.actions.any { it.labelAr.contains(query, ignoreCase = true) }
                }
            }
        }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SkyAccent.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🧰", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isArabic) "إضافات مدمجة" else "Bundled extensions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text =
                            if (isArabic) {
                                "${tools.size} أداة · ${BuiltinTools.actionCount} إجراء — جاهزة بلا تثبيت"
                            } else {
                                "${tools.size} tools · ${BuiltinTools.actionCount} actions — no install needed"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SkyBadge(text = "2.8.2", showDot = false)
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = SkyAccent.copy(alpha = 0.16f))
                tools.forEach { tool -> BuiltinToolRow(tool, isArabic) { onOpen(tool.id) } }
            }

            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(
                    if (expanded) {
                        if (isArabic) "إخفاء القائمة" else "Hide list"
                    } else {
                        if (isArabic) "عرض ${tools.size} أداة" else "Show ${tools.size} tools"
                    }
                )
            }
        }
    }
}

@Composable
private fun BuiltinToolRow(tool: BuiltinTool, isArabic: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(tool.emoji, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = tool.name(isArabic),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = tool.description(isArabic),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${tool.actions.size}",
            style = MaterialTheme.typography.labelMedium,
            color = SkyAccent,
        )
    }
}

/**
 * شاشة أداة مدمجة واحدة: إدخال ← تنفيذ ← نتيجة قابلة للنسخ والمشاركة.
 */
@Composable
fun BuiltinToolScreen(toolId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val tool = remember(toolId) { BuiltinTools.byId(toolId) }

    if (tool == null) {
        PreferenceLayout(
            label = if (isArabic) "أداة غير معروفة" else "Unknown tool",
            modifier = modifier,
            backArrowVisible = true,
        ) {
            Text(
                text = if (isArabic) "لا توجد أداة بهذا المعرّف: $toolId" else "No tool with id: $toolId",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    PreferenceLayout(
        label = "${tool.emoji} ${tool.name(isArabic)}",
        modifier = modifier,
        backArrowVisible = true,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = tool.description(isArabic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text =
                        if (isArabic) {
                            "${tool.actions.size} إجراء · يعمل بلا إنترنت"
                        } else {
                            "${tool.actions.size} actions · works offline"
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }

        PreferenceGroup(heading = if (isArabic) "الإدخال" else "Input") {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                minLines = 4,
                maxLines = 14,
                textStyle = MaterialTheme.typography.bodySmall,
                placeholder = {
                    Text(
                        text =
                            if (isArabic) {
                                "الصق أو اكتب النص هنا…"
                            } else {
                                "Paste or type your text here…"
                            },
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        }

        PreferenceGroup(heading = if (isArabic) "الإجراءات" else "Actions") {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tool.actions.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { action ->
                            Button(
                                onClick = { output = action.run(input) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = if (isArabic) action.labelAr else action.labelEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        PreferenceGroup(heading = if (isArabic) "النتيجة" else "Result") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                SelectionContainer {
                    Text(
                        text =
                            output.ifBlank {
                                if (isArabic) {
                                    "ستظهر النتيجة هنا بعد اختيار إجراء."
                                } else {
                                    "The result appears here after choosing an action."
                                }
                            },
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color =
                            if (output.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    enabled = output.isNotBlank(),
                    onClick = { copyToClipboard(output) },
                ) {
                    Text(if (isArabic) "نسخ" else "Copy")
                }
                TextButton(
                    enabled = output.isNotBlank(),
                    onClick = {
                        val intent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, output)
                            }
                        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                    },
                ) {
                    Text(if (isArabic) "مشاركة" else "Share")
                }
                TextButton(
                    enabled = input.isNotBlank() || output.isNotBlank(),
                    onClick = {
                        input = ""
                        output = ""
                    },
                ) {
                    Text(if (isArabic) "مسح" else "Clear")
                }
            }
        }
    }
}
