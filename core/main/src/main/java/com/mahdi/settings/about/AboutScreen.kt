package com.mahdi.settings.about

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.mahdi.components.SettingsItem
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.components.compose.preferences.base.PreferenceTemplate
import com.mahdi.editor.BuildConfig
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.utils.copyToClipboard
import com.mahdi.utils.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** رابط مستودع المشروع، وروابط القناة الرسمية. */
const val MAHDI_GITHUB_REPO = "https://github.com/m777m44/Mahdi-editor"
const val MAHDI_TELEGRAM_URL = "https://t.me/HEROES2002"
const val MAHDI_LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** اسم الجهة صاحبة الحقوق (العلامة التجارية للمشروع). */
const val MAHDI_COPYRIGHT_HOLDER = "Mahdi-Editor-Code"

/** سطر الحقوق المعروض في شاشة «حول» وفي التوثيق. */
const val MAHDI_COPYRIGHT = "© 2026 Mahdi-Editor-Code — جميع الحقوق محفوظة"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val pm = context.packageManager
    val appIcon = pm.getApplicationIcon(context.packageName)
    val packageInfo = pm.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply { data = url.toUri() })
    }

    PreferenceLayout(label = stringResource(id = strings.about), backArrowVisible = true) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = appIcon,
                contentDescription = null,
                modifier = Modifier.size(96.dp).padding(bottom = 8.dp),
            )

            Text(
                text = stringResource(strings.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = versionName.toString().uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = stringResource(strings.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
            )

            Text(
                text = MAHDI_COPYRIGHT,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .padding(top = 12.dp, start = 24.dp, end = 24.dp, bottom = 8.dp)
                        .combinedClickable(onClick = {}, onLongClick = { copyToClipboard(MAHDI_COPYRIGHT) }),
            )
        }

        PreferenceGroup(heading = stringResource(strings.build_info)) {
            InfoRow(
                title = stringResource(id = strings.version),
                value = versionName.toString(),
                onLongClick = { copyToClipboard(versionName.toString()) },
            )

            InfoRow(
                title = stringResource(id = strings.version_code),
                value = versionCode.toString(),
                onLongClick = { copyToClipboard(versionCode.toString()) },
            )

            InfoRow(
                title = stringResource(id = strings.git_commit),
                value = BuildConfig.GIT_SHORT_COMMIT_HASH,
                onLongClick = { copyToClipboard(BuildConfig.GIT_SHORT_COMMIT_HASH) },
            )

            InfoRow(
                title = stringResource(id = strings.package_name),
                value = context.packageName,
                onLongClick = { copyToClipboard(context.packageName) },
            )
        }

        PreferenceGroup(heading = stringResource(strings.community)) {
            val stars = remember { mutableStateOf(strings.loading.getString()) }

            LaunchedEffect(Unit) {
                val client = okHttpClient
                val url = "https://api.github.com/repos/m777m44/Mahdi-editor"
                val request = Request.Builder().url(url).build()

                withContext(Dispatchers.IO) {
                    try {
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val jsonBody = response.body.string()
                                val json = JSONObject(jsonBody)
                                val count = json.getInt("stargazers_count")

                                withContext(Dispatchers.Main) { stars.value = count.toString() }
                            } else {
                                stars.value = strings.error.getString()
                            }
                        }
                    } catch (e: Exception) {
                        stars.value = e.message ?: strings.api_error.getString()
                    }
                }
            }

            PreferenceTemplate(
                title = {
                    Text(text = stringResource(strings.github_stars), style = MaterialTheme.typography.titleMedium)
                },
                description = { Text(text = stars.value, style = MaterialTheme.typography.titleSmall) },
            )

            LinkRow(
                label = stringResource(id = strings.github),
                description = stringResource(id = strings.github_desc),
                onClick = { openUrl(MAHDI_GITHUB_REPO) },
            )

            LinkRow(
                label = stringResource(id = strings.telegram_channel),
                description = stringResource(id = strings.telegram_desc),
                onClick = { openUrl(MAHDI_TELEGRAM_URL) },
            )

            LinkRow(
                label = stringResource(id = strings.source_code),
                description = stringResource(id = strings.view_repo),
                onClick = { openUrl(MAHDI_GITHUB_REPO) },
            )
        }

        PreferenceGroup(heading = stringResource(strings.license_label)) {
            PreferenceTemplate(
                title = {
                    Text(text = stringResource(id = strings.license_label), style = MaterialTheme.typography.titleMedium)
                },
                description = {
                    Text(
                        text = stringResource(id = strings.license_notice),
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                modifier = Modifier.combinedClickable(enabled = true, onClick = {}, onLongClick = {}),
            )

            LinkRow(
                label = "GNU GPL v3.0",
                description = "gnu.org/licenses/gpl-3.0.html",
                onClick = { openUrl(MAHDI_LICENSE_URL) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoRow(title: String, value: String, onLongClick: () -> Unit) {
    PreferenceTemplate(
        modifier =
            Modifier.combinedClickable(
                enabled = true,
                onClick = {},
                onLongClick = onLongClick,
            ),
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        description = { Text(text = value, style = MaterialTheme.typography.titleSmall) },
    )
}

@Composable
private fun LinkRow(label: String, description: String, onClick: () -> Unit) {
    SettingsItem(
        label = label,
        description = description,
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
        onClick = onClick,
    )
}
