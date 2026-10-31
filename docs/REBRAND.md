# تقرير التعديلات — من المشروع الأصلي إلى Mahdi-Editor

<!--
هذا الملف يوثّق التعديلات على البرنامج الأصلي كما تقتضي رخصة GNU GPL v3.0 (§5).
مسار المشروع الأصلي مذكور هنا لأغراض الامتثال للترخيص والتوضيح التقني فقط، ولا يظهر في واجهة التطبيق.
-->

## الأساس

بدأ هذا المشروع من نسخة من محرر نصوص أندرويد مفتوح المصدر (المشار إليه في هذا الملف بـ«المشروع
الأصلي») صادر بموجب **GNU GPL v3.0**. لم تُنقل الشيفرة من فرع `main` ساعة أخذ النسخة فحسب، بل
أُجريت عليها التعديلات التالية، والإصدار الناتج يُسمّى **Mahdi-Editor** وهو غير رسمي وغير مرتبط
بالمشروع الأصلي.

- **سنة التعديل:** 2026
- **المسؤول عن التعديل:** Mahdi — <https://github.com/m777m44/Mahdi-editor>
- **الترخيص بعد التعديل:** GNU GPL v3.0 (كما هو، دون تغيير، مع الإبقاء على إشعار حقوق النشر الأصلي)

## 1) إعادة التسمية (Rebranding)

| العنصر | قبل | بعد |
|---|---|---|
| اسم التطبيق الظاهر | `Xed-Editor` | `Mahdi-Editor` |
| معرّف التطبيق (applicationId) | `com.rk.xededitor` | `com.mahdi.editor` |
| معرّف نسخة debug | `com.rk.xededitor.debug` | `com.mahdi.editor.debug` |
| اسم المشروع الجذري | `Xed-Editor` | `Mahdi-Editor` |
| namespace لتطبيق `app` | `com.rk.application` | `com.mahdi.application` |
| namespace لـ `core:main` | `com.rk.xededitor` | `com.mahdi.editor` |
| namespace لـ `core:components` | `org.robok.engine.core.components` | `com.mahdi.components` |
| حزم الكود | `com.rk.*` | `com.mahdi.*` |
| كلاس التطبيق | `XedApplication` | `MahdiApplication` |
| بادئات الفئات | `Xed*` (مثل `XedIcon`, `XedTheme`, `XedExtensionPoint`) | `Mahdi*` |
| وحدة الطرفية CLI | `features/terminal/xed-cli` (`com.rk.xed_cli`, ثنائي `xed`) | `features/terminal/mahdi-cli` (`com.mahdi.cli`, ثنائي `mahdi`) |
| الامتداد الخاص بالحزم | `.xed` | `.mahdi` |
| مجلد بيانات المشروع داخل المستخدم | `.xed/runner.sh` | `.mahdi/runner.sh` |

حزم `com.termux.*` لم تُعد تسميتها؛ لأنها شيفرة Termux الخارجية (Apache-2.0) وترتبط برموز JNI
أصلية (`Java_com_termux_...`) وإعادة تسميتها تكسر الطرفية دون فائدة تجارية.

## 2) الحقوق والروابط

- حُذفت كل روابط المطوّر الأصلي من الواجهة: موقع المشروع، Discord، حسابات التبرّع
  (GitHub Sponsors وBuy Me a Coffee وUPI)، ومتجر الإضافات.
- أُضيفت الروابط الرسمية الجديدة:
  - GitHub: <https://github.com/m777m44/Mahdi-editor>
  - Telegram: <https://t.me/HEROES2002>
- شاشة «حول» أُعيد بناؤها بالكامل بالعربية وتعرض الإصدار ومعرّف الحزمة وبصمة الالتزام والرخصة.
- فحص التحديثات صار يشير إلى مستودع `m777m44/Mahdi-editor` وإلى صفحة الإصدارات الجديدة.

## 3) التعريب

- `core/resources/src/main/res/values-ar/strings.xml` كُتب من الصفر ويغطي **كل** مفاتيح
  `values/strings.xml` (‏944 مدخلًا + 4 صيغ جمع)، بما فيها صيغ الجمع العربية الكاملة
  (zero/one/two/few/many/other).
- الترجمة الأصلية القديمة (418 مدخلًا) استُبدلت بنسخة مُراجعة ومصطلحات موحّدة.
- **حُذفت كل الترجمات غير العربية** (41 مجلد `values-*`: de, fr, zh, ru, …). هذا التطبيق
  عربي فقط، فلا معنى لبقاء لغات لا تُصان ولا تُراجع.
- المجلد الافتراضي `values/` صار **عربيًا** أيضًا، أي أن الجهاز الذي لم يُضبط على العربية
  يرى الواجهة العربية بدلًا من الإنجليزية. لذلك صار المولّد `GenerateSupportedLocales`
  في `core/main/build.gradle.kts` يعيد `"ar"` للمجلد الافتراضي، و`supported_locales.json`
  يحتوي `["ar"]` فقط، فشاشة اللغة تعرض العربية وحدها.
- وحدات `terminal-view` و`core/termux-shared` عُرّبت أيضًا (مجلد افتراضي + `values-ar`).
- لغة التطبيق الافتراضية صارت `ar` بدلًا من لغة الجهاز، وتُطبّق في `App.onCreate` قبل بناء الواجهة.
- `android:supportsRtl="true"` في AndroidManifest، والأيقونات الاتجاهية تستخدم `autoMirrored`.

## 4) المزايا المضافة

| الميزة | التنفيذ |
|---|---|
| 7 ثيمات عربية | `core/main/src/main/java/com/mahdi/theme/ArabicThemes.kt` |
| نمط ليلي تلقائي حسب الوقت | `THEME_MODE_AUTO_TIME` في `ThemeManager.kt` + خيار في شاشة الثيم |
| شاشة «حول» عربية جديدة | `settings/about/AboutScreen.kt` |
| صفحة دعم جديدة بلا منصّات تبرّع | `settings/support/Support.kt` |
| إزالة مطالبات التبرّع الدورية | `handleSupport()` أصبحت فارغة |
| تعطيل المتجر الإلكتروني مع رسالة عربية | `MAHDI_STORE_ENABLED = false` في `StoreScreen.kt` |
| توقيع تلقائي بمفتاح الاختبار عند غياب keystore | `app/build.gradle.kts` |

## 5) البنية والبناء

- حُذف توزيع Google Play (`flavor = playstore`, `applicationId = com.rk.axion`) ومعه واجهة
  `MahdiApplication` البديلة وملف rootfs المرفق (~29MB). بقي توزيع واحد: `community`.
- حُذفت ملفات baseline profile القديمة الخاصة بمتغيّرات لم تعد موجودة.
- `soraX` (محرّك التحرير، LGPL-2.1) صار **مضمّنًا** داخل المستودع بدل أن يكون submodule،
  حتى يعمل البناء بعد `git clone` مباشرة. أُزيلت وحدته التجريبية `app/` ومجلد الصور.
- أُعيدت كتابة سير عمل CI ليبني نسخة الإصدار ويرفعها كـ Artifact وينشر Release عند الوسم.
- حُذفت أدوات CI التي كانت تعتمد على خدمات المشروع الأصلي (Gemini، Weblate، تحديث submodule).
- `versionName = 1.0.0` و`versionCode = 1`.

## 6) ما لم يتغيّر

- ملف `LICENSE` (نص GNU GPL v3.0 + إشعار حقوق النشر الأصلي) — **غير معدّل**.
- رخصة `soraX` (LGPL-2.1) وإشعارات Rosemoe.
- شيفرة Termux وPRoot ورخصها.
- قواعد TextMate والخطوط.

## 7) ملاحظات

- معظم امتدادات المتجر الأصلية تحتاج إلى إعادة تجميع ضد حزمة `com.mahdi.*` لتعمل مع هذه النسخة،
  ولهذا عُطّل المتجر وتُرك التثبيت المحلي فقط.
- مورد خارجي واحد ما زال يُنزّل صورة Ubuntu للطرفية من مستودع إصدارات خارجي؛ لا يظهر في واجهة
  المستخدم، ويمكن إعادة استضافته بتعديل `ROOTFS_BASE` في
  `features/terminal/src/main/java/com/mahdi/terminal/Constants.kt`.
