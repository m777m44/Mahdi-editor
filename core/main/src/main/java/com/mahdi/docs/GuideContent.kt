package com.mahdi.docs

/**
 * «دليل Mahdi-Editor» — التوثيق الكامل داخل التطبيق (2.8.0).
 *
 * كل ما يحتاجه المستخدم والمطوّر في مكان واحد، بلا إنترنت وبلا روابط خارجية:
 * البدء، المحرّر، الطرفية، Git، الثيمات، الحزم، الاستوديو، الخوادم، اللغات،
 * الترميزات، والخصوصية — ثم سطر الحقوق.
 *
 * المحتوى مكتوب لهذا المشروع بالكامل، وحقوقه `Mahdi-Editor-Code © 2026`.
 */
object GuideContent {

    fun markdown(isArabic: Boolean): String = if (isArabic) arabic() else english()

    private fun arabic(): String =
        """
        # دليل Mahdi-Editor

        محرّر أكواد عربي بالكامل، يعمل دون إنترنت، بلا إعلانات وبلا تتبّع وبلا حسابات.
        هذه الصفحة هي المرجع الكامل لكل ما في التطبيق.

        ## ١ · البدء السريع

        1. افتح مجلدًا من الشاشة الرئيسية (**فتح مجلد**)، أو أنشئ مشروعًا جديدًا (**مشروع جديد**).
        2. اضغط أي ملف من شجرة الملفات ليُفتح في تبويب جديد.
        3. زرّ **التشغيل ▶** يشغّل الملف مباشرة داخل طرفية Ubuntu المدمجة.
        4. زرّ **الطرفية** يفتح جلسة كاملة، ويمكن فتح أكثر من جلسة في الوقت نفسه.

        ## ٢ · المحرّر

        - **تلوين نحوي** لأكثر من خمسين لغة عبر قواعد TextMate.
        - **إكمال وتشخيص** عبر خوادم LSP المدمجة، مع انتقال إلى التعريف والمراجع وإعادة تسمية الرموز.
        - **طيّ الشيفرة**، أرقام الأسطر، المسافة البادئة التلقائية، والبحث والاستبدال داخل الملف.
        - **بطاقات التبويبات** مع مؤشّر تعديل غير محفوظ، وإغلاق سريع، واستعادة الجلسة عند إعادة الفتح.
        - **لوحة الأوامر** (من زرّ البحث) للوصول إلى كل أمر بكتابة اسمه.

        ## ٣ · الطرفية

        - طرفية Linux كاملة (Ubuntu) مع `apt` و`pip` و`npm`، تعمل داخل التطبيق دون روت.
        - **بطاقة التقدّم** أعلى الجلسة تُظهر اسم الحزمة ونسبة التثبيت.
        - **مخزن المكتبات الداخلي** في `~/.mahdi/cache`: أي مكتبة تُنزَّل مرة واحدة تُستخدم بعدها بلا إنترنت.
        - المفاتيح الإضافية (Ctrl، Esc، الأسهم) قابلة للتعديل من **الإعدادات ‹ الطرفية**.

        ## ٤ · Git

        - استنساخ المستودعات، إنشاء فرع، تبديل الفروع، سحب ودفع.
        - عرض التغييرات سطرًا بسطر مع تمييز الإضافة والحذف.
        - سجلّ الالتزامات برسم بياني للفروع، وتفاصيل كل التزام.
        - كل العمليات محلية؛ لا يُرسَل شيء إلى أي خدمة خارجية.

        ## ٥ · الثيمات

        أربعة ثيمات أساسية: **الفاتح**، **الداكن** (الافتراضي، كياني)، **المظلم**، **السمائي**.
        ويضاف إليها **أربعون ثيمًا مميزًا مزدوج الوضع** في المتجر:

        - كل ثيم يحمل لوحة فاتحة ولوحة داكنة حقيقيتين، وتُختار تلقائيًا حسب وضع النظام.
        - بضغطة واحدة يُثبَّت الثيم في «المخزون» ويُطبَّق على الواجهة والمحرّر والطرفية.
        - الثيمات المثبَّتة تبقى بعد إعادة التشغيل، وقابلة للحذف من شاشة الثيمات.

        ## ٦ · الاستوديو والحزم

        **الإعدادات ‹ استوديو الحزم** يجمع كل أدوات الصناعة:

        | القالب | المحتوى |
        |---|---|
        | قالب ثيم | `manifest.json` + `theme.json` بلوحتين |
        | قالب أيقونات | بيان + أيقونات SVG بترخيص MIT |
        | قالب إضافة | بيان + نقطة بداية Kotlin على `ExtensionAPI` + `build.gradle.kts` |

        وثلاث قدرات مساندة:

        1. **فحص الحزمة** قبل التثبيت: المعرّف، الإصدار، النوع، وجود الملفات، مسار كل أيقونة، الحجم.
        2. **استيراد `theme.json`** من أي محرّر آخر وتحويله إلى ثيم داخلي بلوحتين.
        3. **دليل الصيغة** الكامل مكتوب داخل الشاشة نفسها.

        ## ٧ · صيغ الحزم

        كل حزمة أرشيف ZIP بامتداد `.mahdi` أو `.xed` أو `.xedx`، وتحتوي على:

        ```text
        manifest.json   إلزامي (id, name, version, type, author, license)
        theme.json      لحزم الثيمات: light.baseColors و dark.baseColors
        icons/          لحزم الأيقونات: ملفات SVG
        src/ + APK      للإضافات: نقطة البداية والملف المبني
        ```

        الأنواع المدعومة: `theme` و`icon_pack` و`extension`.

        ## ٨ · خوادم اللغات

        HTML · CSS · JSON · TypeScript · Markdown · ESLint · Python (Pyright) · Bash · XML · Emmet.

        - التثبيت من **الإعدادات ‹ تصفح الإضافات ‹ خوادم ومشغّلات**.
        - كل خادم يعمل داخل بيئة التطبيق نفسها، ويُدار تشغيله وإيقافه من الشاشة نفسها.

        ## ٩ · اللغات والتشغيل

        - **٤٨ لغة** كلها قابلة للتشغيل المباشر، ولكل لغة زرّ تثبيت المفسّر.
        - الحالة حقيقية: تُقرأ من القرص (`~/.mahdi/installed` + `~/.mahdi/install-status`)، ولا تظهر
          شارة **«مثبت»** إلا بعد التحقّق من وجود المفسّر وتشغيله.
        - JavaScript وTypeScript تُثبَّت بتثبيت **Node**، وJava وKotlin بـ**JDK**، وC/C++ بـ**GCC** —
          بنفس شاشة «بيئات التشغيل» وبنسبة تقدّم حقيقية.
        - زرّ التشغيل يشتق امتداد الملف من سجلّ اللغات تلقائيًا، فلا حاجة لكتابة الأوامر.

        ### اختصارات التشغيل داخل الطرفية

        بدل `python3 script.py` اكتب:

        ```bash
        py script.py      # أو Py · pY · PY — كلها سواء
        js app.js         # أو Js · jS · JS
        ts app.ts         # ترجمة ثم تشغيل
        run file.any      # أي امتداد مدعوم
        ```

        - الشعار `MAHDI` يظهر في بداية كل تشغيل — للسكربتات الداخلية والخارجية على حد سواء.
        - المكتبات الناقصة تُثبَّت تلقائيًا؛ وإن كانت كلها موجودة فلا يُطبع أي سطر إضافي.

        ## ١٠ · الترميزات

        **٢٣ ترميزًا** بأسماء عربية، منها UTF-8/16/32 (LE وBE)، ASCII، ISO-8859-1، **ISO-8859-6** (العربية)،
        Windows-1256، ومنها ما يُكتشف تلقائيًا عند فتح الملف.

        ## ١١ · اللغة والاتجاه

        - الواجهة عربية بالكامل من اليمين إلى اليسار، مع إمكانية التبديل إلى الإنجليزية كاملة أيضًا.
        - العربية تُعرض موصولة ومرتّبة في كل مكان، بما في ذلك داخل الطرفية.
        - محاذاة المحرّر قابلة للتبديل بين RTL وLTR لكل ملف.

        ## ١٢ · الخصوصية

        - لا إعلانات، لا تتبّع، لا حسابات، ولا رفع لأي ملف.
        - العمليات الشبكية الوحيدة تحدث عند طلبك: تثبيت حزمة أو خادم، أو استنساخ مستودع.

        ---

        **Mahdi-Editor-Code © 2026 — جميع الحقوق محفوظة**
        """.trimIndent()

    private fun english(): String =
        """
        # Mahdi-Editor guide

        A fully Arabic, offline-first code editor with no ads, no tracking and no accounts.
        This page is the complete reference for everything in the app.

        ## 1 · Quick start

        1. Open a folder from the home screen (**Open folder**), or create a project (**New project**).
        2. Tap any file in the file tree to open it in a new tab.
        3. **Run ▶** executes the file directly inside the built-in Ubuntu terminal.
        4. **Terminal** opens a full shell session; several sessions can run side by side.

        ## 2 · Editor

        - **Syntax highlighting** for 50+ languages through TextMate grammars.
        - **Completion and diagnostics** from built-in LSP servers, with go-to-definition,
          find references and symbol rename.
        - Code folding, line numbers, auto-indent, and in-file find and replace.
        - Tab cards with an unsaved-changes indicator, quick close, and session restore.
        - **Command palette** for reaching every command by typing its name.

        ## 3 · Terminal

        - A complete Ubuntu Linux terminal with `apt`, `pip` and `npm`, no root required.
        - A progress card above the session shows the package name and install percentage.
        - The internal library cache at `~/.mahdi/cache` reuses every download offline.
        - Extra keys (Ctrl, Esc, arrows) are configurable from **Settings ‹ Terminal**.

        ## 4 · Git

        - Clone repositories, create and switch branches, pull and push.
        - Line-by-line diffs with added and removed line highlighting.
        - Commit history as a branch graph, plus per-commit details.
        - Everything runs locally; nothing is sent to an external service.

        ## 5 · Themes

        Four base themes: **Light**, **Dark** (default, Kiyani), **Dim** and **Sky**.
        On top of them, **40 dual-mode featured themes** in the store:

        - Each theme carries real light and dark palettes chosen automatically from the system mode.
        - One tap installs the theme into the inventory and applies it to the UI, editor and terminal.
        - Installed themes persist across restarts and can be removed from the theme screen.

        ## 6 · Studio and packages

        **Settings ‹ Package studio** bundles every authoring tool:

        | Template | Contents |
        |---|---|
        | Theme template | `manifest.json` + `theme.json` with both palettes |
        | Icon template | manifest + MIT-licensed SVG icons |
        | Extension template | manifest + Kotlin entry point on `ExtensionAPI` + `build.gradle.kts` |

        Three supporting capabilities:

        1. **Pack validation** before install: id, version, type, required files, every icon path, size.
        2. **`theme.json` import** from any other editor into a dual-palette internal theme.
        3. **The full format guide**, rendered inside the same screen.

        ## 7 · Package formats

        Every pack is a ZIP archive with a `.mahdi`, `.xed` or `.xedx` extension containing:

        ```text
        manifest.json   required (id, name, version, type, author, license)
        theme.json      theme packs: light.baseColors and dark.baseColors
        icons/          icon packs: SVG files
        src/ + APK      extensions: entry point and built APK
        ```

        Supported types: `theme`, `icon_pack` and `extension`.

        ## 8 · Language servers

        HTML · CSS · JSON · TypeScript · Markdown · ESLint · Python (Pyright) · Bash · XML · Emmet.

        - Install them from **Settings ‹ Browse packages ‹ Servers & runners**.
        - Each server runs inside the app sandbox and is managed from the same screen.

        ## 9 · Languages and running

        - **48 languages**, all directly runnable, each with its interpreter install button.
        - Installing writes a marker in `~/.mahdi/installed`, so the sky **Installed** badge
          survives restarts.
        - The run button derives the interpreter from the language registry automatically.

        ## 10 · Encodings

        **23 encodings** with Arabic names, including UTF-8/16/32 (LE and BE), ASCII, ISO-8859-1,
        **ISO-8859-6** (Arabic) and Windows-1256; unsupported files fall back to auto-detection.

        ## 11 · Language and direction

        - The whole UI is Arabic RTL by default, with a complete English alternative.
        - Arabic renders shaped and correctly ordered everywhere, terminals included.
        - Editor alignment toggles between RTL and LTR per file.

        ## 12 · Privacy

        - No ads, no tracking, no accounts, and no file uploads.
        - Network access happens only when you ask for it: installing a package or server,
          or cloning a repository.

        ---

        **Mahdi-Editor-Code © 2026 — All rights reserved**
        """.trimIndent()
}
