package com.mahdi.editor.complete

/**
 * 9.8.0 — «تحسين الكود» لمحرّر بايثون (نظير Auto Import + Reformat في Pydroid).
 *
 * عملٌ **نقيّ** على النصّ: لا أندرويد ولا ملفات، فيُختبر مباشرة في `src/test`.
 *
 * الميزتان اللتان تصنعان فرق Pydroid الحقيقي في الهاتف:
 *
 * 1. **الاستيراد التلقائي** — تكتب `requests.get(...)` أو `json.loads(...)`
 *    ويُضاف `import requests` في الأعلى. أشهر سبب لـ `NameError` عند
 *    المبتدئين، ويُصلَح هنا **قبل** التشغيل لا بعده.
 * 2. **ترتيب الاستيرادات** — كل `import` في مكانه: القياسية، ثم الخارجية،
 *    ثم المحلية، بلا تكرار وبلا سطر فارغ زائد. هذا ما تفعله أدوات مثل isort،
 *    لكنه هنا محليّ تمامًا.
 *
 * ما **لا** نفعله: لا نُعيد تنسيق الأسطر كلها (ذلك يفسد سلاسل نصّية ومصفوفات
 * وكتل `match`)، ولا نحذف سطرًا كتبه المستخدم. التغيير محدود ومرئي.
 */
object PythonAutoImport {

    /** وحدة ← أسماء تدلّ عليها. الأطول أولًا عند المطابقة لأخذ الأخصّ. */
    private val MODULE_HINTS: Map<String, List<String>> =
        linkedMapOf(
            "requests" to listOf("requests"),
            "json" to listOf("json"),
            "math" to listOf("math"),
            "random" to listOf("random"),
            "time" to listOf("time"),
            "datetime" to listOf("datetime"),
            "pathlib" to listOf("Path", "PurePath"),
            "re" to listOf("re"),
            "sys" to listOf("sys"),
            "os" to listOf("os", "environ", "getcwd", "makedirs", "listdir"),
            "asyncio" to listOf("asyncio", "await_"),
            "threading" to listOf("threading", "Thread"),
            "subprocess" to listOf("subprocess"),
            "sqlite3" to listOf("sqlite3"),
            "typing" to listOf("typing"),
            "logging" to listOf("logging"),
            "collections" to listOf("collections", "defaultdict", "Counter", "deque", "namedtuple"),
            "itertools" to listOf("itertools"),
            "functools" to listOf("functools", "reduce", "lru_cache"),
            "hashlib" to listOf("hashlib"),
            "base64" to listOf("base64"),
            "csv" to listOf("csv"),
            "shutil" to listOf("shutil"),
            "glob" to listOf("glob"),
            "traceback" to listOf("traceback"),
            "socket" to listOf("socket"),
            "numpy" to listOf("numpy", "np"),
            "pandas" to listOf("pandas", "pd"),
            "PIL" to listOf("PIL", "Image"),
            "flask" to listOf("flask", "Flask"),
            "telebot" to listOf("telebot"),
            "telegram" to listOf("telegram"),
            "tkinter" to listOf("tkinter", "Tk"),
        )

    /** استيرادات خاصة تُضاف بصيغة `from x import y`. */
    private val FROM_IMPORTS: Map<String, String> =
        mapOf(
            "Path" to "from pathlib import Path",
            "Image" to "from PIL import Image",
            "Flask" to "from flask import Flask",
            "defaultdict" to "from collections import defaultdict",
            "Counter" to "from collections import Counter",
            "deque" to "from collections import deque",
            "namedtuple" to "from collections import namedtuple",
            "reduce" to "from functools import reduce",
            "Tk" to "from tkinter import Tk",
        )

    /** نتيجة التحليل: قائمة الاستيرادات الناقصة المطلوب إضافتها. */
    data class ImportScan(
        val missing: List<String>,
        /** هل النصّ يحتوي على أي استخدام يحتاج استيرادًا؟ */
        val hasUsage: Boolean,
    )

    /**
     * يكشف ما ينقص من استيرادات.
     *
     * @param code نصّ الملف.
     * @param caret مؤشر المستخدم — لا نُقترح ما لم يُكتب بعدُ؛ نفحص النصّ كاملًا
     *   لأن الترتيب يجب أن يعرف كل الاستيرادات الموجودة.
     */
    fun scan(code: String): ImportScan {
        val imported = importedModules(code)
        val used = usedIdentifiers(code)
        val missing = LinkedHashSet<String>()

        used.forEach { token ->
            // اسم مقتبس بـfrom يعرف وحدة النصّ نفسه، فلا يُكرَّر
            val statement =
                FROM_IMPORTS[token]
                    ?: MODULE_HINTS.entries.firstOrNull { (_, names) -> token in names }?.let { (module, _) -> "import $module" }
            if (statement == null) return@forEach
            val module = statement.substringAfter(if (statement.startsWith("from")) "from " else "import ")
                .substringBefore(" import").substringBefore(" as ").trim()
            // مستورد جزئيًا (‏from json import dumps) يعني أن الوحدة متاحة
            if (module in imported) return@forEach
            missing += statement
        }

        return ImportScan(missing.toList(), used.isNotEmpty())
    }

    /**
     * يُضيف الاستيرادات الناقصة في موضعها الصحيح.
     *
     * الموضع: بعد آخر سطر `import`/`from` قائم إن وُجد، وبعد docstring الوحدة
     * إن لم يوجد، وفي أوّل الملف بعد `#!`/ترميز إن لم يكن هناك أي منهما.
     *
     * @return نصّ جديد، أو نفس النصّ إن لم يكن هناك ما يُضاف.
     */
    fun addMissing(code: String): String {
        val scan = scan(code)
        if (scan.missing.isEmpty()) return code
        val lines = code.lines().toMutableList()
        val insertAt = importInsertionLine(lines)
        lines.addAll(insertAt, scan.missing)
        return lines.joinToString("\n")
    }

    /**
     * يُرتّب كل كتل الاستيراد في الملف: قياسية ← خارجية ← محلية، وبلا تكرار.
     * يحافظ على مقدّمة الملف (`#!` · `# -*- coding -*-`) وعلى أي تعليق داخلي.
     */
    fun sortImports(code: String): String {
        val lines = code.lines().toMutableList()
        // ‏**استيرادات المستوى الأعلى فقط.** `import requests` داخل دالة أو
        // داخل `if` **موضعي بقصد** (تأخير تحميل، أو استيراد اختياري في
        // try/except). رفعها إلى أعلى الملف يغيّر معنى البرنامج — لذا
        // نستبعد كل سطر مُزاح، ونرتّب ما في العمود صفر وحده.
        val importLines = lines.filter { isTopLevelImport(it) }
        if (importLines.size < 2) return code

        val sorted = sortImportLines(importLines)
        if (sorted == importLines) return code

        // نستبدل **أول** سطر استيراد بالكتلة المرتّبة ونحذف الباقي، فالموضع
        // يبقى في مكانه العلوي ولا تنزل الاستيرادات إلى وسط الكود.
        var replaced = false
        val out = mutableListOf<String>()
        fun flush() {
            if (replaced) return
            out.addAll(sorted)
            replaced = true
        }
        lines.forEach { line ->
            if (isTopLevelImport(line)) {
                if (!replaced) {
                    flush()
                } // وإلا: تُحذف لأنها نُقلت إلى الكتلة المرتّبة
            } else {
                out += line
            }
        }
        if (out.isEmpty()) return code
        // تنظيف أسطر فارغة متتالية نتجت عن الحذف: نتجاوز عن أكثر من سطرين
        val cleaned = mutableListOf<String>()
        var blankRun = 0
        out.forEach { line ->
            if (line.isBlank()) {
                blankRun++
                if (blankRun <= 2) cleaned += line
            } else {
                blankRun = 0
                cleaned += line
            }
        }
        return cleaned.joinToString("\n")
    }

    /**
     * ترتيب أسطر الاستيراد: القياسية، ثم الخارجية، ثم المحلية/النسبية، ثم أبجديًا.
     * يُستخرج كدالة مستقلة لأن الاختبارات تتحقّق من الترتيب وحده.
     */
    fun sortImportLines(imports: List<String>): List<String> {
        val cleaned = imports.map { it.trimEnd() }.filter { it.isNotBlank() }.distinct()
        return cleaned.sortedWith(
            compareBy(
                { groupOf(it) },
                { it.substringAfter("import ").substringAfter("from ").trim().trimStart('.').lowercase() },
                { it },
            ),
        )
    }

    /** 0 = قياسية · 1 = خارجية · 2 = محلية/نسبية. */
    fun groupOf(importLine: String): Int {
        val statement = importLine.trim()
        val module =
            if (statement.startsWith("from ")) {
                statement.removePrefix("from ").trim().substringBefore(' ').substringBefore(" import")
            } else {
                statement.removePrefix("import ").trim().substringBefore(' ').substringBefore(',')
            }
        if (module.startsWith(".")) return 2
        return when {
            module in STDLIB -> 0
            module in LOCAL_ROOTS -> 2
            else -> 1
        }
    }

    /** وحدات بايثون القياسية الشائعة (القائمة الكاملة غير لازمة للترتيب). */
    private val STDLIB: Set<String> =
        setOf(
            "abc", "argparse", "array", "asyncio", "base64", "bisect", "builtins", "calendar",
            "collections", "concurrent", "contextlib", "copy", "csv", "ctypes", "dataclasses",
            "datetime", "decimal", "difflib", "email", "enum", "errno", "fnmatch", "fractions",
            "functools", "gc", "getpass", "glob", "gzip", "hashlib", "heapq", "hmac", "html",
            "http", "imaplib", "importlib", "inspect", "io", "ipaddress", "itertools", "json",
            "keyword", "linecache", "locale", "logging", "math", "mimetypes", "multiprocessing",
            "operator", "os", "pathlib", "pickle", "platform", "pprint", "profile", "queue",
            "random", "re", "secrets", "select", "shelve", "shlex", "shutil", "signal",
            "smtplib", "socket", "sqlite3", "ssl", "stat", "statistics", "string", "struct",
            "subprocess", "sys", "tarfile", "tempfile", "textwrap", "threading", "time",
            "timeit", "tkinter", "traceback", "types", "typing", "unicodedata", "unittest",
            "urllib", "uuid", "venv", "warnings", "weakref", "xml", "zipfile", "zlib",
        )

    /** جذور تُعدّ «محلية» في المشاريع الشائعة (تُرتَّب مع النسبية). */
    private val LOCAL_ROOTS: Set<String> = setOf("app", "core", "src", "utils", "tests", "test")

    /**
     * الأسماء المستخدمة في الكود، بعد استثناء ما هو **مُستورَد** أو **معرَّف**
     * أو **مرفق بنقطة** (`x.json` ليس استخدامًا لـ`json`) أو **داخل نصّ/تعليق**.
     */
    fun usedIdentifiers(code: String): Set<String> {
        val stripped = stripStringsAndComments(code)
        val imported = importedNames(code)
        val defined =
            (
                DEFINITION.findAll(stripped).map { it.groupValues[1] } +
                    ASSIGNMENT.findAll(stripped).map { it.groupValues[1] } +
                    DEF_PARAMS.findAll(stripped).flatMap { entry ->
                        PARAM_NAME.findAll(entry.groupValues[1]).map { it.value }
                    }
            )
                .filter { it.isNotEmpty() }
                .toSet()
        val out = LinkedHashSet<String>()
        IDENTIFIER.findAll(stripped).forEach { match ->
            val name = match.value
            if (name.length < 2) return@forEach
            if (name in imported || name in defined) return@forEach
            if (name in PYTHON_KEYWORDS) return@forEach
            // مرفق بنقطة: `requests.get` ✔ تحتسب · `self.requests` ✘ (عضو)
            val before = stripped.getOrNull(match.range.first - 1)
            if (before == '.') return@forEach
            out += name
        }
        return out
    }

    // ————————————————————————————————— مساعدات —————————————————————————————————

    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
    /** تعريفات الدالة/الصنف — تُستثنى من «المستخدم» لأنها معرَّفة لا مستوردة. */
    private val DEFINITION = Regex("""(?:^|\s)(?:def|class)\s+([A-Za-z_][A-Za-z0-9_]*)""")

    /** المتغيّرات المعرَّفة بتخصيص (`name = …`). */
    private val ASSIGNMENT = Regex("""(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)\s*(?::[^=\n]+)?=(?!=)""")

    /**
     * أسماء معاملات الدوال داخل قوسي التعريف.
     *
     * لماذا؟ لأن `def __init__(self, json):` تُعرّف اسمًا اسمه `json`؛ بدون
     * هذا الاستثناء نطلب `import json` لمجرّد أن المستخدم سمّى معاملًا بذلك.
     */
    private val DEF_PARAMS = Regex("""(?:def|lambda)\s+[A-Za-z_][A-Za-z0-9_]*\s*\(([^)]*)\)""")
    private val PARAM_NAME = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

    private val PYTHON_KEYWORDS =
        setOf(
            "def", "class", "return", "import", "from", "as", "if", "elif", "else", "for", "while",
            "try", "except", "finally", "with", "lambda", "yield", "raise", "pass", "break",
            "continue", "global", "nonlocal", "assert", "del", "async", "await", "and", "or",
            "not", "in", "is", "None", "True", "False", "self", "match", "case",
            // المدمجة لا تُستورد
            "print", "len", "range", "enumerate", "zip", "map", "filter", "sorted", "reversed",
            "sum", "min", "max", "abs", "round", "any", "all", "open", "input", "type", "isinstance",
            "str", "int", "float", "bool", "list", "dict", "set", "tuple", "bytes", "object",
            "super", "property", "staticmethod", "classmethod", "Exception", "ValueError",
            "TypeError", "KeyError", "IndexError", "RuntimeError", "StopIteration", "format",
            "repr", "id", "hash", "dir", "vars", "iter", "next", "getattr", "setattr", "hasattr",
        )

    /** الوحدات المستوردة بأي صيغة (`import x`, `import x as y`, `from x import y`). */
    fun importedModules(code: String): Set<String> {
        val out = hashSetOf<String>()
        IMPORT_LINE.findAll(code).forEach { match ->
            val statement = match.value.trim()
            if (statement.startsWith("import ")) {
                statement.removePrefix("import ").split(',').forEach { part ->
                    val module = part.trim().substringBefore(" as ").trim()
                    if (module.isNotEmpty()) out += module.substringBefore('.')
                }
            } else if (statement.startsWith("from ")) {
                val module = statement.removePrefix("from ").trim().substringBefore(' ').trim()
                if (module.isNotEmpty() && !module.startsWith(".")) out += module.substringBefore('.')
            }
        }
        return out
    }

    /** الأسماء المستوردة صراحةً (`import os as o` ← o، `from json import loads` ← loads). */
    private fun importedNames(code: String): Set<String> {
        val out = hashSetOf<String>()
        IMPORT_LINE.findAll(code).forEach { match ->
            val statement = match.value.trim()
            if (statement.startsWith("import ")) {
                statement.removePrefix("import ").split(',').forEach { part ->
                    val pieces = part.trim().split(" as ")
                    if (pieces.size == 2) out += pieces[1].trim() else out += pieces[0].trim().substringBefore('.')
                }
            } else if (statement.startsWith("from ")) {
                statement.substringAfter(" import ").split(',').forEach { part ->
                    out += part.trim().substringBefore(" as ").trim()
                }
            }
        }
        return out
    }

    private val IMPORT_LINE = Regex("""(?m)^\s*(?:import\s+\S.*|from\s+\S+\s+import\s+.*)$""")

    fun isImportLine(line: String): Boolean =
        line.trim().let { it.startsWith("import ") || it.startsWith("from ") } && !it.trimStart().startsWith("#")

    /**
     * هل السطر استيراد **في العمود صفر**؟
     *
     * المزاح استيراد موضعي مقصود — لا يُرتَّب ولا يُنقل. هذا هو الفرق بين
     * أداة تُصلح الكود وأداة تُفسده.
     */
    fun isTopLevelImport(line: String): Boolean = line.isNotEmpty() && !line[0].isWhitespace() && isImportLine(line)

    /** سطر الإدراج: بعد آخر استيراد، أو بعد المقدّمة/docstring. */
    fun importInsertionLine(lines: List<String>): Int {
        val lastImport = lines.indexOfLast { isImportLine(it) }
        if (lastImport >= 0) return lastImport + 1

        var i = 0
        // مقدّمة: shebang، ترميز، تعليقات أوّلية
        while (i < lines.size && (lines[i].startsWith("#!") || lines[i].trimStart().startsWith("#") || lines[i].isBlank())) {
            i++
        }
        // docstring وحدة
        if (i < lines.size) {
            val trimmed = lines[i].trimStart()
            if (trimmed.startsWith("\"\"\"") || trimmed.startsWith("'''")) {
                val quote = trimmed.take(3)
                // إن كان يفتح ويغلق في نفس السطر
                val rest = trimmed.removePrefix(quote)
                if (rest.contains(quote)) {
                    i += 1
                } else {
                    i += 1
                    while (i < lines.size && !lines[i].contains(quote)) i++
                    if (i < lines.size) i += 1
                }
            }
        }
        return i.coerceIn(0, lines.size)
    }

    /** يُزيل السلاسل النصّية والتعليقات حتى لا نقرأ `"json"` كاستخدام. */
    fun stripStringsAndComments(code: String): String {
        val sb = StringBuilder(code.length)
        var i = 0
        val n = code.length
        while (i < n) {
            val c = code[i]
            when {
                // تعليق حتى نهاية السطر
                c == '#' -> {
                    while (i < n && code[i] != '\n') i++
                    sb.append(' ')
                }
                // سلسلة ثلاثية
                c == '"' || c == '\'' -> {
                    val triple = i + 2 < n && code[i + 1] == c && code[i + 2] == c
                    if (triple) {
                        i += 3
                        while (i + 2 < n && !(code[i] == c && code[i + 1] == c && code[i + 2] == c)) {
                            if (code[i] == '\n') sb.append('\n')
                            i++
                        }
                        i = (i + 3).coerceAtMost(n)
                        sb.append(' ')
                    } else {
                        i++
                        while (i < n && code[i] != c && code[i] != '\n') {
                            if (code[i] == '\\') i++
                            i++
                        }
                        if (i < n && code[i] == c) i++
                        sb.append(' ')
                    }
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }
}
