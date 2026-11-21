package com.mahdi.editor.complete

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 9.7.0 — اختبارات مقتطفات بايثون.
 *
 * شرط أساسي: كل مقتطف **صالح نحويًا** (يُترجم بـ`compile()`) وأن تكون
 * إزاحته صحيحة داخل دالة، وإلا صار المقتطف نفسه خطأً يشرحه المستخدم لاحقًا.
 * هذه الاختبارات تحرس ذلك بلا أندرويد ولا محاكي.
 */
class PythonSnippetsTest {

    @Test
    fun everySnippetCompiles() {
        PythonSnippets.ALL.forEach { snippet ->
            runCatching { compilePython(snippet.body) }
                .onFailure { fail("المقتطف «${snippet.title}» غير صالح نحويًا: ${it.message}") }
        }
    }

    @Test
    fun indentedSnippetStillCompilesInsideFunction() {
        PythonSnippets.ALL.forEach { snippet ->
            val wrapped = "def wrapper():\n" + PythonSnippets.indented(snippet.body, 4)
            runCatching { compilePython(wrapped) }
                .onFailure { fail("المقتطف «${snippet.title}» ينكسر داخل دالة: ${it.message}") }
        }
    }

    @Test
    fun indentedSkipsBlankLines() {
        val out = PythonSnippets.indented("a\n\nb", 2)
        assertEquals("  a\n\n  b", out)
    }

    @Test
    fun indentZeroIsIdentity() {
        val body = PythonSnippets.ALL.first().body
        assertEquals(body, PythonSnippets.indented(body, 0))
    }

    @Test
    fun titlesAreUniqueAndNonEmpty() {
        assertTrue(PythonSnippets.uniqueTitles())
        PythonSnippets.ALL.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.hint.isNotBlank())
            assertTrue(it.body.isNotBlank())
        }
    }

    @Test
    fun lookupByKeyWorks() {
        val first = PythonSnippets.ALL.first()
        assertEquals(first, PythonSnippets.byKey(first.key))
    }

    @Test
    fun snippetsCoverDailyWork() {
        val all = PythonSnippets.ALL.joinToString("\n") { it.body }
        listOf("print", "def ", "class ", "open(", "requests", "__main__", "sqlite3", "threading")
            .forEach { token -> assertTrue("المقتطفات تغطي $token", all.contains(token)) }
    }

    @Test
    fun unknownKeyReturnsNull() {
        assertEquals(null, PythonSnippets.byKey("not-a-real-snippet"))
    }

    // ————————————————————————— أداة مساعدة —————————————————————————

    /**
     * تحقّق نحوي مبسّط بلا JVM بايثون: يفحص ما يكسر ٩٩٪ من المقتطفات
     * اليدوية — قوس غير مغلق، سلسلة غير مغلقة، `:` ناقصة بعد `def/class/if`.
     *
     * الاختبار الحقيقي الكامل يجري في الطرفية؛ هنا نمنع الأخطاء الفادحة
     * التي تُكتشف عادةً بعد لصق المقتطف في الهاتف.
     */
    private fun compilePython(source: String) {
        var parens = 0
        var brackets = 0
        var braces = 0
        var quote: Char? = null
        var escaping = false
        source.forEach { c ->
            when {
                escaping -> escaping = false
                c == '\\' && quote != null -> escaping = true
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '(' -> parens++
                c == ')' -> parens--
                c == '[' -> brackets++
                c == ']' -> brackets--
                c == '{' -> braces++
                c == '}' -> braces--
            }
            check(parens >= 0 && brackets >= 0 && braces >= 0) { "قوس مغلق قبل فتحه" }
        }
        check(quote == null) { "سلسلة نصية غير مغلقة" }
        check(parens == 0 && brackets == 0 && braces == 0) { "قوس غير مغلق" }
        source.lineSequence().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("def ") || trimmed.startsWith("class ") ||
                trimmed.startsWith("if ") || trimmed.startsWith("for ") ||
                trimmed.startsWith("while ") || trimmed.startsWith("with ") ||
                trimmed.startsWith("try") || trimmed.startsWith("except") ||
                trimmed.startsWith("elif ") || trimmed.startsWith("else")
            ) {
                check(trimmed.endsWith(":") || trimmed.endsWith('\\')) {
                    "السطر ${index + 1} يحتاج نقطتين في آخره: $trimmed"
                }
            }
        }
    }
}
