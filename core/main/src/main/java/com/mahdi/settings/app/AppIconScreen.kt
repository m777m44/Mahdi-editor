package com.mahdi.settings.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahdi.appicon.AppIconSwitcher
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayoutLazyColumn
import com.mahdi.editor.R
import com.mahdi.resources.strings
import com.mahdi.theme.SkyAccent
import com.mahdi.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * شاشة «أيقونة التطبيق» (4.2.0).
 *
 * - **126 أيقونة مدمجة**: 14 رمزًا (أقواس الكود، الطرفية، الأقواس المعقوفة، الشبكة،
 *   لامبدا، سيجما، الفرع، السُّدُسي، الدرع، الصاعقة، الطبقات، اللانهاية، الأقواس
 *   المربّعة، النجمة) × 9 لوحات ألوان = 126.
 * - **بحث فوري** بالاسم العربي أو الإنجليزي + **تصفية بالرمز** في صفّ أفقي.
 * - المعاينة حقيقية: خلفية الأيقونة نفسها + رمزها بلونه الفعلي.
 * - التبديل فوري على الشاشة الرئيسية عبر activity-alias (بلا إعادة تثبيت).
 * - **أُزيل اختيار صورة من المعرض** في 4.2.0 بطلب من صاحب التطبيق: أندرويد لا يسمح
 *   بتغيير أيقونة التطبيق من صورة خارجية إلا عبر اختصار على الشاشة الرئيسية،
 *   فكانت الميزة مضلّلة. الآن كل الأيقونات مدمجة وحقيقية 100%.
 */

/**
 * خريطة معرّف الأيقونة → مورد رمزها.
 *
 * مكتوبة صراحةً لا عبر `getIdentifier`، فأي خطأ في اسم مورد يظهر وقت الترجمة
 * لا كأيقونة فارغة على جهاز المستخدم.
 */
private val foregroundRes: Map<String, Int> =
    mapOf(
        "code_sky" to R.drawable.ic_launcher_code_sky_foreground,
        "code_amber" to R.drawable.ic_launcher_code_amber_foreground,
        "code_mint" to R.drawable.ic_launcher_code_mint_foreground,
        "code_violet" to R.drawable.ic_launcher_code_violet_foreground,
        "code_rose" to R.drawable.ic_launcher_code_rose_foreground,
        "code_slate" to R.drawable.ic_launcher_code_slate_foreground,
        "code_cyan" to R.drawable.ic_launcher_code_cyan_foreground,
        "code_lime" to R.drawable.ic_launcher_code_lime_foreground,
        "code_magenta" to R.drawable.ic_launcher_code_magenta_foreground,
        "terminal_sky" to R.drawable.ic_launcher_terminal_sky_foreground,
        "terminal_amber" to R.drawable.ic_launcher_terminal_amber_foreground,
        "terminal_mint" to R.drawable.ic_launcher_terminal_mint_foreground,
        "terminal_violet" to R.drawable.ic_launcher_terminal_violet_foreground,
        "terminal_rose" to R.drawable.ic_launcher_terminal_rose_foreground,
        "terminal_slate" to R.drawable.ic_launcher_terminal_slate_foreground,
        "terminal_cyan" to R.drawable.ic_launcher_terminal_cyan_foreground,
        "terminal_lime" to R.drawable.ic_launcher_terminal_lime_foreground,
        "terminal_magenta" to R.drawable.ic_launcher_terminal_magenta_foreground,
        "braces_sky" to R.drawable.ic_launcher_braces_sky_foreground,
        "braces_amber" to R.drawable.ic_launcher_braces_amber_foreground,
        "braces_mint" to R.drawable.ic_launcher_braces_mint_foreground,
        "braces_violet" to R.drawable.ic_launcher_braces_violet_foreground,
        "braces_rose" to R.drawable.ic_launcher_braces_rose_foreground,
        "braces_slate" to R.drawable.ic_launcher_braces_slate_foreground,
        "braces_cyan" to R.drawable.ic_launcher_braces_cyan_foreground,
        "braces_lime" to R.drawable.ic_launcher_braces_lime_foreground,
        "braces_magenta" to R.drawable.ic_launcher_braces_magenta_foreground,
        "hash_sky" to R.drawable.ic_launcher_hash_sky_foreground,
        "hash_amber" to R.drawable.ic_launcher_hash_amber_foreground,
        "hash_mint" to R.drawable.ic_launcher_hash_mint_foreground,
        "hash_violet" to R.drawable.ic_launcher_hash_violet_foreground,
        "hash_rose" to R.drawable.ic_launcher_hash_rose_foreground,
        "hash_slate" to R.drawable.ic_launcher_hash_slate_foreground,
        "hash_cyan" to R.drawable.ic_launcher_hash_cyan_foreground,
        "hash_lime" to R.drawable.ic_launcher_hash_lime_foreground,
        "hash_magenta" to R.drawable.ic_launcher_hash_magenta_foreground,
        "lambda_sky" to R.drawable.ic_launcher_lambda_sky_foreground,
        "lambda_amber" to R.drawable.ic_launcher_lambda_amber_foreground,
        "lambda_mint" to R.drawable.ic_launcher_lambda_mint_foreground,
        "lambda_violet" to R.drawable.ic_launcher_lambda_violet_foreground,
        "lambda_rose" to R.drawable.ic_launcher_lambda_rose_foreground,
        "lambda_slate" to R.drawable.ic_launcher_lambda_slate_foreground,
        "lambda_cyan" to R.drawable.ic_launcher_lambda_cyan_foreground,
        "lambda_lime" to R.drawable.ic_launcher_lambda_lime_foreground,
        "lambda_magenta" to R.drawable.ic_launcher_lambda_magenta_foreground,
        "sigma_sky" to R.drawable.ic_launcher_sigma_sky_foreground,
        "sigma_amber" to R.drawable.ic_launcher_sigma_amber_foreground,
        "sigma_mint" to R.drawable.ic_launcher_sigma_mint_foreground,
        "sigma_violet" to R.drawable.ic_launcher_sigma_violet_foreground,
        "sigma_rose" to R.drawable.ic_launcher_sigma_rose_foreground,
        "sigma_slate" to R.drawable.ic_launcher_sigma_slate_foreground,
        "sigma_cyan" to R.drawable.ic_launcher_sigma_cyan_foreground,
        "sigma_lime" to R.drawable.ic_launcher_sigma_lime_foreground,
        "sigma_magenta" to R.drawable.ic_launcher_sigma_magenta_foreground,
        "branch_sky" to R.drawable.ic_launcher_branch_sky_foreground,
        "branch_amber" to R.drawable.ic_launcher_branch_amber_foreground,
        "branch_mint" to R.drawable.ic_launcher_branch_mint_foreground,
        "branch_violet" to R.drawable.ic_launcher_branch_violet_foreground,
        "branch_rose" to R.drawable.ic_launcher_branch_rose_foreground,
        "branch_slate" to R.drawable.ic_launcher_branch_slate_foreground,
        "branch_cyan" to R.drawable.ic_launcher_branch_cyan_foreground,
        "branch_lime" to R.drawable.ic_launcher_branch_lime_foreground,
        "branch_magenta" to R.drawable.ic_launcher_branch_magenta_foreground,
        "hexagon_sky" to R.drawable.ic_launcher_hexagon_sky_foreground,
        "hexagon_amber" to R.drawable.ic_launcher_hexagon_amber_foreground,
        "hexagon_mint" to R.drawable.ic_launcher_hexagon_mint_foreground,
        "hexagon_violet" to R.drawable.ic_launcher_hexagon_violet_foreground,
        "hexagon_rose" to R.drawable.ic_launcher_hexagon_rose_foreground,
        "hexagon_slate" to R.drawable.ic_launcher_hexagon_slate_foreground,
        "hexagon_cyan" to R.drawable.ic_launcher_hexagon_cyan_foreground,
        "hexagon_lime" to R.drawable.ic_launcher_hexagon_lime_foreground,
        "hexagon_magenta" to R.drawable.ic_launcher_hexagon_magenta_foreground,
        "shield_sky" to R.drawable.ic_launcher_shield_sky_foreground,
        "shield_amber" to R.drawable.ic_launcher_shield_amber_foreground,
        "shield_mint" to R.drawable.ic_launcher_shield_mint_foreground,
        "shield_violet" to R.drawable.ic_launcher_shield_violet_foreground,
        "shield_rose" to R.drawable.ic_launcher_shield_rose_foreground,
        "shield_slate" to R.drawable.ic_launcher_shield_slate_foreground,
        "shield_cyan" to R.drawable.ic_launcher_shield_cyan_foreground,
        "shield_lime" to R.drawable.ic_launcher_shield_lime_foreground,
        "shield_magenta" to R.drawable.ic_launcher_shield_magenta_foreground,
        "layers_sky" to R.drawable.ic_launcher_layers_sky_foreground,
        "layers_amber" to R.drawable.ic_launcher_layers_amber_foreground,
        "layers_mint" to R.drawable.ic_launcher_layers_mint_foreground,
        "layers_violet" to R.drawable.ic_launcher_layers_violet_foreground,
        "layers_rose" to R.drawable.ic_launcher_layers_rose_foreground,
        "layers_slate" to R.drawable.ic_launcher_layers_slate_foreground,
        "layers_cyan" to R.drawable.ic_launcher_layers_cyan_foreground,
        "layers_lime" to R.drawable.ic_launcher_layers_lime_foreground,
        "layers_magenta" to R.drawable.ic_launcher_layers_magenta_foreground,
        "infinity_sky" to R.drawable.ic_launcher_infinity_sky_foreground,
        "infinity_amber" to R.drawable.ic_launcher_infinity_amber_foreground,
        "infinity_mint" to R.drawable.ic_launcher_infinity_mint_foreground,
        "infinity_violet" to R.drawable.ic_launcher_infinity_violet_foreground,
        "infinity_rose" to R.drawable.ic_launcher_infinity_rose_foreground,
        "infinity_slate" to R.drawable.ic_launcher_infinity_slate_foreground,
        "infinity_cyan" to R.drawable.ic_launcher_infinity_cyan_foreground,
        "infinity_lime" to R.drawable.ic_launcher_infinity_lime_foreground,
        "infinity_magenta" to R.drawable.ic_launcher_infinity_magenta_foreground,
        "brackets_sky" to R.drawable.ic_launcher_brackets_sky_foreground,
        "brackets_amber" to R.drawable.ic_launcher_brackets_amber_foreground,
        "brackets_mint" to R.drawable.ic_launcher_brackets_mint_foreground,
        "brackets_violet" to R.drawable.ic_launcher_brackets_violet_foreground,
        "brackets_rose" to R.drawable.ic_launcher_brackets_rose_foreground,
        "brackets_slate" to R.drawable.ic_launcher_brackets_slate_foreground,
        "brackets_cyan" to R.drawable.ic_launcher_brackets_cyan_foreground,
        "brackets_lime" to R.drawable.ic_launcher_brackets_lime_foreground,
        "brackets_magenta" to R.drawable.ic_launcher_brackets_magenta_foreground,
        "bolt_sky" to R.drawable.ic_launcher_bolt_sky_foreground,
        "bolt_amber" to R.drawable.ic_launcher_bolt_amber_foreground,
        "bolt_mint" to R.drawable.ic_launcher_bolt_mint_foreground,
        "bolt_violet" to R.drawable.ic_launcher_bolt_violet_foreground,
        "bolt_rose" to R.drawable.ic_launcher_bolt_rose_foreground,
        "bolt_slate" to R.drawable.ic_launcher_bolt_slate_foreground,
        "bolt_cyan" to R.drawable.ic_launcher_bolt_cyan_foreground,
        "bolt_lime" to R.drawable.ic_launcher_bolt_lime_foreground,
        "bolt_magenta" to R.drawable.ic_launcher_bolt_magenta_foreground,
        "star_sky" to R.drawable.ic_launcher_star_sky_foreground,
        "star_amber" to R.drawable.ic_launcher_star_amber_foreground,
        "star_mint" to R.drawable.ic_launcher_star_mint_foreground,
        "star_violet" to R.drawable.ic_launcher_star_violet_foreground,
        "star_rose" to R.drawable.ic_launcher_star_rose_foreground,
        "star_slate" to R.drawable.ic_launcher_star_slate_foreground,
        "star_cyan" to R.drawable.ic_launcher_star_cyan_foreground,
        "star_lime" to R.drawable.ic_launcher_star_lime_foreground,
        "star_magenta" to R.drawable.ic_launcher_star_magenta_foreground,
    )

/** الرموز الـ14 لشريط التصفية. */
private val SYMBOL_FILTERS =
    listOf(
        "code" to ("أقواس" to "Code"),
        "terminal" to ("الطرفية" to "Terminal"),
        "braces" to ("معقوفة" to "Braces"),
        "hash" to ("شبكة" to "Hash"),
        "lambda" to ("لامبدا" to "Lambda"),
        "sigma" to ("سيجما" to "Sigma"),
        "branch" to ("فرع" to "Branch"),
        "hexagon" to ("سُدُسي" to "Hexagon"),
        "shield" to ("درع" to "Shield"),
        "bolt" to ("صاعقة" to "Bolt"),
        "layers" to ("طبقات" to "Layers"),
        "infinity" to ("لانهاية" to "Infinity"),
        "brackets" to ("مربّعة" to "Brackets"),
        "star" to ("نجمة" to "Star"),
    )

@Composable
fun AppIconScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val scope = rememberCoroutineScope()

    var currentId by remember { mutableStateOf(AppIconSwitcher.currentId(context)) }
    var query by remember { mutableStateOf("") }
    var symbolFilter by remember { mutableStateOf<String?>(null) }

    val all = AppIconSwitcher.icons
    val filtered =
        remember(query, symbolFilter) {
            val q = query.trim().lowercase()
            all.filter { icon ->
                val symbolOk = symbolFilter == null || AppIconSwitcher.symbolOf(icon.id) == symbolFilter
                val textOk =
                    q.isEmpty() ||
                        icon.nameAr.lowercase().contains(q) ||
                        icon.nameEn.lowercase().contains(q) ||
                        icon.id.contains(q)
                symbolOk && textOk
            }
        }

    PreferenceLayoutLazyColumn(label = stringResource(strings.app_icon_title), modifier = modifier) {
        item {
            Text(
                text = stringResource(strings.app_icon_desc_v2, all.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(strings.app_icon_search)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    AssistChip(
                        onClick = { symbolFilter = null },
                        label = { Text(stringResource(strings.app_icon_all)) },
                        colors =
                            if (symbolFilter == null) {
                                AssistChipDefaults.assistChipColors(labelColor = SkyAccent)
                            } else {
                                AssistChipDefaults.assistChipColors()
                            },
                    )
                }
                items(SYMBOL_FILTERS) { (symbol, names) ->
                    AssistChip(
                        onClick = { symbolFilter = if (symbolFilter == symbol) null else symbol },
                        label = { Text(if (isArabic) names.first else names.second) },
                        colors =
                            if (symbolFilter == symbol) {
                                AssistChipDefaults.assistChipColors(labelColor = SkyAccent)
                            } else {
                                AssistChipDefaults.assistChipColors()
                            },
                    )
                }
            }
        }

        // ————— الشبكة: صفوف من 3 داخل LazyColumn —————
        items(filtered.chunked(3)) { rowIcons ->
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
                                    if (icon.id == currentId) return@clickable
                                    scope.launch(Dispatchers.IO) {
                                        val ok = AppIconSwitcher.apply(context, icon.id)
                                        launch(Dispatchers.Main) {
                                            if (ok) {
                                                currentId = icon.id
                                                toast(strings.app_icon_changed)
                                            } else {
                                                toast(strings.unknown_err)
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp)
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, SkyAccent, RoundedCornerShape(16.dp))
                                    } else {
                                        Modifier
                                    }
                                ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(icon.previewBackground)),
                            contentAlignment = Alignment.Center,
                        ) {
                            foregroundRes[icon.id]?.let { res ->
                                Image(
                                    painter = painterResource(id = res),
                                    contentDescription = icon.displayName(isArabic),
                                    modifier = Modifier.size(40.dp),
                                    colorFilter = ColorFilter.tint(Color(icon.previewAccent)),
                                )
                            }
                        }
                        Spacer(Modifier.padding(3.dp))
                        Text(
                            text = icon.displayName(isArabic),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) SkyAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                repeat(3 - rowIcons.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    text = stringResource(strings.app_icon_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
