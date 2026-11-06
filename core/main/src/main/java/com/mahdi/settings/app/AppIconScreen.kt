package com.mahdi.settings.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mahdi.appicon.AppIconSwitcher
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.resources.strings
import com.mahdi.theme.SkyAccent
import com.mahdi.ui.graphics.rememberIconImage
import com.mahdi.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * شاشة «أيقونة التطبيق» (5.5.0).
 *
 * **شبكة أيقونات فقط** — لا نصوص توضيحية، لا عناوين فرعية، لا فقرات شرح:
 * أُزيلت كلها بطلب صاحب التطبيق. تبقى:
 * - صورة كل أيقونة كما ستظهر على الشاشة الرئيسية،
 * - إطار سمائي حول المختارة،
 * - علامة ✓ صغيرة على المختارة.
 *
 * الأسماء تبقى فقط في `contentDescription` لقارئ الشاشة (لا تُعرض ككتابة).
 *
 * ملاحظة تقنية: الأيقونات `adaptive-icon` في `mipmap-anydpi-v26`، و`painterResource`
 * لا يدعمها فيرمي `IllegalArgumentException`. لذلك تُقرأ كـ Drawable ثم تُرسم إلى
 * `Bitmap` وتُعرض بـ `Image(bitmap = …)`.
 */
@Composable
fun AppIconScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val scope = rememberCoroutineScope()

    var currentId by remember { mutableStateOf(AppIconSwitcher.currentId(context)) }
    var busy by remember { mutableStateOf(false) }

    val all = AppIconSwitcher.icons

    PreferenceLayout(
        label = stringResource(strings.app_icon_title),
        modifier = modifier,
        backArrowVisible = true,
    ) {
        all.chunked(4).forEach { rowIcons ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
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
                // أكمل الصف الأخير بفراغات حتى تبقى مربّعات الصفّ بنفس الحجم
                repeat(4 - rowIcons.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.size(24.dp))
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
    val scale by
        animateFloatAsState(
            targetValue = if (selected) 1f else 0.97f,
            animationSpec = tween(150),
            label = "tileScale",
        )

    Box(
        modifier =
            modifier
                .scale(scale)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(icon.backgroundColor))
                .then(
                    if (selected) {
                        Modifier.border(2.5.dp, SkyAccent, RoundedCornerShape(20.dp))
                    } else {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(20.dp),
                        )
                    },
                )
                .clickable(enabled = enabled, onClick = onClick),
    ) {
        val bitmap = rememberIconImage(icon.previewRes, sizePx = 256)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = icon.displayName(isArabic),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
            )
        }

        if (selected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(SkyAccent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF04121F),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
