package com.mahdi.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 9.6.0 — اختبار تشخيص عطل الربط الديناميكي لبايثون.
 *
 * العطل الحقيقي الذي أبلغ عنه المستخدم:
 * `CANNOT LINK EXECUTABLE "…/Pyahmed.so": library "libpython3.11.so.1.0"
 *  not found: needed by main executable`
 *
 * الهدف: ألّا يمرّ هذا النص إلى المستخدم خامًا أبدًا، وأن يُصنَّف بدقة (فلا
 * يُخلط بخطأ سكربت عادي ولا بفشل شبكة).
 */
class PythonLinkDiagnosticsTest {

    private val androidError =
        "CANNOT LINK EXECUTABLE \"/data/user/0/com.mahdi.editor/tmp/tmpl12rec_h/Pyahmed.so\": " +
            "library \"libpython3.11.so.1.0\" not found: needed by main executable"

    private val glibcError =
        "python3: error while loading shared libraries: libpython3.12.so.1.0: " +
            "cannot open shared object file: No such file or directory"

    @Test
    fun androidLinkerMessageIsDetected() {
        assertTrue(PythonLinkDiagnostics.isLinkError(androidError))
    }

    @Test
    fun glibcLoaderMessageIsDetected() {
        assertTrue(PythonLinkDiagnostics.isLinkError(glibcError))
    }

    @Test
    fun ordinaryErrorsAreNotLinkErrors() {
        assertFalse(PythonLinkDiagnostics.isLinkError("ModuleNotFoundError: No module named 'telegram'"))
        assertFalse(PythonLinkDiagnostics.isLinkError("Traceback (most recent call last):\nZeroDivisionError"))
        assertFalse(PythonLinkDiagnostics.isLinkError(""))
        assertFalse(PythonLinkDiagnostics.isLinkError(null))
    }

    @Test
    fun sonameAndVersionAreExtracted() {
        assertEquals("libpython3.11.so.1.0", PythonLinkDiagnostics.soname(androidError))
        assertEquals("3.11", PythonLinkDiagnostics.version(androidError))
        assertEquals("libpython3.12.so.1.0", PythonLinkDiagnostics.soname(glibcError))
        assertEquals("3.12", PythonLinkDiagnostics.version(glibcError))
    }

    @Test
    fun missingSonameFallsBackSafely() {
        assertEquals("libpython", PythonLinkDiagnostics.soname("CANNOT LINK EXECUTABLE foo"))
        assertNull(PythonLinkDiagnostics.version("no version here"))
    }

    @Test
    fun arabicReasonHasOneCauseAndOneAction() {
        val reason = PythonLinkDiagnostics.arabicReason(androidError)
        requireNotNull(reason)
        assertTrue("السبب يجب أن يذكر اسم المكتبة", reason.contains("libpython3.11.so.1.0"))
        assertTrue("الإجراء يجب أن يكون mh pyfix", reason.contains("mh pyfix"))
        // لا نص إنجليزي خام يصل إلى المستخدم
        assertFalse(reason.contains("CANNOT LINK"))
        // سطر واحد فقط: لا سطرين متعارضين
        assertFalse(reason.contains("\n"))
    }

    @Test
    fun englishVariantIsOffered() {
        val reason = PythonLinkDiagnostics.arabicReason(androidError, isArabic = false)
        requireNotNull(reason)
        assertTrue(reason.contains("mh pyfix"))
    }

    @Test
    fun nonLinkOutputHasNoReason() {
        assertNull(PythonLinkDiagnostics.arabicReason("hello world"))
        assertNull(PythonLinkDiagnostics.arabicReason(null))
    }
}
