package com.mahdi.settings.support

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mahdi.components.SettingsItem
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.components.compose.preferences.base.PreferenceTemplate
import com.mahdi.resources.drawables
import com.mahdi.resources.strings
import com.mahdi.settings.about.MAHDI_GITHUB_REPO
import com.mahdi.settings.about.MAHDI_TELEGRAM_URL

/**
 * صفحة دعم المشروع.
 *
 * لا تحتوي هذه النسخة على أي حسابات تبرّع أو منصّات دفع خارجية؛ أفضل دعم هو مشاركة التطبيق مع الآخرين والإبلاغ عن
 * الأخطاء والمساهمة في الشيفرة عبر المستودع.
 */
@Composable
fun Support(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.support), backArrowVisible = true) {
        val context = LocalContext.current

        fun open(url: String) {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply { data = url.toUri() })
        }

        PreferenceGroup {
            PreferenceTemplate(
                title = {
                    Text(text = stringResource(strings.support_desc), style = MaterialTheme.typography.titleMedium)
                },
                description = {
                    Text(text = stringResource(strings.support_community_text), style = MaterialTheme.typography.bodyMedium)
                },
            )
        }

        PreferenceGroup(heading = stringResource(strings.community)) {
            SettingsItem(
                label = stringResource(strings.telegram_channel),
                description = stringResource(strings.telegram_desc),
                isEnabled = true,
                showSwitch = false,
                default = false,
                startWidget = {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        painter = painterResource(drawables.telegram),
                        contentDescription = null,
                    )
                },
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        painter = painterResource(drawables.open_in_new),
                        contentDescription = null,
                    )
                },
                onClick = { open(MAHDI_TELEGRAM_URL) },
            )

            SettingsItem(
                label = stringResource(strings.github),
                description = stringResource(strings.github_desc),
                isEnabled = true,
                showSwitch = false,
                default = false,
                startWidget = {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        painter = painterResource(drawables.github),
                        contentDescription = null,
                    )
                },
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        painter = painterResource(drawables.open_in_new),
                        contentDescription = null,
                    )
                },
                onClick = { open(MAHDI_GITHUB_REPO) },
            )

            SettingsItem(
                label = stringResource(strings.report_issue),
                description = stringResource(strings.telegram_desc),
                isEnabled = true,
                showSwitch = false,
                default = false,
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        painter = painterResource(drawables.open_in_new),
                        contentDescription = null,
                    )
                },
                onClick = { open(MAHDI_TELEGRAM_URL) },
            )
        }
    }
}

/**
 * كان الإصدار السابق يعرض نافذة تبرّع دورية. أُزيلت في Mahdi-Editor، ويُترك التابع فارغًا حتى لا تظهر أي مطالبة
 * بالتبرّع في التطبيق.
 *
 * The recurring donation prompt has been removed; the hook stays for API compatibility.
 */
@Suppress("UNUSED_PARAMETER")
fun Activity.handleSupport() {
    // لا توجد مطالبات تبرّع في Mahdi-Editor.
}
