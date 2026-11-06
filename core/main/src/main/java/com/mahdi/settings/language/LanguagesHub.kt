package com.mahdi.settings.language

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mahdi.components.compose.preferences.base.LocalIsExpandedScreen
import com.mahdi.components.compose.preferences.base.PreferenceScaffold
import com.mahdi.resources.strings
import com.mahdi.settings.lsp.LspContent
import com.mahdi.theme.SkyAccent

/**
 * مركز اللغات (6.0.0) — كل ما يخصّ اللغة في شاشة واحدة، خمسة تبويبات:
 *
 * 1. **لغة الواجهة** — العربية/الإنجليزية، وعلامة ✓ على المختار.
 * 2. **لغات البرمجة** — المضمّنة · المثبّتة (✖️ للحذف) · المتاحة للتثبيت.
 * 3. **المشغلات** — إصدار كل مفسّر، مساره، التثبيت بنسبة حقيقية، وإعادة المحاولة.
 * 4. **الأوامر** — الأسماء الحقيقية والاختصارات التي تُحلّ داخليًا (`py` → `python3`).
 * 5. **خوادم اللغة** — خوادم LSP: تثبيت، تشغيل/إيقاف، ✖️ للحذف.
 *
 * ## لماذا أُعيدت كتابة هذه الشاشة (إصلاح الشاشة الفارغة)
 * النسخة السابقة كانت تبني الشاشة كـ `Column` ثم تُسند إلى القائمة `Modifier.weight(1f)`
 * داخل `PreferenceColumn`. إن جاءت قيود الارتفاع غير محدودة (`Constraints.Infinity`)،
 * فإن `weight` لا معنى له وتُعطى القائمة ارتفاعًا صفريًا — فتظهر الشاشة **فارغة تمامًا**
 * بعد العنوان، وهو ما ظهر في الجهاز الحقيقي.
 *
 * الحل: **لا أوزان ولا أعمدة متداخلة**. قائمة `LazyColumn` واحدة تملك التمرير بنفسها
 * (`fillMaxSize` داخل `Scaffold` الذي يعطي ارتفاعًا محدودًا دائمًا)، ويُثبَّت شريط
 * التبويبات في أعلاها بـ `stickyHeader` بخلفية معتمة. حتى لو تعطّل محتوى تبويب،
 * يبقى الشريط والقائمة ظاهرين — فلا يمكن أن تعود الشاشة الفارغة.
 *
 * كل تبويب يعرض **المحتوى فقط** بلا فقرات شرح، والتبويب المختار يُحفظ عبر
 * تغيير الاتجاه/التكوين بـ [rememberSaveable].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LanguagesHub(modifier: Modifier = Modifier, navController: NavController? = null) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val haptics = LocalHapticFeedback.current

    val tabs =
        listOf(
            stringResource(strings.app_language_section),
            stringResource(strings.programming_languages),
            stringResource(strings.languages_tab_runtimes),
            stringResource(strings.languages_tab_commands),
            stringResource(strings.languages_tab_lsp),
        )

    PreferenceScaffold(
        label = stringResource(strings.languages_hub),
        isExpandedScreen = LocalIsExpandedScreen.current,
        modifier = modifier,
        backArrowVisible = true,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 32.dp,
                ),
        ) {
            stickyHeader(key = "language-tabs") {
                // خلفية معتمة بلون الشاشة نفسه حتى لا يظهر المحتوى تحت الشريط أثناء التمرير
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    PrimaryScrollableTabRow(
                        edgePadding = 4.dp,
                        selectedTabIndex = selectedTab.coerceIn(0, tabs.lastIndex),
                    ) {
                        tabs.forEachIndexed { index, label ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = {
                                    if (selectedTab != index) {
                                        // ردّ لمسي خفيف عند تبديل التبويب (6.0.0)
                                        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        selectedTab = index
                                    }
                                },
                                text = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                selectedContentColor = SkyAccent,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item(key = "language-pane") {
                // Crossfade قصير: انتقال ناعم بلا إعادة قياس مفاجئة (6.0.0)
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(140),
                    label = "language-pane",
                ) { tab ->
                    Column(Modifier.fillMaxWidth()) {
                        when (tab) {
                            0 -> LanguageContent()
                            1 -> SupportedLanguagesContent()
                            2 -> RuntimesContent()
                            3 -> TerminalAliasesContent()
                            else ->
                                if (navController != null) {
                                    LspContent(navController)
                                } else {
                                    Text(
                                        text =
                                            if (isArabic) {
                                                "افتح هذه الشاشة من الإعدادات لعرض خوادم اللغة"
                                            } else {
                                                "Open this screen from Settings to manage language servers"
                                            },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(24.dp),
                                    )
                                }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
