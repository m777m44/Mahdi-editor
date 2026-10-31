# الإضافات والثيمات وحزم الأيقونات

> **تنبيه مهم:** بعد إعادة التسمية (‏`com.rk.*` ← `com.mahdi.*`) لم تعد الإضافات المبنية للمشروع
> الأصلي تعمل مع Mahdi-Editor؛ يجب إعادة تجميعها مقابل حزمة `com.mahdi.extension.api`.
> ولأن المتجر الإلكتروني معطّل في هذه النسخة، يُثبَّت كل شيء من ملف محلي عبر
> **الإعدادات ‹ الإضافات ‹ التثبيت من التخزين**.

## صيغة الحزمة

الحزمة ملف ZIP بامتداد `.mahdi` (أو `.zip`) يحتوي في جذره:

```
manifest.json
classes.dex        (للإضافات فقط)
icon.png           (اختياري)
```

### `manifest.json` للإضافة

```json
{
  "id": "com.example.my-extension",
  "name": "اسم الإضافة",
  "description": "وصف قصير",
  "version": "1.0.0",
  "author": { "displayName": "اسم المطوّر", "github": "https://github.com/..." },
  "mainClass": "com.example.MyExtension",
  "repository": "https://github.com/example/my-extension",
  "minAppVersion": 1,
  "license": "GPL-3.0",
  "localization": ["ar"],
  "dependencies": [],
  "recommendations": [],
  "tags": [],
  "hasSettings": false
}
```

الحقول الاختيارية: `description`, `author`, `minAppVersion`, `supportedArchitectures`,
`license`, `localization`, `dependencies`, `recommendations`, `tags`, `hasSettings`.
الحقول الإلزامية: `id`, `name`, `mainClass`, `repository`.

### `manifest.json` للثيم

```json
{
  "id": "com.example.my-theme",
  "name": "اسم الثيم",
  "author": { "displayName": "اسم المطوّر" },
  "version": "1.0.0",
  "minAppVersion": 1,
  "inheritBase": true,
  "dark": {
    "baseColors": {
      "primary": "#E3B341",
      "onPrimary": "#14110B",
      "background": "#0F0D09",
      "onBackground": "#F8F4EA",
      "surface": "#17130C",
      "onSurface": "#F8F4EA"
    },
    "terminalColors": { "background": "#0F0D09", "foreground": "#F8F4EA" }
  },
  "light": {
    "baseColors": {
      "primary": "#8A5A00",
      "background": "#FAF5EA",
      "onBackground": "#1A1610"
    }
  }
}
```

> الثيمات القديمة بصيغة ملف JSON أحادي لم تعد مدعومة (راجع رسالة `theme_format_error`).

### `manifest.json` لحزمة أيقونات

```json
{
  "id": "com.example.iconpack",
  "name": "اسم الحزمة",
  "author": { "displayName": "المطوّر" },
  "version": "1.0.0",
  "minAppVersion": 1
}
```

مع مجلد `icons/` يحوي ملفات PNG بأسماء الأيقونات (مثل `folder.png`, `file.png`, `kt.png`).

## بيئة تطوير الإضافات

- حزمة SDK: مجلد `plugin-sdk/` في المستودع، وتُبنى بـ`bash plugin-sdk/build-auto.sh` لإنتاج
  `plugin-sdk/output/sdk.jar`.
- واجهة الإضافة: `com.mahdi.extension.ExtensionAPI` و`ExtensionContext` في
  `features/extensions/src/main/java/com/mahdi/extension/api/`.
- تعليمة `@MahdiExtensionPoint` تُستخدم لوسم واجهات API المستقرة التي يُنصح مطوّرو الإضافات باستخدامها
  (`core/main/src/main/java/com/mahdi/extension/api/MahdiExtensionPoint.kt`).

```kotlin
package com.example

import com.mahdi.extension.ExtensionAPI
import com.mahdi.extension.ExtensionContext

class MyExtension(context: ExtensionContext) : ExtensionAPI(context) {
    override fun onLoad() {
        // تُنفَّذ عند كل تحميل للإضافة (بدء التطبيق أو التثبيت)
    }

    override fun onInstalled() {
        // تُنفَّذ مرة واحدة عند أول تثبيت
    }

    override fun onDispose() {
        // تُنفَّذ عند إلغاء تحميل الإضافة (إزالة أو تحديث)
    }
}
```

يجب أن يكون للفئة **مُنشئ عام يقبل `ExtensionContext`**، وأن ترث `ExtensionAPI`.

## الأمان

يمكن للإضافات تنفيذ شيفرة عشوائية والوصول إلى ملفاتك. لا تثبّت إضافة إلا إذا كنت تثق بمطوّرها أو
راجعت شيفرتها المصدرية. التطبيق يُظهر تحذيرًا قبل تثبيت أي إضافة من الخارج.
