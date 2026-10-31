# بناء Mahdi-Editor

## المتطلبات

| الأداة | الإصدار المطلوب |
|---|---|
| JDK | 21 (Temurin أو OpenJDK) |
| Android SDK | Platform 37 + Build-Tools حديثة |
| Android NDK | 28+ (مطلوب لوحدات الطرفية وoniguruma) |
| CMake | 3.22.1 (يُنزّله Gradle تلقائيًا) |
| Gradle | يُدار عبر `./gradlew` (Wrapper مرفق) |
| Git | أي إصدار حديث |

أنشئ ملف `local.properties` في جذر المشروع:

```properties
sdk.dir=/path/to/Android/sdk
```

> ملاحظة: مشروع `soraX` **مضمّن داخل المستودع**، لذلك لا تحتاج إلى
> `git submodule update` ولا إلى اتصال بشبكة خاصة أثناء البناء.

## أوامر البناء

```bash
# نسخة تجريبية
./gradlew assembleCommunityDebug

# نسخة الإصدار
./gradlew assembleCommunityRelease

# كل المتغيّرات (يشمل benchmark)
./gradlew assemble

# تنسيق الشيفرة (نفس أسلوب المشروع)
./gradlew ktfmtFormat
```

مكان الناتج:

```
app/build/outputs/apk/community/debug/app-community-debug.apk
app/build/outputs/apk/community/release/app-community-release.apk
```

التثبيت:

```bash
adb install -r app/build/outputs/apk/community/debug/app-community-debug.apk
```

## التوقيع

أولوية البحث عن مادة التوقيع في `app/build.gradle.kts`:

1. متغيّر البيئة `MAHDI_SIGNING_PROPERTIES` (مسار ملف الخصائص).
2. `signing.properties` في جذر المستودع.
3. `app/testkey.keystore` (مفتاح اختبار مرفق) — يُستخدم تلقائيًا كحلٍّ أخير حتى تعمل نسخة
   الإصدار على جهازك.

### إنشاء مفتاح للنشر

```bash
keytool -genkeypair -v \
  -keystore mahdi-editor.jks \
  -alias mahdi \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Mahdi-Editor, OU=Android, O=Mahdi, L=Mosul, C=IQ"
```

ثم أنشئ `signing.properties`:

```properties
storeFile=/absolute/path/to/mahdi-editor.jks
storePassword=******
keyAlias=mahdi
keyPassword=******
```

> ⚠️ لا ترفع ملف المفتاح أو كلماته إلى Git. أضفهما إلى `.gitignore` واستخدم أسرار GitHub Actions
> (`KEYSTORE` و`PROP` بصيغة base64) عند البناء في CI.

### البناء في GitHub Actions

سير العمل `.github/workflows/android.yml` يبني `assembleCommunityRelease` تلقائيًا عند كل دفعة
إلى `main`، وعند كل طلب دمج، وعند كل وسم إصدار. يرفع الـAPK كـ Artifact، وينشر Release عند دفع
وسم يبدأ بـ `v` (مثل `v1.0.0`). إن لم تكن أسرار التوقيع مضبوطة، سيُوقّع APK بمفتاح الاختبار.

### إصدار نسخة جديدة

```bash
git tag v1.0.1 && git push origin v1.0.1
```

ينشر هذا الأمر إصدارًا جديدًا تلقائيًا. **اسم ملف الـAPK داخل كل إصدار ثابت دائمًا**
(`Mahdi-Editor.apk`)، ولذلك يعمل رابط التنزيل الدائم التالي مع كل إصدار جديد دون تغييره:

<https://github.com/m777m44/Mahdi-editor/releases/latest/download/Mahdi-Editor.apk>

وإلى جانبه نسخة باسم الإصدار (`Mahdi-Editor-v1.0.1.apk`) للأرشفة.

## حلّ المشكلات

| المشكلة | الحل |
|---|---|
| `SDK location not found` | أنشئ `local.properties` أو اضبط `ANDROID_HOME` |
| فشل بناء الـ NDK | ثبّت NDK 28 عبر SDK Manager ثم أعد المحاولة بعد `./gradlew clean` |
| `Unsupported class file major version` | تأكد أن Gradle يعمل على JDK 21 (`./gradlew -version`) |
| تعطّل عند بدء الطرفية | ثبّت مكوّن Terminal من داخل التطبيق (تنزيل Ubuntu rootfs يحتاج إنترنت) |
| بطء أول بناء | طبيعي: تُجمّع وحدات `soraX` الأصلية وPRoot لأول مرة |

## نصائح للأداء

```properties
# في ~/.gradle/gradle.properties
org.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g
org.gradle.parallel=true
org.gradle.caching=true
```
