package com.mahdi.lang

/**
 * لغة برمجية مدعومة في المحرر.
 *
 * @property id معرّف ثابت
 * @property nameAr الاسم العربي
 * @property nameEn الاسم الإنجليزي
 * @property extensions الامتدادات التي تُفتح بهذه اللغة
 * @property scope نطاق TextMate الفعلي المحمّل من `assets/textmate/languages.json` (null يعني تلوينًا عامًا)
 * @property runnable هل يمكن تشغيل ملفها مباشرة من التطبيق عبر الطرفية (كل اللغات قابلة للتشغيل في 2.5.0)
 */
data class CodeLanguage(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val extensions: List<String>,
    val scope: String?,
    val runnable: Boolean = true,
)

/**
 * سجلّ اللغات المدعومة: أكثر من 30 لغة.
 *
 * التلوين النحوي يعمل فعليًا لأن القواعد موجودة في `assets/textmate/` ويعالجها محرّك soraX؛
 * والتشغيل المباشر يتم داخل طرفية Ubuntu المدمجة عبر `universal_runner.sh` الذي يثبّت المفسّر
 * تلقائيًا عند أول استخدام.
 */
object LanguageRegistry {

    val all: List<CodeLanguage> =
        listOf(
            CodeLanguage("python", "بايثون", "Python", listOf("py", "pyw"), "source.python"),
            CodeLanguage("javascript", "جافاسكربت", "JavaScript", listOf("js", "mjs", "cjs"), "source.js"),
            CodeLanguage("typescript", "تايب سكربت", "TypeScript", listOf("ts", "tsx"), "source.ts"),
            CodeLanguage("php", "بي إتش بي", "PHP", listOf("php"), "source.php"),
            CodeLanguage("html", "HTML", "HTML", listOf("html", "htm", "xhtml"), "text.html.basic"),
            CodeLanguage("css", "CSS", "CSS", listOf("css"), "source.css"),
            CodeLanguage("scss", "SCSS", "SCSS", listOf("scss"), "source.css.scss"),
            CodeLanguage("less", "Less", "Less", listOf("less"), "source.css.less"),
            CodeLanguage("c", "سي", "C", listOf("c"), "source.c"),
            CodeLanguage("cpp", "سي بلس بلس", "C++", listOf("cpp", "cc", "cxx"), "source.cpp"),
            CodeLanguage("csharp", "سي شارب", "C#", listOf("cs"), "source.cs"),
            CodeLanguage("java", "جافا", "Java", listOf("java"), "source.java"),
            CodeLanguage("kotlin", "كوتلن", "Kotlin", listOf("kt", "kts"), "source.kotlin"),
            CodeLanguage("swift", "سويفت", "Swift", listOf("swift"), "source.swift"),
            CodeLanguage("go", "غو", "Go", listOf("go"), "source.go"),
            CodeLanguage("rust", "رست", "Rust", listOf("rs"), "source.rust"),
            CodeLanguage("ruby", "روبي", "Ruby", listOf("rb"), "source.ruby"),
            CodeLanguage("perl", "بيرل", "Perl", listOf("pl", "pm"), null),
            CodeLanguage("lua", "لوا", "Lua", listOf("lua"), "source.lua"),
            CodeLanguage("r", "آر", "R", listOf("r", "R"), "source.r"),
            CodeLanguage("dart", "دارت", "Dart", listOf("dart"), "source.dart"),
            CodeLanguage("scala", "سكالا", "Scala", listOf("scala"), null),
            CodeLanguage("groovy", "غروفي", "Groovy", listOf("groovy"), "source.groovy"),
            CodeLanguage("haskell", "هاسكل", "Haskell", listOf("hs"), null),
            CodeLanguage("julia", "جوليا", "Julia", listOf("jl"), null),
            CodeLanguage("ocaml", "أوكامل", "OCaml", listOf("ml", "mli"), null),
            CodeLanguage("elixir", "إليكسير", "Elixir", listOf("ex", "exs"), null),
            CodeLanguage("erlang", "إرلانغ", "Erlang", listOf("erl"), null),
            CodeLanguage("shell", "شل", "Shell", listOf("sh", "bash", "zsh", "fish"), "source.shell"),
            CodeLanguage("powershell", "باور شل", "PowerShell", listOf("ps1"), "source.powershell"),
            CodeLanguage("sql", "SQL", "SQL", listOf("sql"), "source.sql"),
            CodeLanguage("json", "JSON", "JSON", listOf("json"), "source.json"),
            CodeLanguage("xml", "XML", "XML", listOf("xml", "svg"), "text.xml"),
            CodeLanguage("yaml", "YAML", "YAML", listOf("yaml", "yml"), "source.yaml"),
            CodeLanguage("markdown", "مارك داون", "Markdown", listOf("md", "markdown"), "text.html.markdown"),
            CodeLanguage("asm", "لغة التجميع", "Assembly", listOf("asm", "s"), "source.asm"),
            CodeLanguage("bat", "سكربت ويندوز", "Batch", listOf("bat", "cmd"), "source.batchfile"),
            CodeLanguage("toml", "TOML", "TOML", listOf("toml"), "source.toml"),
            CodeLanguage("ini", "INI", "INI", listOf("ini", "cfg", "conf"), "source.ini"),
            CodeLanguage("cmake", "CMake", "CMake", listOf("cmake"), "source.cmake"),
            CodeLanguage("pascal", "باسكال", "Pascal", listOf("pas"), "source.pascal"),
            CodeLanguage("tcl", "تيكل", "Tcl", listOf("tcl"), null),
            CodeLanguage("nix", "نيكس", "Nix", listOf("nix"), "source.nix"),
            CodeLanguage("zig", "زيغ", "Zig", listOf("zig"), "source.zig"),
            CodeLanguage("nim", "نيم", "Nim", listOf("nim"), "source.nim"),
            CodeLanguage("latex", "لاتخ", "LaTeX", listOf("tex"), "text.tex.latex"),
            CodeLanguage("diff", "فرق الملفات", "Diff", listOf("diff", "patch"), "source.diff"),
            CodeLanguage("txt", "نص عادي", "Plain text", listOf("txt", "log"), null, runnable = true),
        )

    /** عدد اللغات التي تعمل في المحرر. */
    val supportedCount: Int
        get() = all.size

    /** عدد اللغات القابلة للتشغيل المباشر. */
    val runnableCount: Int
        get() = all.count { it.runnable }

    private val byExtension: Map<String, CodeLanguage> =
        buildMap {
            all.forEach { lang -> lang.extensions.forEach { ext -> putIfAbsent(ext.lowercase(), lang) } }
        }

    /** يجد اللغة من اسم الملف (بالامتداد). */
    fun forFileName(fileName: String): CodeLanguage? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        // بعض الملفات تُكتب بامتداد بحالة مختلفة (R)
        return byExtension[ext] ?: byExtension[fileName.substringAfterLast('.')]
    }

    /** اللغات القابلة للتشغيل — كل اللغات في 2.5.0. */
    val runnable: List<CodeLanguage>
        get() = all.filter { it.runnable }
}
