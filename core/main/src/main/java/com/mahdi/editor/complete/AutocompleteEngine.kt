package com.mahdi.editor.complete

/** نوع الاقتراح — يحدّد ترتيبه والأيقونة المعروضة في الشريط. */
enum class SuggestionKind { WORD, FUNCTION, KEYWORD }

/** اقتراح واحد جاهز للإدراج في المحرّر. */
data class Suggestion(
    val text: String,
    val kind: SuggestionKind,
) {
    /** رمز صغير يظهر قبل الاقتراح في الشريط (بلا أيقونات إضافية). */
    val glyph: String
        get() =
            when (kind) {
                SuggestionKind.FUNCTION -> "ƒ"
                SuggestionKind.KEYWORD -> "🔑"
                SuggestionKind.WORD -> "🔤"
            }
}

/**
 * محرّك الاقتراحات المحلي (3.3.0) — يعمل بلا إنترنت وبلا خادم لغة.
 *
 * يجمع المرشّحين من ثلاثة مصادر ثم يرتّبهم:
 * 1. **كلمات الملف نفسه** (الأكثر استخدامًا أولًا) — وهذا ما يجعل الاقتراحات مفيدة فورًا
 *    في أي لغة، حتى التي لا خادم LSP لها.
 * 2. **دوال مدمجة** للغة الحالية (`print`, `console.log`, `len`…).
 * 3. **كلمات اللغة المفتاحية** (`def`, `const`, `function`…).
 *
 * كل شيء O(حجم النافذة) على جزء محدود من الملف (٣٠ ألف حرف قبل المؤشر و٤ آلاف بعده)،
 * فلا يتأثّر المحرّر بالملفات الكبيرة.
 */
object AutocompleteEngine {

    /** أقصر بادئة تُشغّل الاقتراحات — حرفان يمنعان الإزعاج عند الكتابة السريعة. */
    const val MIN_PREFIX = 2

    /** أقصى عدد اقتراحات معروضة في الشريط. */
    const val MAX_SUGGESTIONS = 10

    private const val WINDOW_BEFORE = 30_000
    private const val WINDOW_AFTER = 4_000

    /**
     * يعيد الاقتراحات المناسبة للمؤشر.
     *
     * @param text نصّ المحرّر (قد يكون `Content` في soraX، وهو CharSequence).
     * @param cursor موضع المؤشر كفهرس حرف.
     * @param languageId امتداد الملف أو معرّف اللغة (`py`, `js`, `java`…).
     */
    fun suggest(
        text: CharSequence,
        cursor: Int,
        languageId: String?,
        limit: Int = MAX_SUGGESTIONS,
    ): List<Suggestion> {
        if (cursor <= 0 || cursor > text.length) return emptyList()
        val prefix = currentPrefix(text, cursor)
        if (prefix.length < MIN_PREFIX) return emptyList()

        val key = normalize(languageId)
        val usage = HashMap<String, Int>(256)

        // ——— 1) نافذة من الملف: عدّ الكلمات ———
        val from = (cursor - WINDOW_BEFORE).coerceAtLeast(0)
        val to = (cursor + WINDOW_AFTER).coerceAtMost(text.length)
        var i = from
        while (i < to) {
            if (isIdentifierStart(text[i])) {
                val start = i
                i++
                while (i < to && isIdentifierPart(text[i])) i++
                if (i - start in 2..40) {
                    val word = text.subSequence(start, i).toString()
                    if (!word.equals(prefix, ignoreCase = true)) {
                        usage[word] = (usage[word] ?: 0) + 1
                    }
                }
            } else {
                i++
            }
        }

        val lowerPrefix = prefix.lowercase()
        val result = LinkedHashMap<String, Suggestion>(32)

        fun add(source: Iterable<String>, kind: SuggestionKind) {
            source.forEach { candidate ->
                if (result.size >= limit * 2) return@forEach
                if (candidate.length <= prefix.length) return@forEach
                if (!candidate.lowercase().startsWith(lowerPrefix)) return@forEach
                if (candidate.equals(prefix, ignoreCase = true)) return@forEach
                if (result.containsKey(candidate)) return@forEach
                result[candidate] = Suggestion(candidate, kind)
            }
        }

        // الدوال المدمجة أولًا، ثم الكلمات المفتاحية، ثم كلمات الملف (الأكثر تكرارًا)
        add(builtins[key].orEmpty(), SuggestionKind.FUNCTION)
        add(keywords[key].orEmpty(), SuggestionKind.KEYWORD)
        add(usage.entries.sortedByDescending { it.value }.map { it.key }, SuggestionKind.WORD)

        return result.values
            .sortedWith(
                compareBy<Suggestion> { kindPriority(it.kind) }
                    .thenByDescending { usage[it.text] ?: 0 }
                    .thenBy { it.text.length }
                    .thenBy { it.text }
            )
            .take(limit)
    }

    /** الكلمة الجارية قبل المؤشر (حروف/أرقام/شرطة سفلية فقط). */
    fun currentPrefix(text: CharSequence, cursor: Int): String {
        if (cursor <= 0 || cursor > text.length) return ""
        var start = cursor
        while (start > 0 && isIdentifierPart(text[start - 1])) start--
        // لا نقترح داخل رقم أو بعد نقطة عشرية
        if (start < cursor && text[start].isDigit()) return ""
        return text.subSequence(start, cursor).toString()
    }

    fun prefixStart(cursor: Int, prefix: String): Int = (cursor - prefix.length).coerceAtLeast(0)

    private fun kindPriority(kind: SuggestionKind): Int =
        when (kind) {
            SuggestionKind.FUNCTION -> 0
            SuggestionKind.KEYWORD -> 1
            SuggestionKind.WORD -> 2
        }

    private fun normalize(languageId: String?): String =
        when (languageId?.lowercase()?.removePrefix(".")) {
            "py", "python", "python3" -> "python"
            "js", "mjs", "cjs", "javascript", "jsx", "jsx2" -> "javascript"
            "ts", "tsx", "typescript" -> "typescript"
            "java" -> "java"
            "kt", "kts", "kotlin" -> "kotlin"
            "c", "h" -> "c"
            "cpp", "cc", "cxx", "hpp" -> "cpp"
            "php" -> "php"
            "rb", "ruby" -> "ruby"
            "go", "golang" -> "go"
            "rs", "rust" -> "rust"
            "lua" -> "lua"
            "sh", "bash", "shell", "zsh" -> "shell"
            else -> "generic"
        }

    private fun isIdentifierStart(c: Char): Boolean = c.isLetter() || c == '_'

    private fun isIdentifierPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    private val keywords: Map<String, List<String>> =
        mapOf(
            "python" to
                listOf(
                    "def", "class", "return", "import", "from", "as", "if", "elif", "else", "for", "while",
                    "try", "except", "finally", "with", "lambda", "yield", "raise", "pass", "break",
                    "continue", "global", "nonlocal", "assert", "del", "async", "await", "and", "or",
                    "not", "in", "is", "None", "True", "False", "self", "match", "case",
                ),
            "javascript" to
                listOf(
                    "const", "let", "var", "function", "return", "if", "else", "for", "while", "do",
                    "switch", "case", "break", "continue", "class", "extends", "super", "new", "this",
                    "import", "export", "default", "from", "async", "await", "try", "catch", "finally",
                    "throw", "typeof", "instanceof", "delete", "in", "of", "yield", "null", "undefined",
                    "true", "false", "static", "get", "set",
                ),
            "typescript" to
                listOf(
                    "const", "let", "var", "function", "return", "interface", "type", "enum", "implements",
                    "readonly", "public", "private", "protected", "abstract", "declare", "namespace",
                    "import", "export", "as", "async", "await", "class", "extends", "new", "this",
                    "null", "undefined", "true", "false", "keyof", "infer", "satisfies",
                ),
            "java" to
                listOf(
                    "public", "private", "protected", "static", "final", "class", "interface", "enum",
                    "extends", "implements", "return", "if", "else", "for", "while", "switch", "case",
                    "break", "continue", "new", "this", "super", "import", "package", "try", "catch",
                    "finally", "throw", "throws", "void", "int", "boolean", "String", "null", "true", "false",
                ),
            "kotlin" to
                listOf(
                    "fun", "val", "var", "class", "object", "interface", "data", "sealed", "enum",
                    "return", "if", "else", "when", "for", "while", "do", "try", "catch", "finally",
                    "import", "package", "override", "private", "public", "internal", "suspend", "companion",
                    "null", "true", "false", "this", "super", "is", "as", "in", "val", "lateinit",
                ),
            "c" to
                listOf(
                    "int", "char", "float", "double", "void", "struct", "union", "enum", "typedef",
                    "return", "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
                    "sizeof", "const", "static", "extern", "include", "define", "NULL",
                ),
            "cpp" to
                listOf(
                    "int", "char", "float", "double", "void", "class", "struct", "namespace", "template",
                    "typename", "public", "private", "protected", "virtual", "override", "constexpr",
                    "auto", "return", "if", "else", "for", "while", "switch", "break", "continue",
                    "new", "delete", "this", "nullptr", "true", "false", "std",
                ),
            "php" to
                listOf(
                    "function", "class", "return", "if", "else", "elseif", "foreach", "for", "while",
                    "switch", "case", "break", "continue", "echo", "print", "public", "private",
                    "protected", "static", "new", "use", "namespace", "require", "include", "null", "true", "false",
                ),
            "ruby" to
                listOf(
                    "def", "class", "module", "end", "if", "elsif", "else", "unless", "while", "until",
                    "for", "do", "begin", "rescue", "ensure", "return", "yield", "require", "require_relative",
                    "attr_accessor", "self", "nil", "true", "false",
                ),
            "go" to
                listOf(
                    "func", "package", "import", "var", "const", "type", "struct", "interface", "map",
                    "chan", "go", "defer", "return", "if", "else", "for", "range", "switch", "case",
                    "break", "continue", "select", "nil", "true", "false", "error",
                ),
            "rust" to
                listOf(
                    "fn", "let", "mut", "const", "struct", "enum", "trait", "impl", "pub", "use", "mod",
                    "match", "if", "else", "for", "while", "loop", "return", "break", "continue",
                    "Some", "None", "Ok", "Err", "self", "Self", "true", "false",
                ),
            "lua" to
                listOf(
                    "function", "local", "return", "if", "then", "elseif", "else", "end", "for", "while",
                    "repeat", "until", "do", "break", "in", "pairs", "ipairs", "require", "nil", "true", "false",
                ),
            "shell" to
                listOf(
                    "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
                    "function", "return", "export", "local", "readonly", "source", "echo", "printf",
                    "read", "exit", "set", "shift", "trap",
                ),
            "generic" to
                listOf("if", "else", "for", "while", "return", "break", "continue", "function", "class", "true", "false"),
        )

    private val builtins: Map<String, List<String>> =
        mapOf(
            "python" to
                listOf(
                    "print", "len", "range", "str", "int", "float", "bool", "list", "dict", "set", "tuple",
                    "open", "input", "type", "isinstance", "hasattr", "getattr", "enumerate", "zip", "map",
                    "filter", "sorted", "reversed", "sum", "min", "max", "abs", "round", "any", "all",
                    "super", "property", "staticmethod", "classmethod", "format", "join", "split", "strip",
                    "replace", "startswith", "endswith", "append", "extend", "items", "keys", "values",
                ),
            "javascript" to
                listOf(
                    "console.log", "console.error", "parseInt", "parseFloat", "String", "Number", "Boolean",
                    "Array", "Object", "JSON.stringify", "JSON.parse", "setTimeout", "setInterval",
                    "clearTimeout", "Promise", "Map", "Set", "Date", "Math.floor", "Math.random",
                    "document.querySelector", "addEventListener", "require", "module.exports",
                ),
            "typescript" to
                listOf(
                    "console.log", "Promise", "Array", "Object", "JSON.stringify", "JSON.parse", "Partial",
                    "Record", "Readonly", "string", "number", "boolean", "unknown", "never",
                ),
            "java" to
                listOf(
                    "System.out.println", "String.valueOf", "Integer.parseInt", "Math.max", "Math.min",
                    "ArrayList", "HashMap", "List", "Map", "Optional", "StringBuilder", "Objects.requireNonNull",
                ),
            "kotlin" to
                listOf(
                    "println", "listOf", "mutableListOf", "mapOf", "mutableMapOf", "setOf", "arrayOf",
                    "require", "check", "runCatching", "let", "also", "apply", "run", "with", "requireNotNull",
                    "toString", "toInt", "toFloat", "isEmpty", "isNotEmpty",
                ),
            "c" to listOf("printf", "scanf", "malloc", "free", "strlen", "strcpy", "memcpy", "memset", "fopen", "fclose"),
            "cpp" to
                listOf(
                    "std::cout", "std::cin", "std::vector", "std::string", "std::map", "std::unordered_map",
                    "std::make_unique", "std::make_shared", "push_back", "size", "begin", "end",
                ),
            "php" to
                listOf(
                    "echo", "print_r", "var_dump", "strlen", "str_replace", "array_map", "array_filter",
                    "count", "implode", "explode", "json_encode", "json_decode", "isset", "empty",
                ),
            "ruby" to listOf("puts", "print", "p", "gets", "each", "map", "select", "reject", "to_s", "to_i", "length"),
            "go" to listOf("fmt.Println", "fmt.Printf", "make", "append", "len", "cap", "panic", "recover", "errorf"),
            "rust" to listOf("println!", "format!", "vec!", "unwrap", "expect", "clone", "iter", "collect", "to_string"),
            "lua" to listOf("print", "pairs", "ipairs", "tostring", "tonumber", "table.insert", "string.format", "require"),
            "shell" to listOf("echo", "printf", "read", "exit", "cd", "ls", "cat", "grep", "sed", "awk", "find"),
            "generic" to listOf("print", "length", "toString"),
        )
}
