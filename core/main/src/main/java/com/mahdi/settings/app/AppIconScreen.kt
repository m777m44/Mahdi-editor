package com.mahdi.settings.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahdi.appicon.AppIconSwitcher
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.components.compose.preferences.base.PreferenceTemplate
import com.mahdi.resources.strings
import com.mahdi.theme.SkyAccent
import com.mahdi.ui.components.AppLogo
import com.mahdi.ui.graphics.rememberIconImage
import com.mahdi.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * شاشة «أيقونة التطبيق» (4.3.0).
 *
 * **عشر أيقونات أنمي ثلاثية الأبعاد** مرسومة خصيصًا، كل واحدة بحرف **M** مجسّم:
 * ثعلب مبرمج · قط عبقري · روبوت نشيط · بومة حكيمة · بطريق الطرفية · تنين صغير ·
 * أرنب وردي · باندا فضائي · أكسولوتل · همستر ذهبي.
 *
 * - **معاينة حقيقية 1:1** للصورة نفسها التي ستظهر على الشاشة الرئيسية (لا رموز
 *   ملوّنة ولا صور مشوّهة).
 * - **بطاقة كبيرة** للأيقونة المختارة في الأعلى، وشبكة أنيقة للبقية.
 * - التبديل فوري عبر `activity-alias` بلا إعادة تثبيت وبلا إغلاق التطبيق.
 * - أُزيل اختيار صورة من المعرض نهائيًا في 4.2.0: أندرويد لا يسمح بتغيير أيقونة
 *   التطبيق من صورة خارجية إلا عبر اختصار على الشاشة الرئيسية.
 */
@Composable
fun AppIconScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val scope = rememberCoroutineScope()

    var currentId by remember { mutableStateOf(AppIconSwitcher.currentId(context)) }
    var busy by remember { mutableStateOf(false) }

    val all = AppIconSwitcher.icons
    val selected = all.firstOrNull { it.id == currentId } ?: all.first()

    PreferenceLayout(label = stringResource(strings.app_icon_title), modifier = modifier, backArrowVisible = true) {
        // ————— البطاقة الكبيرة للأيقونة الحالية —————
        PreferenceGroup {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(132.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Brush.linearGradient(listOf(Color(selected.backgroundColor), Color(0xFF0B1220))))
                            .border(2.dp, SkyAccent.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                            .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 5.0.0: أيقونة تكيّفية ⇒ Bitmap جاهز، لا painterResource (كان يُسقط التطبيق)
                    val preview = rememberIconImage(selected.previewRes, sizePx = 384)
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = selected.displayName(isArabic),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)),
                        )
                    } else {
                        AppLogo(size = 108.dp, cornerRadius = 26.dp, showBackground = false)
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = selected.displayName(isArabic),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SkyAccent,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(strings.app_icon_desc_v3, all.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ————— الشبكة —————
        PreferenceGroup(heading = stringResource(strings.app_icon_pick)) {
            all.chunked(2).forEach { rowIcons ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowIcons.forEach { icon ->
                        IconTile(
                            icon = icon,
                            isArabic = isArabic,
                            selected = icon.id == currentId,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (icon.id == currentId || busy) return@IconTile
                                busy = true
                                scope.launch(Dispatchers.IO) {
                                    val ok = AppIconSwitcher.apply(context, icon.id)
                                    launch(Dispatchers.Main) {
                                        busy = false
                                        if (ok) {
                                            currentId = icon.id
                                            toast(strings.app_icon_changed)
                                        } else {
                                            toast(strings.unknown_err)
                                        }
                                    }
                                }
                            },
                        )
                    }
                    if (rowIcons.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // ————— الشعار الداخلي: يتبع نفس الأيقونة —————
        PreferenceGroup(heading = stringResource(strings.app_icon_internal_note_title)) {
            PreferenceTemplate(
                applyPaddings = true,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(size = 34.dp, cornerRadius = 10.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = selected.displayName(isArabic),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                description = {
                    Text(
                        text = stringResource(strings.app_icon_internal_note_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        // ————— ملاحظة —————
        PreferenceGroup {
            PreferenceTemplate(
                applyPaddings = true,
                title = { Text(stringResource(strings.app_icon_note_title), style = MaterialTheme.typography.bodyMedium) },
                description = {
                    Text(
                        text = stringResource(strings.app_icon_note_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

@Composable
private fun IconTile(
    icon: AppIconSwitcher.AppIcon,
    isArabic: Boolean,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(targetValue = if (selected) 1f else 0.97f, animationSpec = tween(150), label = "tileScale")

    Column(
        modifier =
            modifier
                .scale(scale)
                .clip(RoundedCornerShape(20.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(icon.backgroundColor))
                    .then(
                        if (selected) {
                            Modifier.border(2.5.dp, SkyAccent, RoundedCornerShape(22.dp))
                        } else {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                        }
                    ),
        ) {
            val tileBitmap = rememberIconImage(icon.previewRes, sizePx = 256)
            if (tileBitmap != null) {
                Image(
                    bitmap = tileBitmap,
                    contentDescription = icon.displayName(isArabic),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)),
                )
            } else {
                // أيقونة تالفة على جهاز معيّن لا تُسقط الشاشة ولا تُخفي الشبكة
                AppLogo(size = 72.dp, cornerRadius = 22.dp, showBackground = false)
            }

            if (selected) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(SkyAccent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF04121F),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = icon.displayName(isArabic),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) SkyAccent else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
