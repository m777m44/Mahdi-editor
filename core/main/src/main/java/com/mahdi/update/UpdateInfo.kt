package com.mahdi.update

import java.util.Locale

/**
 * بيانات تحديث متاح (5.0.0).
 *
 * تُبنى من رد GitHub Releases API مباشرة، فلا حاجة إلى أي خادم وسيط.
 */
data class UpdateInfo(
    /** رقم الإصدار الجديد بلا بادئة `v` (مثال: `5.0.0`). */
    val version: String,
    /** رقم الإصدار المثبَّت حاليًا على الجهاز. */
    val currentVersion: String,
    /** رابط تنزيل ملف APK من أصول الإصدار. */
    val downloadUrl: String,
    /** حجم الملف بالبايت (0 إن لم يُذكر). */
    val sizeBytes: Long,
    /** ملاحظات الإصدار كما كتبها الناشر (Markdown خام). */
    val notes: String,
    /** تاريخ النشر بصيغة ISO. */
    val publishedAt: String,
) {
    /** الحجم بالميغابايت برقم عشري واحد — للإنجليزية دائمًا حتى لا تختلط الفواصل. */
    val sizeText: String
        get() = if (sizeBytes <= 0) "—" else String.format(Locale.US, "%.1f", sizeBytes / (1024.0 * 1024.0))

    /** التاريخ بصيغة قصيرة `yyyy-MM-dd`. */
    val dateText: String
        get() = publishedAt.take(10).ifBlank { "—" }
}
