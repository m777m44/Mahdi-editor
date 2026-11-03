package com.mahdi.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mahdi.resources.strings

/**
 * نافذة تأكيد موحّدة (4.0.0).
 *
 * تُستخدم في كل عملية حذف داخل التطبيق (ثيم، لغة، حزمة…) بنفس الشكل والنصوص،
 * فلا تختلف النوافذ من شاشة لأخرى. زرّ الإلغاء هو الافتراضي في RTL وبعيد عن الإبهام.
 *
 * @param title العنوان («إزالة الثيم»)
 * @param message شرح ما سيُحذف فعليًا، مع اسم العنصر
 * @param confirmLabel نص زرّ التأكيد (افتراضيًا «موافق»)
 * @param dismissLabel نص زرّ الإلغاء (افتراضيًا «إلغاء»)
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    dismissLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel ?: stringResource(strings.ok),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissLabel ?: stringResource(strings.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
