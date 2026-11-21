package com.mahdi.git

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahdi.components.compose.utils.addIf
import com.mahdi.filetree.FileNameIcon
import com.mahdi.utils.drawErrorUnderline

/** A single changed-file row. Includes file icon, name and path. If `checked` is not null, a checkbox is rendered. */
@Composable
fun ChangesFileRow(
    change: GitChange,
    underlineColor: Color?,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            // 9.9.0: كان `getDrawerWidth() - 61.dp` — أي ٨٣٪ من **عرض الشاشة**
            // ناقص ثابت، بينما اللوحة أضيق من ذلك، فيمتدّ الصفّ خارجها وتنزاح
            // الأسماء. العرض الآن يتبع الأب مباشرةً.
            Modifier.fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        checked?.let {
            Checkbox(
                enabled = enabled,
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(20.dp),
            )
        }

        FileNameIcon(fileName = change.fileName, isDirectory = false)

        Text(
            text = change.fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = change.getColor(),
            modifier = Modifier.addIf(underlineColor != null) { drawErrorUnderline(underlineColor!!) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = change.path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
