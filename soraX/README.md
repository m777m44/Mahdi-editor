## تقرير عن محرّك التحرير المضمّن (soraX)

`soraX` هو محرّك التحرير المستخدم في Mahdi-Editor. وهو مبني على مكتبة
[sora-editor](https://github.com/Rosemoe/sora-editor) للمطوّر Rosemoe، الموزّعة بموجب رخصة
**GNU LGPL v2.1** (انظر ملف `LICENSE` في هذا المجلد)، مع سلسلة LSP ومحوّل TextMate
ومحرّك oniguruma الأصلي مضافين إلى شجرة البناء نفسها.

> **ملاحظة قانونية:** هذا المجلد شيفرة خارجية (third-party) ولا يخضع لرخصة المشروع الأساسي.
> يبقى إشعار حقوق النشر والرخصة الأصلي كما هو، وأي تعديل عليه يجب أن يحافظ على رخصة LGPL v2.1.

### كيف يستهلك Mahdi-Editor هذا المحرّك

يعلن المشروع الرئيسي عن اعتماد على وحدات **soraX**
(`soraX:editor`، `soraX:editor-lsp`، `soraX:language-textmate`، `soraX:oniguruma-native`)
في كتالوج الإصدارات، ويستخدم في `settings.gradle.kts` بناءً مُضمَّنًا (included build) مع استبدال
الاعتماديات:

```kotlin
includeBuild("soraX") {
    dependencySubstitution {
        substitute(module("soraX:editor")).using(project(":editor"))
        substitute(module("soraX:editor-lsp")).using(project(":editor-lsp"))
        substitute(module("soraX:language-textmate")).using(project(":language-textmate"))
        substitute(module("soraX:oniguruma-native")).using(project(":oniguruma-native"))
    }
}
```

إحداثيات `soraX:*` ليست منشورة في أي مستودع؛ فهي مجرّد أسماء بديلة يُستبدلها Gradle بالمشروع
المقابل داخل مجلد `soraX/`. لذلك كل بناء لـ Mahdi-Editor يُجمّع هذه الوحدات مباشرةً من الشيفرة
الموجودة هنا — أي تعديل في `soraX/` يدخل في البناء فورًا دون أي خطوة إضافية.

تم تضمين الشيفرة داخل المستودع (vendored) حتى يعمل `./gradlew assembleCommunityRelease` بعد
استنساخ المشروع مباشرةً دون الحاجة إلى `git submodule update`.

### ملاحظات

- الوحدة التجريبية `app/` ومجلد الصور في المستودع الأصلي لم يتم ضمّهما لأنهما غير مطلوبين للبناء.
- لتحديث المحرّك: استبدل محتوى هذا المجلد بنسخة أحدث من sora-editor (أو من فرع soraX المناسب)
  ثم أعد البناء.
