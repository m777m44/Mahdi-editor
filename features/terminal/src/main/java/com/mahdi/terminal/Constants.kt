package com.mahdi.terminal

/**
 * مستودع تنزيل صور Ubuntu الجاهزة (rootfs).
 *
 * ملفات `ubuntu-base-24.04.3-*` الثنائية مضيفة خارج هذا المستودع، لذا يبقى الرابط كما هو: نحن لا نملك مرآة خاصة
 * لهذه الملفات، وتغيير الرابط إلى مستودعنا سيكسر ميزة تثبيت Ubuntu نهائيًا. هذا رابط تنزيل ثنائيات فقط
 * ولا يظهر في واجهة التطبيق (يُذكر في NOTICE وREADME ضمن المكوّنات الخارجية).
 * لاستخدام مرآتك الخاصة: بدّل القيمة أدناه في نسختك.
 */
private const val ROOTFS_BASE = "https://github.com/Xed-Editor/Karbon-PackagesX/releases/download/ubuntu"

const val ROOTFS_ARM = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-armhf.tar.gz"
const val ROOTFS_ARM64 = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-arm64.tar.gz"
const val ROOTFS_X64 = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-amd64.tar.gz"

/**
 * مسار صورة rootfs المضمّنة في حزمة الطرفية، ويُستخدم فقط في نسخة Google Play التي أُزيلت من هذا المستودع.
 *
 * نسخة `community` (وهي الوحيدة هنا) تنزّل [ROOTFS_ARM64] عند أول تشغيل بدلًا من تضمين ~29 ميغابايت في الـAPK.
 */
const val BUNDLED_ROOTFS_ASSET = "arm64-v8a/ubuntu.rootfs"

/** المعمارية الوحيدة التي كانت نسخة Google Play تضمّن لها صورة rootfs مضمّنة. */
const val BUNDLED_ROOTFS_ABI = "arm64-v8a"
