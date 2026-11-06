package com.mahdi.settings.language

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mahdi.components.compose.preferences.base.LocalIsExpandedScreen
import com.mahdi.components.compose.preferences.base.PreferenceColumn
import com.mahdi.components.compose.preferences.base.PreferenceScaffold
import com.mahdi.resources.strings
import com.mahdi.settings.lsp.LspContent

/**
 * مركز اللغات (5.5.0) — **مكان واحد** لكل ما يخصّ اللغة، بأربعة تبويبات:
 *
 * 1. **لغة الواجهة** — العربية/الإنجليزية…
 * 2. **لغات البرمجة** — المضمّنة، المثبّتة (مع ✖️ للحذف)، والمتاحة للتثبيت.
 * 3. **المشغلات** — إصدار كل مفسّر، مساره، التثبيت بشريط تقدّم حقيقي، وإعادة المحاولة.
 * 4. **خوادم اللغة** — خوادم LSP: تثبيت، تشغيل/إيقاف، ✖️ لحذف الخادم الخارجي.
 *
 * كل تبويب يعرض **المحتوى فقط** بلا فقرات شرح: الشاشة تبدأ بالتبويبات ثم القائمة
 * مباشرةً. ولا يوجد مدخل ثانٍ للغات أو المشغلات أو خوادم اللغة في أي شاشة إعدادات
 * أخرى — أُزيلت كلها في 5.5.0.
 *
 * التبويب المختار يُحفظ عبر تغيير الاتجاه/التكوين بـ [rememberSaveable].
 */
@Composable
fun LanguagesHub(modifier: Modifier = Modifier, navController: NavController? = null) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    val tabs =
        listOf(
            stringResource(strings.app_language_section),
            stringResource(strings.programming_languages),
            stringResource(strings.languages_tab_runtimes),
            stringResource(strings.languages_tab_lsp),
        )

    PreferenceScaffold(
        label = stringResource(strings.languages_hub),
        isExpandedScreen = LocalIsExpandedScreen.current,
        modifier = modifier,
        backArrowVisible = true,
    ) { paddingValues ->
        Column(Modifier.fillMaxSize()) {
            PrimaryScrollableTabRow(
                edgePadding = 8.dp,
                selectedTabIndex = selectedTab,
            ) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            PreferenceColumn(
                contentPadding =
                    PaddingValues(
                        top = 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 24.dp,
                    ),
                // weight حتى يأخذ ما تبقّى بعد التبويبات ولا يتجاوزها
                modifier = Modifier.weight(1f),
                scrollState = scrollState,
            ) {
                when (selectedTab) {
                    0 -> LanguageContent()
                    1 -> SupportedLanguagesContent()
                    2 -> RuntimesContent()
                    else ->
                        if (navController != null) {
                            LspContent(navController)
                        }
                    // بلا NavController لا يوجد مسار لتفاصيل الخادم، فلا نعرض تبويبًا ناقصًا.
                }
            }
        }
    }
}
