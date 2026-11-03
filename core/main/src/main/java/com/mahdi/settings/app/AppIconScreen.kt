package com.mahdi.settings.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahdi.appicon.AppIconSwitcher
import com.mahdi.appicon.CustomIconInstaller
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.editor.R
import com.mahdi.resources.strings
import com.mahdi.theme.SkyAccent
import com.mahdi.utils.toast
import kotlinx.coroutines.launch

/**
 * شاشة «أيقونة التطبيق» (4.0.0).
 *
 * - شبكة من 22 أيقونة مدمجة، المعاينة الحقيقية لكل واحدة (خلفيتها + رمزها).
 * - اختيار أي أيقونة يبدّلها **فورًا** على الشاشة الرئيسية عبر activity-alias.
 * - بطاقة «من المعرض» لاختيار صورة تُقصّ مربّعة 512×512 وتُضاف كأيقونة على الشاشة
 *   الرئيسية (القيد الحقيقي في أندرويد موضّح نصًّا داخل البطاقة).
 */

/**
 * خريطة معرّف الأيقونة → مورد رمزها.
 *
 * مكتوبة صراحةً لا عبر `getIdentifier`، فأي خطأ في اسم مورد يظهر وقت الترجمة
 * لا كأيقونة فارغة على جهاز المستخدم.
 */
private val foregroundRes: Map<String, Int> =
    mapOf(
        "default" to R.drawable.ic_launcher_default_foreground,
        "gold" to R.drawable.ic_launcher_gold_foreground,
        "blue" to R.drawable.ic_launcher_blue_foreground,
        "green" to R.drawable.ic_launcher_green_foreground,
        "purple" to R.drawable.ic_launcher_purple_foreground,
        "dark" to R.drawable.ic_launcher_dark_foreground,
        "glass" to R.drawable.ic_launcher_glass_foreground,
        "neon" to R.drawable.ic_launcher_neon_foreground,
        "fire" to R.drawable.ic_launcher_fire_foreground,
        "ocean" to R.drawable.ic_launcher_ocean_foreground,
        "forest" to R.drawable.ic_launcher_forest_foreground,
        "sky" to R.drawable.ic_launcher_sky_foreground,
        "rose" to R.drawable.ic_launcher_rose_foreground,
        "mint" to R.drawable.ic_launcher_mint_foreground,
        "sunset" to R.drawable.ic_launcher_sunset_foreground,
        "midnight" to R.drawable.ic_launcher_midnight_foreground,
        "royal" to R.drawable.ic_launcher_royal_foreground,
        "cyberpunk" to R.drawable.ic_launcher_cyberpunk_foreground,
        "retro" to R.drawable.ic_launcher_retro_foreground,
        "minimal" to R.drawable.ic_launcher_minimal_foreground,
        "ember" to R.drawable.ic_launcher_ember_foreground,
        "arctic" to R.drawable.ic_launcher_arctic_foreground,
    )

@Composable
fun AppIconScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val scope = rememberCoroutineScope()

    var currentId by remember { mutableStateOf(AppIconSwitcher.currentId(context)) }
    var busy by remember { mutableStateOf(false) }
    var hasCustom by remember { mutableStateOf(CustomIconInstaller.hasCustomIcon(context)) }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            busy = true
            scope.launch {
                val ok = CustomIconInstaller.install(context, uri)
                busy = false
                hasCustom = CustomIconInstaller.hasCustomIcon(context)
                toast(if (ok) strings.custom_icon_done else strings.custom_icon_failed)
            }
        }

    PreferenceLayout(
        label = stringResource(strings.app_icon_title),
        modifier = modifier,
        backArrowVisible = true,
    ) {
        Text(
            text = stringResource(strings.app_icon_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        PreferenceGroup(heading = stringResource(strings.app_icon_title)) {
            // صفوف من 3 أيقونات: معاينة كبيرة + الاسم، والحدّ السماوي على المختارة
            AppIconSwitcher.icons.chunked(3).forEach { rowIcons ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowIcons.forEach { icon ->
                        val selected = icon.id == currentId
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        val ok = AppIconSwitcher.apply(context, icon.id)
                                        if (ok) {
                                            currentId = icon.id
                                            toast(strings.app_icon_changed)
                                        } else {
                                            toast(strings.app_icon_failed)
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // معاينة الأيقونة: خلفية بلونها + رمزها الحقيقي (VectorDrawable)
                            Box(
                                modifier =
                                    Modifier
                                        .size(58.dp)
                                        .clip(CircleShape)
                                        .background(Color(icon.previewBackground))
                                        .then(
                                            if (selected) {
                                                Modifier.border(2.5.dp, SkyAccent, CircleShape)
                                            } else {
                                                Modifier
                                            }
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(foregroundRes.getValue(icon.id)),
                                    contentDescription = icon.displayName(isArabic),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(40.dp),
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = icon.displayName(isArabic),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) SkyAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (selected) {
                                Text(
                                    text = stringResource(strings.app_icon_current),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SkyAccent,
                                )
                            }
                        }
                    }
                    // سدّ الفراغ في الصفّ الأخير حتى لا تتمدد الأيقونات
                    repeat(3 - rowIcons.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // ————— صورة من المعرض —————
        PreferenceGroup(heading = stringResource(strings.custom_icon_title)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = stringResource(strings.custom_icon_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                    }
                    TextButton(onClick = { picker.launch("image/*") }, enabled = !busy) {
                        Text(text = stringResource(strings.custom_icon_title), color = SkyAccent)
                    }
                    if (hasCustom) {
                        TextButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    CustomIconInstaller.remove(context)
                                    busy = false
                                    hasCustom = false
                                    toast(strings.custom_icon_removed)
                                }
                            },
                            enabled = !busy,
                        ) {
                            Text(
                                text = stringResource(strings.custom_icon_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
