package com.mahdi.runtime

/**
 * حالة تثبيت حقيقية لأي بيئة تشغيل (3.0.0).
 *
 * لا تُعرَض «مثبت» إلا بعد أن يتحقّق التطبيق من وجود الملفّ التنفيذي داخل الـ sandbox
 * أو من وجود علامة نجاح كتبها أمر التثبيت. أي حالة أخرى لها تمثيل واضح في الواجهة،
 * بما في ذلك الفشل مع سببه وإمكانية إعادة المحاولة.
 */
sealed interface InstallState {
    /** غير مثبَّتة بعد. */
    data object NotInstalled : InstallState

    /** في قائمة الانتظار (قبل بدء الأمر). */
    data object Queued : InstallState

    /** جارٍ التنزيل/التثبيت بنسبة من 0 إلى 100. */
    data class Downloading(val progress: Int) : InstallState

    /** جارٍ فك الضغط أو إعداد الأذونات. */
    data object Extracting : InstallState

    /** مثبَّتة ومتحقَّق منها فعليًا. */
    data class Installed(val version: String? = null) : InstallState

    /** مضمّنة داخل التطبيق أصلًا (لا تحتاج تثبيتًا). */
    data object Bundled : InstallState

    /** فشل التثبيت بسبب رسالة من الطرفية. */
    data class Failed(val error: String) : InstallState
}

/** وصف بيئة تشغيل واحدة وحالتها الحالية. */
data class RuntimeStatus(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val state: InstallState,
    val version: String? = null,
    val sizeMb: Double = 0.0,
    val packages: String = "",
) {
    fun name(isArabic: Boolean): String = if (isArabic) nameAr else nameEn

    /** هل الحالة نهائية وناجحة؟ */
    val isReady: Boolean
        get() = state is InstallState.Installed || state is InstallState.Bundled

    /** هل يمكن للمستخدم الضغط على «تثبيت» الآن؟ */
    val canInstall: Boolean
        get() = state is InstallState.NotInstalled || state is InstallState.Failed
}

/**
 * بيئة تشغيل معروفة: ما يجب أن يوجد على القرص، وما يُثبَّت من الحزم، وحجمها التقريبي.
 *
 * @property binaries أسماء الملفّات التنفيذية التي تُثبت أن البيئة موجودة فعلًا
 *                   (تُفحص داخل `sandboxDir()/usr/bin`).
 * @property packages حزم apt المطلوبة (فارغة يعني جاهزة داخل الصورة).
 * @property postInstall أوامر إضافية تُنفَّذ بعد apt (مثل `pip install`).
 */
data class RuntimeSpec(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val binaries: List<String>,
    val packages: String = "",
    val postInstall: String = "",
    val sizeMb: Double = 0.0,
    val versionCommand: String = "",
)

/** كتالوج بيئات التشغيل المدعومة في Mahdi-Editor. */
object RuntimeCatalog {

    val all: List<RuntimeSpec> =
        listOf(
            RuntimeSpec(
                    id = "python",
                    nameAr = "بايثون ٣",
                    nameEn = "Python 3",
                    binaries = listOf("python3", "pip3"),
                    packages = "python3 python3-pip python3-venv",
                    postInstall = "python3 -m pip install --upgrade --quiet pip setuptools wheel",
                    sizeMb = 25.0,
                    versionCommand = "python3 --version",
                )
                .copy(),
            RuntimeSpec(
                id = "node",
                nameAr = "نود دوت جي إس",
                nameEn = "Node.js",
                binaries = listOf("node", "npm"),
                packages = "nodejs npm",
                postInstall = "",
                sizeMb = 20.0,
                versionCommand = "node --version",
            ),
            RuntimeSpec(
                id = "php",
                nameAr = "بي إتش بي",
                nameEn = "PHP",
                binaries = listOf("php"),
                packages = "php-cli php-mbstring php-curl",
                sizeMb = 15.0,
                versionCommand = "php --version",
            ),
            RuntimeSpec(
                id = "ruby",
                nameAr = "روبي",
                nameEn = "Ruby",
                binaries = listOf("ruby", "gem"),
                packages = "ruby-full",
                sizeMb = 8.0,
                versionCommand = "ruby --version",
            ),
            RuntimeSpec(
                id = "lua",
                nameAr = "لوا",
                nameEn = "Lua",
                binaries = listOf("lua5.4", "lua"),
                packages = "lua5.4 luarocks",
                sizeMb = 2.0,
                versionCommand = "lua5.4 -v",
            ),
            RuntimeSpec(
                id = "git",
                nameAr = "جيت",
                nameEn = "Git",
                binaries = listOf("git"),
                packages = "git",
                sizeMb = 10.0,
                versionCommand = "git --version",
            ),
            RuntimeSpec(
                id = "java",
                nameAr = "جافا",
                nameEn = "Java (JDK)",
                binaries = listOf("javac", "java"),
                packages = "default-jdk-headless",
                sizeMb = 60.0,
                versionCommand = "javac -version",
            ),
            RuntimeSpec(
                id = "gcc",
                nameAr = "سي / سي++",
                nameEn = "C / C++ (GCC)",
                binaries = listOf("gcc", "g++"),
                packages = "build-essential",
                sizeMb = 45.0,
                versionCommand = "gcc --version",
            ),
            RuntimeSpec(
                id = "go",
                nameAr = "غو",
                nameEn = "Go",
                binaries = listOf("go"),
                packages = "golang-go",
                sizeMb = 80.0,
                versionCommand = "go version",
            ),
            RuntimeSpec(
                id = "rust",
                nameAr = "رست",
                nameEn = "Rust",
                binaries = listOf("rustc", "cargo"),
                packages = "rustc cargo",
                sizeMb = 90.0,
                versionCommand = "rustc --version",
            ),
            RuntimeSpec(
                id = "perl",
                nameAr = "بيرل",
                nameEn = "Perl",
                binaries = listOf("perl"),
                packages = "perl",
                sizeMb = 6.0,
                versionCommand = "perl -v",
            ),
            RuntimeSpec(
                id = "sqlite",
                nameAr = "سكلايت",
                nameEn = "SQLite",
                binaries = listOf("sqlite3"),
                packages = "sqlite3",
                sizeMb = 2.0,
                versionCommand = "sqlite3 --version",
            ),
        )

    fun byId(id: String): RuntimeSpec? = all.firstOrNull { it.id == id }

    /**
     * خريطة «لغة المحرّر ← بيئة التشغيل التي تُشغّلها».
     *
     * JavaScript وTypeScript تحتاجان Node، وJava وKotlin تحتاجان JDK، وC/C++ تحتاجان GCC…
     * فبدل تثبيت حزم ناقصة عبر أوامر خام، تُثبَّت البيئة الصحيحة ويُقرأ تقدّمها الحقيقي.
     */
    private val languageToRuntime =
        mapOf(
            "python" to "python",
            "javascript" to "node",
            "typescript" to "node",
            "node" to "node",
            "php" to "php",
            "ruby" to "ruby",
            "lua" to "lua",
            "java" to "java",
            "kotlin" to "java",
            "c" to "gcc",
            "cpp" to "gcc",
            "go" to "go",
            "rust" to "rust",
            "perl" to "perl",
            "sql" to "sqlite",
        )

    /** بيئة التشغيل التي تُشغّل لغة المحرّر (أو null إن كانت لا تحتاج تثبيتًا). */
    fun forLanguage(languageId: String): RuntimeSpec? = languageToRuntime[languageId]?.let { byId(it) }
}
