package com.mahdi.settings.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mahdi.App.Companion.iconPackManager
import com.mahdi.DefaultScope
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.events.AppEvent
import com.mahdi.events.Events
import com.mahdi.icons.pack.BundledIconPacks
import com.mahdi.icons.pack.currentIconPack
import com.mahdi.icons.pack.iconPackDir
import com.mahdi.icons.rememberSvgImageLoader
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import com.mahdi.theme.SkyAccent
import com.mahdi.utils.toast
import java.io.File
import kotlinx.coroutines.launch

/**
 * «حزم الأيقونات» (4.2.0).
 *
 * ثلاث حزم مضمّنة تعمل بلا إنترنت (خطّي · ثنائي · نيون) وفيها **111 أيقونة لكل حزمة**
 * تغطّي 104 امتدادات + المجلدات والملفات الافتراضية. الاختيار فوري: أيقونات شجرة
 * الملفات والتبويبات تتغيّر في الحال بلا إعادة تشغيل.
 *
 * الحزم المثبّتة من المتجر تظهر هنا أيضًا — فالمكان واحد لكل الأيقونات.
 */
@Composable
fun IconPackScreen(modifier: Modifier = Modifier) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val localPacks by iconPackManager.localIconPacks.collectAsStateWithLifecycle()
    var currentId by remember { mutableStateOf(Settings.icon_pack) }
    val imageLoader = rememberSvgImageLoader()

    PreferenceLayout(label = stringResource(strings.icon_packs), modifier = modifier, backArrowVisible = true) {
        Text(
            text = stringResource(strings.icon_packs_desc, localPacks.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        PreferenceGroup(heading = stringResource(strings.icon_packs)) {
            // «أيقونات النظام» — إلغاء الحزمة
            IconPackRow(
                title = stringResource(strings.icon_pack_builtin),
                subtitle = stringResource(strings.icon_pack_builtin_desc),
                selected = currentId.isEmpty(),
                samples = emptyList(),
                imageLoader = imageLoader,
                onClick = {
                    val old = currentIconPack.value
                    currentIconPack.value = null
                    Settings.icon_pack = ""
                    currentId = ""
                    DefaultScope.launch { Events.publish(AppEvent.IconPackChanged(null, old)) }
                    toast(strings.icon_pack_reset)
                },
            )

            localPacks.values.sortedBy { it.id }.forEach { pack ->
                val dir = File(iconPackDir, pack.id)
                val samples =
                    listOfNotNull(
                        pack.manifest.icons.defaultFile,
                        pack.manifest.icons.fileExtensions["py"],
                        pack.manifest.icons.fileExtensions["js"],
                        pack.manifest.icons.fileExtensions["json"],
                        pack.manifest.icons.defaultFolder,
                    ).map { File(dir, it) }.filter { it.exists() }

                IconPackRow(
                    title = pack.name,
                    subtitle = pack.description ?: pack.id,
                    selected = currentId == pack.id,
                    samples = samples,
                    imageLoader = imageLoader,
                    onClick = {
                        if (currentId == pack.id) return@IconPackRow
                        val old = currentIconPack.value
                        currentIconPack.value = pack
                        Settings.icon_pack = pack.id
                        currentId = pack.id
                        DefaultScope.launch { Events.publish(AppEvent.IconPackChanged(pack, old)) }
                        toast(strings.icon_pack_applied)
                    },
                )
            }
        }

        if (localPacks.isEmpty()) {
            Text(
                text = stringResource(strings.icon_packs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        // زرّ إعادة نسخ الحزم المضمّنة (لو حُذفت يدويًا أو تلفت)
        Text(
            text = stringResource(strings.icon_packs_restore),
            style = MaterialTheme.typography.labelLarge,
            color = SkyAccent,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clickable {
                        DefaultScope.launch {
                            val app = com.mahdi.utils.application
                            if (app != null) {
                                BundledIconPacks.ensureInstalled(app, force = true)
                                iconPackManager.indexLocalPacks()
                                toast(strings.icon_pack_restored)
                            }
                        }
                    },
        )

        if (isArabic) {
            Text(
                text = "الحزم الثلاث مضمّنة في التطبيق: لا تحتاج إنترنت ولا تثبيتًا. أي حزمة تُثبّتها من المتجر تظهر في هذه القائمة نفسها.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun IconPackRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    samples: List<File>,
    imageLoader: coil.ImageLoader,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (selected) {
                        Modifier.border(2.dp, SkyAccent, RoundedCornerShape(14.dp))
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            samples.take(4).forEach { file ->
                Box(
                    modifier =
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = file,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) SkyAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = SkyAccent,
            )
        }
    }
}
