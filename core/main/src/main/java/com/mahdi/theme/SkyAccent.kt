package com.mahdi.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahdi.text.bidiNumbers

/**
 * اللون السمائي الموحّد لشارات الحالة في Mahdi-Editor:
 * «مثبت» في المخزون، «مطبّق» للثيمات، «جاهز» للغات التي لا تحتاج تثبيتًا.
 */
val SkyAccent = Color(0xFF38BDF8)

/** نسخة أفتح قليلًا تُستخدم كخلفية للشريط الملوّن. */
val SkyAccentSoft = Color(0xFF7DD3FC)

/**
 * شارة حالة صغيرة أنيقة: خلفية سمائية شفافة + حدّ رقيق + نقطة مضيئة + نصّ واضح.
 *
 * تُستخدم من الوحدات المختلفة (الإعدادات + متجر الإضافات) فتبقى الهوية البصرية واحدة.
 */
@Composable
fun SkyBadge(
    text: String,
    modifier: Modifier = Modifier,
    showDot: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(SkyAccent.copy(alpha = 0.14f))
                .border(1.dp, SkyAccent.copy(alpha = 0.45f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (showDot) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(SkyAccent))
        }
        // 3.0.0: الأرقام داخل الشارة معزولة LTR فلا تنعكس داخل واجهة RTL
        Text(
            text = text.bidiNumbers(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = SkyAccent,
        )
    }
}
