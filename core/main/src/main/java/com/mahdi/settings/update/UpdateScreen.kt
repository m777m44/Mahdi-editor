package com.mahdi.settings.update

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.resources.strings
import com.mahdi.theme.SkyAccent
import com.mahdi.ui.components.AppLogo
import com.mahdi.ui.components.ProfessionalProgressBar
import com.mahdi.update.AppUpdateChecker
import com.mahdi.update.UpdateDownloader
import com.mahdi.update.UpdateInfo
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * «مركز التحديثات» (5.0.0) — تحديث داخلي كامل: فحص، تنزيل بشريط تقدّم، وتثبيت.
 *
 * كانت النسخ السابقة تفتح صفحة GitHub في المتصفح، فيخرج المستخدم من التطبيق
 * ويُعيد التثبيت يدويًا. الآن كل الخطوات هنا، والتثبيت عبر FileProvider.
 */
@Composable
fun UpdateScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var checking by remember { mutableStateOf(true) }
    var checkedOnce by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var currentVersion by remember { mutableStateOf(AppUpdateChecker.currentVersion(context)) }

    val progress by UpdateDownloader.progress.collectAsState()
    val status by UpdateDownloader.status.collectAsState()

    fun runCheck() {
        scope.launch {
            checking = true
            info = AppUpdateChecker.check(context, force = true)
            currentVersion = AppUpdateChecker.currentVersion(context)
            checking = false
            checkedOnce = true
        }
    }

    LaunchedEffect(Unit) { runCheck() }

    PreferenceLayout(label = stringResource(strings.update_center_title), modifier = modifier) {

        // ─────────────── بطاقة الهوية: الشعار + الإصدار المثبَّت ───────────────
        PreferenceGroup {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppLogo(size = 62.dp, cornerRadius = 18.dp)

                Spacer(Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Mahdi-Editor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(strings.update_current_version, currentVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ─────────────── الحالة ───────────────
        when {
            checking -> {
                PreferenceGroup {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(38.dp), color = SkyAccent)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(strings.update_checking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            status is UpdateDownloader.Status.Downloading -> {
                PreferenceGroup {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(
                            text = stringResource(strings.update_downloading),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(20.dp))
                        ProfessionalProgressBar(
                            progress = progress,
                            label = stringResource(
                                strings.update_percent,
                                (progress * 100f).roundToInt(),
                            ),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(strings.update_dont_close),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            status is UpdateDownloader.Status.Done -> {
                val apk = (status as UpdateDownloader.Status.Done).apk
                PreferenceGroup {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StatusBadge(success = true)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(strings.update_downloaded),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = apk.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(18.dp))

                        if (!AppUpdateChecker.canInstall(context)) {
                            Text(
                                text = stringResource(strings.update_permission_needed),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        Button(
                            onClick = {
                                if (AppUpdateChecker.canInstall(context)) {
                                    UpdateDownloader.install(context, apk)
                                } else {
                                    AppUpdateChecker.openInstallPermission(context)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text =
                                    if (AppUpdateChecker.canInstall(context)) {
                                        stringResource(strings.update_install)
                                    } else {
                                        stringResource(strings.update_open_permission)
                                    },
                            )
                        }
                    }
                }
            }

            status is UpdateDownloader.Status.Failed -> {
                val reason = (status as UpdateDownloader.Status.Failed).reason
                PreferenceGroup {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StatusBadge(success = false)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(strings.update_failed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                val target = info
                                if (target != null) {
                                    scope.launch { UpdateDownloader.download(context, target) }
                                } else {
                                    runCheck()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(strings.update_retry))
                        }
                    }
                }
            }

            info != null -> {
                val update = info!!
                PreferenceGroup(heading = stringResource(strings.update_available)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier
                                        .clip(CircleShape)
                                        .background(SkyAccent.copy(alpha = 0.16f))
                                        .padding(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = SkyAccent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(strings.update_latest_version, update.version),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SkyAccent,
                                )
                                Text(
                                    text = stringResource(strings.update_size, update.sizeText),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = stringResource(strings.update_published, update.dateText),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(18.dp))

                        Text(
                            text = stringResource(strings.update_release_notes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(14.dp),
                        ) {
                            Text(
                                text = update.notes.ifBlank { stringResource(strings.update_notes_empty) }.trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 14,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = {
                                UpdateDownloader.reset()
                                scope.launch { UpdateDownloader.download(context, update) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(strings.update_now))
                        }
                    }
                }
            }

            else -> {
                val failed = AppUpdateChecker.lastCheckFailed
                PreferenceGroup {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StatusBadge(success = !failed)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text =
                                if (failed) {
                                    stringResource(strings.update_no_network)
                                } else {
                                    stringResource(strings.update_up_to_date)
                                },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(strings.update_current_version, currentVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ─────────────── فحص يدوي ───────────────
        if (!checking && status is UpdateDownloader.Status.Idle) {
            PreferenceGroup {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { runCheck() }, enabled = checkedOnce) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(strings.update_check_now))
                    }
                }
            }
        }
    }
}

/** شارة حالة دائرية بسيطة: صحّ خضراء أو تحذير أحمر — بلا صور. */
@Composable
private fun StatusBadge(success: Boolean) {
    val color = if (success) SkyAccent else MaterialTheme.colorScheme.error
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (success) Icons.Rounded.Check else Icons.Rounded.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(28.dp),
        )
    }
}
