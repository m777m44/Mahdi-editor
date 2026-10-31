package com.mahdi.settings.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.mahdi.components.InfoBlock
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.components.compose.preferences.base.PreferenceTemplate
import com.mahdi.encoding.EncodingManager
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import java.nio.charset.Charset

/**
 * شاشة الترميز الافتراضي.
 *
 * تعرض الترميزات الأكثر استخدامًا أولًا بأسماء عربية (يبدأ بالعربية والـUTF)، ثم بقية ترميزات
 * المنصّة، مع مفتاح «كشف الترميز تلقائيًا» الذي يجعل المحرر يخمّن ترميز كل ملف عند فتحه.
 */
@Composable
fun DefaultEncoding(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.default_encoding), modifier = modifier, backArrowVisible = true) {
        var selectedEncoding by remember { mutableStateOf(Settings.encoding) }
        var autoDetect by remember { mutableStateOf(Settings.encoding_auto_detect) }

        InfoBlock(
            icon = { Icon(imageVector = Icons.Outlined.Warning, contentDescription = null) },
            text = strings.encoding_warning.getString(),
            warning = true,
        )

        // ————— الكشف التلقائي —————
        PreferenceGroup(heading = stringResource(strings.encoding_detect_title)) {
            PreferenceTemplate(
                modifier =
                    modifier.clickable(
                        indication = ripple(),
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        autoDetect = !autoDetect
                        Settings.encoding_auto_detect = autoDetect
                    },
                contentModifier = Modifier.fillMaxHeight(),
                title = { Text(fontWeight = FontWeight.Bold, text = stringResource(strings.encoding_detect_title)) },
                description = { Text(text = stringResource(strings.encoding_detect_desc)) },
                enabled = true,
                applyPaddings = true,
                startWidget = { RadioButton(selected = autoDetect, onClick = null) },
            )

            InfoBlock(
                text = "${Charset.defaultCharset().name()} — ${EncodingManager.displayName(Charset.defaultCharset())}",
            )
        }

        // ————— الترميزات الموصى بها —————
        PreferenceGroup(heading = stringResource(strings.encoding_recommended)) {
            EncodingRow(
                title = Charset.defaultCharset().name() + " (${stringResource(strings.default_option)})",
                selected = selectedEncoding == Charset.defaultCharset().name(),
                modifier = modifier,
                onClick = {
                    selectedEncoding = Charset.defaultCharset().name()
                    Settings.encoding = selectedEncoding
                },
            )

            val isArabic = LocalConfiguration.current.locales[0].language == "ar"
            EncodingManager.supported.forEach { encoding ->
                if (!encoding.name.equals(Charset.defaultCharset().name(), ignoreCase = true)) {
                    EncodingRow(
                        title = if (isArabic) encoding.labelAr else encoding.labelEn,
                        subtitle = encoding.name,
                        selected = encoding.name == selectedEncoding,
                        modifier = modifier,
                        onClick = {
                            selectedEncoding = encoding.name
                            Settings.encoding = selectedEncoding
                        },
                    )
                }
            }
        }

        // ————— بقية الترميزات —————
        val recommendedNames =
            EncodingManager.supported.map { it.name.uppercase() }.toSet() + Charset.defaultCharset().name().uppercase()
        PreferenceGroup(heading = stringResource(strings.encoding_all)) {
            EncodingManager.others(recommendedNames).forEach { charset ->
                EncodingRow(
                    title = charset.name(),
                    selected = charset.name() == selectedEncoding,
                    modifier = modifier,
                    onClick = {
                        selectedEncoding = charset.name()
                        Settings.encoding = selectedEncoding
                    },
                )
            }
        }
    }
}

@Composable
private fun EncodingRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    PreferenceTemplate(
        modifier = modifier.clickable(indication = ripple(), interactionSource = remember { MutableInteractionSource() }) { onClick() },
        contentModifier = Modifier.fillMaxHeight(),
        title = { Text(fontWeight = FontWeight.Bold, text = title) },
        description = { subtitle?.let { Text(text = it) } },
        enabled = true,
        applyPaddings = true,
        startWidget = { RadioButton(selected = selected, onClick = null) },
    )
}
