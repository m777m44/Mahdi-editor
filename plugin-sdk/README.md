# حزمة تطوير الإضافات (Plugin SDK)

هذه الحزمة تُنتج `sdk.jar` يحتاجه مطوّرو إضافات Mahdi-Editor للبناء مقابل واجهة الإضافة.

## البناء

```bash
# من جذر المستودع، بعد بناء التطبيق مرة واحدة على الأقل
bash plugin-sdk/build-auto.sh
```

الناتج: `plugin-sdk/output/sdk.jar`.

## المحتوى

- `setup.sh` — يجمع ملفات `classes.jar` من وحدات المشروع إلى مجلد `libs/`.
- `build.gradle.kts` — يبني jar مظلّلًا (shadow jar) بالاسم `sdk.jar`.
- `gradle/libs.versions.toml` — إصدارات التبعيات.

## الاستخدام

أضف `sdk.jar` إلى `libs/` في مشروع الإضافة، ثم أعلن اعتمادك على الواجهة:

```kotlin
dependencies {
    compileOnly(files("libs/sdk.jar"))
}
```

راجع [docs/EXTENSIONS.md](../docs/EXTENSIONS.md) لصيغة `manifest.json` وواجهة `ExtensionAPI`.
