package com.mahdi.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 9.6.0 — اختبارات ثبات عرض أسماء الملفات العربية والمختلطة.
 *
 * الظاهرة التي أبلغ عنها المستخدم: اسم مثل `صيد حسابات هوتميل ربط العاب.py`
 * كان يُعرض والامتداد `.py` منفصلًا في أقصى اليسار، لأن خوارزمية الاتجاه
 * تعامل `.py` كسلسلة محايدة تندمج مع ما حولها.
 *
 * الحلّ المعياري: عزل الاسم بين FSI…PDI (‏U+2068 … U+2069) — علامتان غير
 * مرئيتين تُثبّتان ترتيب الاسم وامتداده معًا. هذه الاختبارات تثبّت السلوك
 * (Snapshot) كي لا يعود العطل بصمت بعد أي تعديل.
 */
class BidiFileNameTest {

    private fun assertIsolated(name: String) {
        val shown = name.bidiFileName()
        assertEquals(BidiText.FSI, shown.first())
        assertEquals(BidiText.PDI, shown.last())
        assertEquals(name, shown.substring(1, shown.length - 1))
    }

    @Test
    fun pureArabicNameKeepsExtensionAttached() {
        assertIsolated("صيد حسابات هوتميل ربط العاب.py")
    }

    @Test
    fun mixedArabicLatinAndDigitsName() {
        assertIsolated("تقرير API 2026 v2.py")
    }

    @Test
    fun pureLatinNameIsIsolatedToo() {
        assertIsolated("main.py")
    }

    @Test
    fun snapshotArabicNamesRenderIdentically() {
        val names =
            listOf(
                "صيد حسابات هوتميل ربط العاب.py",
                "استنساخ الاصوات مهدي.py",
                "بيانات.py",
                "ملف 2026.txt",
                "README.md",
            )
        val first = names.map { it.bidiFileName() }
        val second = names.map { it.bidiFileName() }
        assertEquals(first, second)
        first.forEachIndexed { index, shown ->
            assertEquals(BidiText.FSI, shown.first())
            assertEquals(BidiText.PDI, shown.last())
            // النص المنطقي لا يتغيّر: العلامات للعرض فقط
            assertEquals(names[index], shown.substring(1, shown.length - 1))
        }
    }

    @Test
    fun storedNamesNeverCarryControlCharacters() {
        val dirty = "\u2068صيد.py\u2069"
        assertEquals("صيد.py", BidiText.stripControls(dirty))
        // العزل لا يُخزَّن: الاسم العادي يبقى كما هو
        assertEquals("صيد.py", BidiText.stripControls("صيد.py"))
    }

    @Test
    fun numbersIsolatedOnlyInArabicContext() {
        val arabic = "الملف 2026".bidiNumbers()
        assertTrue(arabic.contains("${BidiText.LRI}2026${BidiText.PDI}"))
        // النص اللاتيني لا يحتاج عزلًا ولا يُلوَّث
        assertEquals("version 2026", "version 2026".bidiNumbers())
    }

    @Test
    fun arabicDetection() {
        assertTrue(BidiText.hasArabic("صيد.py"))
        assertFalse(BidiText.hasArabic("hunt.py"))
    }
}
