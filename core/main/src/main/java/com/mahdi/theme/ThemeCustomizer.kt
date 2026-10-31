package com.mahdi.theme

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mahdi.App.Companion.themeManager
import com.mahdi.DefaultScope
import com.mahdi.components.compose.preferences.base.PreferenceGroup
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.events.AppEvent
import com.mahdi.events.Events
import com.mahdi.resources.strings
import com.mahdi.settings.Settings
import com.mahdi.settings.editor.refreshEditors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** خانات الألوان الإحدى عشرة القابلة للتعديل. */
private enum class ColorSlot(val labelRes: Int) {
    BACKGROUND(strings.slot_background),
    SURFACE(strings.slot_surface),
    PRIMARY(strings.slot_primary),
    SECONDARY(strings.slot_secondary),
    ACCENT(strings.slot_accent),
    TEXT(strings.slot_text),
    TEXT_SECONDARY(strings.slot_text_secondary),
    OUTLINE(strings.slot_outline),
    SUCCESS(strings.slot_success),
    ERROR(strings.slot_error),
    WARNING(strings.slot_warning),
}

private fun MahdiColors.get(slot: ColorSlot): Long =
    when (slot) {
        ColorSlot.BACKGROUND -> background
        ColorSlot.SURFACE -> surface
        ColorSlot.PRIMARY -> primary
        ColorSlot.SECONDARY -> secondary
        ColorSlot.ACCENT -> accent
        ColorSlot.TEXT -> text
        ColorSlot.TEXT_SECONDARY -> textSecondary
        ColorSlot.OUTLINE -> outline
        ColorSlot.SUCCESS -> success
        ColorSlot.ERROR -> error
        ColorSlot.WARNING -> warning
    }

private fun MahdiColors.set(slot: ColorSlot, value: Long): MahdiColors =
    when (slot) {
        ColorSlot.BACKGROUND -> copy(background = value)
        ColorSlot.SURFACE -> copy(surface = value)
        ColorSlot.PRIMARY -> copy(primary = value)
        ColorSlot.SECONDARY -> copy(secondary = value)
        ColorSlot.ACCENT -> copy(accent = value)
        ColorSlot.TEXT -> copy(text = value)
        ColorSlot.TEXT_SECONDARY -> copy(textSecondary = value)
        ColorSlot.OUTLINE -> copy(outline = value)
        ColorSlot.SUCCESS -> copy(success = value)
        ColorSlot.ERROR -> copy(error = value)
        ColorSlot.WARNING -> copy(warning = value)
    }

/** يُطبّق ثيمًا مخصّصًا فورًا على التطبيق كله. */
private fun applyTheme(holder: ThemeHolder) {
    val old = themeManager.currentTheme
    themeManager.addCustomTheme(holder)
    Settings.theme = holder.id
    themeManager.ensureThemeMode(holder)
    refreshEditors()
    DefaultScope.launch { Events.publish(AppEvent.ThemeChanged(holder, old)) }
}

/**
 * شاشة «تخصيص الثيم»: دائرة ألوان HSL + 11 خانة لون + معاينة حية + فحص تباين WCAG
 * + توليد باليت + حفظ/تصدير/استيراد JSON.
 */
@Composable
fun ThemeCustomizer(navController: NavController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val base = remember { themeManager.currentTheme }

    var name by remember { mutableStateOf(base.name) }
    var style by remember { mutableStateOf(ThemeStyle.SOLID) }
    var colors by remember { mutableStateOf(base.toMahdiColors()) }
    var slot by remember { mutableStateOf(ColorSlot.PRIMARY) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val bg = Color(colors.background)
    val text = Color(colors.text)
    val textSecondary = Color(colors.textSecondary)

    PreferenceLayout(label = stringResource(strings.theme_customizer), modifier = modifier) {
        // ————— الاسم والنمط —————
        PreferenceGroup(heading = stringResource(strings.theme_details)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(strings.theme_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeStyle.entries.take(4).forEach { s ->
                    StyleChip(text = s.label, selected = style == s, onClick = { style = s })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeStyle.entries.drop(4).forEach { s ->
                    StyleChip(text = s.label, selected = style == s, onClick = { style = s })
                }
            }
        }

        // ————— دائرة الألوان —————
        PreferenceGroup(heading = stringResource(strings.color_wheel)) {
            Text(
                text = stringResource(strings.editing_color, stringResource(slot.labelRes)),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            key(slot) {
                ColorWheel(
                    initialColor = Color(colors.get(slot)),
                    onColorChange = { colors = colors.set(slot, it.toArgbLong()) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(Color(colors.get(slot)).toHexString()))
                    toast(context.getString(strings.copied))
                }) { Text("HEX") }
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(Color(colors.get(slot)).toRgbString()))
                    toast(context.getString(strings.copied))
                }) { Text("RGB") }
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(Color(colors.get(slot)).toHslString()))
                    toast(context.getString(strings.copied))
                }) { Text("HSL") }
            }
        }

        // ————— توليد باليت —————
        PreferenceGroup(heading = stringResource(strings.palette_generate)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = {
                    colors = colors.generatePalette(PaletteKind.COMPLEMENTARY)
                    toast(context.getString(strings.palette_applied))
                }) { Text(stringResource(strings.palette_complementary)) }
                OutlinedButton(onClick = {
                    colors = colors.generatePalette(PaletteKind.TRIADIC)
                    toast(context.getString(strings.palette_applied))
                }) { Text(stringResource(strings.palette_triadic)) }
            }
            OutlinedButton(
                onClick = {
                    colors = colors.generatePalette(PaletteKind.ANALOGOUS)
                    toast(context.getString(strings.palette_applied))
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text(stringResource(strings.palette_analogous)) }
        }

        // ————— الخانات الإحدى عشرة —————
        PreferenceGroup(heading = stringResource(strings.theme_colors)) {
            ColorSlot.entries.forEach { s ->
                val value = Color(colors.get(s))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { slot = s }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorSwatch(color = value, selected = slot == s)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(s.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = value.toHexString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
            }
        }

        // ————— المعاينة الحية —————
        PreferenceGroup(heading = stringResource(strings.live_preview)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = bg),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = name.ifBlank { stringResource(strings.theme_name) },
                        color = text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(strings.preview_description),
                        color = textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewButton(stringResource(strings.preview_primary), Color(colors.primary))
                        PreviewButton(stringResource(strings.preview_secondary), Color(colors.secondary))
                        PreviewButton(stringResource(strings.preview_accent), Color(colors.accent))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusDot(stringResource(strings.slot_success), Color(colors.success))
                        StatusDot(stringResource(strings.slot_warning), Color(colors.warning))
                        StatusDot(stringResource(strings.slot_error), Color(colors.error))
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(colors.surface))
                            .border(1.dp, Color(colors.outline), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = stringResource(strings.preview_surface),
                            color = text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // ————— التباين —————
        PreferenceGroup(heading = stringResource(strings.contrast_check)) {
            ContrastRow(stringResource(strings.slot_text), text, bg)
            ContrastRow(stringResource(strings.slot_text_secondary), textSecondary, bg)
            ContrastRow(stringResource(strings.slot_primary), Color(colors.primary), bg)
            ContrastRow(stringResource(strings.slot_accent), Color(colors.accent), bg)
        }

        // ————— الإجراءات —————
        PreferenceGroup(heading = stringResource(strings.theme_actions)) {
            Button(
                onClick = {
                    val spec =
                        ThemeSpec(
                            id = CustomThemes.newId(),
                            name = name.ifBlank { context.getString(strings.my_theme) },
                            style = style,
                            colors = colors,
                            dark = true,
                            extras = listOf(colors.primary, colors.accent),
                        )
                    val holder = CustomThemes.save(spec)
                    applyTheme(holder)
                    toast(context.getString(strings.theme_saved))
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text(stringResource(strings.save_theme)) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { colors = base.toMahdiColors() },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(strings.reset)) }

                OutlinedButton(
                    onClick = {
                        val spec = ThemeSpec(CustomThemes.newId(), name.ifBlank { "custom" }, style, colors)
                        clipboard.setText(AnnotatedString(CustomThemes.encode(spec)))
                        toast(context.getString(strings.theme_exported))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(strings.export_json)) }
            }

            OutlinedButton(
                onClick = {
                    val raw = clipboard.getText()?.text.orEmpty()
                    val spec = CustomThemes.decode(raw)
                    if (spec == null) {
                        toast(context.getString(strings.invalid_theme_json))
                    } else {
                        colors = spec.colors
                        name = spec.name
                        style = spec.style
                        toast(context.getString(strings.theme_imported))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text(stringResource(strings.import_json)) }
        }
    }
}

@Composable
private fun StyleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text = text, color = fg, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun PreviewButton(label: String, color: Color) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(color)
                .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (color.relativeLuminance() > 0.45f) Color.Black else Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun StatusDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContrastRow(label: String, color: Color, background: Color) {
    val ratio = contrastRatio(color, background)
    val ok = ratio >= 4.5f
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorSwatch(color = color, selected = false)
        Spacer(Modifier.width(12.dp))
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "${(ratio * 10).roundToInt() / 10f}:1  " +
                if (ok) stringResource(strings.contrast_pass) else stringResource(strings.contrast_fail),
            style = MaterialTheme.typography.labelSmall,
            color = if (ok) Color(0xFF10B981) else Color(0xFFEF4444),
        )
    }
}

// ——————————————————————————————————————————————————————————————
// توليد الباليت
// ——————————————————————————————————————————————————————————————

private enum class PaletteKind { COMPLEMENTARY, TRIADIC, ANALOGOUS }

private fun Long.toComposeColor(): Color = Color(this)

private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

private fun MahdiColors.generatePalette(kind: PaletteKind): MahdiColors {
    val baseColor = primary.toComposeColor()
    val (h, s, l) = colorToHsl(baseColor)
    return when (kind) {
        PaletteKind.COMPLEMENTARY ->
            copy(
                secondary = hslToColor(h + 180f, s, l).toArgbLong(),
                accent = hslToColor(h + 180f, (s * 0.7f).coerceIn(0f, 1f), (l + 0.18f).coerceIn(0f, 1f)).toArgbLong(),
            )

        PaletteKind.TRIADIC ->
            copy(
                secondary = hslToColor(h + 120f, s, l).toArgbLong(),
                accent = hslToColor(h + 240f, s, l).toArgbLong(),
            )

        PaletteKind.ANALOGOUS ->
            copy(
                secondary = hslToColor(h + 30f, s, l).toArgbLong(),
                accent = hslToColor(h - 30f, s, (l + 0.15f).coerceIn(0f, 1f)).toArgbLong(),
            )
    }
}
