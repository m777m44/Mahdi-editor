package com.mahdi.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 9.8.0 — اختبارات استيراد ثيمات VS Code.
 *
 * المدخلات هنا **مقتطفات حقيقية** من صيغ الثيمات الثلاث التي يراها المستخدم:
 * `tmTheme` (‏plist من TextMate)، و`color-theme.json` بصيغة `colors+tokenColors`،
 * و`settings[]` بصيغة JSON.
 */
class VsCodeThemeImporterTest {

    private val vscodeJson =
        """
        {
          "name": "Monokai",
          "colors": {
            "editor.background": "#272822",
            "editor.foreground": "#F8F8F2",
            "button.background": "#75715E",
            "sideBar.background": "#1E1F1C"
          },
          "tokenColors": [
            { "scope": "comment", "settings": { "foreground": "#75715E" } }
          ]
        }
        """.trimIndent()

    private val plistTheme =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <plist version="1.0">
        <dict>
          <key>name</key><string>Quiet Light</string>
          <key>settings</key>
          <array>
            <dict>
              <key>settings</key>
              <dict>
                <key>background</key><string>#F5F5F5</string>
                <key>foreground</key><string>#333333</string>
              </dict>
            </dict>
            <dict>
              <key>scope</key><string>comment</string>
              <key>settings</key>
              <dict>
                <key>foreground</key><string>#AAAAAA</string>
                <key>fontStyle</key><string>italic</string>
              </dict>
            </dict>
          </array>
        </dict>
        </plist>
        """.trimIndent()

    private val jsonSettingsTheme =
        """
        {
          "name": "Abyss",
          "settings": [
            { "settings": { "background": "#000C18", "foreground": "#6688CC" } },
            { "scope": "string", "settings": { "foreground": "#22AA44" } }
          ]
        }
        """.trimIndent()

    @Test
    fun vscodeJsonIsParsed() {
        val imported = VsCodeThemeImporter.import(vscodeJson)
        assertNotNull(imported)
        assertEquals(0xFF272822L, imported!!.background)
        assertEquals(0xFFF8F8F2L, imported.text)
        assertTrue(imported.dark)
        assertEquals("Monokai", imported.sourceName)
        assertTrue("يجب أن تُحفظ قواعد التلوين", imported.tokens.contains("comment"))
    }

    @Test
    fun plistTmThemeIsParsed() {
        val imported = VsCodeThemeImporter.import(plistTheme)
        assertNotNull(imported)
        assertEquals(0xFFF5F5F5L, imported!!.background)
        assertEquals(0xFF333333L, imported.text)
        assertFalse("خلفية #F5F5F5 فاتحة", imported.dark)
        assertTrue(imported.tokens.contains("#AAAAAA"))
        assertTrue("اسم الثيم من plist", imported.sourceName?.contains("Quiet Light") == true)
    }

    @Test
    fun jsonSettingsArrayIsParsed() {
        val imported = VsCodeThemeImporter.import(jsonSettingsTheme)
        assertNotNull(imported)
        assertEquals(0xFF000C18L, imported!!.background)
        assertEquals(0xFF6688CCL, imported.text)
        assertTrue(imported.dark)
    }

    @Test
    fun invalidInputIsRejected() {
        assertNull(VsCodeThemeImporter.import("هذا ليس ثيمًا"))
        assertNull(VsCodeThemeImporter.import("{ تالف"))
        assertNull(VsCodeThemeImporter.import("""{"name":"x"}"""))
    }

    // ————————————————————— أدوات اللون —————————————————————

    @Test
    fun hexAcceptsAllForms() {
        assertEquals(0xFFAABBCCL, VsCodeThemeImporter.hex("#AABBCC"))
        assertEquals(0xFFAABBCCL, VsCodeThemeImporter.hex("aabbcc"))
        assertEquals(0xFFFFFFFFL, VsCodeThemeImporter.hex("#FFF"))
        assertEquals(0xFF112233L, VsCodeThemeImporter.hex("#112233FF")) // RRGGBBAA
        assertEquals(0x80112233L, VsCodeThemeImporter.hex("#80112233")) // AARRGGBB
    }

    @Test
    fun hexRejectsGarbage() {
        assertNull(VsCodeThemeImporter.hex("nope"))
        assertNull(VsCodeThemeImporter.hex("#GGHHII"))
        assertNull(VsCodeThemeImporter.hex(null))
        assertNull(VsCodeThemeImporter.hex("#1234567"))
    }

    /**
     * 9.8.0 — انحدار حقيقي: كانت `#AARRGGBB` المعتمة (‏tmTheme) تُنتج **عشرة**
     * أرقام hex لأن الكود كان يبني النصّ ثم يحوّله، فيخرج لون عشوائي مكان
     * لون الثيم. والأسوأ أن `#12345` (خمسة أرقام) كان يمرّ من فحص الطول ثم
     * ينفجر على `pair(6)`، فيسقط الاستيراد كله بدل رفض اللون وحده.
     */
    @Test
    fun hexNeverOverflowsOrThrows() {
        // معتم بصيغة tmTheme: القيمة نفسها كما هي
        assertEquals(0xFF1E1E1EL, VsCodeThemeImporter.hex("#FF1E1E1E"))
        // الأطوال الغريبة تُرفض بلا استثناء
        assertNull(VsCodeThemeImporter.hex("#12345"))
        assertNull(VsCodeThemeImporter.hex("#123456789"))
        // أربعة أرقام = RGBA مضغوط
        assertEquals(0xDDAABBCCL, VsCodeThemeImporter.hex("#ABCD"))
    }

    @Test
    fun darkDetection() {
        assertTrue(VsCodeThemeImporter.isDark("#101418"))
        assertFalse(VsCodeThemeImporter.isDark("#F5F5F5"))
        assertTrue(VsCodeThemeImporter.isDark("#272822"))
    }

    @Test
    fun lerpMovesTowardTarget() {
        val mid = VsCodeThemeImporter.lerpHex("#000000", "#FFFFFF", 0.5f)
        assertEquals("#808080", mid)
        assertEquals("#000000", VsCodeThemeImporter.lerpHex("#000000", "#FFFFFF", 0f))
        assertEquals("#FFFFFF", VsCodeThemeImporter.lerpHex("#000000", "#FFFFFF", 1f))
    }

    @Test
    fun plistRulesFindBaseAndScopedRules() {
        val rules = VsCodeThemeImporter.plistRules(plistTheme)
        val base = rules.firstOrNull { it.scope == null }
        assertNotNull("لابد من قاعدة عامة تحمل الألوان", base)
        assertEquals("#F5F5F5", base!!.background)
        assertEquals("#333333", base.foreground)
        val comment = rules.firstOrNull { it.scope?.contains("comment") == true }
        assertNotNull("لابد من قاعدة للتعليقات", comment)
        assertEquals("#AAAAAA", comment!!.foreground)
        assertEquals("italic", comment.fontStyle)
    }

    @Test
    fun plistRulesIgnoreNestedColourDictionaries() {
        // قاموس `settings` الداخلي ليس قاعدة: لو حُسب لصار عندنا 3 قواعد بدل 2
        assertEquals(2, VsCodeThemeImporter.plistRules(plistTheme).size)
    }

    @Test
    fun assetFileNamesAreTheThreeExpectedThemes() {
        // أسماء الأصول ملفات فعليّة في `assets/vscode/themes`؛ هذا الاختبار يحمي
        // الثابت من تغيير عرضيّ (إعادة تسمية تُخفي الثيمات بلا خطأ بناء).
        assertEquals(listOf("darcula.json", "abyss.json", "quiet-light.json"), VsCodeThemes.ASSET_FILES)
    }
}
