# فنّ الأيقونات (6.1.0)

`masters/` يحوي نسخة مصغّرة (384×384 WebP) من كل عمل فني أصلي استُخدمت لتوليد
أيقونات `core/main/src/main/res/mipmap-*/ic_launcher_anime_*.png`.

- الأعمال الأصلية بدقّة 1024 لا تُحفظ في المستودع (حجمها ~19MB)؛ الموجود هنا
  نسخ معاينة خفيفة، والمصدر الحقيقي هو ملفات `mipmap` المولّدة نفسها.
- الأيقونات التكيّفية: `mipmap-anydpi-v26/ic_launcher_anime_<id>.xml` تشير إلى
  لون خلفية في `values/colors_app_icons.xml` وإلى طبقة أمامية `_fg`.
- عند إضافة شخصية جديدة: صورة 1024 + طبقة أمامية + لون + adaptive-icon + alias
  في `app/src/main/AndroidManifest.xml` + سطر في `AppIconSwitcher`.
