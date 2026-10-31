package com.mahdi.lang

/**
 * أوامر تثبيت المفسّرات/المترجمات داخل طرفية Ubuntu المدمجة.
 *
 * كل أمر مكتوب ليعمل في `proot` بدون تفاعل (`DEBIAN_FRONTEND=noninteractive`)،
 * ويُشغَّل من زرّ «تثبيت المفسّر» في متجر الإضافات أو من إعدادات المشغّلات.
 */
object LanguageToolchain {

    private val packages: Map<String, String> =
        mapOf(
            "python" to "python3 python3-pip",
            "javascript" to "nodejs npm",
            "typescript" to "nodejs npm",
            "java" to "default-jdk",
            "kotlin" to "default-jdk",
            "c" to "build-essential",
            "cpp" to "build-essential",
            "csharp" to "mono-complete",
            "rust" to "rustc cargo",
            "go" to "golang-go",
            "php" to "php-cli",
            "ruby" to "ruby",
            "lua" to "lua5.4",
            "perl" to "perl",
            "r" to "r-base",
            "swift" to "",
            "dart" to "",
            "zig" to "",
            "nim" to "",
            "haskell" to "ghc",
            "julia" to "julia",
            "ocaml" to "ocaml",
            "elixir" to "elixir",
            "erlang" to "erlang",
            "scala" to "scala",
            "groovy" to "groovy",
            "pascal" to "fpc",
            "tcl" to "tcl",
            "sql" to "sqlite3",
            "shell" to "bash",
            "powershell" to "",
            "asm" to "nasm",
            "bat" to "",
            "latex" to "texlive-latex-base",
            "cmake" to "cmake",
            "nix" to "",
            "html" to "",
            "css" to "",
            "scss" to "",
            "less" to "",
            "markdown" to "",
            "json" to "jq",
            "xml" to "libxml2-utils",
            "yaml" to "",
            "toml" to "",
            "ini" to "",
            "diff" to "",
            "txt" to "",
        )

    /** حزمة apt المطلوبة لهذه اللغة (فارغة إذا كانت لا تحتاج تثبيتًا). */
    fun packagesFor(id: String): String = packages[id].orEmpty()

    /** أمر التثبيت الكامل — يُمرَّر إلى `bash -lc`. */
    fun installCommandFor(id: String): String? {
        val pkgs = packagesFor(id)
        if (pkgs.isBlank()) return null
        return "export DEBIAN_FRONTEND=noninteractive; apt-get update -qq && apt-get install -y $pkgs"
    }

    /** هل تحتاج هذه اللغة تثبيتًا صريحًا؟ (المجهولة تحتاج تثبيتًا احتياطيًا) */
    fun needsInstall(id: String): Boolean = packages[id]?.isNotBlank() ?: true
}
