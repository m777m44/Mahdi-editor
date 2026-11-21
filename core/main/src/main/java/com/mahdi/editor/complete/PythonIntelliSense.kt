package com.mahdi.editor.complete

/**
 * 9.7.0 — محرّك إكمال بايثون المحلي (بلا خادم لغة وبلا إنترنت).
 *
 * خادم Pyright ممتاز لكنه يحتاج Node + حزمة npm + ذاكرة 200–400 ميغابايت،
 * ولا يعمل إن لم يُثبَّت. هذا المحرّك يعمل **دائمًا**: يعرف مكتبة بايثون
 * القياسية، والوحدات الشائعة الخارجية، وأسماء الرموز المعرّفة في ملفك نفسه
 * (`def` · `class` · المتغيّرات · `import as`)، ويُكمل:
 *
 *  - `pri` → `print`           (كلمة/دالة بادئة)
 *  - `os.pa` → `os.path`       (سلسلة نقاط: وحدة ثم عضو)
 *  - `str.` → `strip · split …` (طرق النوع إن عُرِف)
 *  - `my_func(` → توقيع المعاملات
 *  - `import ` → أسماء الوحدات المتاحة
 *
 * كلّه على نافذة محدودة حول المؤشر (‏٣٠ ألف حرف) وبمقارنة بادئة واحدة،
 * فالتعقيد O(عدد المرشّحين) ولا يتأثّر بملفات ضخمة. لا يعتمد على أندرويد
 * إطلاقًا، فاختباره في `src/test` مباشر.
 */
object PythonIntelliSense {

    /** أقصى اقتراحات معروضة. */
    const val MAX = 12

    /** أقل عدد أحرف قبل الاقتراح (حرف واحد يكفي بعد نقطة أو قبل قوس). */
    const val MIN_PREFIX = 1

    private const val WINDOW_BEFORE = 30_000

    /** رتبة النوع تحدد الترتيب في الشريط. */
    private val KIND_RANK =
        mapOf(
            SuggestionKind.LOCAL to 0,
            SuggestionKind.MODULE to 1,
            SuggestionKind.FUNCTION to 2,
            SuggestionKind.KEYWORD to 3,
            SuggestionKind.SNIPPET to 4,
            SuggestionKind.WORD to 5,
        )

    /**
     * اقتراحات للمؤشر الحالي.
     *
     * @param text نصّ الملف كاملًا (فقط نافذة قبله تُقرأ).
     * @param cursor فهرس المؤشر.
     */
    fun suggest(
        text: CharSequence,
        cursor: Int,
        limit: Int = MAX,
    ): List<Suggestion> {
        if (cursor <= 0 || cursor > text.length) return emptyList()

        val tail = readExpressionTail(text, cursor)
        val prefix = tail.prefix
        // لا شيء لإكماله: مؤشر بعد مسافة/سطر جديد بلا بادئة ولا مستقبِل.
        // (كانت هذه الحالة تعرض ١٢ كلمة مفتاحية عند كل سطر جديد — إزعاج حقيقي.)
        if (prefix.isEmpty() && tail.receiver == null && !tail.afterImport) return emptyList()
        if (prefix.isNotEmpty() && prefix.length < MIN_PREFIX) return emptyList()

        val out = LinkedHashMap<String, Suggestion>(limit * 3)
        // رتبة مستقلة عن نوع الاقتراح: **رموز الملف** تتقدّم دائمًا، لأن اسمًا
        // كتبه المستخدم قبل سطرين أقرب إلى ذهنه من دالة مدمجة في المكتبة.
        val priority = HashMap<String, Int>(32)
        val lower = prefix.lowercase()

        fun offer(
            candidate: String,
            kind: SuggestionKind,
            detail: String? = null,
            isLocal: Boolean = false,
        ) {
            if (candidate.length <= prefix.length) return
            if (!candidate.lowercase().startsWith(lower)) return
            if (candidate.equals(prefix, ignoreCase = true)) return
            if (out.containsKey(candidate)) return
            out[candidate] = Suggestion(candidate, kind, detail)
            priority[candidate] = if (isLocal) -1 else (KIND_RANK[kind] ?: 9)
        }

        // ——— ١) بعد نقطة: عضو من وحدة/كائن أوَّل ———
        if (tail.receiver != null) {
            val receiver = tail.receiver
            val members =
                moduleMembers[receiver]
                    ?: typeMembers(receiver)
                    ?: typeMembers(inferType(text, cursor, receiver).orEmpty())
                    ?: objectMembers(text, cursor, receiver)
            members.orEmpty().forEach { offer(it.first, it.second, it.third) }
            return out.values.take(limit)
        }

        // ——— ١.٥) `import ` و`from x import `: أسماء الوحدات فقط ———
        // الكلمات المفتاحية هنا ضجيج: من كتب `import ` يريد اسم وحدة لا `print`.
        if (tail.afterImport) {
            MODULES.forEach { name, kind -> offer(name, kind) }
            symbolsInFile(text, cursor).forEach { offer(it.first, it.second, it.third, isLocal = true) }
            return out.values.take(limit)
        }

        // ——— ٢) رموز الملف: def/class/متغيّرات ———
        symbolsInFile(text, cursor).forEach { symbol ->
            offer(symbol.first, symbol.second, symbol.third, isLocal = true)
        }

        // ——— ٣) توقيع دالة معروفة عند فتح قوس ———
        if (tail.opensParen) {
            signatureOf(text, prefix)?.let { signature ->
                if (!out.containsKey("$prefix(")) {
                    out["$prefix("] =
                        Suggestion(
                            text = prefix,
                            kind = SuggestionKind.SNIPPET,
                            detail = signature,
                            insert = signature,
                        )
                    return out.values.take(limit)
                }
            }
        }

        // ——— ٤) الكلمات المفتاحية والدوال المدمجة ———
        PYTHON_KEYWORDS.forEach { offer(it, SuggestionKind.KEYWORD) }
        BUILTINS.forEach { offer(it, SuggestionKind.FUNCTION) }

        // ——— ٦) كلمات الملف المتكرّرة (شبكة أمان لأي اسم آخر) ———
        frequentWords(text, cursor, prefix).forEach { offer(it, SuggestionKind.WORD) }

        return out.values
            .sortedWith(
                compareBy<Suggestion> { priority[it.text] ?: 9 }
                    .thenBy { it.text.length }
                    .thenBy { it.text },
            )
            .take(limit)
    }

    // ——————————————————————————————————— تحليل السطر ———————————————————

    /** نتيجة قراءة آخر تعبير قبل المؤشر. */
    data class ExpressionTail(
        /** البادئة المكتوبة (بعد آخر نقطة إن وُجدت). */
        val prefix: String,
        /** المستقبِل قبل النقطة إن كان التعبير `recv.pref`، وإلا null. */
        val receiver: String?,
        /** هل تلي البادئة قوس مفتوح مباشرة؟ (`name(`) */
        val opensParen: Boolean,
        /** هل نحن في سياق `import` أو `from … import`؟ */
        val afterImport: Boolean,
    )

    /**
     * يقرأ آخر تعبير على السطر قبل المؤشر.
     *
     * يقبل `os.pa` (recv=os، prefix=pa) و`pri` (recv=null) و`name(` (opensParen).
     * لا يُصفّر السلسلة على نقطة عشرية في رقم (`3.14` ← prefix فارغ).
     */
    fun readExpressionTail(text: CharSequence, cursor: Int): ExpressionTail {
        var start = cursor
        val floor = (cursor - 400).coerceAtLeast(0)
        while (start > floor && isIdentPart(text[start - 1])) start--
        if (start < cursor && text[start].isDigit()) {
            return ExpressionTail("", null, false, false)
        }
        val prefix = text.subSequence(start, cursor).toString()

        val lineStart = lineStartIndex(text, cursor)
        val line = text.subSequence(lineStart, cursor).toString()
        val afterImport = IMPORT_LINE.containsMatchIn(line)

        var receiver: String? = null
        if (start > 0 && text[start - 1] == '.') {
            var rStart = start - 1
            val rFloor = (rStart - 200).coerceAtLeast(0)
            while (rStart > rFloor && isIdentPart(text[rStart - 1])) rStart--
            val name = text.subSequence(rStart, start - 1).toString()
            receiver = name.takeIf { it.isNotEmpty() }
        }

        val opensParen = cursor < text.length && text[cursor] == '('
        return ExpressionTail(prefix, receiver, opensParen, afterImport)
    }

    /** بداية السطر الحالي (بعد آخر `\n`). */
    fun lineStartIndex(text: CharSequence, cursor: Int): Int {
        var i = cursor - 1
        while (i >= 0 && text[i] != '\n') i--
        return i + 1
    }

    private fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    private val IMPORT_LINE = Regex("""(^|\s)(import|from)\s+[\w.]*$""")

    // ————————————————————————————————— رموز الملف —————————————————————————

    /**
     * يستخرج رموز الملف قبل المؤشر: أسماء `def`/`class`، والمتغيّرات المُسنَدة،
     * والمستوردة (`import x` · `from m import a, b` · `import m as n`).
     *
     * النتيجة (الاسم، النوع، الوصف) — والوصف يحمل السطر الأصلي كي يراه المستخدم
     * في الشريط (`def parse(self, text)` مثلًا).
     */
    fun symbolsInFile(text: CharSequence, cursor: Int): List<Triple<String, SuggestionKind, String?>> {
        val from = (cursor - WINDOW_BEFORE).coerceAtLeast(0)
        val window = text.subSequence(from, cursor).toString()
        val found = LinkedHashMap<String, Triple<String, SuggestionKind, String?>>()

        DEF.findAll(window).forEach { m ->
            found.putIfAbsent(m.groupValues[1], Triple(m.groupValues[1], SuggestionKind.FUNCTION, m.value.trim().take(60)))
        }
        CLASS.findAll(window).forEach { m ->
            found.putIfAbsent(m.groupValues[1], Triple(m.groupValues[1], SuggestionKind.MODULE, "class ${m.groupValues[1]}"))
        }
        ASSIGN.findAll(window).forEach { m ->
            found.putIfAbsent(m.groupValues[1], Triple(m.groupValues[1], SuggestionKind.LOCAL, "متغيّر"))
        }
        FROM_IMPORT.findAll(window).forEach { m ->
            m.groupValues[2].split(',').forEach { raw ->
                val name = raw.trim().substringBefore(" as ").trim()
                if (IDENT.matches(name)) {
                    found.putIfAbsent(name, Triple(name, SuggestionKind.MODULE, "from ${m.groupValues[1]}"))
                }
            }
        }
        IMPORT_AS.findAll(window).forEach { m ->
            val module = m.groupValues[1]
            val alias = m.groupValues[2].ifEmpty { module.substringAfterLast('.') }
            found.putIfAbsent(alias, Triple(alias, SuggestionKind.MODULE, "import $module"))
        }
        return found.values.toList()
    }

    private val IDENT = Regex("""[A-Za-z_]\w*""")
    private val DEF = Regex("""(?m)^\s*(?:async\s+)?def\s+([A-Za-z_]\w*)\s*\([^)\n]*\)?""")
    private val CLASS = Regex("""(?m)^\s*class\s+([A-Za-z_]\w*)""")
    private val ASSIGN = Regex("""(?m)^\s*([A-Za-z_]\w*)\s*(?::[^=\n]+)?=(?!=)""")
    private val FROM_IMPORT = Regex("""(?m)^\s*from\s+([\w.]+)\s+import\s+([^\n#]+)""")
    private val IMPORT_AS = Regex("""(?m)^\s*import\s+([\w.]+)(?:\s+as\s+([A-Za-z_]\w*))?""")

    /**
     * توقيع دالة/صنف مُعرَّف في الملف — يُعرض بجانب شريحة الإكمال ويُستخدم
     * لإدراج القوسين مع المعاملات عند اختيارها.
     */
    fun signatureOf(text: CharSequence, name: String): String? {
        if (!IDENT.matches(name)) return null
        val from = (text.length - WINDOW_BEFORE).coerceAtLeast(0)
        if (text.length <= from) return null
        val window = text.subSequence(from, text.length).toString()
        val m = Regex("""(?m)^\s*(?:async\s+)?def\s+${Regex.escape(name)}\s*\(([^)]*)\)""").find(window)
            ?: return null
        val params =
            m.groupValues[1]
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "self" && it != "cls" }
                .map { it.substringBefore('=').substringBefore(':').trim() }
        return "$name(${params.joinToString(", ")})"
    }

    /** أسماء الكائنات المتاحة في الملف (لاستخراج أعضائها من `obj.attr`). */
    private fun objectMembers(
        text: CharSequence,
        cursor: Int,
        receiver: String,
    ): List<Triple<String, SuggestionKind, String?>>? {
        val from = (cursor - WINDOW_BEFORE).coerceAtLeast(0)
        val window = text.subSequence(from, cursor).toString()
        val names = LinkedHashSet<String>()
        Regex("""(?m)^\s*${Regex.escape(receiver)}\.([A-Za-z_]\w*)\s*=(?!=)""").findAll(window).forEach {
            names += it.groupValues[1]
        }
        return names.map { Triple(it, SuggestionKind.LOCAL, "$receiver.$it") }.takeIf { it.isNotEmpty() }
    }

    /** الكلمات المتكرّرة في نافذة الملف — شبكة أمان أخيرة. */
    private fun frequentWords(
        text: CharSequence,
        cursor: Int,
        prefix: String,
    ): List<String> {
        val from = (cursor - WINDOW_BEFORE).coerceAtLeast(0)
        val counts = HashMap<String, Int>(128)
        var i = from
        while (i < cursor) {
            if (isIdentPart(text[i]) && !text[i].isDigit()) {
                val start = i
                i++
                while (i < cursor && isIdentPart(text[i])) i++
                if (i - start in 3..40) {
                    val w = text.subSequence(start, i).toString()
                    if (!w.equals(prefix, ignoreCase = true)) counts[w] = (counts[w] ?: 0) + 1
                }
            } else {
                i++
            }
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key }.take(40)
    }

    // ————————————————————————————————— أعضاء الوحدات ————————————————————————

    /**
     * يستنتج نوع متغيّر من آخر إسناد قبله — بلا تنفيذ ولا خادم لغة:
     * `s = "نص"` ← str · `items = [...]` ← list · `cfg = {...}` ← dict ·
     * `f = open(...)` ← file · `n = 42` ← int · `ok = True` ← bool.
     *
     * هذا ما يجعل `s.` يقترح `strip` و`items.` يقترح `append` كما في Pydroid،
     * وبتكلفة فحص سطر واحد داخل نافذة محدودة.
     */
    fun inferType(
        text: CharSequence,
        cursor: Int,
        receiver: String,
    ): String? {
        if (!IDENT.matches(receiver)) return null
        val from = (cursor - WINDOW_BEFORE).coerceAtLeast(0)
        val window = text.subSequence(from, cursor).toString()
        val m =
            Regex("""(?m)^\s*${Regex.escape(receiver)}\s*=\s*([^\n#]+)""")
                .findAll(window)
                .lastOrNull()
                ?: return null
        val value = m.groupValues[1].trim()
        return when {
            value.startsWith("\"") || value.startsWith("'") || value.startsWith("f\"") -> "str"
            value.startsWith("[") -> "list"
            value.startsWith("{") -> "dict"
            value.startsWith("(") -> "tuple"
            value.startsWith("set(") -> "set"
            value.startsWith("open(") -> "file"
            value == "True" || value == "False" -> "bool"
            value.toDoubleOrNull() != null ->
                if ('.' in value) "float" else "int"
            else -> null
        }
    }

    private fun typeMembers(receiver: String): List<Triple<String, SuggestionKind, String?>>? =
        when (receiver) {
            "str", "string" -> LIST_OF("strip", "split", "join", "replace", "format", "lower", "upper", "startswith", "endswith", "find", "count", "encode", "title")
            "list", "dict", "set", "tuple" -> LIST_OF("append", "extend", "insert", "pop", "remove", "sort", "reverse", "keys", "values", "items", "get", "update", "add", "discard")
            "int", "float" -> LIST_OF("bit_length", "to_bytes", "is_integer", "hex")
            "file", "f" -> LIST_OF("read", "readline", "readlines", "write", "writelines", "close", "flush", "seek", "tell", "name")
            else -> null
        }

    private fun LIST_OF(vararg names: String): List<Triple<String, SuggestionKind, String?>> =
        names.map { Triple(it, SuggestionKind.FUNCTION, "طريقة") }

    /** أعضاء الوحدات القياسية والشائعة: الاسم ← النوع ← وصف قصير. */
    private val moduleMembers: Map<String, List<Triple<String, SuggestionKind, String?>>> =
        mapOf(
            "os" to
                MEMBERS(
                    "path" to SuggestionKind.MODULE,
                    "getenv" to SuggestionKind.FUNCTION,
                    "environ" to SuggestionKind.LOCAL,
                    "listdir" to SuggestionKind.FUNCTION,
                    "mkdir" to SuggestionKind.FUNCTION,
                    "makedirs" to SuggestionKind.FUNCTION,
                    "remove" to SuggestionKind.FUNCTION,
                    "rename" to SuggestionKind.FUNCTION,
                    "system" to SuggestionKind.FUNCTION,
                    "getcwd" to SuggestionKind.FUNCTION,
                    "walk" to SuggestionKind.FUNCTION,
                    "stat" to SuggestionKind.FUNCTION,
                ),
            "os.path" to
                MEMBERS(
                    "join" to SuggestionKind.FUNCTION,
                    "exists" to SuggestionKind.FUNCTION,
                    "isfile" to SuggestionKind.FUNCTION,
                    "isdir" to SuggestionKind.FUNCTION,
                    "basename" to SuggestionKind.FUNCTION,
                    "dirname" to SuggestionKind.FUNCTION,
                    "abspath" to SuggestionKind.FUNCTION,
                    "splitext" to SuggestionKind.FUNCTION,
                    "expanduser" to SuggestionKind.FUNCTION,
                ),
            "sys" to
                MEMBERS(
                    "argv" to SuggestionKind.LOCAL,
                    "exit" to SuggestionKind.FUNCTION,
                    "version" to SuggestionKind.LOCAL,
                    "version_info" to SuggestionKind.LOCAL,
                    "path" to SuggestionKind.LOCAL,
                    "stdout" to SuggestionKind.LOCAL,
                    "stderr" to SuggestionKind.LOCAL,
                    "stdin" to SuggestionKind.LOCAL,
                    "platform" to SuggestionKind.LOCAL,
                ),
            "json" to
                MEMBERS(
                    "loads" to SuggestionKind.FUNCTION,
                    "dumps" to SuggestionKind.FUNCTION,
                    "load" to SuggestionKind.FUNCTION,
                    "dump" to SuggestionKind.FUNCTION,
                ),
            "re" to
                MEMBERS(
                    "match" to SuggestionKind.FUNCTION,
                    "search" to SuggestionKind.FUNCTION,
                    "findall" to SuggestionKind.FUNCTION,
                    "sub" to SuggestionKind.FUNCTION,
                    "compile" to SuggestionKind.FUNCTION,
                    "split" to SuggestionKind.FUNCTION,
                ),
            "math" to
                MEMBERS(
                    "sqrt" to SuggestionKind.FUNCTION,
                    "floor" to SuggestionKind.FUNCTION,
                    "ceil" to SuggestionKind.FUNCTION,
                    "pi" to SuggestionKind.LOCAL,
                    "inf" to SuggestionKind.LOCAL,
                    "pow" to SuggestionKind.FUNCTION,
                    "log" to SuggestionKind.FUNCTION,
                ),
            "time" to
                MEMBERS(
                    "time" to SuggestionKind.FUNCTION,
                    "sleep" to SuggestionKind.FUNCTION,
                    "strftime" to SuggestionKind.FUNCTION,
                    "localtime" to SuggestionKind.FUNCTION,
                ),
            "random" to
                MEMBERS(
                    "random" to SuggestionKind.FUNCTION,
                    "randint" to SuggestionKind.FUNCTION,
                    "choice" to SuggestionKind.FUNCTION,
                    "shuffle" to SuggestionKind.FUNCTION,
                    "seed" to SuggestionKind.FUNCTION,
                ),
            "requests" to
                MEMBERS(
                    "get" to SuggestionKind.FUNCTION,
                    "post" to SuggestionKind.FUNCTION,
                    "put" to SuggestionKind.FUNCTION,
                    "delete" to SuggestionKind.FUNCTION,
                    "Session" to SuggestionKind.MODULE,
                    "exceptions" to SuggestionKind.MODULE,
                ),
            "telebot" to
                MEMBERS(
                    "TeleBot" to SuggestionKind.MODULE,
                    "types" to SuggestionKind.MODULE,
                    "apihelper" to SuggestionKind.MODULE,
                ),
            "sqlite3" to
                MEMBERS(
                    "connect" to SuggestionKind.FUNCTION,
                    "Cursor" to SuggestionKind.MODULE,
                    "Row" to SuggestionKind.MODULE,
                ),
            "threading" to
                MEMBERS(
                    "Thread" to SuggestionKind.MODULE,
                    "Lock" to SuggestionKind.MODULE,
                    "Event" to SuggestionKind.MODULE,
                ),
            "subprocess" to
                MEMBERS(
                    "run" to SuggestionKind.FUNCTION,
                    "Popen" to SuggestionKind.MODULE,
                    "check_output" to SuggestionKind.FUNCTION,
                    "PIPE" to SuggestionKind.LOCAL,
                ),
            "datetime" to
                MEMBERS(
                    "datetime" to SuggestionKind.MODULE,
                    "date" to SuggestionKind.MODULE,
                    "timedelta" to SuggestionKind.MODULE,
                    "now" to SuggestionKind.FUNCTION,
                ),
            "pathlib" to
                MEMBERS(
                    "Path" to SuggestionKind.MODULE,
                ),
            "tkinter" to
                MEMBERS(
                    "Tk" to SuggestionKind.MODULE,
                    "Label" to SuggestionKind.MODULE,
                    "Button" to SuggestionKind.MODULE,
                    "Entry" to SuggestionKind.MODULE,
                    "mainloop" to SuggestionKind.FUNCTION,
                ),
        )

    private fun MEMBERS(vararg pairs: Pair<String, SuggestionKind>): List<Triple<String, SuggestionKind, String?>> =
        pairs.map { Triple(it.first, it.second, null) }

    /** الوحدات المتاحة أثناء `import`. */
    private val MODULES: Map<String, SuggestionKind> =
        mapOf(
            "os" to SuggestionKind.MODULE,
            "sys" to SuggestionKind.MODULE,
            "re" to SuggestionKind.MODULE,
            "json" to SuggestionKind.MODULE,
            "math" to SuggestionKind.MODULE,
            "random" to SuggestionKind.MODULE,
            "time" to SuggestionKind.MODULE,
            "datetime" to SuggestionKind.MODULE,
            "pathlib" to SuggestionKind.MODULE,
            "subprocess" to SuggestionKind.MODULE,
            "threading" to SuggestionKind.MODULE,
            "asyncio" to SuggestionKind.MODULE,
            "sqlite3" to SuggestionKind.MODULE,
            "collections" to SuggestionKind.MODULE,
            "itertools" to SuggestionKind.MODULE,
            "functools" to SuggestionKind.MODULE,
            "typing" to SuggestionKind.MODULE,
            "logging" to SuggestionKind.MODULE,
            "requests" to SuggestionKind.MODULE,
            "telebot" to SuggestionKind.MODULE,
            "telegram" to SuggestionKind.MODULE,
            "flask" to SuggestionKind.MODULE,
            "fastapi" to SuggestionKind.MODULE,
            "numpy" to SuggestionKind.MODULE,
            "pandas" to SuggestionKind.MODULE,
            "PIL" to SuggestionKind.MODULE,
            "socket" to SuggestionKind.MODULE,
            "hashlib" to SuggestionKind.MODULE,
            "base64" to SuggestionKind.MODULE,
            "csv" to SuggestionKind.MODULE,
            "shutil" to SuggestionKind.MODULE,
            "glob" to SuggestionKind.MODULE,
            "traceback" to SuggestionKind.MODULE,
            "argparse" to SuggestionKind.MODULE,
            "tkinter" to SuggestionKind.MODULE,
        )

    private val PYTHON_KEYWORDS =
        listOf(
            "def", "class", "return", "import", "from", "as", "if", "elif", "else", "for", "while",
            "try", "except", "finally", "with", "lambda", "yield", "raise", "pass", "break",
            "continue", "global", "nonlocal", "assert", "del", "async", "await", "and", "or",
            "not", "in", "is", "None", "True", "False", "self", "match", "case",
        )

    private val BUILTINS =
        listOf(
            "print", "len", "range", "enumerate", "zip", "map", "filter", "sorted", "reversed",
            "sum", "min", "max", "abs", "round", "any", "all", "open", "input", "type", "isinstance",
            "hasattr", "getattr", "setattr", "repr", "id", "hash", "dir", "vars", "iter", "next",
            "str", "int", "float", "bool", "list", "dict", "set", "tuple", "bytes", "bytearray",
            "super", "property", "staticmethod", "classmethod",
        )
}
