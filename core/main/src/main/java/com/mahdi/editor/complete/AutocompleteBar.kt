package com.mahdi.editor.complete

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahdi.text.bidiNumbers
import com.mahdi.theme.SkyAccent

/**
 * شريط الاقتراحات فوق لوحة المفاتيح (3.3.0).
 *
 * صفّ أفقي من الاقتراحات المتاحة للمؤشر، يظهر بانزلاق ناعم من الأسفل،
 * ولا يحجز أي مساحة عندما لا توجد اقتراحات.
 *
 * اختيار أي اقتراح يستبدل البادئة المكتوبة به فورًا (المنطق في شاشة المحرّر).
 */
@Composable
fun AutocompleteBar(
    suggestions: List<Suggestion>,
    visible: Boolean,
    isArabic: Boolean,
    onSelect: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && suggestions.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column {
                HorizontalDivider(color = SkyAccent.copy(alpha = 0.25f))
                LazyRow(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 56.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(suggestions, key = { it.text }) { suggestion ->
                        SuggestionChip(suggestion = suggestion, isArabic = isArabic) { onSelect(suggestion) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    suggestion: Suggestion,
    isArabic: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SkyAccent.copy(alpha = 0.12f))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = suggestion.glyph, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(6.dp))
        Text(
            // الأرقام والرموز تبقى LTR داخل واجهة عربية
            text = if (isArabic) suggestion.text.bidiNumbers() else suggestion.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
