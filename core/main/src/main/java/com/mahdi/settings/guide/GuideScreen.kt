package com.mahdi.settings.guide

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.docs.GuideContent
import com.mahdi.markdown.MarkdownText

/**
 * شاشة «دليل Mahdi-Editor» (2.8.0).
 *
 * التوثيق الكامل داخل التطبيق: البدء، المحرّر، الطرفية، Git، الثيمات، الاستوديو،
 * الحزم، الخوادم، اللغات، الترميزات، والخصوصية — كل ذلك بلا إنترنت.
 */
@Composable
fun GuideScreen(modifier: Modifier = Modifier) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    PreferenceLayout(
        label = if (isArabic) "دليل Mahdi-Editor" else "Mahdi-Editor guide",
        modifier = modifier,
        backArrowVisible = true,
        // التمرير يتولّاه عارض Markdown نفسه، فلا داعي لتمرير مزدوج
        scrollState = null,
    ) {
        MarkdownText(
            content = GuideContent.markdown(isArabic),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            scrollable = true,
            baseTextStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}
