package com.mahdi.settings.terminal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.components.compose.preferences.base.PreferenceTemplate
import com.mahdi.icons.LucideCircleQuestionMark
import com.mahdi.resources.getFilledString
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.theme.SkyBadge
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.greenStatus
import com.mahdi.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CheckStatus {
    PENDING,
    LOADING,
    SUCCESS,
    FAILED,
}

data class Check(
    val label: String,
    val status: CheckStatus = CheckStatus.PENDING,
    val logs: List<String> = emptyList(),
    val isExpanded: Boolean = false,
    val run: suspend (printLog: (String) -> Unit) -> Boolean,
)

/**
 * «سلامة الطرفية» (4.3.0) — أُعيد تصميمها.
 *
 * - الفحوص تعمل **بالترتيب على خيط IO** مع مهلة لكل فحص، فلا تتجمّد الواجهة ولا
 *   يبقى فحص عالقًا (كان سبب علامة «؟» والدائرة الأزرق الدائم في 4.2.0).
 * - **ملخّص أعلى الشاشة**: عدد الناجح من الإجمالي وشريط تقدّم ولون الحالة.
 * - **إعادة الفحص** بضغطة، و**إصلاح تلقائي** يعيد كتابة إعدادات الشبكة وأذونات
 *   المجلدات ويُزيل أقفال مدير الحزم.
 */
@Composable
fun TerminalCheckScreen() {
    val checks = terminalChecks()
    val scope = rememberCoroutineScope()

    var running by remember { mutableStateOf(false) }
    var runId by remember { mutableIntStateOf(0) }
    var repairing by remember { mutableStateOf(false) }
    val repairLog = remember { mutableStateListOf<String>() }

    val passed = checks.count { it.status == CheckStatus.SUCCESS }
    val failed = checks.count { it.status == CheckStatus.FAILED }
    val done = passed + failed

    LaunchedEffect(runId) {
        running = true
        checks.indices.forEach { i ->
            checks[i] = checks[i].copy(status = CheckStatus.PENDING, logs = emptyList(), isExpanded = false)
        }

        checks.indices.forEach { i ->
            checks[i] = checks[i].copy(status = CheckStatus.LOADING, logs = emptyList())

            val success =
                withContext(Dispatchers.IO) {
                    try {
                        checks[i].run { line ->
                            checks[i] = checks[i].copy(logs = checks[i].logs + line)
                        }
                    } catch (e: Exception) {
                        checks[i] = checks[i].copy(logs = checks[i].logs + "Crash: ${e.localizedMessage}")
                        false
                    }
                }

            checks[i] = checks[i].copy(status = if (success) CheckStatus.SUCCESS else CheckStatus.FAILED, isExpanded = !success)
        }
        running = false
    }

    PreferenceLayout(label = stringResource(strings.terminal_health), backArrowVisible = true) {
        // ————— ملخّص الحالة —————
        PreferenceGroup {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIcon(
                        status =
                            when {
                                running -> CheckStatus.LOADING
                                failed > 0 -> CheckStatus.FAILED
                                else -> CheckStatus.SUCCESS
                            },
                        size = 28,
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                when {
                                    running -> stringResource(strings.terminal_health_checking)
                                    failed > 0 -> stringResource(strings.terminal_health_issues, failed)
                                    else -> stringResource(strings.terminal_health_all_good)
                                },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(strings.terminal_health_summary, passed, checks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SkyBadge(text = "${done}/${checks.size}", showDot = running)
                }

                Spacer(Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { if (checks.isEmpty()) 0f else done.toFloat() / checks.size },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (failed > 0) MaterialTheme.colorScheme.error else SkyAccent,
                )

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { if (!running) runId++ },
                        enabled = !running && !repairing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(strings.terminal_health_rerun))
                    }

                    OutlinedButton(
                        onClick = {
                            if (repairing) return@OutlinedButton
                            repairing = true
                            repairLog.clear()
                            scope.launch {
                                val fixes = TerminalRepair.repair { line -> repairLog.add(line) }
                                repairing = false
                                if (fixes > 0) {
                                    toast(strings.terminal_health_repair_done.getFilledString(fixes))
                                    runId++
                                } else {
                                    toast(strings.terminal_health_repair_none.getString())
                                }
                            }
                        },
                        enabled = !repairing,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (repairing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(strings.terminal_health_repair))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(strings.terminal_health_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ————— الفحوص —————
        checks.forEachIndexed { index, check ->
            PreferenceGroup {
                PreferenceTemplate(
                    modifier = Modifier.clickable { checks[index] = check.copy(isExpanded = !check.isExpanded) },
                    title = { Text(text = check.label, style = MaterialTheme.typography.bodyLarge) },
                    startWidget = { StatusIcon(status = check.status) },
                    endWidget = {
                        val rotation by
                            animateFloatAsState(
                                targetValue = if (check.isExpanded) 90f else 0f,
                                label = "ChevronRotation",
                            )

                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.rotate(rotation).size(24.dp),
                        )
                    },
                )

                if (check.isExpanded) {
                    Box(
                        modifier =
                            Modifier.padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .padding(12.dp)
                    ) {
                        Column {
                            if (check.logs.isEmpty()) {
                                Text(
                                    text = stringResource(strings.no_logs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                check.logs.forEach { log ->
                                    Text(
                                        text = if (log.startsWith(">")) log else "> $log",
                                        style =
                                            MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp,
                                            ),
                                        color =
                                            if (
                                                log.contains("✘") ||
                                                    log.contains("Error", ignoreCase = true) ||
                                                    log.contains("Fail", ignoreCase = true) ||
                                                    log.contains("Crash", ignoreCase = true) ||
                                                    log.contains("⚠")
                                            ) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: CheckStatus, size: Int = 24) {
    Box(modifier = Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        when (status) {
            CheckStatus.PENDING ->
                Icon(
                    imageVector = LucideCircleQuestionMark,
                    contentDescription = stringResource(strings.pending),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size((size - 4).dp),
                )

            CheckStatus.LOADING ->
                CircularProgressIndicator(
                    modifier = Modifier.size((size - 8).dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                )

            CheckStatus.SUCCESS ->
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(strings.success),
                    tint = MaterialTheme.colorScheme.greenStatus,
                    modifier = Modifier.size((size - 4).dp),
                )

            CheckStatus.FAILED ->
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(strings.failed),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size((size - 4).dp),
                )
        }
    }
}
