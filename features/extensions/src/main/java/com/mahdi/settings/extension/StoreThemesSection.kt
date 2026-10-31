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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahdi.App.Companion.themeManager
import com.mahdi.theme.CustomThemes
import com.mahdi.resources.drawables
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import com.mahdi.settings.editor.refreshEditors
import com.mahdi.theme.SkyAccent
import com.mahdi.theme.SkyBadge
import com.mahdi.theme.FeaturedTheme
import com.mahdi.theme.StoreThemeCatalog
import com.mahdi.theme.toThemeHolder
import com.mahdi.utils.toast

/**
 * «ثيمات مميزة» داخل متجر الإضافات (2.7.0).
 *
 * صفّ بطاقات مصغّرة يعاين كل ثيم بألوانه الحقيقية (خلفية + شريط أساسي + تمييز)،
 * وبضغطة واحدة يُثبَّت الثيم في «المخزون» ويُطبَّق فورًا على الواجهة والمحرّر والطرفية،
 * ويبقى متاحًا في شاشة الثيمات بعد إعادة تشغيل التطبيق.
 */
@Composable
fun FeaturedThemesSection(query: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    var installedIds by remember {
        mutableStateOf(
            (CustomThemes.themes.map { it.id } + themeManager.loadedThemesState.value.map { it.id }).toSet()
        )
    }
    var appliedId by remember { mutableStateOf(Settings.theme) }

    val themes =
        remember(query) {
            StoreThemeCatalog.themes.filter { theme ->
                query.isBlank() ||
                    theme.nameAr.contains(query, ignoreCase = true) ||
                    theme.nameEn.contains(query, ignoreCase = true) ||
                    theme.source.contains(query, ignoreCase = true)
            }
        }

    fun install(theme: FeaturedTheme) {
        runCatching {
                CustomThemes.save(theme.spec)
                themeManager.addCustomTheme(theme.spec.toThemeHolder())
                installedIds = installedIds + theme.id
            }
            .onFailure { toast(strings.unknown_err) }
    }

    fun apply(theme: FeaturedTheme) {
        runCatching {
                if (theme.id !in installedIds) install(theme)
                Settings.theme = theme.id
                themeManager.ensureThemeMode(theme.spec.toThemeHolder())
                refreshEditors()
                appliedId = theme.id
            }
            .onFailure { toast(strings.unknown_err) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🎨 " + stringResource(strings.featured_themes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${themes.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(themes, key = { it.id }) { theme ->
                val colors = theme.spec.colors
                val isApplied = appliedId == theme.id
                val isInstalled = theme.id in installedIds

                Card(
                    modifier = Modifier.width(150.dp).clickable { apply(theme) },
                    shape = RoundedCornerShape(18.dp),
                    colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isApplied) 6.dp else 1.dp),
                ) {
                    Column {
                        // معاينة حقيقية لألوان الثيم
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(84.dp)
                                    .background(Color(colors.background))
                                    .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(10.dp)
                                    .background(Color(colors.primary), RoundedCornerShape(6.dp))
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(0.62f)
                                    .height(7.dp)
                                    .background(Color(colors.accent), RoundedCornerShape(4.dp))
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(0.74f)
                                    .height(7.dp)
                                    .background(Color(colors.textSecondary), RoundedCornerShape(4.dp))
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(0.45f)
                                    .height(7.dp)
                                    .background(Color(colors.primary).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            )
                        }

                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                text = if (isArabic) theme.nameAr else theme.nameEn,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = theme.source,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )

                            Spacer(Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when {
                                    isApplied -> SkyBadge(stringResource(strings.applied_state))
                                    isInstalled -> SkyBadge(stringResource(strings.installed_state))
                                    else -> TextButton(
                                        onClick = { install(theme) },
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(drawables.download),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(strings.install_action),
                                            style = MaterialTheme.typography.labelSmall,
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
}

/** بطاقة عدّاد صغيرة تُستخدم أعلى تبويب الثيمات. */
@Composable
fun ThemesHeaderCard(modifier: Modifier = Modifier) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val installedStore = StoreThemeCatalog.themes.size

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SkyAccent.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "🎨", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(strings.featured_themes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text =
                        if (isArabic) {
                            "$installedStore ثيمًا مميزًا بضغطة واحدة — تُثبَّت في المخزون وتُطبَّق فورًا"
                        } else {
                            "$installedStore featured themes, one tap — installed to inventory and applied instantly"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
