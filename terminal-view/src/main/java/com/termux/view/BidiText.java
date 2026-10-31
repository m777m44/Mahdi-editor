package com.termux.view;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * عرض النصوص ذات الاتجاه من اليمين إلى اليسار (العربية/العبرية) داخل الطرفية.
 *
 * الطرفية تتعامل مع الشاشة كشبكة خانات ثابتة العرض، ولذلك كانت الحروف العربية تُرسم
 * من اليسار لليمين وبشكل منفصل: «ةيبرعلا» بدل «العربية».
 *
 * هذه الفئة تُقسّم النص إلى مقاطع حسب اتجاهه، ثم ترسم كل مقطع بالاتجاه الصحيح مع
 * تفعيل تشكيل الحروف (HarfBuzz) داخل Android، فيظهر النص متّصلًا ومرتّبًا.
 */
final class BidiText {

    private BidiText() {}

    private static final int DIR_NEUTRAL = -1;
    private static final int DIR_LTR = 0;
    private static final int DIR_RTL = 1;

    private static final int MAX_SEGMENTS = 64;

    /** هل يحتوي النص على أي حرف عربي/عبري؟ */
    static boolean hasRtl(char[] text, int start, int count) {
        final int end = start + count;
        for (int i = start; i < end; i++) {
            if (isRtlChar(text[i])) return true;
        }
        return false;
    }

    private static boolean isRtlChar(char c) {
        return (c >= 0x0590 && c <= 0x05FF) // العبرية
                || (c >= 0x0600 && c <= 0x06FF) // العربية
                || (c >= 0x0750 && c <= 0x077F) // العربية الإضافية
                || (c >= 0x08A0 && c <= 0x08FF) // العربية الممتدة-أ
                || (c >= 0xFB50 && c <= 0xFDFF) // صور العرض العربية-أ
                || (c >= 0xFE70 && c <= 0xFEFF) // صور العرض العربية-ب
                || (c >= 0x10800 && c <= 0x10FFF); // كتابات تاريخية (نادرة)
    }

    private static boolean isLtrChar(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= 0x00C0 && c <= 0x024F) // حروف لاتينية ممتدة
                || (c >= 0x0370 && c <= 0x04FF) // يونانية/سيريلية
                || (c >= '0' && c <= '9');
    }

    /**
     * يرسم النص بترتيب بصري صحيح. يُستخدم فقط عندما يحتوي النص على حروف RTL،
     * وإلا تبقى الطريقة القديمة (أسرع وأدقّ لشبكة الطرفية).
     */
    static void draw(Canvas canvas, Paint paint, char[] text, int start, int count, float x, float y) {
        if (count <= 0) return;

        int[] segStart = new int[MAX_SEGMENTS];
        int[] segLength = new int[MAX_SEGMENTS];
        boolean[] segRtl = new boolean[MAX_SEGMENTS];
        int segments = 0;

        // تجاهل فراغات نهاية السطر حتى لا تزحزح النص العربي
        int end = start + count;
        while (end > start && text[end - 1] == ' ') end--;
        if (end <= start) return;

        int currentDir = DIR_NEUTRAL;
        int currentStart = start;
        int baseDir = DIR_NEUTRAL;

        for (int i = start; i < end; i++) {
            final char c = text[i];
            final int dir;
            if (isRtlChar(c)) dir = DIR_RTL;
            else if (isLtrChar(c)) dir = DIR_LTR;
            else dir = DIR_NEUTRAL;

            if (dir == DIR_NEUTRAL) continue; // المسافات وعلامات الترقيم تبقى مع المقطع الحالي

            if (baseDir == DIR_NEUTRAL) baseDir = dir;

            if (currentDir == DIR_NEUTRAL) {
                currentDir = dir;
                currentStart = i;
            } else if (dir != currentDir) {
                if (segments >= MAX_SEGMENTS) break;
                segStart[segments] = currentStart;
                segLength[segments] = i - currentStart;
                segRtl[segments] = currentDir == DIR_RTL;
                segments++;
                currentStart = i;
                currentDir = dir;
            }
        }

        if (segments < MAX_SEGMENTS && currentDir != DIR_NEUTRAL) {
            segStart[segments] = currentStart;
            segLength[segments] = end - currentStart;
            segRtl[segments] = currentDir == DIR_RTL;
            segments++;
        }

        if (segments <= 0) {
            canvas.drawTextRun(text, start, count, start, count, x, y, false, paint);
            return;
        }

        // المسافات البادئة تُرسم كجزء من أول مقطع حتى لا ينزاح السطر (للاتجاه LTR فقط)
        if (baseDir == DIR_LTR && segStart[0] > start) {
            final int pad = segStart[0] - start;
            segStart[0] = start;
            segLength[0] += pad;
        }

        float cursorX = x;
        if (baseDir == DIR_RTL) {
            for (int s = segments - 1; s >= 0; s--) {
                final float width = paint.measureText(text, segStart[s], segLength[s]);
                canvas.drawTextRun(
                        text, segStart[s], segLength[s], segStart[s], segLength[s],
                        cursorX, y, segRtl[s], paint);
                cursorX += width;
            }
        } else {
            for (int s = 0; s < segments; s++) {
                final float width = paint.measureText(text, segStart[s], segLength[s]);
                canvas.drawTextRun(
                        text, segStart[s], segLength[s], segStart[s], segLength[s],
                        cursorX, y, segRtl[s], paint);
                cursorX += width;
            }
        }
    }
}
