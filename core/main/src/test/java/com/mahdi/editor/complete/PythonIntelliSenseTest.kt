package com.mahdi.editor.complete

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 9.7.0 — اختبارات محرّك إكمال بايثون المحلي.
 *
 * كل حالة هنا وقعت فعلًا في محرّر بايثون على الهاتف: `os.pa` لا يكتمل،
 * `import ` لا يقترح وحدات، الدالة المعرَّفة في الملف نفسه لا تُكمَل،
 * ومناداة دالة بلا توقيع تُجبر المطوّر على العودة لتعريفها.
 *
 * الخريطة: النص → (المؤشر في آخره) → المتوقّع.
 */
class PythonIntelliSenseTest {

    private fun suggestAt(code: String): List<Suggestion> =
        PythonIntelliSense.suggest(code, code.length)

    private fun texts(code: String): List<String> = suggestAt(code).map { it.text }

    @Test
    fun builtinFunctionIsCompleted() {
        assertTrue("pri", "print" in texts("pri"))
    }

    @Test
    fun moduleMemberIsCompletedAfterDot() {
        // `os.pa` → os.path (عضو وحدة، لا كلمة عشوائية)
        val list = texts("import os\nos.pa")
        assertTrue(list.toString(), "path" in list)
    }

    @Test
    fun typeMethodIsCompletedForCommonTypes() {
        assertTrue("strip" in texts("s = \"x\"\ns."))
    }

    @Test
    fun functionDefinedInSameFileIsSuggested() {
        val code = "def download_file(url):\n    pass\n\ndown"
        val suggestion = suggestAt(code).firstOrNull { it.text == "download_file" }
        assertNotNull("دالة الملف يجب أن تُقترح", suggestion)
        assertEquals(SuggestionKind.FUNCTION, suggestion!!.kind)
    }

    @Test
    fun classAndVariableSymbolsAreSuggested() {
        val code = "class TelegramBot:\n    pass\n\nsession = None\nTele"
        assertTrue("TelegramBot" in texts(code))
        val vars = texts("session = None\nses")
        assertTrue(vars.toString(), "session" in vars)
    }

    @Test
    fun importedAliasIsSuggested() {
        val code = "import numpy as np\nn" // np يجب أن يظهر مع n
        assertTrue(texts(code).toString(), "np" in texts(code))
    }

    @Test
    fun fromImportNamesAreSuggested() {
        val code = "from os.path import join, exists\nexi"
        assertTrue(texts(code).toString(), "exists" in texts(code))
    }

    @Test
    fun importKeywordOffersModuleNames() {
        // بعد `import ` بلا بادئة: الوحدات الأكثر استخدامًا تظهر أولًا
        val list = texts("import re\nimport ")
        assertTrue(list.toString(), "os" in list)
        assertTrue(list.toString(), "requests" in list)
    }

    @Test
    fun importPrefixFiltersModules() {
        // وبادئة تُرشّح: `import req` ← requests فقط
        val list = texts("import req")
        assertEquals(list.toString(), listOf("requests").toList())
    }

    @Test
    fun callSignatureIsOfferedWhenParenthesisOpens() {
        val code = "def greet(name, age):\n    pass\n\ngreet("
        val s = suggestAt(code).firstOrNull { it.kind == SuggestionKind.SNIPPET }
        assertNotNull("توقيع الدالة يجب أن يُقترح عند القوس", s)
        assertEquals("greet(name, age)", s!!.insert)
    }

    @Test
    fun numbersAreNotCompleted() {
        // مؤشر داخل رقم عشري: لا اقتراحات (وإلا صار 3.14 ← 3.print)
        val code = "x = 3.14"
        assertTrue(suggestAt(code).toString(), suggestAt(code).isEmpty())
    }

    @Test
    fun emptyPrefixOnPlainNewLineSuggestsNothing() {
        // أهم حالة إزعاج: سطر جديد فارغ كان يعرض ١٢ كلمة مفتاحية فوق لوحة المفاتيح
        assertTrue(suggestAt("x = 1\n").isEmpty())
        assertTrue(suggestAt("    ").isEmpty())
    }

    @Test
    fun variableTypeIsInferredForMethodCompletion() {
        // items = [...] ← list ← items. يُقترح append
        val list = texts("items = [1, 2, 3]\nitems.")
        assertTrue(list.toString(), "append" in list)
        // نص ← str ← strip
        val str = texts("s = \"hello\"\ns.")
        assertTrue(str.toString(), "strip" in str)
    }

    @Test
    fun nonPythonTextDoesNotCrash() {
        // العربية والأقواس والرموز: لا استثناء ولا اقتراح عشوائي
        suggestAt("طباعة(س) ⚠️ @@@")
        suggestAt("")
        PythonIntelliSense.suggest("x", 99)
    }

    @Test
    fun expressionTailParsing() {
        val t = PythonIntelliSense.readExpressionTail("import os\nos.pa", 15)
        assertEquals("pa", t.prefix)
        assertEquals("os", t.receiver)
        assertTrue(t.afterImport.not())
    }

    @Test
    fun deduplicatesAndRespectsLimit() {
        val code = "import os\n" + "a = 1\n".repeat(50) + "os."
        val list = suggestAt(code)
        assertEquals("لا تكرار", list.size, list.map { it.text }.distinct().size)
        assertTrue("حد أقصى", list.size <= PythonIntelliSense.MAX)
    }

    @Test
    fun localSymbolsOutrankKeywords() {
        val code = "def printer():\n    pass\npri"
        val list = suggestAt(code).map { it.text }
        // printer (رمز ملف) قبل print (مدمجة) — الأقرب لسياق المستخدم أولًا
        assertTrue(list.toString(), list.indexOf("printer") < list.indexOf("print"))
    }
}
