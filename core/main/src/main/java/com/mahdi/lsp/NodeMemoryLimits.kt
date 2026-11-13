package com.mahdi.lsp

/**
 * حدود ذاكرة خوادم Node (9.1.0).
 *
 * ## لماذا؟
 * كل خادم لغة يعمل داخل `proot` منفصل. خوادم Node (Pyright · TypeScript · CSS ·
 * HTML · JSON · Markdown · Bash · ESLint · Emmet) تبدأ بحدّ V8 الافتراضي الذي
 * يمكن أن يبلغ gigabytes على هواتف ٦+ غيغابايت، لكنه على جهاز ٣–٤ غيغابايت
 * يدفع قاتل الذاكرة في أندرويد إلى إرسال SIGKILL — وهذا بالضبط ما ظهر في
 * اللقطة: `[Process completed (signal 9)]` مباشرة بعد تشغيل سكربت.
 *
 * ## القيم
 * - `--max-old-space-size=192` — كومة كافية لتحليل ملفات المشاريع العادية،
 *   ولا تسمح لـNode بأن يبتلع ذاكرة الجلسة كلها.
 * - `--max-semi-space-size=8` — جيل صغير للشباب، يقلّل فترات جمع القمامة.
 *
 * الخادم لا ينهار عند بلوغ الحدّ: V8 يجمع القمامة بجدّية أكبر ويُبطئ قليلًا،
 * وهو أفضل بكثير من قتل الجلسة كاملةً. يبقى للمستخدم أن يرفعه بنفسه عبر
 * `NODE_OPTIONS` في الطرفية إن أراد تحليل مشروع ضخم.
 */
object NodeMemoryLimits {
    const val MAX_OLD_SPACE_MB = 192
    const val MAX_SEMI_SPACE_MB = 8

    /** المتغيّرات التي تُمرَّر لعمليات Node داخل الحاوية. */
    val ENV: Map<String, String> =
        mapOf(
            "NODE_OPTIONS" to "--max-old-space-size=$MAX_OLD_SPACE_MB --max-semi-space-size=$MAX_SEMI_SPACE_MB",
            // لا تُنزّل Node ملفات تعريف أداء ولا تُرسل تقارير
            "NODE_NO_WARNINGS" to "1",
            "NODE_ENV" to "production",
        )
}
