package com.mahdi.settings.language

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.mahdi.components.SettingsItem
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.theme.SkyAccent

/**
 * «الأوامر» (6.0.0) — خريطة الأسماء الحقيقية والاختصارات في طرفية Mahdi.
 *
 * الأسماء الحقيقية تبقى كما هي (`python3` · `node` · `php` · `tsc`)، والاختصارات
 * القصيرة (`py` · `python` · `pip` · `js` · `ts` · `run`) ملفّات تنفيذية حقيقية
 * على `PATH` تُحلّ داخليًا إلى الاسم الحقيقي:
 *
 * - لا سطر يُعاد كتابته في السجلّ ⇒ **السجلّ يعرض ما كتبه المستخدم حرفيًا**.
 * - تعمل في كل الأصداف والسكربتات والطرفيات الفرعية، لا في الجلسة التفاعلية وحدها.
 *
 * الشاشة للقراءة فقط: تعرض الخريطة كاملة حتى يعرف المستخدم ما يعمل فعليًا.
 */
@Composable
fun TerminalAliasesContent() {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    val rows =
        listOf(
            Triple("py", "python3", if (isArabic) "تشغيل بايثون" else "Run Python"),
            Triple("python", "python3", if (isArabic) "توافق مع السكربتات القديمة" else "Legacy scripts"),
            Triple("pip", "python3 -m pip", if (isArabic) "مدير حزم بايثون" else "Python packages"),
            Triple("js", "node", if (isArabic) "تشغيل جافاسكربت" else "Run JavaScript"),
            Triple("ts", "tsc + node", if (isArabic) "ترجمة ثم تشغيل" else "Compile then run"),
            Triple("run", "universal_runner", if (isArabic) "أي ملف بأي لغة" else "Any file, any language"),
        )

    PreferenceGroup(heading = if (isArabic) "الاختصارات" else "Shortcuts") {
        rows.forEach { (shortcut, real, note) ->
            SettingsItem(
                label = shortcut,
                description = "$real · $note",
                singleLineDescription = true,
                default = false,
                showSwitch = false,
                onClick = {},
                endWidget = {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyAccent,
                        maxLines = 1,
                    )
                },
            )
        }
    }

    PreferenceGroup(heading = if (isArabic) "الأسماء الحقيقية" else "Real commands") {
        listOf(
            "python3" to (if (isArabic) "المفسّر الفعلي — لا يُستبدل" else "The real interpreter — never replaced"),
            "node" to (if (isArabic) "مشغّل جافاسكربت" else "JavaScript runtime"),
            "php" to (if (isArabic) "مفسّر PHP" else "PHP interpreter"),
        ).forEach { (name, note) ->
            SettingsItem(
                label = name,
                description = note,
                singleLineDescription = true,
                default = false,
                showSwitch = false,
                onClick = {},
            )
        }
    }

    Text(
        text =
            if (isArabic) {
                "الاختصارات ملفّات تنفيذية على PATH، فتعمل في السكربتات أيضًا — " +
                    "والسجلّ يحفظ ما كتبته حرفيًا، لا ما يُنفَّذ."
            } else {
                "Shortcuts are real executables on PATH, so they work inside scripts too — " +
                    "history keeps exactly what you typed, not what runs."
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )

    Spacer(modifier = Modifier.height(24.dp))
}
