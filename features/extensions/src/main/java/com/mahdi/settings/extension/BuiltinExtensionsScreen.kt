package com.mahdi.settings.extension

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import com.mahdi.activities.settings.SettingsRoutes
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.ext.BuiltinExtension
import com.mahdi.ext.BuiltinExtensions
import com.mahdi.ext.ExtensionApi
import com.mahdi.ext.ExtensionKind
import com.mahdi.text.bidiNumbers
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.SkyBadge

/**
 * «الإضافات المدمجة» (3.0.0) — كتالوج ٣٦ إضافة تعمل فورًا بلا إنترنت.
 *
 * ١٢ قدرة نظام (ثيمات، لغات، بيئات تشغيل، Git، منسّقات…) + ٢٤ أداة نصّية.
 * كل بطاقة تفتح شاشتها الحقيقية؛ وعند الفتح تُسجَّل الإضافات كأوامر في ExtensionApi
 * فيظهر عدد الأوامر المتاحة للوحة الأوامر وشريط الحالة.
 */
@Composable
fun BuiltinExtensionsScreen(navController: NavController, modifier: Modifier = Modifier) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val commands by ExtensionApi.commands.collectAsState()
    var expandedTools by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { BuiltinExtensions.registerCommands() }

    val capabilities = remember { BuiltinExtensions.capabilities() }
    val tools = remember { BuiltinExtensions.tools() }

    PreferenceLayout(
        label = if (isArabic) "الإضافات المدمجة" else "Bundled extensions",
        modifier = modifier,
        backArrowVisible = true,
    ) {
        // ————— بطاقة الملخّص —————
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SkyAccent.copy(alpha = 0.10f)),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧩", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text =
                            if (isArabic) {
                                "${BuiltinExtensions.all.size} إضافة مدمجة".bidiNumbers()
                            } else {
                                "${BuiltinExtensions.all.size} bundled extensions"
                            },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    SkyBadge(text = "3.0.0", showDot = false)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text =
                        if (isArabic) {
                            (
                                "${capabilities.size} قدرة نظام · ${tools.size} أداة · " +
                                    "${BuiltinExtensions.actionCount} إجراء — كلها تعمل بلا تثبيت ولا إنترنت."
                            ).bidiNumbers()
                        } else {
                            "${capabilities.size} capabilities · ${tools.size} tools · ${BuiltinExtensions.actionCount} actions " +
                                "— all work without installing or going online."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text =
                        if (isArabic) {
                            "أوامر مسجّلة في ExtensionAPI: ${commands.size}".bidiNumbers()
                        } else {
                            "Commands registered in ExtensionAPI: ${commands.size}"
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = SkyAccent,
                )
            }
        }

        // ————— قدرات النظام —————
        SectionHeading(if (isArabic) "قدرات النظام" else "Capabilities")
        capabilities.forEach { extension ->
            ExtensionRow(extension = extension, isArabic = isArabic) {
                extension.route?.let { route -> navController.navigate(route) }
            }
        }

        // ————— أدوات النصوص —————
        SectionHeading(if (isArabic) "أدوات النصوص" else "Text tools")
        val shown = if (expandedTools) tools else tools.take(6)
        shown.forEach { extension ->
            ExtensionRow(extension = extension, isArabic = isArabic) {
                extension.toolId?.let { toolId ->
                    navController.navigate("${SettingsRoutes.BuiltinTool.route}/$toolId")
                }
            }
        }
        if (tools.size > 6) {
            Text(
                text =
                    if (expandedTools) {
                        if (isArabic) "إخفاء الأدوات الباقية" else "Hide the rest"
                    } else {
                        if (isArabic) "عرض كل الأدوات (${tools.size})" else "Show all ${tools.size} tools"
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expandedTools = !expandedTools }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = SkyAccent,
            )
        }

        Text(
            text =
                if (isArabic) {
                    "الكتالوج يُحدَّث تلقائيًا مع كل أداة جديدة، وكل ما هنا يعمل دون إنترنت."
                } else {
                    "The catalogue follows every new tool automatically; everything here works offline."
                },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = SkyAccent,
    )
}

@Composable
private fun ExtensionRow(extension: BuiltinExtension, isArabic: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (extension.kind == ExtensionKind.CAPABILITY) {
                        SkyAccent.copy(alpha = 0.06f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(extension.emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = extension.name(isArabic),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = extension.description(isArabic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            SkyBadge(text = extension.version, showDot = false)
        }
    }
}
